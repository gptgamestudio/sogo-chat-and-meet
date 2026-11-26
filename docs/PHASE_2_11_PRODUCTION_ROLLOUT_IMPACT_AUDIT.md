# SOGO CHAT & MEET — Phase 2.11: Read-Only Production Rollout Impact Audit

**Date:** 2026-11-24  
**Mode:** `STRICT READ-ONLY AUDIT`  
**Target Firebase Project:** `gen-lang-client-0431487205` (Android package `com.sogo.chatandmeet`)  
**Target Named Firestore Database:** `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` (configured in `firebase.json:4` and `app/src/main/res/values/firebase_applet_config.xml:3`)  
**Baseline:** Phase 2.10 (`docs/PHASE_2_10_GOOGLE_PROVIDER_RULES_REPORT.md`), where `isSignedIn()` in `firestore.rules:11-17` and `firestore.owner-admin.proposed.rules:11-17` was updated locally to require `request.auth.token.firebase.sign_in_provider == 'google.com'`.

> **Audit Boundaries Attestation:** No source code, rules, tests, configuration files, or other documentation files were modified. No Firestore or Storage rules were deployed. No Firebase Console settings, Authentication providers, custom claims, or user accounts were inspected or modified, and no personal user data, tokens, or credentials were accessed or exported.

---

## 1. Executive Summary & Verification Matrix

| Audit Area | Classification | Key Finding |
|---|---|---|
| **1. Client Sign-In Creation Paths (`app/src/main`)** | **PASS** *(Code Only)* | `AuthScreen` and `AuthComponents.kt` create new sessions exclusively via Jetpack `CredentialManager` (`GetGoogleIdOption` / `GetSignInWithGoogleOption`) and `GoogleAuthProvider.getCredential(...)`. Zero Email/Password, Anonymous, Phone, Custom Token, or Account-Linking calls exist in `app/src/main`. |
| **2. Existing Client Session Gate (`MainActivity`, `SogoRepository`)** | **FAIL** *(Session Gap on Device)* | Although `FirebaseAuthListener` (`L68-81`) tracks `isGoogleSignIn`, `MainActivity.AppContent()` (`L115-136`) gates `PostLoginFlow` solely on `authSession.userId != null`, and `SogoRepository.requireUserId()` (`L193-196`) checks only `auth.currentUser?.uid`. Only Owner access checks `GoogleAuthProvider.PROVIDER_ID` on the client. Any pre-existing cached non-Google Firebase Auth session survives into `PostLoginFlow` and will immediately hit `PERMISSION_DENIED` once Phase 2.10 rules deploy. |
| **3. Scope of `isSignedIn()` Across Firestore Rules** | **PASS** *(Audited)* | **100% of active Firestore collections and subcollections** (28 `match` blocks across 21 top-level paths and 4 subcollections) evaluate `isSignedIn()` directly or via `isOwner(userId)` / `isVerifiedOwner()`. |
| **4. Pre-Existing Non-Google Production Sessions** | **NOT VERIFIED** | Codebase contains zero client-side non-Google login UI today, **but** live Firebase Auth user provider counts, active session types, and historical pre-migration accounts in `gen-lang-client-0431487205` cannot be queried from this container. |
| **5. Firebase Console Auth & Project Settings** | **NOT VERIFIED** | No `.firebaserc`, gcloud/Firebase CLI auth, service-account credentials, or Firebase Console access exist in this container. Provider toggles, email-linking policy, Owner claims, and App Check enforcement remain unverified against live production. |
| **6. Local Emulator & Unit Verification Baseline** | **PASS** *(Local Emulator / SDK-35)* | From Phase 2.10: `firestore.test.js` (**34/34 passed**), `firestore.owner-admin.test.js` (**5/5 passed**), `storage.test.js` (**24/24 passed**, `(default)` emulator only), `admin-tools/owner-security.test.js` (**4/4 passed**), `compile_applet` (**PASS**), `compileDebugUnitTestKotlin` (**PASS**), and 9 unit/SDK-35 Robolectric classes (**48/48 passed**). |
| **7. Remaining Automated Test Blockers** | **BLOCKED** / **NOT VERIFIED** | 3 SDK-36 Robolectric classes (`SogoRepositoryRuleTest`, `AuthComponentsTest`, `FirebaseAuthListenerTest`) are **BLOCKED** by Maven Central HTTP `429` fetching `org.robolectric:android-all-instrumented:16-robolectric-13921718-i7.jar`. `storage.rules` named-DB cross-service behavior (`storage.rules:52-74`), instrumented `androidTest`, and live production rules/Auth remain **NOT VERIFIED**. |

