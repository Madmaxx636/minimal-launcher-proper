# Minimal Launcher Proper

*Imagine the falls became minimal.*

An Android home screen built for e-ink screens, in the spirit of Niagara Launcher: a short list of
favorite apps, an alphabet strip, and very little else. No ads, no accounts, and no internet permission.
Nothing the app reads ever leaves the phone.

Looking for apps to go with it? See [E-ink Apps](https://github.com/Madmaxx636/eink-apps): Ink Pad (notes), Weather, Clock,
Calculator, Calendar and Files, made for the same screens.

## What it does

- Favorites list with paging, an A–Z scrubber, and a drawer with search
- Typing on a physical keyboard starts a search of apps, the web and the device
- Pop-up folders, per-app pop-ups with notifications, shortcuts and widgets
- Widgets, including stacks you swipe across, and widgets that appear while their app plays music
- Music bar with artwork, one swipeable bar for all players
- Clock styles, agenda, quick button with tap and swipe-up actions
- Themes, icon shapes and icon packs, list and widget scaling
- E-ink refresh controls: flash style, length, and when it happens
- Backup and restore of every setting, plus a best-effort importer for Niagara backups

## Building

Needs JDK 21 and the Android SDK (platform 34, build-tools 34.0.0).

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleDebug
```

A release build is signed with a key read from `~/.minimal-launcher-keystore/keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). That file is not part of this repository.

## Permissions

Calendar and contacts (only if you turn on the agenda or contact search), media read access for file
search, notification access for dots, the notification panel and music controls, and usage access for
the optional app-use limiter. There is no `INTERNET` permission.

## Status

Built with Android's plain View system and no third-party libraries. It has been tested on a phone by
its author, not on a wide range of e-ink devices.
