# SOGO CHAT & MEET — Phase 2.9: Profile Visits Security Remediation Report

**Remediation Date:** 2026-11-24  
**Mode:** `IMPLEMENT + TEST + DOCUMENT`  
**Scope:** Targeted Firestore security fix for `/profile_visits/{visitId}` only (plus parity sync to `firestore.owner-admin.proposed.rules` and emulator regression tests)  
**Target Firebase Project:** `gen-lang-client-0431487205` (`com.sogo.chatandmeet`)  
**Configured Named Firestore Database:** `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` (`firebase.json:4`, `app/src/main/res/values/firebase_applet_config.xml:3`)  
**Git Branch / Commit Hash:** **NOT VERIFIED** — `.git` directory is absent in the build container (`git rev-parse HEAD` → `fatal: not a git repository (or any of the parent directories): .git`).

---

## 1. Executive Summary & Classifications

| Area / Criterion | Classification | Summary |
|---|---|---|
| **`/profile_visits/{visitId}` Security Fix (`firestore.rules`)** | **PASS** | Split `allow create, update:` into dedicated `create` and `update` rules backed by `isValidProfileVisit(data, visitId)`, deterministic ID binding (`'visit_' + request.auth.uid + '_' + data.visitedUserId`), `existing().visitorId == request.auth.uid`, immutable identity locks (`visitId`, `visitorId`, `visitedUserId`), and strict `affectedKeys()` allowlist. |
| **Proposed Rules Parity (`firestore.owner-admin.proposed.rules`)** | **PASS** | Applied the exact same `isValidProfileVisit` helper and `/profile_visits/{visitId}` rules; `diff -u firestore.rules firestore.owner-admin.proposed.rules` confirms zero functional differences (8 comment-only lines differ). |
| **Android Repository Compatibility (`SogoRepository.kt`, `OwnProfileTab.kt`, `Models.kt`)** | **PASS (No App Code Changes Needed)** | Inspected `SogoRepository.recordProfileVisit()` (`SogoRepository.kt:1219-1248`), `observeProfileVisitors()` (`L1200-1217`), `ChatViewModel.kt:274-282`, `MainDashboardViewModel.kt:796-803`, `OwnProfileTab.kt:353-376`, and `ProfileVisitorRecord` (`Models.kt:226-234`). Android already writes deterministic `visit_${uid}_${targetUid}` documents using `set(payload)` with exact allowed keys and `FieldValue.serverTimestamp()`. |
| **Firestore Security Emulator Suite (`firestore.test.js`)** | **PASS** | **33 passed, 0 failed, 0 skipped** — includes new `Profile Visits Security` suite covering creation, authorized `.set()`/`.update()` refreshes, cross-user overwrite denial, identity immutability, forged document IDs, unauthenticated denials, and valid reads/deletes. |
| **Owner-Admin Emulator Rules Suite (`firestore.owner-admin.test.js`)** | **PASS** | **4 passed, 0 failed, 0 skipped** — includes `/profile_visits` anti-overwrite parity check. |
| **Storage Emulator Rules Suite (`storage.test.js`)** | **PASS (Local `(default)` Emulator Only)** | **24 passed, 0 failed, 0 skipped** *(cross-service named-DB validation in production remains `NOT VERIFIED — PRODUCTION VALIDATION REQUIRED`)*. |
| **Admin-Tools Security Tests (`admin-tools/owner-security.test.js`)** | **PASS** | **4 passed, 0 failed, 0 skipped**. |
| **Android Compilation (`compile_applet`)** | **PASS** | `Build succeeded - the applet is compiled`. |
| **Android Unit & Robolectric Tests (SDK-35 & Pure JVM, 9 classes)** | **PASS** | **48 passed, 0 failed, 0 skipped** across `ExampleUnitTest`, `OwnerAdminTest`, `RegressionRepairsTest`, `AdMobManagerTest`, `ModelsSerializationTest`, `AuthValidatingCardTest`, `GoogleAuthOnlyRegressionTest`, `MemberProfileSetupScreenTest`, and `CommunitySafetyGuidelinesTest`. |
| **Previously Blocked SDK-36 Robolectric Classes (`SogoRepositoryRuleTest`, `AuthComponentsTest`, `FirebaseAuthListenerTest`)** | **BLOCKED** | Retried in Phase 2.9; Maven Central (`repo1.maven.org`) continues to return **HTTP 429** when Robolectric attempts to download `org.robolectric:android-all-instrumented:16-robolectric-13921718-i7.jar`. |
| **Instrumented Tests (`androidTest`) & Live Production Deployment** | **NOT VERIFIED** | No Android emulator / ADB in container; zero `firebase deploy` or live Firebase mutations performed per Phase 2.9 restrictions. |

