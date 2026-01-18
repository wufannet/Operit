package com.ai.assistance.showerclient // Package declaration for ShowerController class

import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import android.util.Log
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.IShowerVideoSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** // Start of class documentation comment
 * Lightweight controller to talk to the Shower server running locally on the device. // Description of class purpose
 *
 * Responsibilities: // List of responsibilities of this class
 * - Maintain a Binder connection to the Shower service // Manage binder connection
 * - Send commands for a specific virtual display: ensureDisplay, launchApp, tap, swipe, touch, key, screenshot // Command sending
 * - Track the virtual display id and video size for this session // State tracking
 */
class ShowerController { // Declaration of ShowerController class

    companion object { // Companion object to hold static members
        private const val TAG = "ShowerController" // Log tag for this class

        @Volatile // Volatile annotation for thread-safe access
        private var binderService: IShowerService? = null // Cached binder service reference

        private suspend fun getBinder(context: Context? = null): IShowerService? = withContext(Dispatchers.IO) { // Suspend function to get binder service
            if (binderService?.asBinder()?.isBinderAlive == true) { // Check if cached binder is alive
                return@withContext binderService // Return cached binder if alive
            }

            fun clearDeadService() { // Local function to clear dead service references
                binderService = null // Clear cached service
                ShowerBinderRegistry.setService(null) // Clear registry service
            }

            val maxAttempts = if (context != null) 2 else 1 // Determine max attempts based on context presence
            var attempt = 0 // Initialize attempt counter
            while (attempt < maxAttempts) { // Loop for connection attempts
                attempt++ // Increment attempt count
                try { // Try block for connection attempt
                    val cachedService = ShowerBinderRegistry.getService() // Get cached service from registry
                    val binder = cachedService?.asBinder() // Get binder from cached service
                    val alive = binder?.isBinderAlive == true // Check if binder is alive
                    Log.d(TAG, "getBinder: attempt=$attempt cachedService=$cachedService binder=$binder alive=$alive") // Log attempt details
                    if (cachedService != null && alive) { // If cached service is valid and alive
                        binderService = cachedService // Cache the service locally
                        Log.d(TAG, "Connected to Shower Binder service on attempt=$attempt") // Log successful connection
                        return@withContext binderService // Return the service
                    } else { // If service is null or dead
                        Log.w(TAG, "No alive Shower Binder cached in ShowerBinderRegistry on attempt=$attempt") // Log warning
                        clearDeadService() // Clear dead service references
                    }
                } catch (e: Exception) { // Catch any exception during connection
                    Log.e(TAG, "Failed to connect to Binder service on attempt=$attempt", e) // Log error
                    clearDeadService() // Clear dead service references
                }

                if (context != null && attempt == 1) { // If context is provided and first attempt failed
                    try { // Try to restart Shower server
                        val ctx = context.applicationContext // Get application context
                        Log.d(TAG, "getBinder: attempting to restart Shower server after connection failure") // Log restart attempt
                        val ok = ShowerServerManager.ensureServerStarted(ctx) // Attempt to start server
                        if (!ok) { // If failed to start
                            Log.e(TAG, "getBinder: failed to restart Shower server") // Log failure
                            break // Exit loop
                        }
                        // Wait a bit for broadcast to propagate
                        delay(200) // Delay to allow server start propagation
                    } catch (e: Exception) { // Catch exceptions during restart
                        Log.e(TAG, "getBinder: exception while restarting Shower server", e) // Log exception
                        break // Exit loop
                    }
                }
            }
            null // Return null if all attempts fail
        }
    }

    @Volatile // Volatile annotation for thread-safe access
    private var virtualDisplayId: Int? = null // Current virtual display ID

    fun getDisplayId(): Int? = virtualDisplayId // Getter for virtual display ID

    @Volatile // Volatile annotation for thread-safe access
    private var videoWidth: Int = 0 // Current video width

    @Volatile // Volatile annotation for thread-safe access
    private var videoHeight: Int = 0 // Current video height

    fun getVideoSize(): Pair<Int, Int>? = // Getter for video size as pair or null
        if (videoWidth > 0 && videoHeight > 0) Pair(videoWidth, videoHeight) else null // Return size if valid

    private val binaryLock = Any() // Lock object for synchronizing binary frame access
    private val earlyBinaryFrames = ArrayDeque<ByteArray>() // Buffer for early video frames before handler is set

    @Volatile // Volatile annotation for thread-safe access
    private var binaryHandler: ((ByteArray) -> Unit)? = null // Current binary frame handler callback