---

## 2. Audit Task 1 — App Authentication Flow (Session Creation vs. Session Maintenance)

### 2.1 How Sessions Can Be Created in Current App Code (`app/src/main`)
1. **Silent Auto Sign-In (`AuthComponents.kt:215-300`):**
   - If `Firebase.auth.currentUser != null`, `attemptAutoSignIn` returns immediately (`L222-225`) without re-checking the provider.
   - Otherwise, it builds a `GetGoogleIdOption` (`setFilterByAuthorizedAccounts(true)`, `serverClientId = R.string.default_web_client_id`, `L241-245`), requests a `GoogleIdTokenCredential` via `CredentialManager`, wraps the ID token in `GoogleAuthProvider.getCredential(googleIdToken, null)` (`L259`), and calls `Firebase.auth.signInWithCredential(authCredential)` (`L260`).
2. **Interactive Sign-In & Re-Authentication (`AuthComponents.kt:302-431`, `L626-649`):**
   - `AuthScreen` renders a single authentication action (`"Sign In with Google"`, `L691`), invoking `onGoogleSignInClicked` with `GetSignInWithGoogleOption` (`L363`), `GoogleAuthProvider.getCredential`, and `Firebase.auth.signInWithCredential` (`L380-381`).
   - Sensitive re-authentication uses `requestGoogleIdToken` (`L302-340`) with `GetSignInWithGoogleOption`.
3. **Absence of Other Client Sign-In Flows:**
   - Verified via repository search across `app/src/main`: **0 occurrences** of `signInAnonymously`, `signInWithEmailAndPassword`, `createUserWithEmailAndPassword`, `signInWithCustomToken`, or `linkWithCredential`.

### 2.2 How Existing Sessions Are Maintained & Validated at Runtime
1. **Firebase Auth SDK Local Persistence:**
   - Firebase Authentication SDK persists authenticated user state and refresh tokens locally in app-private storage on the device across app restarts and APK upgrades.
   - `AndroidManifest.xml:16` sets `android:allowBackup="false"`, which prevents Android cloud backup/restore across device transfers, **not** local persistence across in-place Play Store/APK updates on the same device.
2. **Session Observation (`FirebaseAuthListener.kt:59-81`):**
   - `FirebaseAuthSessionSource.currentProviderSnapshot()` inspects `user.providerData` for `GoogleAuthProvider.PROVIDER_ID` and populates `AuthSessionSnapshot.isGoogleSignIn` (`L34, L70-79`).
3. **Critical Client-Side Enforcement Asymmetry (`MainActivity.kt:115-136`, `SogoRepository.kt:183-196`, `OwnerAdminRepository.kt:260-281`):**
   - **Regular Members:** `MainActivity.AppContent()` (`L115-136`) enters `PostLoginFlow` whenever `authSession.userId != null`, **without checking `authSession.isGoogleSignIn`**. Likewise, `SogoRepository.requireUserId()` (`L193-196`) checks only `auth.currentUser?.uid`.
   - **Owner Candidate / Admin UI:** Only `SogoRepository.isOwnerCandidateSignedIn()` (`L183-191`) and `FirebaseOwnerAdminRepository.verifyOwnerAccess()` (`L260-281`) verify `GoogleAuthProvider.PROVIDER_ID`, `isEmailVerified`, matching owner email, and custom claims (`{ owner: true, admin: true }`).
   - **Result:** If any device already holds a cached non-Google Firebase Auth session (or if a token is minted through a non-Google Identity Toolkit endpoint while enabled in Firebase Console), the Android app will treat that user as signed in and immediately open `PostLoginFlow` against Firestore—where the updated `isSignedIn()` rule will reject every query.