---

## 2. Root Cause & Inspected Application Data Model

### 2.1 Application Data Model & Write Behavior Verified Firsthand
- **`Models.kt:224-234` (`ProfileVisitorRecord`):**
  - Fields: `visitId: String`, `visitorId: String`, `visitedUserId: String`, `visitorName: String`, `visitorPhotoUrl: String?`, `visitedAt: Timestamp?` (`@ServerTimestamp`).
- **`SogoRepository.kt:1219-1248` (`recordProfileVisit`):**
  - Requires signed-in `uid = auth.currentUser?.uid`, exits if `targetUid.isBlank() || targetUid == uid`.
  - Uses deterministic document ID: `val visitId = "visit_${uid}_${targetUid}"`.
  - Writes via `db.collection("profile_visits").document(visitId).set(payload).await()` (which invokes Firestore `create` on the first visit and Firestore `update` on repeat visits by the same visitor) with keys `visitId`, `visitorId`, `visitedUserId`, `visitorName` (`1..60`), optional `visitorPhotoUrl` (`<= 65000`, omitted if null/blank), and `visitedAt = FieldValue.serverTimestamp()`.
- **`SogoRepository.kt:1200-1217` (`observeProfileVisitors`) & `OwnProfileTab.kt:353-376`:**
  - Queries `.whereEqualTo("visitedUserId", uid).limit(100L)` and uses `visit.visitId` as the `LazyRow` key and `visit.visitorId` to navigate to `membersById[visit.visitorId]`.

### 2.2 Root Cause of the Vulnerability
In Phase 2.8, `firestore.rules:669-679` (and `firestore.owner-admin.proposed.rules:666-676`) merged `allow create, update:` into a single rule that checked only `incoming()` fields (`incoming().visitorId == request.auth.uid`, `incoming().visitId == visitId`, `incoming().visitedUserId != request.auth.uid`, `!isBlockedBetween(...)`) without:
1. Checking `existing().visitorId == request.auth.uid` on `update`;
2. Binding `visitId` to `'visit_' + request.auth.uid + '_' + data.visitedUserId`;
3. Enforcing immutability of `visitId`, `visitorId`, and `visitedUserId` on `update`.

Because `SogoRepository.recordProfileVisit` uses predictable IDs (`visit_${uid}_${targetUid}`), any authenticated non-blocked member (Charlie) could call `.set(...)` or `.update(...)` on Alice's existing `visit_${ALICE_UID}_${BOB_UID}` document (or claim that ID before Alice visited) and overwrite `visitorId`, `visitorName`, `visitorPhotoUrl`, `visitedUserId`, and `visitedAt` with Charlie's own data.

---

## 3. Exact Files Changed & Authorization Diff

| File | Change Type | Reason |
|---|---|---|
| `firestore.rules` | Modified (`L664-696`) | Added `isValidProfileVisit(data, visitId)` and replaced `allow create, update:` with separated `allow create:` and `allow update:` rules enforcing deterministic ID binding, original visitor ownership, immutable identity fields, and strict `affectedKeys()` allowlist. |
| `firestore.owner-admin.proposed.rules` | Modified (`L661-693`) | Synchronized identical `/profile_visits/{visitId}` fix to maintain 100% functional parity with `firestore.rules`. |
| `firestore.test.js` | Modified (`L1605-1710`) | Added `Profile Visits Security` test suite verifying all 7 required remediation scenarios. |
| `firestore.owner-admin.test.js` | Modified (`L262-279`) | Added `/profile_visits` cross-user anti-overwrite parity check inside `Proposed Rules Parity`. |
| `docs/PHASE_2_9_PROFILE_VISITS_REMEDIATION_REPORT.md` | Created | Phase 2.9 remediation and test verification report. |