    fun setBinaryHandler(handler: ((ByteArray) -> Unit)?) { // Setter for binary frame handler
        val framesToReplay: List<ByteArray> // List to hold buffered frames to replay
        synchronized(binaryLock) { // Synchronize access to binary frames
            binaryHandler = handler // Set the handler
            Log.d(TAG, "setBinaryHandler: id=${virtualDisplayId} handlerSet=${handler != null}, bufferedFrames=${earlyBinaryFrames.size}") // Log handler set info
            framesToReplay = if (handler != null && earlyBinaryFrames.isNotEmpty()) { // If handler set and buffered frames exist
                val list = earlyBinaryFrames.toList() // Copy buffered frames to list
                earlyBinaryFrames.clear() // Clear buffer
                list // Assign list to replay variable
            } else { // No frames to replay
                emptyList() // Empty list
            }
        }
        if (handler != null && framesToReplay.isNotEmpty()) { // If handler set and frames to replay exist
            Log.d(TAG, "setBinaryHandler: replaying ${framesToReplay.size} buffered frames") // Log replaying frames
            framesToReplay.forEach { frame -> // Iterate over buffered frames
                try { // Try to invoke handler on each frame
                    handler(frame) // Invoke handler
                } catch (_: Exception) { // Ignore exceptions during replay
                }
            }
        }
    }

    private val videoSink = object : IShowerVideoSink.Stub() { // Implementation of video sink interface
        override fun onVideoFrame(data: ByteArray) { // Callback for receiving video frames
            val handler: ((ByteArray) -> Unit)? // Local reference to handler
            synchronized(binaryLock) { // Synchronize access to handler and buffer
                handler = binaryHandler // Get current handler
                if (handler == null) { // If no handler set
                    if (earlyBinaryFrames.size >= 120) { // Limit buffer size to 120 frames
                        earlyBinaryFrames.removeFirst() // Remove oldest frame
                    }
                    earlyBinaryFrames.addLast(data) // Add new frame to buffer
                }
            }
            handler?.invoke(data) // Invoke handler if present
        }
    }

    suspend fun requestScreenshot(timeoutMs: Long = 3000L): ByteArray? = // Suspend function to request screenshot with timeout
        withContext(Dispatchers.IO) { // Run in IO dispatcher
            val service = getBinder() ?: return@withContext null // Get binder service or return null
            val id = virtualDisplayId ?: return@withContext null // Get display ID or return null
            try { // Try to request screenshot
                withTimeout(timeoutMs) { // Apply timeout
                    service.requestScreenshot(id) // Request screenshot from service
                }
            } catch (e: Exception) { // Catch exceptions
                Log.e(TAG, "requestScreenshot failed for $id", e) // Log failure
                null // Return null on failure
            }
        }

