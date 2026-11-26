# SOGO CHAT & MEET — Phase 2.10: Enforce Google Sign-In in Firestore Rules Report

**Date:** 2026-11-24  
**Mode:** `IMPLEMENT + TEST + DOCUMENT`  
**Scope:** Minimal `isSignedIn()` Google-provider enforcement in Firestore rules, directly affected emulator tests, and focused reporting only (no rule deployment, no Storage rule edits, no Console/claims/production changes).  
**Target Firebase Project / Named DB:** `gen-lang-client-0431487205` / `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`  
**Git Commit Hash:** `NOT VERIFIED` (`.git` directory absent in container)

---

## 1. Executive Summary & Classifications

| Area / Check | Classification | Summary |
|---|---|---|
| **Minimal `isSignedIn()` Google-Provider Rule Change (`firestore.rules`)** | **PASS** | Enforces non-null auth, `'firebase' in request.auth.token`, `firebase is map`, `'sign_in_provider' in firebase`, and `sign_in_provider == 'google.com'`. Preserves all ownership, block, Owner claims, `/verification_requests`, `/activities`, and `/profile_visits` protections. |
| **Proposed Rules Parity (`firestore.owner-admin.proposed.rules`)** | **PASS** | Identical `isSignedIn()` condition applied (`L11-17`); zero functional differences between `firestore.rules` and `firestore.owner-admin.proposed.rules`. |
| **Non-Google & Missing Provider Denial (`firestore.test.js`, `firestore.owner-admin.test.js`)** | **PASS** | Explicitly verified `assertFails` for `password`, `anonymous`, `custom`, missing `firebase` claim, empty `firebase: {}`, malformed `firebase: "google.com"`, and Owner tokens with `{ email, email_verified, owner, admin }` paired with non-Google/missing providers across `/users`, `/conversations`, `/activities`, `/profile_visits`, `/admin_dashboard`, and `/verification_requests`. |
| **Firestore Emulator Rules Suite (`firestore.test.js`)** | **PASS** | **34 passed, 0 failed, 0 skipped** (all legitimate contexts updated to `googleContext(...)` + new `Google Provider Enforcement` suite). |
| **Owner-Admin Emulator Rules Suite (`firestore.owner-admin.test.js`)** | **PASS** | **5 passed, 0 failed, 0 skipped** (includes new `Google Provider Enforcement` parity test). |
| **Storage Emulator Rules Suite (`storage.test.js`)** | **PASS** *(Local `(default)` Emulator Only)* | **24 passed, 0 failed, 0 skipped** (`storage.rules` untouched per Phase 2.10 scope; named-DB cross-service validation remains `NOT VERIFIED`). |
| **Admin Security Tests (`admin-tools/owner-security.test.js`)** | **PASS** | **4 passed, 0 failed, 0 skipped**. |
| **Android Build & Unit Test Compilation (`compile_applet`, `compileDebugUnitTestKotlin`)** | **PASS** | Both app compilation and unit-test compilation (including updated `FirestoreEmulatorTestBase.kt`) succeeded cleanly. |
| **Android Unit & SDK-35 Robolectric Tests (9 classes)** | **PASS** | **48 passed, 0 failed, 0 skipped**. |
| **Auth Emulator Google IdP Test-Token Flow (`FirestoreEmulatorTestBase.kt`)** | **PASS** (Auth Emulator API + Compilation) / **BLOCKED** (Robolectric SDK-36 Execution) | Verified Auth Emulator `accounts:signInWithIdp` mints `firebase.sign_in_provider == "google.com"` (`STATUS 200`); updated `FirestoreEmulatorTestBase.kt` to use `GoogleAuthProvider.getCredential(...)` and assert `sign_in_provider == "google.com"`. Execution of `SogoRepositoryRuleTest`, `AuthComponentsTest`, and `FirebaseAuthListenerTest` remains **BLOCKED** by Maven Central HTTP `429`. |
| **Expression-Limit Verification** | **PASS** | Adding `sign_in_provider == 'google.com'` adds 4 expressions per `isSignedIn()` check; all `/activities` updates and social mutations pass without hitting the 1,000-expression limit. |
| **Live Console Provider Lockdown & Existing Production Sessions** | **NOT VERIFIED** | No live Firebase Console queries, production data reads, user sign-outs, claim updates, or deployments were performed. |

