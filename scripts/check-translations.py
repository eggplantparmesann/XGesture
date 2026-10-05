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

Mojibake checks (default locale)
--------------------------------
`values/strings.xml` is the English source of truth, so a Chinese character in it is a UTF-8
file that some editor or script read and wrote back as GBK/CP936. Real example:
`gesture_action_toggle_wifi` shipped as `Toggle Wi鈥慒i` — a `Wi‑Fi` U+2011 non-breaking
hyphen whose `E2 80 91` bytes were reinterpreted as `鈥` plus the first byte of the following
`F`. Three more strings were damaged the same way (`group鈥檚`, `px 路 Dot`). The corrupted
strings render as Chinese in the English UI, which is how this was noticed.

The heuristic is a byte-pattern round trip, not a Chinese-character allowlist:

1. the string is not U+FFFD-damaged and does contain non-ASCII;
2. it is wholly encodable to GBK; and
3. those GBK bytes are wholly valid UTF-8, with no replacement character, decoding either to
   mostly-ASCII text (`MOJIBAKE_ASCII_RATIO`) or to text containing CJK.

Condition 2 is the legitimacy guard: every intentionally multilingual default
(`简体中文`, `العربية`, `한국어`, `XGesture · X手势`) contains a character GBK cannot encode, so
it is skipped before the round trip. Condition 1 matters too — a string that is pure ASCII
re-encodes to itself and would otherwise pass condition 3 trivially. Condition 3 is the
detection: mangled text re-encodes to exactly the UTF-8 bytes it came from. Both halves of
condition 3 are needed: `Toggle Wi鈥慒i` decodes back to `Toggle Wi‑Fi` (ASCII, no CJK at all),
while mangling a run of Chinese produces CJK in the decode.

This check runs on default-locale (`values/`) files only. Applying it to translated locales
would be noise: 7 of 3764 non-ASCII strings in values-zh round-trip by accident (`约 %1$s`,
`LSPosed 状态`, …). Run `--mojibake-stats` to see that census.

Note that the mangled text need not contain a Han character at all: `Toggle Wi鈥慒i` decodes
back to a non-breaking hyphen and `…px 路 Dot…` back to `·`, so the giveaway is the byte
pattern, not the visible glyphs. Strings already carrying U+FFFD are skipped — the offending
glyphs are visible on screen and no byte pattern is left to match.

If a legitimate default string ever trips the heuristic, add its resource name (optionally
qualified as `module:name`) to `MOJIBAKE_ALLOWLIST`.

Usage
-----
    python scripts/check-translations.py                 # exit 1 on any missing translation
    python scripts/check-translations.py --warn-only     # report everything, always exit 0
    python scripts/check-translations.py --quiet         # only print problems
    python scripts/check-translations.py --mojibake-stats  # census over all locales, exit 0
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

# A mangled default string decodes back to mostly-ASCII text (`Toggle Wi鈥慒i` -> `Toggle Wi‑Fi`)
# or, when whole runs of Chinese were mangled, to CJK text. Measured with --mojibake-stats: 0
# hits across the non-ASCII strings of values/; the 7 accidental round trips that exist in
# values-zh are never checked, because the check runs on default-locale files only.
MOJIBAKE_ASCII_RATIO = 0.8

# Default-locale resource names that legitimately contain Han characters, keyed either by
# resource name alone or as "<module>:<name>" (e.g. "app:some_label"), matching the
# `module` label that find_mojibake() derives from the res directory.
# Keep this empty unless a false positive actually shows up; the round-trip heuristic in
# `looks_like_gbk_mojibake` is deliberately narrow.
MOJIBAKE_ALLOWLIST: frozenset[str] = frozenset()


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


def is_cjk(ch: str) -> bool:
    """Han, plus the CJK punctuation ranges mojibake commonly lands in."""
    code = ord(ch)
    return (
        0x4E00 <= code <= 0x9FFF  # CJK unified ideographs
        or 0x3000 <= code <= 0x303F  # CJK symbols and punctuation
        or 0x3400 <= code <= 0x4DBF  # CJK extension A
        or 0xF900 <= code <= 0xFAFF  # CJK compatibility ideographs
    )