**Unchanged by design:** `SogoRepository.kt`, `Models.kt`, `ChatViewModel.kt`, `MainDashboardViewModel.kt`, `OwnProfileTab.kt`, `storage.rules`, `firebase.json`, `app/build.gradle.kts`, and all auth/UI/verification-removal code.

### 3.1 Before-and-After Authorization Behavior

- **Before (`firestore.rules:664-683`):**
  - `create` and `update` shared one expression validating only `incoming()`.
  - Any signed-in, non-blocked user could write to any `visitId` (even `visit_alice_bob`) on `create` or overwrite an existing `visit_alice_bob` record on `update`, replacing `visitorId` and `visitedUserId`.
- **After (`firestore.rules:664-696`):**
  - `isValidProfileVisit(data, visitId)` enforces:
    - `data.visitId is string && data.visitId == visitId`
    - `data.visitorId is string && data.visitorId == request.auth.uid`
    - `isValidId(data.visitedUserId) && data.visitedUserId != request.auth.uid`
    - **`visitId == 'visit_' + request.auth.uid + '_' + data.visitedUserId`** (prevents doc-ID manipulation or pre-claiming another user's visit ID)
    - `!isBlockedBetween(request.auth.uid, data.visitedUserId)`
    - `visitorName` (`1..60`), optional `visitorPhotoUrl` (`null` or string `<= 65000`), exact key sets (`hasAll`/`hasOnly` preventing protected/extra field injection), and `visitedAt is timestamp && visitedAt <= request.time`.
  - **`allow create:`** requires `isSignedIn() && isValidId(visitId) && isValidProfileVisit(incoming(), visitId)`.
  - **`allow update:`** requires `isSignedIn() && isValidId(visitId)`, **`existing().visitorId == request.auth.uid`**, immutable **`incoming().visitId == existing().visitId && incoming().visitorId == existing().visitorId && incoming().visitedUserId == existing().visitedUserId`**, **`incoming().diff(existing()).affectedKeys().hasOnly(['visitorName', 'visitorPhotoUrl', 'visitedAt'])`**, and `isValidProfileVisit(incoming(), visitId)`. This preserves `SogoRepository.recordProfileVisit()` repeat-visit `.set(payload)` refreshes (including adding/updating/omitting `visitorPhotoUrl`) while blocking every cross-user overwrite and identity mutation.
  - **`allow get` / `list` / `delete`:** Unchanged — unauthenticated users remain denied (`isSignedIn()`), `get`/`list` remain scoped to `visitorId` or `visitedUserId`, and `delete` remains scoped to `existing().visitorId == request.auth.uid || existing().visitedUserId == request.auth.uid`.

---

## 4. New & Updated Regression Tests

Added `test("Profile Visits Security: ownership, deterministic visitId, immutable identity fields, and anti-overwrite protection", ...)` (`firestore.test.js:1605-1710`) plus parity check (`firestore.owner-admin.test.js:262-279`), verifying:
1. **Unauthenticated Denial:** Unauthenticated `get`, `where("visitedUserId", "==", BOB_UID)` query, `set`, and `delete` fail.
2. **Manipulated / Forged Document ID Denial:** Charlie cannot create `visit_${ALICE_UID}_${BOB_UID}` (another user's prefix) or arbitrary `visit_arbitrary_123` even when setting `visitorId: CHARLIE_UID`.
3. **Valid Creation Preserved:** Alice creates `visit_${ALICE_UID}_${BOB_UID}` matching `SogoRepository.recordProfileVisit()` schema.
4. **Valid Reads Preserved:** Alice (`visitorId`) and Bob (`visitedUserId`, `get` and `list`) succeed; Charlie (`get`) is denied.
5. **Valid Authorized Updates Preserved:** Alice refreshes her visit via both `.set(...)` (updating `visitorName`, removing `visitorPhotoUrl`, updating `visitedAt`) and `.update(...)` (updating `visitorName`, `visitorPhotoUrl`, `visitedAt`).
6. **Cross-User Overwrite Denied:** Charlie cannot overwrite Alice's existing `visit_${ALICE_UID}_${BOB_UID}` via `.set(...)` or `.update(...)`; visited user Bob also cannot overwrite Alice's visit metadata via `.update(...)`.
7. **Immutable Identity & Protected Field Protection:** Even Alice cannot update `visitorId`, `visitedUserId`, or inject disallowed fields (e.g., `isVerifiedMember: true`).
8. **Authorized Delete Scope:** Unauthenticated and third-party (`Charlie`) deletes fail; visited user (`Bob`) succeeds.

---

## 5. Exact Commands & Complete Verification Results

> **Emulator Scope Disclaimer:** All emulator tests ran locally against `--project demo-no-project`. Live production Firebase (`gen-lang-client-0431487205`) was neither deployed to nor modified.

### 5.1 Rules Parity Diff
- **Command:** `diff -u firestore.rules firestore.owner-admin.proposed.rules; echo "DIFF_EXIT=$?"`
- **Result:** `DIFF_EXIT=1` — **0 functional differences** (only the 8 pre-existing comment-only lines in `firestore.rules` lines `69`, `243`, `246`, `859`, `861`, `998`, `1003`, `1007` differ).

### 5.2 Android Compilation (`compile_applet`)
- **Result:** **PASS** — `Build succeeded - the applet is compiled`.

### 5.3 Firestore, Storage, Owner-Admin & Admin-Tools JS Suites
- **Command:**  
  `firebase emulators:exec --only auth,firestore,storage --project demo-no-project "node --test --test-reporter=spec firestore.test.js && node --test --test-reporter=spec storage.test.js && node --test --test-reporter=spec firestore.owner-admin.test.js" && node --test --test-reporter=spec admin-tools/owner-security.test.js`
- **Exit Code:** `0` (`Script exited successfully (code 0)`)
- **Verified Counts:**
  - `firestore.test.js`: **33 passed, 0 failed, 0 skipped** (`duration_ms: 38312.59`, includes new `Profile Visits Security` test)
  - `storage.test.js`: **24 passed, 0 failed, 0 skipped** (`duration_ms: 17705.44`, local `(default)` emulator only)
  - `firestore.owner-admin.test.js`: **4 passed, 0 failed, 0 skipped** (`duration_ms: 5915.92`, includes `/profile_visits` parity assertion)
  - `admin-tools/owner-security.test.js`: **4 passed, 0 failed, 0 skipped** (`duration_ms: 405.36`)

### 5.4 Android Unit & Robolectric Suite — SDK-35 & Pure JVM Classes (`PASS`)
- **Command:**  
  `gradle :app:testDebugUnitTest --tests "com.example.ExampleUnitTest" --tests "com.example.OwnerAdminTest" --tests "com.example.RegressionRepairsTest" --tests "com.example.ads.*" --tests "com.example.data.model.*" --tests "com.example.ui.auth.GoogleAuthOnlyRegressionTest" --tests "com.example.ui.auth.AuthValidatingCardTest" --tests "com.example.ui.safety.*" --tests "com.example.ui.profile.*"`
- **Exit Code:** `0` (`BUILD SUCCESSFUL in 36s`)
- **Verified Counts (`app/build/test-results/testDebugUnitTest/TEST-*.xml`):** **48 total, 48 passed, 0 failed, 0 errors, 0 skipped**
  - `com.example.ExampleUnitTest`: 1/1 passed
  - `com.example.OwnerAdminTest`: 5/5 passed
  - `com.example.RegressionRepairsTest`: 21/21 passed
  - `com.example.ads.AdMobManagerTest`: 5/5 passed
  - `com.example.data.model.ModelsSerializationTest`: 4/4 passed
  - `com.example.ui.auth.AuthValidatingCardTest`: 1/1 passed
  - `com.example.ui.auth.GoogleAuthOnlyRegressionTest`: 7/7 passed
  - `com.example.ui.profile.MemberProfileSetupScreenTest`: 2/2 passed
  - `com.example.ui.safety.CommunitySafetyGuidelinesTest`: 2/2 passed

### 5.5 Retry of Previously Blocked SDK-36 Robolectric Classes (`BLOCKED`)
- **Commands Retried:**
  1. `firebase emulators:exec --only auth,firestore --project demo-no-project "gradle :app:testDebugUnitTest --tests com.example.data.repository.SogoRepositoryRuleTest --tests com.example.ui.auth.AuthComponentsTest --tests com.example.ui.auth.FirebaseAuthListenerTest --continue"` → **Exit Code `1`** (`SogoRepositoryRuleTest > classMethod FAILED`, `AuthComponentsTest > classMethod FAILED`; stopped before `FirebaseAuthListenerTest` due to `failFast = true` in `app/build.gradle.kts:75`)
  2. `gradle :app:testDebugUnitTest --tests com.example.ui.auth.FirebaseAuthListenerTest` → **Exit Code `1`** (`FirebaseAuthListenerTest > classMethod FAILED`)
- **Exact Error Observed in All 3 XML Reports (`TEST-com.example.data.repository.SogoRepositoryRuleTest.xml`, `TEST-com.example.ui.auth.AuthComponentsTest.xml`, `TEST-com.example.ui.auth.FirebaseAuthListenerTest.xml`):**
  ```text
  java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:16-robolectric-13921718-i7
    at org.robolectric.internal.dependency.MavenArtifactFetcher.fetchArtifact(MavenArtifactFetcher.java:129)
  Caused by: java.util.concurrent.ExecutionException: java.io.IOException: Server returned HTTP response code: 429 for URL: https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7/android-all-instrumented-16-robolectric-13921718-i7.jar
  ```
- **Classification:** **`BLOCKED`** — HTTP 429 rate limiting from Maven Central persists in this container environment for the uncached SDK 36 Robolectric runtime JAR; these 3 classes cannot execute until Maven Central access succeeds or those tests are configured to use cached SDK 35.

---

## 6. Remaining Phase 2.8 Findings & Pre-Deployment Limitations

The targeted `/profile_visits/{visitId}` vulnerability from Phase 2.8 §3.2 #1 is now **remediated (`PASS`)**. The following Phase 2.8 items remain open outside Phase 2.9's targeted Firestore fix scope:
1. **`storage.rules` Cross-Service Named-Database Mismatch (`NOT VERIFIED — PRODUCTION VALIDATION REQUIRED`):** `storage.rules:52-74` still queries `/databases/(default)/documents/{user_blocks,blocks,conversations}` rather than named database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`. Safe while the Android APK does not use `firebase-storage`, but do not deploy `storage.rules` for production Storage enforcement until reconciled.
2. **SDK-36 Robolectric Execution (`BLOCKED`):** `SogoRepositoryRuleTest`, `AuthComponentsTest`, and `FirebaseAuthListenerTest` remain blocked by Maven Central HTTP `429` on `16-robolectric-13921718-i7.jar`.
3. **Live Firebase Console & Production Preconditions (`NOT VERIFIED`):** `.firebaserc` is absent; live Auth Console Google-only provider lock, Play Integrity App Check enforcement, and Owner `{ owner: true, admin: true }` custom claims on `gptgamestudioph@gmail.com` remain unverified in production.
4. **Low / Spark Defense-in-Depth Items (Unchanged from Phase 2.8):** `isSignedIn()` (`L11-13`) omits explicit `sign_in_provider == 'google.com'`; `/users/{userId}` Owner moderation update (`L124-127`) evaluates `isValidUserProfile(...)` before `affectedKeys().hasOnly(...)`; Spark client-side aggregate counters on `/activities` and `/activity_posts`; `call_sessions/candidates` participant delete scope (`L775-776`); `AuthComponents.kt:562,570` onboarding copy.
5. **No Production Deployment Executed:** Per Phase 2.9 instructions, neither `firestore.rules` nor `storage.rules` were deployed. Wait for explicit approval before any deployment.
