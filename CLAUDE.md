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

Requires `app/google-services.json` (already checked in) for Firebase to initialize, and a Google Maps API key in **`local.properties`** as `MAPS_API_KEY=...` (gitignored; never commit it, the repo is public). `app/build.gradle` turns it into the generated `R.string.google_maps_key` via `resValue`. Directions API requests send `X-Android-Package`/`X-Android-Cert` headers (`Maps/GoogleApiHeaders.kt`) so the key can be restricted to this app in Google Cloud Console. The hospital data scripts in `scripts/` read `PLACES_API_KEY` (or `MAPS_API_KEY`) from the same file.

Build with a JDK 17/21 as `JAVA_HOME`: Android Studio 2026.x bundles Java 25, which Gradle 8.11 cannot run ("Unsupported class file major version 69").

Firestore security rules live in `firestore.rules` (deploy: `firebase deploy --only firestore:rules`). Bookings and their `messages` are restricted to the booking's passenger, its assigned driver, drivers viewing `SEARCHING` bookings, and admins.

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

Exception: map helpers shared by both roles' trip screens live in `Maps/` (added 2026-11-04):
- `RouteGeometry` — pure Kotlin (unit tested in `src/test/.../Maps/RouteGeometryTest.kt`): trims the traveled part of a route by projecting the vehicle onto the nearest route segment, plus distance/bearing helpers.
- `VehicleMarker.kt` — dp-sized ambulance icon with a soft shadow (`VehicleMarkerIcon`) and smooth position/rotation animation (`SmoothMarkerAnimator`).
- `RouteStyle` — route line colors shared by both trip screens (pickup = purple, to-hospital = blue). Deliberately avoids green/orange/red, which the Google traffic layer (`isTrafficEnabled`, on in both screens) uses for congestion.
Both trip screens request Directions with `departure_time=now` and prefer `duration_in_traffic` for ETAs. They fetch the Directions API route once per leg, trim it locally on each location update, re-fetch only when the vehicle is more than 50 m off the route (throttled to once per 10 s), and drop stale responses via a request sequence number.

### v2 design system (UI redesign, started 2026-11-11)

The UI is being migrated to a "v2" design system that mirrors the Figma file desain-TA (section "v2 · Design System"). Use it for any new or touched layout instead of the legacy `pink_*` colors / `rounded_button`:
- Tokens: `res/values/colors_v2.xml` (`v2_brand` #D63B27 for actions — darker than the old #FF624E so white text passes contrast; `v2_brand_accent` #FF624E for gradients/decoration). Theme `Theme.TugasAkhirFinal` now uses these + app-wide font Plus Jakarta Sans (`res/font/`, OFL license in `app/licenses/`).
- Styles: `res/values/styles_v2.xml` — `TextAppearance.V2.*`, `Widget.V2.Button.{Primary,Secondary,Tertiary,Danger}[.Medium]`, `Widget.V2.TextInputLayout`, `Widget.V2.Card`, `Widget.V2.Badge.*`, `Widget.V2.BottomNav`, `Widget.V2.IconBox`, `Widget.V2.CircleButton`.
- Icons: `ic_v2_*.xml` stroke vectors (recolor with `app:tint`); shapes `bg_v2_*.xml` (tint with `android:backgroundTint`).
- All passenger, driver and admin layouts are v2 (passenger designed in Figma first; driver/admin designed directly in code with the same system because the Figma Starter MCP quota ran out). Legacy names were remapped so leftovers still match: `pink_main` = v2 brand, `pink_button` = v2 danger, `rounded_button`/`bg_frame` use v2 radius/colors, legacy TextInputLayout styles inherit `Widget.V2.TextInputLayout`. Layout rewrites kept every view ID that Kotlin references; some view classes changed (e.g. card → MaterialButton/LinearLayout) where code only sets click listeners.
- Font gotcha: never set `android:fontFamily` in the theme (it overrides every TextAppearance weight). The default family comes from `android:textViewStyle` → `TextAppearance.V2.Default`, which must keep a sized parent (`TextAppearance.Material.Small`) or unsized TextViews shrink to 15px.

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