def looks_like_gbk_mojibake(text: str) -> bool:
    """True when `text` looks like UTF-8 that was decoded as GBK/CP936 and written back.

    Conditions, in order (full rationale in the module docstring):
      1. not U+FFFD-damaged, and containing non-ASCII at all;
      2. wholly encodable to GBK;
      3. those GBK bytes are wholly valid UTF-8, with no U+FFFD, decoding either to
         mostly-ASCII text (`MOJIBAKE_ASCII_RATIO`) or to text containing CJK.

    Condition 1 is not decoration: a pure-ASCII string re-encodes to itself and would satisfy
    condition 3 trivially, which once flagged ~3600 perfectly good strings. Condition 2 is the
    legitimacy guard: the intentionally multilingual defaults (`简体中文 · `, `العربية`,
    `한국어`, `… · …`) hold characters GBK cannot encode, so they never reach the round trip.

    Only called for default-locale (`values/`) files. It is deliberately not applied to
    translated locales: 7 of 3717 non-ASCII strings in values-zh round-trip by accident
    (`约 %1$s`, `LSPosed 状态`, …), which is harmless there and noise here.
    """
    if "\ufffd" in text:
        return False  # already damaged with replacement characters
    if not any(ord(ch) > 0x7F for ch in text):
        return False  # plain ASCII re-encodes to itself; nothing to detect
    try:
        raw = text.encode("gbk")
    except UnicodeEncodeError:
        return False  # intentional non-GBK text: CJK punctuation, Kana, Hangul, Arabic, …
    try:
        recovered = raw.decode("utf-8")
    except UnicodeDecodeError:
        return False  # the bytes are not valid UTF-8 at all
    if "\ufffd" in recovered:
        return False  # decoded into garbage: this was genuinely GBK text
    ascii_ratio = sum(1 for ch in recovered if ord(ch) < 0x80) / len(recovered)
    return ascii_ratio >= MOJIBAKE_ASCII_RATIO or any(is_cjk(ch) for ch in recovered)


def mojibake_texts(element) -> list[str]:
    """Non-empty text nodes of a <string>/<plurals> element, entity/tag tails included."""
    return [chunk.strip() for chunk in element.itertext() if chunk.strip()]


def find_mojibake(default_path: Path, repo_root: Path, module_label: str):
    """Yield one error per default-locale resource name that looks GBK-corrupted."""
    try:
        root = ET.fromstring(default_path.read_text(encoding="utf-8"))
    except (ET.ParseError, UnicodeDecodeError, OSError):
        return  # reported by parse_values(), which runs for the same file
    for child in root:
        if child.tag not in ("string", "plurals"):
            continue
        name = child.get("name")
        if name is None:
            continue
        if name in MOJIBAKE_ALLOWLIST or f"{module_label}:{name}" in MOJIBAKE_ALLOWLIST:
            continue
        for text in mojibake_texts(child):
            if looks_like_gbk_mojibake(text):
                yield (
                    f"{default_path.relative_to(repo_root)}: {name} looks like GBK-mangled "
                    f"UTF-8 (would render as Chinese in the English UI): {text!r}"
                )
                break


def module_label(res_dir: Path, repo_root: Path) -> str:
    """`app` for `app/src/main/res`, `core-translate` for `core/translate/src/main/res`."""
    rel = res_dir.relative_to(repo_root)
    head = rel.parts[:-3]  # drop src/main/res
    return "-".join(head) if head else rel.name


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
        module_label_name = module_label(res_dir, repo_root)
        for message in find_mojibake(default_path, repo_root, module_label_name):
            yield "error", message
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


def mojibake_census(res_dirs: list[Path], repo_root: Path) -> int:
    """Print, per values directory, how many non-ASCII strings the heuristic flags.

    This is the false-positive census behind MIN_MOJIBAKE_LEN / MOJIBAKE_ASCII_RATIO: only the
    default `values/` directories are checked for real, so anything flagged in a locale
    directory here is a false positive to weigh before tightening the thresholds.
    """
    for res_dir in res_dirs:
        for xml_path in sorted(res_dir.rglob("*.xml")):
            try:
                root = ET.fromstring(xml_path.read_text(encoding="utf-8"))
            except (ET.ParseError, UnicodeDecodeError, OSError):
                continue
            non_ascii = 0
            flagged: list[str] = []
            for node in root.iter():
                if node.tag not in ("string", "item"):
                    continue
                name = node.get("name", "?")
                for chunk in mojibake_texts(node):
                    if not any(ord(ch) > 0x7F for ch in chunk):
                        continue
                    non_ascii += 1
                    if looks_like_gbk_mojibake(chunk):
                        flagged.append(name)
            if not non_ascii:
                continue
            rel = xml_path.relative_to(repo_root)
            verdict = "OK" if not flagged else f"FLAGGED {len(flagged)}"
            print(f"[{verdict:>10}] {rel}  non-ascii strings={non_ascii}")
            for name in sorted(set(flagged))[:10]:
                print(f"             possible false positive: {name}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check locale completeness, values XML structure, and default-locale mojibake.",
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
    parser.add_argument(
        "--mojibake-stats", action="store_true",
        help="Report mojibake-heuristic hits in every locale (false-positive census), exit 0",
    )
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    res_dirs = find_res_dirs(repo_root)
    if not res_dirs:
        print("ERROR: no <module>/src/main/res/values directories found. "
              "Are you running from the repository root?", file=sys.stderr)
        return 1

    if args.mojibake_stats:
        return mojibake_census(res_dirs, repo_root)

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
