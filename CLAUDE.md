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
- `ForPassanger/` — passenger-side Welcome (sign in/up), `PassangerHomeActivity` hosting bottom-nav `Fragment/` (Home, ActivityDrive, Chat, Profile), `DriveFragment/` (hospital search/booking flow, map), `EditProfileFragment/` (profile & address editing), `Chat/`.
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

- **Package declarations frequently do not match the directory a file lives in.** e.g. `ForDriver/Welcome/SignInActivityDriver.kt` declares `package com.example.tugasakhirfinal.ForPassanger.Welcome`; `ForDriver/Welcome/SignUpActivityDriver.kt` declares `package com.example.tugasakhirfinal.ForDriver` (not `.Welcome`). Model/Adapter classes physically nested in feature folders (e.g. `ForDriver/Booking.kt`, `ForPassanger/Chat/PassangerChatAdapter.kt`) declare flat `com.example.tugasakhirfinal.Model` / `.Adapter` packages instead. **Do not infer a class's package from its file path — check the `package` line or grep for the class name / import.**
- **`AndroidManifest.xml` activity entries are stale for some Passenger screens.** It declares `.ForPassanger.ActivityDrive`, `.ForPassanger.HospitalListActivity`, and `.ForPassanger.ChatPassengerActivity`, but the actual classes live at `ForPassanger.DriveFragment.ActivityDrive`, `ForPassanger.DriveFragment.HospitalListActivity`, and `ForPassanger.ChatPassangerActivity` (note the manifest's "ChatPassenger**e**r" vs the real, consistently-misspelled "ChatPassang**e**rActivity"). If you add/rename activities in this area, double-check the manifest still matches the real FQCN, since it currently doesn't for these three.
- **"Passenger" is spelled "Passanger" throughout code, resources, and layouts** (`ForPassanger`, `PassangerHomeActivity`, `ChatPassangerActivity`, `item_passanger_chat.xml`, `activity_main_passanger.xml`, etc.). Match the existing spelling exactly rather than "fixing" it, since a partial rename will break references.