---

## 3. Audit Task 2 — App Features & Firestore Rule Paths Affected by Updated `isSignedIn()`

`firestore.rules:11-17` defines `isSignedIn()` as:
```rules
function isSignedIn() {
  return request.auth != null &&
         'firebase' in request.auth.token &&
         request.auth.token.firebase is map &&
         'sign_in_provider' in request.auth.token.firebase &&
         request.auth.token.firebase.sign_in_provider == 'google.com';
}
```
Because both `isOwner(userId)` (`L19-21`) and `isVerifiedOwner()` (`L23-29`) call `isSignedIn()`, **every active collection and subcollection in `firestore.rules` and `firestore.owner-admin.proposed.rules` requires `sign_in_provider == 'google.com'`**:

| Feature Area | Rule Path(s) & Line Numbers (`firestore.rules`) | Operations Enforcing `isSignedIn()` |
|---|---|---|
| **Member Profiles & Onboarding** | `/users/{userId}` (`L85-150`) | `get`, `list`, `create` (`isOwner`), `update` (`isOwner` or `isVerifiedOwner` moderation), `delete` (`isOwner`) |
| **Gallery Photos** | `/users/{userId}/gallery_photos/{photoId}` (`L174-194`) | `get`, `list`, `create`, `update`, `delete` (`isOwner`) |
| **Activity Reactions (Per-User Subcollection)** | `/users/{userId}/activity_reactions/{postId}` (`L151-172`) | `get`, `list`, `create`, `update`, `delete` (`isOwner`) |
| **Online Presence** | `/user_presence/{userId}` (`L196-220`) | `get`, `list` (`isSignedIn()`), `create`, `update`, `delete` (`isOwner`) |
| **1:1 Private Chat & Messages** | `/conversations/{conversationId}` (`L222-281`), `/conversations/{conversationId}/messages/{messageId}` (`L283-353`) | `get`, `list`, `create`, `update`, `delete` (participant/sender gated by `isSignedIn()`) |
| **User Blocking** | `/user_blocks/{blockId}` (`L355-378`), `/blocks/{blockId}` (`L380-414`) | `get`, `list`, `create`, `update`, `delete` (`isSignedIn()` + `blockerId == request.auth.uid`) |
| **Safety & Moderation Reports** | `/user_reports/{reportId}` (`L416-429`), `/reports/{reportId}` (`L431-459`), `/content_reports/{reportId}` (`L461-495`), `/activity_reports/{reportId}` (`L497-530`) | `create` (`isSignedIn()`), `get`, `list`, `update`, `delete` (reporter or `isVerifiedOwner()`) |
| **In-App Notifications** | `/notifications/{notificationId}` (`L532-576`) | `get`, `list`, `create`, `update`, `delete` (`isSignedIn()` + recipient/actor checks) |
| **Friend Requests, Follows, Favorites, Likes, Visits** | `/friend_requests/{requestId}` (`L578-611`), `/user_follows/{followId}` (`L613-629`), `/user_favorites/{favoriteId}` (`L631-646`), `/profile_likes/{likeId}` (`L648-680`), `/profile_visits/{visitId}` (`L682-721`) | `get`, `list`, `create`, `update`, `delete` (`isSignedIn()` + actor/participant ownership) |
| **WebRTC Audio/Video Calls** | `/call_sessions/{callId}` (`L723-775`), `/call_sessions/{callId}/candidates/{candidateId}` (`L777-815`) | `get`, `list`, `create`, `update`, `delete` (`isSignedIn()` + caller/callee participation) |
| **Activity Feed & Comments (Legacy + Current)** | `/activity_posts/{postId}` (`L817-885`) + `/comments/{commentId}` (`L886-907`); `/activities/{postId}` (`L950-1030`) + `/comments/{commentId}` (`L1031-1052`) | `get`, `list`, `create`, `update` (author edits, likes toggle, emoji reactions, comment/share counters), `delete` (author or `isVerifiedOwner()`) |
| **Frozen Legacy Verification Queue** | `/verification_requests/{requestId}` (`L1055-1058`) | `get`, `list` (`isVerifiedOwner()` -> requires `isSignedIn()`); client writes always `false` |
| **Owner/Admin Analytics & Audit Logs** | `/admin_dashboard/{docId}` (`L1061-1064`), `/admin_usage_daily/{dateKey}` (`L1066-1069`), `/admin_registration_history/{entryId}` (`L1071-1074`), `/admin_audit_logs/{logId}` (`L1076-1089`) | `get`, `list`, and `create` on `/admin_audit_logs` (`isVerifiedOwner()` -> requires `isSignedIn()` + Owner claims) |

