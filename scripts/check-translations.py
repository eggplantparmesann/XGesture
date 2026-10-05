#!/usr/bin/env python3
"""Fail when a locale is missing strings/plurals, or when a values file is structurally invalid.

Why this exists
---------------
`app/build.gradle.kts` disables the `MissingTranslation` / `ExtraTranslation` lint checks, so a
new English string that no other locale ever receives passes CI silently. That is exactly how
1.35.0 nearly shipped with 10 untranslated strings in `values-ja` and `values-ar` (the
"点词默认状态" and "启动方式" settings), plus 21 older gaps inherited from 1.31.0 / 1.33.0.

The default `values/` file(s) of each module are the source of truth for resource *names*; every
`values-<locale>/` directory must define the same set of names, except entries marked
`translatable="false"`.

Structure checks are also run here because Android rejects nested resource elements with
"Unrecognized tag" only at AAPT2 time, several minutes into a Gradle build. Catching it in
milliseconds locally is worth the few lines.

Usage
-----
    python scripts/check-translations.py                 # exit 1 on any missing translation
    python scripts/check-translations.py --warn-only     # report everything, always exit 0
    python scripts/check-translations.py --quiet         # only print problems
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

# Resource qualifiers that look like a language code but are configuration, not locale.
NON_LOCALE_QUALIFIERS = {
    "night", "notnight", "day", "land", "port", "car", "desk", "television", "appliance",
    "watch", "vrheadset", "small", "normal", "large", "xlarge", "round", "notround",
    "ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "nodpi", "tvdpi", "anydpi",
    "notouch", "finger", "stylus", "keysexposed", "keyshidden", "keyssoft", "nokeys",
    "navhidden", "navexposed", "nonav", "dpad", "trackball", "wheel", "enabled", "disabled",
    "ldrtl", "ldltr", "highdr", "lowdr", "nowidecg", "widecg", "hdr", "maskable",
}

# Element kinds that may legitimately contain <item> children.
CONTAINER_TAGS = ("plurals", "string-array", "integer-array", "array")


def is_locale_dir(name: str) -> bool:
    """True for `values-ja`, `values-zh-rCN`, ... but not `values-night`, `values-v31`."""
    if not name.startswith("values-"):
        return False
    qualifier = name[len("values-"):]
    if qualifier.startswith("b+"):  # BCP-47 form: values-b+zh+Hans
        return True
    lang = qualifier.split("-")[0]
    if lang in NON_LOCALE_QUALIFIERS:
        return False
    return len(lang) in (2, 3) and lang.isalpha() and lang.islower()


def find_res_dirs(repo_root: Path) -> list[Path]:
    """Every `<module>/src/main/res` that carries a default `values/` directory."""
    found = {p.parent for p in repo_root.glob("*/src/main/res/values")}
    found |= {p.parent for p in repo_root.glob("*/*/src/main/res/values")}
    return sorted(found)


def parse_values(path: Path):
    """Return (entries, problems).

    entries maps resource name -> translatable flag, for <string> and <plurals> only.
    problems lists structural violations that Android would reject.
    """
    entries: dict[str, bool] = {}
    problems: list[str] = []
    try:
        root = ET.fromstring(path.read_text(encoding="utf-8"))
    except ET.ParseError as exc:
        return {}, [f"malformed XML: {exc}"]
    except UnicodeDecodeError as exc:
        return {}, [f"not valid UTF-8: {exc}"]

    if root.tag != "resources":
        return {}, [f"root element is <{root.tag}>, expected <resources>"]

    for child in root:
        if child.tag in ("string", "plurals"):
            name = child.get("name")
            if name is None:
                problems.append(f"<{child.tag}> without a name attribute")
                continue
            if name in entries:
                problems.append(f"duplicate resource name: {name}")
            entries[name] = child.get("translatable", "true").lower() != "false"
        if child.tag in CONTAINER_TAGS:
            for sub in child:
                if sub.tag != "item":
                    problems.append(
                        f'<{sub.tag}> nested inside <{child.tag} name="{child.get("name")}">'
                    )
    return entries, problems


def check_module(res_dir: Path, repo_root: Path):
    """Yield (severity, message) for one module."""
    default_dir = res_dir / "values"
    locales = sorted(
        (p for p in res_dir.iterdir() if p.is_dir() and is_locale_dir(p.name)),
        key=lambda p: p.name,
    )
    if not locales:
        return

    for filename in ("strings.xml", "plurals.xml"):
        default_path = default_dir / filename
        if not default_path.is_file():
            continue
        default_entries, problems = parse_values(default_path)
        for problem in problems:
            yield "error", f"{default_path.relative_to(repo_root)}: {problem}"
        required = {n for n, translatable in default_entries.items() if translatable}
        if not required:
            continue

        for locale_dir in locales:
            locale_path = locale_dir / filename
            if not locale_path.is_file():
                yield "error", (
                    f"{locale_path.relative_to(repo_root)}: missing file "
                    f"({len(required)} entries required by {filename})"
                )
                continue
            entries, problems = parse_values(locale_path)
            for problem in problems:
                yield "error", f"{locale_path.relative_to(repo_root)}: {problem}"

            missing = sorted(required - set(entries))
            for name in missing:
                yield "error", (
                    f"{locale_path.relative_to(repo_root)}: missing {name}"
                )
            extra = sorted(set(entries) - set(default_entries))
            for name in extra:
                yield "warning", (
                    f"{locale_path.relative_to(repo_root)}: "
                    f"{name} is not in the default locale"
                )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check locale completeness and values XML structure.",
    )
    parser.add_argument(
        "--repo-root", "-r", default=".", help="Repository root (default: current directory)",
    )
    parser.add_argument(
        "--warn-only", action="store_true",
        help="Report gaps but always exit 0 (use while translations are still catching up)",
    )
    parser.add_argument(
        "--quiet", "-q", action="store_true",
        help="Only print problems, not the per-module summary",
    )
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    res_dirs = find_res_dirs(repo_root)
    if not res_dirs:
        print("ERROR: no <module>/src/main/res/values directories found. "
              "Are you running from the repository root?", file=sys.stderr)
        return 1

    errors: list[str] = []
    warnings: list[str] = []

    for res_dir in res_dirs:
        module = res_dir.relative_to(repo_root).parent.parent.parent
        results = list(check_module(res_dir, repo_root))
        module_errors = [m for s, m in results if s == "error"]
        module_warnings = [m for s, m in results if s == "warning"]
        if not args.quiet:
            status = "FAIL" if module_errors else "OK  "
            detail = ""
            if module_errors:
                detail = f"  ({len(module_errors)} problem(s))"
            elif module_warnings:
                detail = f"  ({len(module_warnings)} extra)"
            print(f"[{status}] {module}{detail}")
        errors.extend(module_errors)
        warnings.extend(module_warnings)

    if args.quiet:
        for message in errors:
            print(f"ERROR: {message}")
    else:
        for message in warnings:
            print(f"WARNING: {message}")

    print()
    locales_checked = sum(
        len([p for p in d.iterdir() if p.is_dir() and is_locale_dir(p.name)])
        for d in res_dirs
    )
    if errors:
        if not args.quiet:
            for message in errors:
                print(f"ERROR: {message}")
        print(f"\nFAILED: {len(errors)} problem(s) across {len(res_dirs)} module(s) "
              f"and {locales_checked} locale(s).")
        if args.warn_only:
            print("(--warn-only given: not failing the build.)")
            return 0
        return 1

    suffix = f" ({len(warnings)} extra entries)" if warnings else ""
    print(f"OK: {len(res_dirs)} module(s), {locales_checked} locale(s), "
          f"no missing translations{suffix}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
