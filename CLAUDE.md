# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

"Ambulansku" (app name in `strings.xml`) — a native Android app connecting ambulance passengers with drivers, built with Kotlin, XML/View-based UI, and Firebase. Single Gradle module (`app`), package `com.example.tugasakhirfinal`.

## Build & run

Standard Gradle Android project. From the repo root:

```
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build & install on connected device/emulator
./gradlew test                   # run JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # run instrumented tests on a device/emulator (app/src/androidTest)
./gradlew test --tests "com.example.tugasakhirfinal.SomeTest"   # run a single unit test class
```

On Windows use `gradlew.bat` instead of `./gradlew`. There is no lint/ktlint config beyond the default AGP lint (`./gradlew lint`).

Requires `app/google-services.json` (already checked in) for Firebase to initialize, and a Google Maps API key, currently hardcoded in `app/src/main/res/values/strings.xml` (`google_maps_key`).

## Architecture

### Role-based dual-flow app

There is no MVVM/DI framework (no ViewModel, Hilt, Dagger, or Repository layer) — Activities and Fragments talk to Firebase directly. Screens are XML layouts inflated via ViewBinding (`viewBinding true`). Jetpack Compose is enabled in Gradle (`compose true`, Compose BOM/UI deps) but is **not actually used anywhere** — treat it as unused boilerplate, not a second UI system in progress.

Entry flow:
1. `SplashScreenActivity` checks `FirebaseAuth.currentUser`. If logged in, reads `users/{uid}.role` from Firestore (`Source.SERVER`, bypassing cache) and routes directly to `DriverHomeActivity` or `PassangerHomeActivity`.
2. If not logged in (or the Firestore user doc is missing), goes to `RoleSelectionActivity`, which lets the user pick Passenger or Driver and routes into that side's sign-in/sign-up flow (`ForPassanger.Welcome.MainActivity` / `ForDriver.Welcome.MainActivityDriver`).

The codebase is split into two parallel, largely independent feature trees mirroring the two roles:
- `ForPassenger/` — passenger-side Welcome (sign in/up), `PassengerHomeActivity` hosting bottom-nav `Fragment/` (Home, ActivityDrive, Chat, Profile), `DriveFragment/` (hospital search/booking flow, map), `EditProfileFragment/` (profile & address editing), `Chat/`.
- `ForDriver/` — driver-side mirror: Welcome, `DriverHomeActivity` with its own `Fragment/` (Home, ActivityDrive, Chat, Profile), `Chat/`.

These two trees do not share Activities/Fragments — expect duplicated logic (e.g. separate chat adapters/activities per role) rather than shared components.

### Firebase data model

Firestore is the primary datastore (Realtime Database dependency is included but unused — no `FirebaseDatabase` calls in the codebase). Collections referenced throughout the code:
- `users` — profile + `role` field (`"driver"` / `"passenger"`), keyed by Firebase Auth uid.
- `bookings` — ride/booking requests connecting a passenger and driver.
- `messages` — chat messages, generally scoped under a booking.
- `addresses` — saved passenger addresses.
- `hospitals` — destination hospital list (`HospitalListActivity`, `HospitalAdapter`).

FirebaseAuth is used for sign-in/sign-up (including Google Sign-In via Credential Manager / `googleid`). Firebase Storage is used for profile photo uploads, done inline in each role's Profile fragment (`ProfileFragment`/`ProfileFragmentDriver`) via a shared `BottomSheetDialog` (layout `activity_edit_foto_profile.xml`) offering gallery pick or camera capture.

### Known inconsistencies (real, not hypothetical — verify before assuming)

- Passenger spelling was normalized project-wide on 2026-07-25 (was previously "Passanger" throughout code, resources, and layouts — `ForPassanger`, `PassangerHomeActivity`, `item_passanger_chat.xml`, etc.). All identifiers, file names, and layout files now consistently spell "Passenger".
- Package declarations were audited and aligned with physical folder layout on 2026-07-28 (previously several classes — e.g. `ForDriver/Welcome/SignUpActivityDriver.kt`, the data classes/adapters declaring flat `Model`/`Adapter` packages, `ForPassenger/DriveFragment/*`  declaring flat `ForPassenger` — had package lines that didn't match their folder). `AndroidManifest.xml` was updated to match. Package declarations now consistently match folder structure; a new mismatch found later is a genuine regression, not a pre-existing quirk.
- `SignInActivityDriver` (`ForDriver/Welcome/SignInActivityDriver.kt`) is registered in `AndroidManifest.xml` and launched from `MainActivityDriver` — it is **not** orphaned. As of 2026-07-28 its auth flow (email verification gate + resend button, input validation, friendlier error messages, auto-redirect for an already-logged-in driver, wiring the "Sign Up" text link) was brought to parity with the passenger-side `SignInActivity`. Driver-specific checks (`isApproved`, `isActive`) remain on top of that. `DriverQualificationActivity` is the one that actually sends the verification email and signs the driver out after they submit qualification docs — by design, not a bug.