    suspend fun ensureDisplay( // Suspend function to ensure virtual display exists
        context: Context, // Android context parameter
        width: Int, // Desired width
        height: Int, // Desired height
        dpi: Int, // Desired DPI
        bitrateKbps: Int? = null, // Optional bitrate in kbps
    ): Boolean = withContext(Dispatchers.IO) { // Run in IO dispatcher
        fun clearCachedBinder() { // Local function to clear cached binder
            binderService = null // Clear cached service
            ShowerBinderRegistry.setService(null) // Clear registry service
        }

        fun resetLocalDisplayState() { // Local function to reset local display state
            virtualDisplayId = null // Clear display ID
            videoWidth = 0 // Reset width
            videoHeight = 0 // Reset height
        }

        fun isBinderDied(e: Throwable): Boolean { // Local function to check if binder died
            return e is DeadObjectException || e is RemoteException || e.cause is DeadObjectException // Check exception types
        }

        // Align size
        val alignedWidth = width and -8 // Align width to multiple of 8
        val alignedHeight = height and -8 // Align height to multiple of 8
        val targetWidth = if (alignedWidth > 0) alignedWidth else width // Use aligned width if valid
        val targetHeight = if (alignedHeight > 0) alignedHeight else height // Use aligned height if valid
        val bitrate = bitrateKbps ?: 0 // Use bitrate or default 0

        suspend fun doEnsure(service: IShowerService): Boolean { // Local suspend function to perform ensure display
            val existingId = virtualDisplayId // Get current display ID
            if (existingId != null && videoWidth == targetWidth && videoHeight == targetHeight) { // If display matches requested size
                service.setVideoSink(existingId, videoSink.asBinder()) // Set video sink on service
                Log.d(TAG, "ensureDisplay reuse existing displayId=$existingId, size=${videoWidth}x${videoHeight}") // Log reuse
                return true // Return success
            }

            if (existingId != null) { // If existing display present but size differs
                try { // Try to destroy existing display
                    service.destroyDisplay(existingId) // Destroy display on service
                    Log.d(TAG, "ensureDisplay: destroyed previous displayId=$existingId before recreate") // Log destruction
                } catch (e: Exception) { // Catch exceptions
                    Log.w(TAG, "ensureDisplay: failed to destroy previous displayId=$existingId before recreate", e) // Log warning
                }
                resetLocalDisplayState() // Reset local state
            }

            // Changed: ensureDisplay now returns the ID and doesn't destroy existing ones.
            val id = service.ensureDisplay(targetWidth, targetHeight, dpi, bitrate) // Request new display from service
            if (id < 0) { // If invalid ID returned
                resetLocalDisplayState() // Reset local state
                Log.e(TAG, "ensureDisplay: server reported invalid displayId=$id") // Log error
                return false // Return failure
            }

            virtualDisplayId = id // Cache new display ID
            videoWidth = targetWidth // Cache width
            videoHeight = targetHeight // Cache height

            // Link local sink to this display on the server
            service.setVideoSink(id, videoSink.asBinder()) // Set video sink on service

            Log.d(TAG, "ensureDisplay complete, new displayId=$virtualDisplayId, size=${videoWidth}x${videoHeight}") // Log completion
            return true // Return success
        }

        val service = getBinder(context) ?: return@withContext false // Get binder service or return false
        try { // Try to ensure display
            doEnsure(service) // Call local ensure function
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "ensureDisplay failed", e) // Log failure
            resetLocalDisplayState() // Reset local state
            if (!isBinderDied(e)) { // If exception is not binder death
                return@withContext false // Return failure
            }

            clearCachedBinder() // Clear cached binder
            try { // Try to restart server and retry
                Log.d(TAG, "ensureDisplay: binder died, restarting Shower server and retrying") // Log restart attempt
                val ok = ShowerServerManager.ensureServerStarted(context.applicationContext) // Restart server
                if (!ok) { // If restart failed
                    Log.e(TAG, "ensureDisplay: failed to restart Shower server") // Log failure
                    return@withContext false // Return failure
                }
                delay(200) // Delay for server to start
                val retryService = getBinder(context) ?: return@withContext false // Get new binder service or fail
                doEnsure(retryService) // Retry ensure display
            } catch (retryError: Exception) { // Catch retry exceptions
                Log.e(TAG, "ensureDisplay retry failed", retryError) // Log retry failure
                resetLocalDisplayState() // Reset local state
                clearCachedBinder() // Clear cached binder
                false // Return failure
            }
        }
    }

    suspend fun launchApp(packageName: String, enableVirtualDisplayFix: Boolean ): Boolean = withContext(Dispatchers.IO) { // Suspend function to launch app
        val service = getBinder() ?: return@withContext false // Get binder or fail
        val id = virtualDisplayId ?: return@withContext false // Get display ID or fail
        if (packageName.isBlank()) return@withContext false // Fail if package name blank
        try { // Try to launch app
            service.launchApp(packageName, id,enableVirtualDisplayFix) // Call launchApp on service
            true // Return success
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "launchApp failed for $packageName on $id", e) // Log failure
            false // Return failure
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) { // Suspend function to perform tap
        val service = getBinder() ?: return@withContext false // Get binder or fail
        val id = virtualDisplayId ?: return@withContext false // Get display ID or fail
        try { // Try to tap
            service.tap(id, x.toFloat(), y.toFloat()) // Call tap on service
            true // Return success
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "tap($x, $y) failed on $id", e) // Log failure
            false // Return failure
        }
    }

    suspend fun swipe( // Suspend function to perform swipe gesture
        startX: Int, // Start X coordinate
        startY: Int, // Start Y coordinate
        endX: Int, // End X coordinate
        endY: Int, // End Y coordinate
        durationMs: Long = 300L, // Duration in milliseconds
    ): Boolean = withContext(Dispatchers.IO) { // Run in IO dispatcher
        val service = getBinder() ?: return@withContext false // Get binder or fail
        val id = virtualDisplayId ?: return@withContext false // Get display ID or fail
        try { // Try to swipe
            service.swipe(id, startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), durationMs) // Call swipe on service
            true // Return success
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "swipe failed on $id", e) // Log failure
            false // Return failure
        }
    }

    suspend fun touchDown(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) { // Suspend function for touch down event
        val service = getBinder() ?: return@withContext false // Get binder or fail
        val id = virtualDisplayId ?: return@withContext false // Get display ID or fail
        try { // Try to send touch down
            service.touchDown(id, x.toFloat(), y.toFloat()) // Call touchDown on service
            true // Return success
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "touchDown($x, $y) failed on $id", e) // Log failure
            false // Return failure
        }
    }

    suspend fun touchMove(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) { // Suspend function for touch move event
        val service = getBinder() ?: return@withContext false // Get binder or fail
        val id = virtualDisplayId ?: return@withContext false // Get display ID or fail
        try { // Try to send touch move
            service.touchMove(id, x.toFloat(), y.toFloat()) // Call touchMove on service
            true // Return success
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "touchMove($x, $y) failed on $id", e) // Log failure
            false // Return failure
        }
    }

    suspend fun touchUp(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) { // Suspend function for touch up event
        val service = getBinder() ?: return@withContext false // Get binder or fail
        val id = virtualDisplayId ?: return@withContext false // Get display ID or fail
        try { // Try to send touch up
            service.touchUp(id, x.toFloat(), y.toFloat()) // Call touchUp on service
            true // Return success
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "touchUp($x, $y) failed on $id", e) // Log failure
            false // Return failure
        }
    }

    suspend fun injectTouchEvent( // Suspend function to inject a detailed touch event
        action: Int, // Motion action
        x: Float, // X coordinate
        y: Float, // Y coordinate
        downTime: Long, // Down time of event
        eventTime: Long, // Event time
        pressure: Float, // Pressure of touch
        size: Float, // Size of touch area
        metaState: Int, // Meta key state
        xPrecision: Float, // X precision
        yPrecision: Float, // Y precision
        deviceId: Int, // Device ID
        edgeFlags: Int, // Edge flags
    ): Boolean = withContext(Dispatchers.IO) { // Run in IO dispatcher
        val service = getBinder() ?: return@withContext false // Get binder or fail
        val id = virtualDisplayId ?: return@withContext false // Get display ID or fail
        try { // Try to inject touch event
            service.injectTouchEvent( // Call injectTouchEvent on service
                id,
                action,
                x,
                y,
                downTime,
                eventTime,
                pressure,
                size,
                metaState,
                xPrecision,
                yPrecision,
                deviceId,
                edgeFlags
            )
            true // Return success
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "injectTouchEvent(action=$action, x=$x, y=$y) failed on $id", e) // Log failure
            false // Return failure
        }
    }

    fun shutdown() { // Function to shutdown and cleanup resources
        Log.d(TAG, "shutdown requested for display $virtualDisplayId") // Log shutdown request
        val service = binderService // Get cached service
        val id = virtualDisplayId // Get display ID
        virtualDisplayId = null // Clear display ID
        videoWidth = 0 // Reset width
        videoHeight = 0 // Reset height
        synchronized(binaryLock) { // Synchronize binary handler and buffer
            binaryHandler = null // Clear handler
            earlyBinaryFrames.clear() // Clear buffered frames
        }
        if (id != null && service?.asBinder()?.isBinderAlive == true) { // If valid display and service alive
            try { // Try to cleanup on service
                service.setVideoSink(id, null) // Clear video sink
                service.destroyDisplay(id) // Destroy display
            } catch (e: Exception) { // Catch exceptions
                Log.e(TAG, "shutdown: destroyDisplay failed for $id", e) // Log failure
            }
        }
    }

    suspend fun key(keyCode: Int): Boolean = withContext(Dispatchers.IO) { // Suspend function to inject a key event
        keyWithMeta(keyCode, 0) // Call keyWithMeta with no meta state
    }

    suspend fun keyWithMeta(keyCode: Int, metaState: Int): Boolean = withContext(Dispatchers.IO) { // Suspend function to inject key with meta state
        val service = getBinder() ?: return@withContext false // Get binder or fail
        val id = virtualDisplayId ?: return@withContext false // Get display ID or fail
        try { // Try to inject key
            if (metaState == 0) { // If no meta state
                service.injectKey(id, keyCode) // Call injectKey
            } else { // With meta state
                service.injectKeyWithMeta(id, keyCode, metaState) // Call injectKeyWithMeta
            }
            true // Return success
        } catch (e: Exception) { // Catch exceptions
            Log.e(TAG, "keyWithMeta(keyCode=$keyCode, metaState=$metaState) failed on $id", e) // Log failure
            false // Return failure
        }
    }
}