---

## 2. Exact Files Changed

| File | Scope of Change |
|---|---|
| `firestore.rules` | Updated `isSignedIn()` (`L11-17`) to require `firebase` map + `sign_in_provider == 'google.com'`. |
| `firestore.owner-admin.proposed.rules` | Applied identical `isSignedIn()` (`L11-17`) update for 100% functional parity. |
| `firestore.test.js` | Added `googleContext(uid, extraClaims)` (`L18-27`), updated legitimate test contexts to pass `firebase: { sign_in_provider: "google.com" }`, and added `Google Provider Enforcement` suite (`L1672-1832`). |
| `firestore.owner-admin.test.js` | Added `GOOGLE_AUTH_CLAIMS` (`L19-23`), updated `ownerDb()` and legitimate member contexts (`L25-272`), and added `Google Provider Enforcement` parity test (`L275-330`). |
| `app/src/test/java/com/example/base/FirestoreEmulatorTestBase.kt` | Replaced `signInWithEmailAndPassword` / `createUserWithEmailAndPassword` (`L83-120`) with `GoogleAuthProvider.getCredential(buildEmulatorGoogleIdToken(email), null)` and explicit `sign_in_provider == "google.com"` token verification. |
| `docs/PHASE_2_10_GOOGLE_PROVIDER_RULES_REPORT.md` | Created this remediation and verification report. |

**Unchanged by strict scope:** `storage.rules`, `firebase.json`, `app/src/main/**`, `app/build.gradle.kts`, `docs/FINAL_FIREBASE_DEPLOYMENT_CHECKLIST.md`, live Firebase Console, Owner custom claims, and production Firestore/Auth data.

---

## 3. Final `isSignedIn()` Condition & Rules Parity

Updated identically in `firestore.rules:11-17` and `firestore.owner-admin.proposed.rules:11-17`:

```rules
    function isSignedIn() {
      return request.auth != null &&
             'firebase' in request.auth.token &&
             request.auth.token.firebase is map &&
             'sign_in_provider' in request.auth.token.firebase &&
             request.auth.token.firebase.sign_in_provider == 'google.com';
    }
```

- **Cascading Coverage:** `isOwner(userId)` (`L19-21`) and `isVerifiedOwner()` (`L23-29`) both invoke `isSignedIn()`, and every collection/subcollection `allow` block in `firestore.rules` delegates through `isSignedIn()`, `isOwner(...)`, `isVerifiedOwner()`, or `false`.
- **Safe Malformed-Claim Handling:** Guarding `'firebase' in request.auth.token && request.auth.token.firebase is map && 'sign_in_provider' in request.auth.token.firebase` ensures missing or non-map claims evaluate cleanly to `false` (returning `PERMISSION_DENIED`) rather than throwing undefined-property evaluation errors.
- **Rules Parity Result:** `firestore.rules` and `firestore.owner-admin.proposed.rules` remain functionally identical; `firestore.owner-admin.test.js` compiles and passes (`5/5`) directly against `firestore.owner-admin.proposed.rules`.
- **Expression-Limit Result:** Adding 4 sub-checks adds only 4 expressions per `isSignedIn()` evaluation (once per `/activities/{postId}` update). All `/activities` like/reaction/comment updates pass cleanly with zero expression-limit regressions. *(The pre-existing expected-denial test at `firestore.test.js:1145` where Owner attempts forbidden `isVerifiedMember`/`verificationStatus` writes on `/users/{userId}` still fails closed with `PERMISSION_DENIED`.)*

---

## 4. Non-Google & Missing Provider Denial Confirmation

