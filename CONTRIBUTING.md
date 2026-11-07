# Contributing to IDEAVAULT

Thank you for helping improve IDEAVAULT.

## Before contributing

1. Search existing issues and pull requests.
2. Open an issue before a major feature or breaking change.
3. Create a feature branch and keep your pull request focused.
4. Explain the change, screenshots if the UI changed, and how you tested it.

## Development

- Use the Gradle wrapper checked into the repository.
- Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` on a machine with the required Android SDK.
- Do not change the application ID or Room schema casually; database migrations must preserve existing user data.
- Preserve offline-first behavior, privacy, and accessibility.
- Do not commit real data, logs, API secrets, local keys, or signing materials.
- Use the existing app UI patterns and avoid placeholder copy or sample user records.

## Code and licenses

Keep new dependencies and third-party assets properly attributed. Project code contributions are submitted under the project's MIT License unless otherwise agreed.
