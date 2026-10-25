# iOS app scaffold for ZUKO TV

This folder contains a first-pass SwiftUI app scaffold intended to start the iPhone port of the Android app in this repository.

## Contents

- `ZukoTViOS/Package.swift` — Swift package for the iPhone app skeleton
- `ZukoTViOS/Sources/ZukoTViOSApp/` — app entry point and UI code

## Open in Xcode

1. Open the `ios/ZukoTViOS` folder in Xcode.
2. Select the iPhone simulator or a connected device.
3. Run the app.

## Notes

This is intentionally a scaffold rather than a full port. It establishes:
- SwiftUI app shell
- API client contract
- TMDB-backed movie model structures
- home screen list UI

The Android implementation in `app/src/main/java/com/zuko/tv/data` should be used as the source of truth for API behavior while porting screens one by one.