- **Excluded / Unaffected Paths:**
  - `/{document=**}` (`L6-8`): Always `false`.
  - **Off-Device Admin Tooling (`admin-tools/owner-admin-sync.mjs`):** Uses `firebase-admin` SDK with service account credentials, which bypasses Firestore Security Rules and is unaffected—**provided** the Owner user's own session in the Android app uses `google.com` when reading the synced collections.
  - **Firebase Storage (`storage.rules`):** Untouched in Phase 2.10; enforces `request.auth != null` without `sign_in_provider == 'google.com'`, and continues to carry the pre-existing `(default)` Firestore database lookup mismatch (`storage.rules:52-74`).

---

## 4. Audit Task 3 — Assessment of Existing Non-Google Production Sessions (Non-Identifying Evidence Only)

- **Classification:** **`NOT VERIFIED`** (live Firebase Auth records and session metrics for `gen-lang-client-0431487205` are inaccessible from this environment).
- **Available Aggregate / Codebase Evidence:**
  1. **Current Client Code Supports Google Sign-In Only:** As verified in `AuthComponents.kt`, `FirebaseAuthListener.kt`, and `app/src/main`, the current build has zero Email/Password, Anonymous, Phone, or custom-token login flows.
  2. **Legacy Pre-Migration History Is Possible:** Prior audit reports (`docs/GOOGLE_AUTH_ONLY_MIGRATION_REPORT.md`, `docs/PHASE_2_6_AUTH_PRODUCTION_AUDIT.md`) confirm the project underwent a migration from earlier iterations (including frozen `/verification_requests`). If any earlier app version or Firebase Console test account allowed non-Google sign-in, those Firebase Auth UIDs and cached device tokens could still exist in `gen-lang-client-0431487205`.
  3. **Admin SDK Sanitization Schema Anticipates Provider Diversity:** `admin-tools/owner-security.mjs:132-135` inspects `user.providerData[0]?.providerId` (falling back to `"google.com"` only when absent) when aggregating sanitized registration metadata—confirming provider IDs are observable in aggregate without exposing raw PII/tokens.
  4. **Client Identity Toolkit Endpoint Exposure:** Like every Firebase Android app, `app/google-services.json` embeds the project's public client configuration (`gen-lang-client-0431487205`). If non-Google providers (e.g., Email/Password or Anonymous) remain enabled in Firebase Console, external clients could still invoke Identity Toolkit REST APIs to mint non-Google tokens (which Phase 2.10 rules are explicitly designed to block at the Firestore layer).
- **Conclusion:** While current Android app code only issues Google Sign-In sessions, **it is NOT VERIFIED whether any active or historical production accounts in `gen-lang-client-0431487205` currently hold non-Google sessions**. No individual user records were inspected or exported.

---

## 5. Audit Task 4 — Firebase Console & Production Configuration Verification