Both `firestore.test.js` (`L1672-1832`) and `firestore.owner-admin.test.js` (`L275-330`) confirm that while Google-authenticated controls succeed (`assertSucceeds`), all of the following tokens fail (`assertFails`):
- `firebase: { sign_in_provider: "password" }`
- `firebase: { sign_in_provider: "anonymous" }`
- `firebase: { sign_in_provider: "custom" }`
- Missing `firebase` claim (`authenticatedContext(uid, {})`)
- Empty `firebase: {}` map and malformed non-map `firebase: "google.com"`
- Owner token carrying valid `{ email: "gptgamestudioph@gmail.com", email_verified: true, owner: true, admin: true }` paired with `password`, `anonymous`, `custom`, missing `firebase`, empty `firebase: {}`, or `firebase: "google.com"`

Verified paths denied for non-Google/missing providers:
- `/users/{userId}` (`get`, `create`, `update`, including Owner `accountStatus` suspension update)
- `/conversations/{conversationId}` (`get`, `create`)
- `/activities/{postId}` (`get`, `create`, `likes` update)
- `/profile_visits/{visitId}` (`get`, `create`, `update`)
- `/admin_dashboard/{docId}` (`get`)
- `/verification_requests/{requestId}` (`get`, `list`)

---

## 5. Android Auth Emulator Test-Token Approach & Evidence

`FirestoreEmulatorTestBase.kt` previously authenticated via `auth.signInWithEmailAndPassword` / `createUserWithEmailAndPassword`, which issued Auth Emulator tokens with `firebase.sign_in_provider == "password"`.

1. **Auth Emulator Verification Executed First:**  
   Command:
   ```bash
   firebase emulators:exec --only auth --project demo-no-project "node -e '...accounts:signInWithIdp (id_token=<unsigned alg:none JWT>, providerId=google.com)...'"
   ```
   Observed Output:
   ```text
   STATUS: 200 providerId: google.com localId: lNHRBwon95YbaA541Pfc0O9ENK9r
   JWT_CLAIMS: {"identities":{"email":["alice_sogo@test.com"],"google.com":["google_alice_123"]},"sign_in_provider":"google.com"} email_verified: true
   ```
2. **Updated `FirestoreEmulatorTestBase.kt` (`L83-120`):**  
   `signInTestUser(email)` now constructs a deterministic unsigned Google IdP test JWT (`alg: "none"`, `sub`, `email`, `email_verified: true`, `iss: "https://accounts.google.com"`, `aud: "demo-no-project"`), signs in via `GoogleAuthProvider.getCredential(unsignedGoogleJwt, null)` + `auth.signInWithCredential(credential).await()`, and explicitly asserts:
   - `tokenResult.claims["firebase"]["sign_in_provider"] == "google.com"`
   - `user.providerData.any { it.providerId == "google.com" }`
3. **Compilation & Execution Status:**  
   - `gradle :app:compileDebugUnitTestKotlin` compiled cleanly (`BUILD SUCCESSFUL`).  
   - End-to-end execution of `SogoRepositoryRuleTest` against the updated Auth Emulator flow remains **`BLOCKED`** locally because Robolectric's SDK-36 dependency download receives Maven Central HTTP `429`.

---

## 6. Exact Verification Commands & Executed Test Counts

> **Emulator Scope Disclaimer:** All emulator tests ran locally against `--project demo-no-project`. Zero rules were deployed and zero live Firebase calls were executed.

