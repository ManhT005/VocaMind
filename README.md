# VocaMind

Initial Android foundation for VocaMind.

## Open in Android Studio

1. Checkout `feature/foundation-architecture`.
2. Open the repository root in Android Studio.
3. Use JDK 17 for Gradle.
4. Let Gradle Sync finish.
5. Run the `app` configuration on an Android API 26+ emulator/device.

The application intentionally starts with a blank screen. Business features are not implemented in this foundation branch.

## Foundation

- Android application module: `app`
- Language: Java
- UI baseline: Android XML/View system
- Minimum SDK: 26
- Architecture placeholders:
  - `core/`
  - `domain/`
  - `data/`
  - `feature/`

Empty architecture packages are kept with `.gitkeep` until implementation begins.
