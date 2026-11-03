# Ambulansku - Emergency Utility App

An Android native utility application designed to streamline emergency data handling and user authentication. Built with a strong focus on clean architecture, efficient data flow, and reliable backend integration.

## 🚀 Key Features
*   **Secure User Authentication:** Integrated with Firebase Authentication for seamless and secure user login/registration.
*   **Real-time Data Management:** Utilizes Firebase (Firestore/Realtime Database) to ensure data is synced efficiently without memory leaks.
*   **Live Ambulance Tracking:** The ambulance appears as a top-down vehicle icon that glides smoothly and turns with the direction of travel, and the part of the route it has already driven disappears from the map as it moves, for both passenger and driver.
*   **Traffic-Aware Navigation:** Both trip maps show the Google Maps traffic layer (green = smooth, orange = busy, red = jammed), and arrival estimates take current traffic into account instead of assuming empty roads.
*   **Color-Coded Route:** The route line is **purple** while the ambulance is on its way to pick up the patient and **blue** while it is heading to the hospital. Neither color is used by the traffic layer, so the route never gets mixed up with congested roads.
*   **Clean UI Flow:** A straightforward, responsive user interface built natively to ensure optimal performance.
*   **Robust Configuration:** Project dependencies and build logic are cleanly structured using **Groovy DSL**, ensuring maintainability and fast build times.

## 🛠️ Tech Stack
*   **Language:** Kotlin / XML
*   **Architecture:** Native Android SDK
*   **Backend & Database:** Firebase Authentication, Cloud Firestore
*   **Build Tool:** Gradle (Groovy DSL)

## 🔑 Test Accounts
Use these ready-made accounts to try the app without signing up. Both are already email-verified, and the driver is already approved by the admin, so you can log in right away.

| Role | Email | Password | Where to log in |
|---|---|---|---|
| Passenger | `passenger@ambulansku.test` | `12345678` | Role selection → **I want to be a Passenger** → Sign In |
| Driver | `driver@ambulansku.test` | `12345678` | Role selection → **I want to be a Driver** → Sign In |

To test a full trip, log in as the passenger on one phone and as the driver on another, set the driver **online**, then call an ambulance from the passenger account.

Notes:
*   An account can only be active on **one device at a time**. If someone else logs in with the same test account, you will be logged out automatically.
*   The `@ambulansku.test` addresses are not real mailboxes, so features that send email (password reset, re-sending verification) will not deliver anything.
*   An admin test account exists but is not published here, because this repository is public. Contact the repository owner if you need admin access.

## 📸 Screenshots
<img width="746" height="1600" alt="image" src="https://github.com/user-attachments/assets/e01a4c21-aa8a-4b52-a44c-b8c5b8638bd4" />
<img width="746" height="1600" alt="image" src="https://github.com/user-attachments/assets/c3c99dcc-cb9b-4080-a7c6-47f61c129e8a" />
<img width="746" height="1600" alt="image" src="https://github.com/user-attachments/assets/78e9cd70-5fcb-4e29-8527-f453c6fa0f64" />

## 💡 Technical Highlights
As a developer with an Information Systems background, my primary focus in this project was the backend logic and data structure. The integration between the Android client and Firebase is optimized to handle asynchronous requests smoothly, preventing UI freezes and ensuring a reliable user experience.
