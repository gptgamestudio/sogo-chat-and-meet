# SOGO CHAT & MEET — Final Firebase Deployment Checklist

**Date:** 2026-11-24  
**Baseline:** Phase 2.8 Pre-Deployment Audit (`docs/PHASE_2_8_PRE_DEPLOYMENT_SECURITY_READINESS_REPORT.md`) & Phase 2.9 Profile Visits Remediation (`docs/PHASE_2_9_PROFILE_VISITS_REMEDIATION_REPORT.md`)  
**Git Commit Hash:** `NOT VERIFIED` (`.git` directory absent in build container)  

---

## 1. Project & Deployment Target Configuration

| Check Item | Status | Verified Value / Evidence |
|---|---|---|
| **Firebase Project ID** | **PASS** (Config) / **NOT VERIFIED** (`.firebaserc`) | `gen-lang-client-0431487205` (project number `492343241434`, Android package `com.sogo.chatandmeet`, `app/google-services.json:3-13`). `.firebaserc` is absent in workspace root; CLI must pass `--project gen-lang-client-0431487205`. |
| **Firestore Database ID** | **PASS** | Named database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` (`firebase.json:4`, `app/src/main/res/values/firebase_applet_config.xml:3`, `SogoRepository.kt:2144`, `admin-tools/owner-admin-sync.mjs:29`). |
| **Configured Firestore Rules & Indexes** | **PASS** | `firebase.json:2-7` maps database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` to `firestore.rules` and `firestore.indexes.json`. |
| **Configured Storage Rules** | **FAIL** (For Production Storage Deployment) | `firebase.json:9-11` also maps `"storage": { "rules": "storage.rules" }`. Because `storage.rules:52-74` still queries `/databases/(default)/documents/...`, bare `firebase deploy` must **not** be run. |

---

## 2. Rules Parity (`firestore.rules` vs `firestore.owner-admin.proposed.rules`)

| Check Item | Status | Evidence |
|---|---|---|
| **Functional Parity Check** | **PASS** | `0` unexplained or functional differences between `firestore.rules` (1,085 lines) and `firestore.owner-admin.proposed.rules` (1,077 lines). Only the 8 pre-existing explanatory comment lines (`// Legacy verification fields...`, `// Action 1..3/2..4...` at `firestore.rules:69, 243, 246, 859, 861, 998, 1003, 1007`) differ. Phase 2.7 and Phase 2.9 (`/profile_visits/{visitId}`) fixes are synced in both files. |

---

## 3. Current Verification & Test Results (Post-Phase 2.9 Edits)

| Suite / Check | Status | Result |
|---|---|---|
| **Android Build (`compile_applet`)** | **PASS** | `Build succeeded - the applet is compiled`. |
| **Firestore Emulator Rules (`firestore.test.js`)** | **PASS** | `33 passed, 0 failed, 0 skipped` (local `--project demo-no-project`; includes `/profile_visits/{visitId}` ownership, deterministic ID, immutability, and anti-overwrite tests). |
| **Owner-Admin Parity Rules (`firestore.owner-admin.test.js`)** | **PASS** | `4 passed, 0 failed, 0 skipped` (includes `/profile_visits` cross-user overwrite parity test). |
| **Admin-Tools Unit Tests (`admin-tools/owner-security.test.js`)** | **PASS** | `4 passed, 0 failed, 0 skipped`. |
| **Storage Emulator Rules (`storage.test.js`)** | **PASS** *(Local `(default)` Emulator Only)* | `24 passed, 0 failed, 0 skipped` — exercises local `(default)` Firestore instance only; does **not** validate production named-DB cross-service lookups. |
| **Android Unit & SDK-35 Robolectric Tests (9 classes)** | **PASS** | `48 passed, 0 failed, 0 skipped` (`ExampleUnitTest`, `OwnerAdminTest`, `RegressionRepairsTest`, `AdMobManagerTest`, `ModelsSerializationTest`, `AuthValidatingCardTest`, `GoogleAuthOnlyRegressionTest`, `MemberProfileSetupScreenTest`, `CommunitySafetyGuidelinesTest`). |
| **SDK-36 Robolectric Classes (`SogoRepositoryRuleTest`, `AuthComponentsTest`, `FirebaseAuthListenerTest`)** | **BLOCKED** | Maven Central (`repo1.maven.org`) returned `HTTP response code: 429` fetching `org.robolectric:android-all-instrumented:16-robolectric-13921718-i7.jar`. |
| **Android Instrumented Tests (`androidTest`)** | **NOT VERIFIED** | No Android emulator / ADB available in build environment. |

