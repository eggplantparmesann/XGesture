#!/usr/bin/env python3
"""
Extract the Markdown section of a given version from a changelog (for GitHub Releases).

The repository keeps two parallel changelogs — `CHANGELOG.md` (Chinese) and
`CHANGELOG.en.md` (English). Release bodies are published with the English section first and
the Chinese one after it, so `--prepend-changelog CHANGELOG.en.md` writes the English section,
a separator, then the Chinese section.

A missing English section is not fatal: `--optional` skips it (with a notice), so a release can
still ship while the English notes are being written. The primary section is always required
unless `--optional` is given explicitly.
"""
import argparse
import re
import sys
from pathlib import Path


def extract_section(changelog_text: str, version: str) -> str:
    v = version.lstrip("v")
    if v.lower() == "unreleased":
        raise ValueError("Cannot extract [Unreleased] for release notes.")

    header = f"## [{v}]"
    start = changelog_text.find(header)
    if start < 0:
        raise ValueError(f"Section '{header}' not found in CHANGELOG.")

    after_header = start + len(header)
    remainder = changelog_text[after_header:]

    next_match = re.search(r"(?m)^## \[", remainder)
    if next_match:
        section = changelog_text[start : after_header + next_match.start()].rstrip()
    else:
        section = changelog_text[start:].rstrip()

    return section


def lint_section(section: str) -> None:
    # Check if section has at least one bullet point (- or *)
    has_bullets = bool(re.search(r"(?m)^[-*]\s+", section))
    if not has_bullets:
        raise ValueError("Changelog section contains no bullet items.")


def resolve_section(changelog_path: Path, version: str, optional: bool, lint: bool) -> str:
    """Section text, or "" when the file/section is missing and `optional` allows it."""
    if not changelog_path.is_file():
        if optional:
            print(f"NOTE: {changelog_path} not found; skipping this language.", file=sys.stderr)
            return ""
        print(f"ERROR: Changelog not found: {changelog_path}", file=sys.stderr)
        sys.exit(1)

    content = changelog_path.read_text(encoding="utf-8")
    try:
        section = extract_section(content, version)
        if lint:
            lint_section(section)
    except ValueError as e:
        if optional:
            print(f"NOTE: {changelog_path}: {e} Skipping this language.", file=sys.stderr)
            return ""
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
    return section


def main():
    parser = argparse.ArgumentParser(description="Extract changelog section for a release.")
    parser.add_argument("--version", "-v", required=True, help="Version string (e.g. 1.9.8.2 or v1.9.8.2)")
    parser.add_argument("--changelog", "-c", default="CHANGELOG.md", help="Path to the changelog (default: CHANGELOG.md)")
    parser.add_argument(
        "--prepend-changelog",
        default="",
        help="Changelog whose section is written BEFORE the primary one (e.g. CHANGELOG.en.md)",
    )
    parser.add_argument(
        "--separator",
        default="",
        help="Text written between the prepended section and the primary one (default: blank line)",
    )
    parser.add_argument(
        "--optional",
        action="store_true",
        help="Do not fail when the primary or prepended section is missing",
    )
    parser.add_argument("--out-file", "-o", default="", help="Path to write output markdown")
    parser.add_argument("--lint", action="store_true", help="Perform linting checks on the extracted section")

    args = parser.parse_args()

    primary = resolve_section(Path(args.changelog), args.version, args.optional, args.lint)

    prepended = ""
    if args.prepend_changelog:
        prepended = resolve_section(
            Path(args.prepend_changelog), args.version, optional=True, lint=args.lint
        )
        if not prepended:
            print(
                f"NOTE: no {args.prepend_changelog} section for {args.version}; "
                f"publishing {args.changelog} only.",
                file=sys.stderr,
            )

    if args.optional and not primary and not prepended:
        print(f"NOTE: no changelog section found for {args.version}.", file=sys.stderr)

    separator = args.separator if args.separator else "\n"
    parts = [part for part in (prepended, primary) if part]
    output = separator.join(parts).rstrip()

    if args.out_file:
        out_path = Path(args.out_file)
        out_path.write_text(output + "\n", encoding="utf-8")
        print(f"Wrote {out_path}")
    else:
        print(output)


if __name__ == "__main__":
    main()
