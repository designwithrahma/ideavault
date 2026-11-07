# Contributing to IDEAVAULT

Thanks for your interest in improving IDEAVAULT.

## Before starting

Read the README and open an issue before a significant change. Keep contributions focused and preserve the existing offline-first behavior and user data.

## Development

1. Fork the repository or create a working branch.
2. Build with the Gradle wrapper: `./gradlew :app:assembleDebug`.
3. Run available unit tests: `./gradlew :app:testDebugUnitTest`.
4. Test UI changes on a real Android device or emulator.
5. Open a pull request with a concise description and screenshots for visual changes.

## Important rules

- Do not introduce destructive Room migrations.
- Do not upload sample personal vault contents or user backups.
- Do not commit API keys, signing credentials or private datasets.
- Preserve the approved IDEAVAULT UI unless a redesign is explicitly agreed upon.
- Respect MIT source terms and separate bundled font licenses.

Bug reports should include device/Android version, reproduction steps, expected behavior and observed behavior.