---

## 4. Remaining Production-Only Checks (Pre-/Post-Deploy Manual Gates)

| Production Check | Status | Required Action / Notes |
|---|---|---|
| **Google Sign-In Provider Lock (Firebase Auth Console)** | **NOT VERIFIED** | Confirm in Firebase Console (`gen-lang-client-0431487205` -> Authentication -> Sign-in method) that **Google** is enabled and **Email/Password** and **Anonymous** are disabled. Required because `firestore.rules:11-13` (`isSignedIn()`) checks `request.auth != null` without enforcing `sign_in_provider == 'google.com'`. |
| **Owner Custom Claims (`gptgamestudioph@gmail.com`)** | **NOT VERIFIED** | Confirm or provision `{ owner: true, admin: true }` custom claims on `gptgamestudioph@gmail.com` in `gen-lang-client-0431487205` via `admin-tools/owner-admin-sync.mjs --grant-claims-only`. |
| **Storage Named-Database Behavior (`storage.rules`)** | **NOT VERIFIED** | `storage.rules:52-74` references `/databases/(default)/documents/{user_blocks,blocks,conversations}` instead of `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`. Safe only while the Android app omits `firebase-storage` and stores compact JPEG data URIs in Firestore; **do not deploy `storage.rules`** until updated and validated for the named DB. |
| **Play Integrity App Check & Live Production State** | **NOT VERIFIED** | Confirm App Check Play Integrity enforcement for release signing fingerprints and verify zero unintended mutations to live user data or frozen `/verification_requests`. |

---

## 5. Exact Firestore-Only Deployment Command (Do Not Execute Until Approved)

```bash
firebase deploy --only firestore:rules --project gen-lang-client-0431487205
```

- Targets **only** `firestore.rules` for database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` as configured in `firebase.json:2-7`.
- Excludes `storage.rules` (`firebase.json:9-11`) due to the unverified named-database lookup mismatch.
- If `firestore.indexes.json` also needs to be synced alongside rules, use `--only firestore --project gen-lang-client-0431487205` (still excludes `storage`). Do **not** run `firebase deploy` without `--only`.

---

## 6. Final Recommendation

**`CONDITIONALLY READY`**

**Basis:**
- **Ready for Firestore-only rule rollout (`firestore.rules`):** All Phase 2.6–2.9 Firestore rule remediations are complete, `firestore.rules` and `firestore.owner-admin.proposed.rules` have zero functional differences, and all local Firestore/Owner-Admin emulator suites (`33/33` and `4/4`) plus `48/48` SDK-35 Android tests pass.
- **Conditions required for safe production deployment:**
  1. Deploy **strictly with `--only firestore:rules --project gen-lang-client-0431487205`** (do **not** deploy `storage.rules` until `storage.rules:52-74` is updated and verified for named database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`).
  2. Manually verify in Firebase Console / Admin SDK for `gen-lang-client-0431487205` that **Google** is the sole enabled Auth provider, **`gptgamestudioph@gmail.com`** holds `{ owner: true, admin: true }` custom claims, and **App Check (Play Integrity)** is configured for release builds.
  3. Re-run `SogoRepositoryRuleTest`, `AuthComponentsTest`, and `FirebaseAuthListenerTest` in an environment without Maven Central HTTP `429` rate limiting (or with SDK 36 Robolectric cached) if full Android SDK-36 verification is required prior to release.
