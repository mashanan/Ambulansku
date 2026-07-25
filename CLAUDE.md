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

FirebaseAuth is used for sign-in/sign-up (including Google Sign-In via Credential Manager / `googleid`). Firebase Storage is used for profile photo uploads (`ActivityEditFotoProfile`).

### Known inconsistencies (real, not hypothetical — verify before assuming)

- **Package declarations frequently do not match the directory a file lives in.** e.g. `ForDriver/Welcome/SignInActivityDriver.kt` declares `package com.example.tugasakhirfinal.ForPassenger.Welcome`; `ForDriver/Welcome/SignUpActivityDriver.kt` declares `package com.example.tugasakhirfinal.ForDriver` (not `.Welcome`). Model/Adapter classes physically nested in feature folders (e.g. `ForDriver/Booking.kt`, `ForPassenger/Chat/PassengerChatAdapter.kt`) declare flat `com.example.tugasakhirfinal.Model` / `.Adapter` packages instead. **Do not infer a class's package from its file path — check the `package` line or grep for the class name / import.** This also means `AndroidManifest.xml` activity entries that look "stale" against the folder layout (e.g. `.ForPassenger.ActivityDrive` for a class physically under `ForPassenger/DriveFragment/`) are usually still correct, because they match the class's *declared package*, not its folder. Verify against the `package` line before assuming a manifest entry is wrong.
- **`SignInActivityDriver` (`ForDriver/Welcome/SignInActivityDriver.kt`) is orphaned** — not registered in `AndroidManifest.xml` and not launched anywhere in the codebase. The driver-side sign-in flow currently appears unfinished/unwired; treat this as a known gap, not something to "fix" incidentally.
- Passenger spelling was normalized project-wide on 2026-07-25 (was previously "Passanger" throughout code, resources, and layouts — `ForPassanger`, `PassangerHomeActivity`, `item_passanger_chat.xml`, etc.). All identifiers, file names, and layout files now consistently spell "Passenger".
