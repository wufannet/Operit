from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path


def _read_whitelist_file(path: Path) -> list[str]:
    if not path.exists():
        raise FileNotFoundError(str(path))

    if path.suffix.lower() == ".json":
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, list) or not all(isinstance(x, str) for x in data):
            raise ValueError(f"Whitelist json must be a string array: {path}")
        return [x.strip() for x in data if x.strip()]

    items: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s:
            continue
        if s.startswith("#"):
            continue
        items.append(s)
    return items


def _normalize_item(item: str) -> str:
    s = item.strip().replace("\\", "/")
    if not s:
        return s
    if not s.lower().endswith(".js"):
        s = f"{s}.js"
    return s


def _default_whitelist(packages_dir: Path) -> list[str]:
    if not packages_dir.exists():
        return []
    return sorted([p.name for p in packages_dir.glob("*.js") if p.is_file()])


def main() -> int:
    repo_root = Path(__file__).resolve().parent
    examples_dir = repo_root / "examples"
    packages_dir = repo_root / "app" / "src" / "main" / "assets" / "packages"
    default_whitelist_file = repo_root / "packages_whitelist.txt"

    parser = argparse.ArgumentParser(
        description=(
            "Copy whitelisted .js files from examples/ into app/src/main/assets/packages/. "
            "Whitelist can be provided by a file (txt/json) or derived from current assets/packages contents."
        )
    )
    parser.add_argument(
        "--whitelist",
        type=str,
        default=None,
        help=(
            "Path to whitelist file (.txt or .json). If omitted, will use packages_whitelist.txt if it exists; "
            "otherwise uses current files in app/src/main/assets/packages/ as the whitelist."
        ),
    )
    parser.add_argument(
        "--include",
        action="append",
        default=[],
        help="Add an extra item to whitelist (e.g. github.js or github). Can be provided multiple times.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be copied without writing files.",
    )
    parser.add_argument(
        "--delete-extra",
        action="store_true",
        help="Delete *.js in assets/packages that are not in the whitelist.",
    )

    args = parser.parse_args()

    if not examples_dir.exists():
        print(f"ERROR: examples dir not found: {examples_dir}", file=sys.stderr)
        return 2

    whitelist: list[str]
    if args.whitelist:
        whitelist = _read_whitelist_file(Path(args.whitelist))
    elif default_whitelist_file.exists():
        whitelist = _read_whitelist_file(default_whitelist_file)
    else:
        whitelist = _default_whitelist(packages_dir)

    whitelist.extend(args.include)
    normalized = [_normalize_item(x) for x in whitelist]
    normalized = [x for x in normalized if x]

    seen: set[str] = set()
    final_items: list[str] = []
    for x in normalized:
        if x in seen:
            continue
        seen.add(x)
        final_items.append(x)

    if not final_items:
        print("No whitelist items provided/found. Nothing to do.")
        print("- Provide --include <name> or create packages_whitelist.txt in repo root.")
        return 0

    if not args.dry_run:
        packages_dir.mkdir(parents=True, exist_ok=True)

    copied = 0
    missing = 0

    for item in final_items:
        src = examples_dir / item
        if not src.exists() or not src.is_file():
            print(f"MISSING: {src}")
            missing += 1
            continue

        dest = packages_dir / src.name
        action = "COPY" if not args.dry_run else "DRY-COPY"
        print(f"{action}: {src} -> {dest}")

        if not args.dry_run:
            shutil.copy2(src, dest)
            copied += 1

    if args.delete_extra and packages_dir.exists():
        whitelist_names = {Path(x).name for x in final_items}
        for p in packages_dir.glob("*.js"):
            if not p.is_file():
                continue
            if p.name in whitelist_names:
                continue
            action = "DELETE" if not args.dry_run else "DRY-DELETE"
            print(f"{action}: {p}")
            if not args.dry_run:
                p.unlink(missing_ok=True)

    print(f"Done. copied={copied}, missing={missing}, whitelist={len(final_items)}, dry_run={bool(args.dry_run)}")
    return 0 if missing == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