- **Classification:** **`NOT VERIFIED`**
- **Reason:** `.firebaserc` is absent, no Firebase Console session or GCP/Firebase service-account credentials are present in the container, and Phase 2.11 strictly prohibits accessing or changing live production settings.
- **Exact Manual Owner Checks Required Before Production Deployment (`gen-lang-client-0431487205`):**
  1. **Authentication → Sign-in method:** Verify **Google** is enabled with the matching Android SHA-1/SHA-256 fingerprints and Web Client ID (`default_web_client_id`), and confirm **Email/Password**, **Anonymous**, **Phone**, and all non-Google providers are **Disabled** (or scheduled to be disabled immediately prior to/with rules rollout).
  2. **Authentication → Settings → User account linking ("One account per email address"):** Verify linking behavior. If a pre-existing Email/Password user shares an email address with a Google account and Google Sign-In is not auto-linked/upgraded, Google Sign-In can fail with `ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL` (`AuthComponents.kt:180-181`). Confirm policy before forcing any legacy user re-authentication.
  3. **Authentication → Users (Aggregate Check Only — Do Not Export PII):** Run an aggregate count check in Firebase Console or via `firebase-admin` on a trusted workstation (`admin-tools/owner-admin-sync.mjs --dry-run` or a local aggregate script counting `providerData.map(p => p.providerId)` without printing emails/UIDs) to verify whether non-`google.com` active users (`lastSignInTime` recent) equal `0`.
  4. **Owner Account & Custom Claims (`gptgamestudioph@gmail.com`):** Confirm the Owner account signs in with `google.com`, has `email_verified == true`, and has custom claims `{ owner: true, admin: true }` applied via `admin-tools/owner-admin-sync.mjs`. (Under Phase 2.10, `isVerifiedOwner()` fails closed if the Owner token lacks `firebase.sign_in_provider == 'google.com'`).
  5. **App Check (`SogoApplication.kt:48-65`):** Confirm Play Integrity attestation is configured in Firebase Console for package `com.sogo.chatandmeet` (`Release` uses `PlayIntegrityAppCheckProviderFactory`, `Debug` uses `DebugAppCheckProviderFactory`), and verify whether Firestore App Check enforcement is enabled or un-enforced.
  6. **Firestore Database Target:** Confirm deployment targets database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` (configured in `firebase.json:4`) and **excludes** `storage.rules`.

---

## 6. Audit Task 5 — Production User Impact Assessment If Deployed Today

### 6.1 Legitimate Google Sign-In Users
- **Expected Impact:** **No disruption.** Standard Android `CredentialManager` -> `GoogleAuthProvider.getCredential` -> `signInWithCredential` mints Firebase ID tokens containing `firebase.sign_in_provider == "google.com"` (confirmed via Auth Emulator `accounts:signInWithIdp` in Phase 2.10 and matching standard Firebase Auth production behavior, though live production token claims remain `NOT VERIFIED` until tested post-deploy).
- **Owner Account:** Unaffected if signed in via Google with `{ owner: true, admin: true }` claims; blocked if ever authenticated via a non-Google credential.

### 6.2 Users With Cached or External Non-Google Sessions (`PERMISSION_DENIED` Paths)
If any production device currently holds a non-Google Firebase Auth session when `firestore.rules` is deployed:
1. **App Startup Failure Loop (`MainActivity.kt:158-261`, `MainDashboardViewModel.kt:629-649`):**
   - `AppContent()` (`MainActivity.kt:116`) passes the non-null `uid` directly into `PostLoginFlow`.
   - `loadUserData(uid)` (`MainDashboardViewModel.kt:633`) opens `observeCurrentUserProfile(uid)`, which receives `FirebaseFirestoreException: PERMISSION_DENIED`.
   - `_profileLoadError` is populated (`L646`), routing the user to `"profile_load_error"` (`"Unable to Open Member Dashboard"`, `MainActivity.kt:209-261`).
   - Tapping **"Retry"** (`L249`) calls `retrySession(currentUserId)` and immediately fails again with `PERMISSION_DENIED` because the non-Google session remains cached.
   - **Self-Recovery Path Exists in UI:** Tapping **"Sign Out"** (`L240-247`) clears Credential Manager state and calls `Firebase.auth.signOut()`, returning the user to `AuthScreen` where they can tap **"Sign In with Google"**.
2. **Active In-Session Breakage (`MainDashboardViewModel.kt:651+`, `SogoRepository.kt`, `CallViewModel`):**
   - If a non-Google session were already open past initial profile load, all background listeners and mutations (`/users`, `/user_presence`, `/conversations`, `/messages`, `/activities`, `/friend_requests`, `/profile_visits`, `/call_sessions`, `/notifications`, safety reports/blocks) would throw `PERMISSION_DENIED` without auto-signing out the session, because `MainActivity` does not force sign-out when `authSession.isGoogleSignIn == false`.
3. **Re-Authentication vs. Account Migration Risks:**
   - **Pure Re-Authentication Sufficient** *only if* Firebase Auth links/merges the user's Google credential to their existing UID (preserving `/users/{uid}`, matches, chats, and friend graph).
   - **UID Mismatch / Data Orphaning Risk:** If a user previously signed in with an Anonymous UID or a different email's Password account and now signs in with Google, Firebase Auth creates/selects a **different UID**—meaning their prior Firestore `/users/{oldUid}` profile and conversations remain inaccessible without manual migration, or if their prior Email/Password account shares the same email and email-linking rules conflict, `AuthComponents.kt:180` surfaces `ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL`.

---

## 7. Audit Task 6 — Proposed Safe Rollout Sequence & Rollback Plan *(Do Not Execute)*

> **Execution Status:** Proposal only. Do **NOT** execute any command or Console change until the Project Owner completes preflight verification and approves deployment.

### Stage 1 — Pre-Deploy Console & Aggregate Readiness Verification (Trusted Owner Workstation)
1. Inspect Firebase Console **Authentication → Sign-in method** for `gen-lang-client-0431487205`:
   - Confirm **Google** provider is enabled and properly configured.
   - Run an aggregate-only Auth provider audit (e.g., Console filter or trusted local `firebase-admin` summary script counting `providerId` frequencies across users with zero PII output).
   - **Branch A (0 non-Google active users):** Disable all non-Google providers (Email/Password, Anonymous, etc.) in Console first.
   - **Branch B (>0 non-Google active users):** Pause rules deployment; determine account-linking / re-auth migration notice first so active users are not stranded on `"Unable to Open Member Dashboard"` or orphaned onto new UIDs.
2. Confirm Owner account (`gptgamestudioph@gmail.com`) uses Google Sign-In and retains `{ owner: true, admin: true }` custom claims.
3. Confirm parity check: `firestore.rules` and `firestore.owner-admin.proposed.rules` have zero functional differences, and `firebase.json:4` targets `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`.

### Stage 2 — Optional Client Hardening Consideration (Future Turn, If Non-Google Legacy Sessions Exist)
- Before or alongside strict rules rollout, consider whether `MainActivity.AppContent()` (`L115-136`) / `FirebaseAuthListener` should proactively sign out or prompt legacy non-Google sessions (`authSession.userId != null && !authSession.isGoogleSignIn`) instead of letting them enter `PostLoginFlow` and hit `"profile_load_error"`. *(Not implemented in Phase 2.11.)*

### Stage 3 — Firestore-Only Rules Deployment (Once Stage 1 Passes)
Deploy **only** Firestore rules to `gen-lang-client-0431487205` (do **not** deploy `storage.rules` due to its `(default)` DB lookup mismatch):
```bash
firebase deploy --only firestore:rules --project gen-lang-client-0431487205
```

### Stage 4 — Immediate Post-Deploy Smoke Checks
1. Sign in on a release/production-pointing Android build with a standard Google member account and verify profile load, member feed, chat message send, activity post/reaction, and profile visit creation succeed.
2. Sign in with the Owner Google account and verify `OwnerAdminScreen` reads `/admin_dashboard`, `/verification_requests`, and writes `/admin_audit_logs` cleanly.
3. Monitor Firebase Firestore Rules / Metrics in Google Cloud / Firebase Console for unexpected spikes in `PERMISSION_DENIED` responses during the first 15–60 minutes.

### Stage 5 — Rollback Plan (If Legitimate Users Experience Unexpected `PERMISSION_DENIED` Spike)
1. **Immediate Rules Rollback (~<1 minute effect):**
   - Restore the pre-Phase-2.10 `isSignedIn()` definition (`return request.auth != null;`) in `firestore.rules` (or redeploy the immediately preceding rules version from Firebase Console **Firestore → Rules → Version History**) and run:
     ```bash
     firebase deploy --only firestore:rules --project gen-lang-client-0431487205
     ```
2. **Important Rollback Coupling Note:**
   - Rolling back `firestore.rules` **does not** re-enable disabled Authentication providers in Firebase Console. If Console providers were disabled in Stage 1 and caused an unintended sign-in regression, re-enable only the specific required provider in Firebase Console independently while diagnosing.
   - Note that the Owner dashboard crash risk from cached non-Google sessions is mitigated by `SogoRepository` / `OwnerAdminRepository` client checks and `isVerifiedOwner()` email + custom-claim checks even during a temporary rollback.

---

## 8. Audit Task 7 — Remaining Test Blockers & Unverified Areas

1. **`BLOCKED` — SDK-36 Robolectric Test Classes (3 classes):**
   - `com.example.data.repository.SogoRepositoryRuleTest`
   - `com.example.ui.auth.AuthComponentsTest`
   - `com.example.ui.auth.FirebaseAuthListenerTest`
   - **Cause:** Local Gradle test runner failed to download `org.robolectric:android-all-instrumented:16-robolectric-13921718-i7` from `https://repo1.maven.org/maven2/...` due to Maven Central returning `HTTP response code: 429`. These classes compiled (`compileDebugUnitTestKotlin` **PASS**), **must NOT be reported as passed or failed**, and remain **`BLOCKED`** until Maven Central throttling clears or the SDK-36 Robolectric JAR is cached in a CI/local runner.
