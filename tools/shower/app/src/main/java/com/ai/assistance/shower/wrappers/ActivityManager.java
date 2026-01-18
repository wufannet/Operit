package com.ai.assistance.shower.wrappers;

import com.ai.assistance.shower.shell.FakeContext;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class ActivityManager {

    private final IInterface manager;
    private Method startActivityAsUserMethod;
    private Method forceStopPackageMethod;
    private java.lang.reflect.Method getTasksMethod;
    private java.lang.reflect.Method moveTaskToDisplayMethod;
    private java.lang.reflect.Method registerTaskStackListenerMethod;

    static ActivityManager create() {
        try {
            Class<?> cls = Class.forName("android.app.ActivityManagerNative");
            Method getDefaultMethod = cls.getDeclaredMethod("getDefault");
            IInterface am = (IInterface) getDefaultMethod.invoke(null);
            return new ActivityManager(am);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private ActivityManager(IInterface manager) {
        this.manager = manager;
    }

    public android.os.IInterface getIInterface(){
        return this.manager;
    }

    private Method getStartActivityAsUserMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (startActivityAsUserMethod == null) {
            Class<?> iApplicationThreadClass = Class.forName("android.app.IApplicationThread");
            Class<?> profilerInfo = Class.forName("android.app.ProfilerInfo");
            startActivityAsUserMethod = manager.getClass()
                    .getMethod("startActivityAsUser", iApplicationThreadClass, String.class, Intent.class, String.class,
                            IBinder.class, String.class, int.class, int.class, profilerInfo, Bundle.class, int.class);
        }
        return startActivityAsUserMethod;
    }

    public int startActivity(Intent intent, Bundle options) {
        try {
            Method method = getStartActivityAsUserMethod();
            return (int) method.invoke(
                    manager,
                    null,
                    FakeContext.PACKAGE_NAME,
                    intent,
                    null,
                    null,
                    null,
                    0,
                    0,
                    null,
                    options,
                    -2
            );
        } catch (Throwable e) {
            return 0;
        }
    }

    private Method getForceStopPackageMethod() throws NoSuchMethodException {
        if (forceStopPackageMethod == null) {
            forceStopPackageMethod = manager.getClass().getMethod("forceStopPackage", String.class, int.class);
        }
        return forceStopPackageMethod;
    }

    public void forceStopPackage(String packageName) {
        try {
            Method method = getForceStopPackageMethod();
            method.invoke(manager, packageName, -2);
        } catch (Throwable e) {
            // ignore
        }
    }

    // 获取运行中的任务列表
    public java.util.List<android.app.ActivityManager.RunningTaskInfo> getTasks(int maxNum) {
        try {
            if (getTasksMethod == null) {
                getTasksMethod = manager.getClass().getMethod("getTasks", int.class);
            }
            return (java.util.List<android.app.ActivityManager.RunningTaskInfo>) getTasksMethod.invoke(manager, maxNum);
        } catch (java.lang.Throwable e) {
            return null;
        }
    }

    // 反射调用搬运任务
    public void moveTaskToDisplay(int taskId, int displayId) {
        try {
            if (moveTaskToDisplayMethod == null) {
                // 部分版本可能叫 moveTaskToDisplay，部分可能在 IActivityManager 内部
                moveTaskToDisplayMethod = manager.getClass().getMethod("moveTaskToDisplay", int.class, int.class);
            }
            moveTaskToDisplayMethod.invoke(manager, taskId, displayId);
        } catch (java.lang.Throwable e) {
            // ignore
        }
    }

    // 反射注册任务栈监听器
    public void registerTaskStackListener(java.lang.Object listener) {
        try {
            if (registerTaskStackListenerMethod == null) {
                java.lang.Class<?> listenerClass = Class.forName("android.app.ITaskStackListener");
                registerTaskStackListenerMethod = manager.getClass().getMethod("registerTaskStackListener", listenerClass);
            }
            registerTaskStackListenerMethod.invoke(manager, listener);
        } catch (java.lang.Throwable e) {
            // ignore
        }
    }
}