| Verification | Exact Command | Result / Executed Counts |
|---|---|---|
| **Android Compilation** | `compile_applet` | **PASS** — `Build succeeded - the applet is compiled` |
| **Android Unit Test Compilation** | `gradle :app:compileDebugUnitTestKotlin` | **PASS** — `BUILD SUCCESSFUL in 1m 28s` |
| **Firestore, Owner-Admin, Storage & Admin-Tools Suites** | `firebase emulators:exec --only auth,firestore,storage --project demo-no-project "node --test --test-reporter=spec firestore.test.js && node --test --test-reporter=spec firestore.owner-admin.test.js && node --test --test-reporter=spec storage.test.js" && node --test --test-reporter=spec admin-tools/owner-security.test.js` | **PASS (Exit `0`)** — `firestore.test.js`: **34/34 passed**; `firestore.owner-admin.test.js`: **5/5 passed**; `storage.test.js`: **24/24 passed** *(local `(default)` emulator only)*; `admin-tools/owner-security.test.js`: **4/4 passed** |
| **Android Unit & SDK-35 Robolectric Tests (9 classes)** | `gradle :app:testDebugUnitTest --tests "com.example.ExampleUnitTest" --tests "com.example.OwnerAdminTest" --tests "com.example.RegressionRepairsTest" --tests "com.example.ads.*" --tests "com.example.data.model.*" --tests "com.example.ui.auth.GoogleAuthOnlyRegressionTest" --tests "com.example.ui.auth.AuthValidatingCardTest" --tests "com.example.ui.safety.*" --tests "com.example.ui.profile.*"` | **PASS (Exit `0`, `BUILD SUCCESSFUL in 28s`)** — **48/48 passed** (`ExampleUnitTest`: 1, `OwnerAdminTest`: 5, `RegressionRepairsTest`: 21, `AdMobManagerTest`: 5, `ModelsSerializationTest`: 4, `AuthValidatingCardTest`: 1, `GoogleAuthOnlyRegressionTest`: 7, `MemberProfileSetupScreenTest`: 2, `CommunitySafetyGuidelinesTest`: 2) |
| **`SogoRepositoryRuleTest` + SDK-36 Classes** | 1. `firebase emulators:exec --only auth,firestore --project demo-no-project "gradle :app:testDebugUnitTest --tests com.example.data.repository.SogoRepositoryRuleTest --tests com.example.ui.auth.AuthComponentsTest --tests com.example.ui.auth.FirebaseAuthListenerTest --continue"`<br>2. `gradle :app:testDebugUnitTest --tests com.example.ui.auth.FirebaseAuthListenerTest`<br>3. `gradle :app:testDebugUnitTest --tests com.example.ui.auth.AuthComponentsTest` | **BLOCKED (Exit `1` on all 3 classes)** — `java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:16-robolectric-13921718-i7` (`HTTP response code: 429 for URL: https://repo1.maven.org/maven2/...`) |

---

## 7. Remaining Production Rollout Risks

1. **Immediate Denial of Existing Non-Google Sessions:** Deploying `firestore.rules` will immediately return `PERMISSION_DENIED` on all Firestore reads and writes for any device still holding a cached non-Google Firebase Auth session (e.g., legacy Email/Password or Anonymous accounts created prior to Google-only migration).
2. **Existing Production-Session Impact (`NOT VERIFIED`):** Whether any active users in `gen-lang-client-0431487205` currently authenticate with non-Google providers was not queried or verified in this task; no users were signed out and no accounts were migrated.
3. **Live Console Provider Lockdown (`NOT VERIFIED`):** Firebase Console Authentication for `gen-lang-client-0431487205` remains **`NOT VERIFIED`** and must still restrict enabled sign-in providers to **Google** only (disabling Email/Password and Anonymous) independently of Firestore rules to prevent non-Google Firebase Auth account creation.
4. **Pending External/Pre-Existing Items:** SDK-36 Robolectric execution (`SogoRepositoryRuleTest`, `AuthComponentsTest`, `FirebaseAuthListenerTest`) remains **BLOCKED** by Maven Central HTTP `429`; `storage.rules` was untouched and its named-database lookup mismatch remains **NOT VERIFIED** for production Storage enforcement (exclude `storage.rules` when deploying Firestore rules).

---

**Deployment Status:** No rules were deployed (`firestore.rules` and `storage.rules` remain local only). Waiting for explicit approval before deployment.