2. **`NOT VERIFIED` — `storage.rules` Named-Database Cross-Service Enforcement:**
   - `storage.rules:52-74` references `firestore.get(/databases/(default)/documents/...)` rather than `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`, and `storage.rules` was untouched in Phase 2.10. Production Storage rule behavior remains **`NOT VERIFIED`** and must continue to be excluded from Firestore rules deployment.
3. **`NOT VERIFIED` — Live Firebase Console, Production Auth Tokens, Owner Claims & Instrumented `androidTest`:**
   - Live production Google Sign-In ID token claims (`firebase.sign_in_provider == 'google.com'`), Owner custom claims (`{ owner: true, admin: true }`), Play Integrity App Check enforcement, and on-device end-to-end UI flows cannot be exercised from this offline/emulator-only execution environment (`NOT VERIFIED`).

---

## 9. Final Readiness Recommendation

- **Code & Local Firestore Rules Parity:** Verified ready against local Firestore/Auth Emulators (`34/34` `firestore.test.js`, `5/5` `firestore.owner-admin.test.js`), **with zero code/rules edits performed in Phase 2.11**.
- **Production Rollout Status:** **HOLD FOR STAGE 1 CONSOLE & AGGREGATE PROVIDER CHECKS (`NOT VERIFIED` LIVE DEPENDENCIES)** — Do not run `firebase deploy --only firestore:rules --project gen-lang-client-0431487205` until the Project Owner confirms via Firebase Console / aggregate check that no active legitimate users depend on non-Google sessions (and that `gptgamestudioph@gmail.com` uses Google Sign-In with Owner claims) and acknowledges the 3 `BLOCKED` SDK-36 Robolectric suites (`SogoRepositoryRuleTest`, `AuthComponentsTest`, `FirebaseAuthListenerTest`).
