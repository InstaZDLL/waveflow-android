# Contributing to WaveFlow for Android

Thanks for helping on the Android client. The main
[WaveFlow CONTRIBUTING guide](https://github.com/InstaZDLL/WaveFlow/blob/main/CONTRIBUTING.md)
(commit conventions, PR process, the family's shared expectations) applies here
too — this file only adds the Android-specific bits.

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## Getting set up

Open the project in Android Studio, or build from the command line with the
Gradle wrapper (JDK 21 — the CI and the version catalog target it):

```bash
./gradlew assembleDebug
```

The app is Kotlin + Jetpack Compose + Media3, with dependencies pinned in the
`gradle/libs.versions.toml` version catalog.

## Before you open a PR

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint
```

Check UI changes in **both light and dark** theme — the player tints itself from
the artwork — and verify playback changes from the notification and lock-screen
media controls.

## Commit conventions

[Conventional Commits](https://www.conventionalcommits.org/) with **kebab-case**
scopes and a **lowercase** subject, same as the rest of the family. Scopes mirror
the areas in [`.github/labeler.yml`](.github/labeler.yml):

- `feat(ui): drag-to-reorder in the queue`
- `fix(playback): keep the album context when tapping a track`
- `refactor(data): fold artwork extraction into the scanner`

## Staying coherent with the other clients

The desktop, iOS, and Android clients are meant to stay coherent. When you add a
user-facing behavior, check whether desktop or iOS already does it and match that
behavior rather than inventing a new one.

## Reporting bugs and security issues

- Bugs and feature requests: use the [issue templates](.github/ISSUE_TEMPLATE/).
- Security vulnerabilities: **do not** open a public issue — follow
  [SECURITY.md](.github/SECURITY.md).

## License

WaveFlow for Android is **GPL-3.0-only**. By submitting a pull request you agree
that your contribution is licensed under those terms.
