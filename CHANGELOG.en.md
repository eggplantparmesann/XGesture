# Changelog (English)

All notable changes to XGesture are documented in this file, newest first.

This is the English counterpart of [CHANGELOG.md](CHANGELOG.md). Keep the version headers
(`## [x.y.z] - YYYY-MM-DD`) and the group headers (`### Added` / `### Changed` / `### Fixed`)
identical to the Chinese file so that the release pipeline can line up both languages.

Where the two files differ, `CHANGELOG.md` is authoritative.

Release pages are published with the English notes first, then the Chinese ones. In-app update
notes follow the system language: `notesEn` is shown to non-Chinese locales and `notes` to
Chinese ones, each falling back to the other when a version is missing.

## [1.36.0] - 2026-10-06

### Added
- New "volume up" and "volume down" gesture actions
- New floating-ball "inward then down" and "inward then up" two-stage gestures; the gesture settings page is grouped by direction
- New freezer "Pause": the app stays installed and its launcher icon stays in place but turns grey, and tapping it shows a system dialog with a one-tap resume; the freezer list, batch actions, manager filter and search-panel quick actions can all pause and resume
- New inverted condition for the notification advanced-match JSON, with stricter JSON validation

### Changed
- The floating ball size limit is raised from 72dp to 96dp, with 1dp steps
- The first stage of the floating-ball two-stage gestures uses the short-swipe threshold
- The trigger gesture settings page is split into sections, with the slot list first
- Ring launcher settings and strings use the "ring launcher" naming; existing configuration migrates automatically on upgrade and gesture bindings are unaffected
- The ring launcher enters edit mode only when no slot is pinned and no app can fill in; otherwise it fills with recent apps
- The notification rule screen state becomes a three-way choice (any / screen on / screen off), and leaving all three charging states unchecked means no restriction
- "Floating window for the current app" first tries to move the window in place on Meizu devices and only relaunches when that fails
- In-app update notes follow the system language (Chinese or English)
- Freezer "Re-freeze" only affects apps in use and keeps paused apps paused; "Unfreeze all" no longer touches paused apps
- The freezer import also covers frozen and paused launcher apps
- Freezer pause and freeze both skip this app itself, system, SystemUI and the current launcher

### Fixed
- Fixed the trigger being drawn higher and shorter than its touchable area in the trigger settings preview
- Fixed a gesture slot losing its launch mode after saving, which still launched fullscreen when the floating window was selected
- Fixed ring launcher slots being cleared after editing
- Fixed charging states being re-checked when reopening a notification rule that had only one phone state selected
- Fixed the app-condition dropdown label and value being misaligned in notification rules
- Fixed the notification-access entry not opening this app's detail page
- Fixed occasional crashes when clipboard monitoring starts
- Fixed the flashlight action still opening the app info page when the permission was already granted
- Fixed four mojibake strings in the English UI

## [1.35.0] - 2026-10-05

### Added
- New floating-ball "lines on both sides" layout: the ball side and the opposite side share one
  line style; the ball appears on drag and the text-pick panel opens from the starting side
- New floating-ball "swipe down then inward" and "swipe up then inward" gestures, bindable to
  actions separately
- New "keep the floating-ball reminder after unlock" switch: reminders received while locked are
  retained and shown after unlock according to the auto-dismiss time
- New text-pick panel "panel position": docked to the bottom, or centered (92% width and 70% of
  screen height in portrait; the whole panel lifts with the keyboard)
- New text-pick "default pick mode" setting: remember the last used state, always on, or always
  off
- New per-app "launch mode" for bound apps: follow the global policy, always fullscreen, or
  always in a floating window, with the current mode shown on the bound row
- New "local app" text-pick translation engine: choose which translation app receives the picked
  text
- New app shortcut search: searches shortcuts declared by installed apps, matches full pinyin and
  initials, invoked with `sc` by default
- New clipboard search: searches text, links and images in clipboard history; tap to copy back to
  the clipboard, long-press to copy and paste into the focused input, invoked with `cb` by default
- New cloud OCR / translation providers Gemini, OpenAI, Claude, DeepSeek, Groq and Grok (xAI),
  bringing the cloud options to ten
- New one-tap presets for custom cloud endpoints: picking a vendor fills in the endpoint and a
  recommended model

### Changed
- The search panel is now always fullscreen: the "presentation" setting is gone, and candidates
  are organised into sectioned cards with filter chips
- Instant translation in the text-pick panel now defaults to on, including for existing users who
  never changed the switch
- With instant translation off, webpage translation becomes "jump translation": the selected
  translation app takes priority, and the web page opens only when none is selected
- Reduced stutter when dragging the edge handle or the floating ball

### Fixed
- Fixed repeated flashing caused by the overlay being rebuilt over and over when opening the
  search panel
- Fixed search panel candidates not sticking to the bottom and the bottom card being covered by
  the engine area
- Fixed "floating window for the current app" snapping back to the app home page
- Fixed the text-pick panel not being width-limited when centered or docked to the bottom in
  landscape
- Fixed the right-edge line being pushed off screen when the keyboard opens
- Fixed the selected provider mark and badge not refreshing immediately after switching the cloud
  provider
