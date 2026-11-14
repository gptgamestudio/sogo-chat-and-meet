# SOGO CHAT AND MEET

Private Android repository for **SOGO CHAT AND MEET** (`com.sogo.chatandmeet`), built with Kotlin, Jetpack Compose (Material 3), Firebase Authentication (Google Sign-In via Jetpack Credential Manager), Cloud Firestore, and Firebase Storage security rules.

## Repository Structure

- `app/` — Android application source code, resources, Gradle build configuration, and Robolectric unit/rule tests.
- `firestore.rules` — Cloud Firestore security rules matching `Models.kt` and `SogoRepository.kt`.
- `storage.rules` — Firebase Storage security rules for `/profile_photos`, `/chat_photos`, and `/voice_messages` with cross-service Firestore authorization and block enforcement.
- `firestore.indexes.json` — Required Firestore composite indexes.
- `firebase.json` — Local Firebase Emulator and rules configuration.
- `firebase-blueprint.json` — Firestore entity schema documentation.
- `firestore.test.js` & `storage.test.js` — Node.js security rules test suites using `@firebase/rules-unit-testing`.
- `.github/workflows/ci.yml` — Verification-only CI workflow (no Firebase deployment).

## Local Development Setup

### 1. Android Debug Keystore (Outside AI Studio)
`debug.keystore` and `debug.keystore.base64` are excluded from Git. If cloning onto a new machine, generate a standard local debug keystore in the repository root before running Gradle:

```bash
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

### 2. Firebase & AdMob Configuration
- `app/google-services.json` is configured for Firebase project `gen-lang-client-0431487205` (`com.sogo.chatandmeet`, Firestore database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`).
- AdMob SDK (`com.google.android.gms:play-services-ads`) is configured with App ID `ca-app-pub-9786472984542330~4017093946` (`AndroidManifest.xml` + `strings.xml`), automatically using Google sample test ad units in `DEBUG` builds and production ad units in `RELEASE` builds (`AdMobManager.kt`).
- `app-ads.txt` is stored at the repository root (and `public/app-ads.txt`) for deployment at the root of your Google Play Developer Website domain (e.g., `https://<username>.github.io/app-ads.txt`).
- Never commit `.env`, `*.jks`, service account JSONs, or personal access tokens.
- Ensure your debug/release APK SHA-1 and SHA-256 fingerprints are registered in Firebase Console for Google Sign-In.

## Running Verification Tests (Emulators Only)

These tests run exclusively against local Firebase Emulators (`demo-no-project`) and never touch production data or rules:

```bash
# Install JS rules test dependency
npm install

# Run Firestore & Storage security rules suites inside Firebase Emulators
npm run test:rules
```

To run Android unit and Robolectric tests (requires Gradle and running Auth + Firestore emulators on `127.0.0.1:9099` and `127.0.0.1:8085`):

```bash
gradle :app:testDebugUnitTest
```

## Security & Deployment Policy

- **No automatic Firebase deployment**: `.github/workflows/ci.yml` only validates configs and compiles/tests locally.
- Production `firestore.rules`, `storage.rules`, and `firestore.indexes.json` must only be reviewed and deployed manually by an authorized maintainer.
