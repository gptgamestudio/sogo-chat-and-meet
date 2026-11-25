# SOGO CHAT & MEET — Phase 2.8: Pre-Deployment Security Readiness Audit Report

**Audit Date:** 2026-11-24  
**Audit Mode:** `STRICTLY READ-ONLY — AUDIT, TEST, VERIFY, DOCUMENT` (zero edits to source code, security rules, Gradle files, tests, or live Firebase state)  
**Target Firebase Project:** `gen-lang-client-0431487205` (`app/google-services.json`, Android package `com.sogo.chatandmeet`)  
**Configured Named Firestore Database:** `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` (`firebase.json:4`, `app/src/main/res/values/firebase_applet_config.xml:3`)  
**Git Branch / Commit Hash:** **NOT VERIFIED** — `.git` directory is absent in the build container (`git rev-parse HEAD` → `fatal: not a git repository (or any of the parent directories): .git`).

---

## 1. Executive Summary & Per-Area Classifications

| Audit Area | Classification | Summary |
|---|---|---|
| **1. Reference Docs & Rules Parity (`firestore.rules` vs `firestore.owner-admin.proposed.rules`)** | **PASS** | `diff -u firestore.rules firestore.owner-admin.proposed.rules` confirms zero functional differences (8 comment-only lines differ). Phase 2.7 reaction path and expression-limit fixes are present in both files. |
| **2. Firestore Security Rules Review (`firestore.rules`)** | **FAIL (Medium Gap)** | Catch-all default deny (`L6-8`), participant chat/call isolation, mutual block enforcement (`isBlockedBetween`), single-UID likes toggle, Owner-only moderation/analytics, and frozen `/verification_requests` are intact. **However**, `match /profile_visits/{visitId}` (`L669-679`) merges `allow create, update:` without checking `existing().visitorId == request.auth.uid`, permitting cross-user overwrite of predictable `visit_<uidA>_<uidB>` documents. |
| **3. Owner / Admin Authorization & Claims Isolation** | **PASS** (Local) / **NOT VERIFIED** (Live Auth) | Client (`SogoRepository.kt`, `OwnerAdminRepository.kt`), server script (`admin-tools/owner-admin-sync.mjs`), and `firestore.rules` (`isVerifiedOwner()`) enforce exact email (`gptgamestudioph@gmail.com`), `email_verified == true`, Google provider (`google.com` in client/UI), and server-set `{ owner: true, admin: true }` custom claims. Live Firebase Auth claims on `gen-lang-client-0431487205` are **NOT VERIFIED**. |
| **4. Storage Rules & Named-Database Configuration (`storage.rules`)** | **NOT VERIFIED — PRODUCTION VALIDATION REQUIRED** | `storage.rules:52-74` queries `/databases/(default)/documents/{user_blocks,blocks,conversations}` while the app uses named Firestore DB `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`. Android does not currently include `firebase-storage` (images use Firestore JPEG data URIs), but deploying `storage.rules` as-is leaves Storage block/conversation rules querying an empty `(default)` DB. |
| **5. Firebase Configuration & Secret Hygiene** | **PASS** (Container) / **NOT VERIFIED** (Console) | No hardcoded secrets in source; `.env` is absent; `.env.example` contains only a commented placeholder; App Check installs `PlayIntegrityAppCheckProviderFactory` in release; `allowBackup="false"`. `.firebaserc` is absent, and live Firebase Console Auth provider settings are **NOT VERIFIED**. |
| **6. Google-Only Auth & Removal of Manual Verification** | **PASS** | Zero matches in `app/src/main` for `verification_requests`, `Get Verified`, `Approve Verified`, `FirebaseStorage`, `signInAnonymously`, `signInWithEmailAndPassword`, or `createUserWithEmailAndPassword`. Google Sign-In + `FirebaseUser.isEmailVerified` remain cleanly distinguished from real-world identity verification (`CommunitySafetyGuidelinesDialog.kt:139`). |
| **7. Android Compilation (`compile_applet`)** | **PASS** | `Build succeeded - the applet is compiled`. |
| **8. JS Emulator Security & Admin-Tools Test Suites** | **PASS** (Local Emulator) | `firestore.test.js` (**32/32**), `firestore.owner-admin.test.js` (**4/4**), `admin-tools/owner-security.test.js` (**4/4**), and `storage.test.js` (**24/24** against local `(default)` emulator only) passed. |
| **9. Android Unit, Robolectric & Rule Tests (`:app:testDebugUnitTest`)** | **BLOCKED (Partial)** | **48/48** SDK-35 Robolectric & JVM tests passed cleanly across 9 classes (`ExampleUnitTest`, `OwnerAdminTest`, `RegressionRepairsTest`, `AdMobManagerTest`, `ModelsSerializationTest`, `AuthValidatingCardTest`, `GoogleAuthOnlyRegressionTest`, `MemberProfileSetupScreenTest`, `CommunitySafetyGuidelinesTest`). **3** classes targeting default SDK 36 (`SogoRepositoryRuleTest`, `AuthComponentsTest`, `FirebaseAuthListenerTest`) are **BLOCKED** in this container run by Maven Central HTTP `429` while Robolectric tried fetching `android-all-instrumented:16-robolectric-13921718-i7.jar`. |
| **10. Instrumented `androidTest` & Live Production Firebase** | **NOT VERIFIED** | No Android emulator / ADB or live Firebase credentials in container; no deployment or production writes executed. |

**Final Deployment Recommendation:** **`NOT READY FOR DEPLOYMENT`**

---

## 2. Reference Documents & Configuration Inspected

- `docs/PHASE_2_6_AUTH_PRODUCTION_AUDIT.md` (349 lines) — confirmed baseline audit identifying prior Phase 2.6 findings (`L146` path mismatch, `L930` expression limit, `storage.rules` `(default)` mismatch, and legacy verification removal audit).
- `docs/PHASE_2_7_RULES_REMEDIATION_REPORT.md` (161 lines) — confirmed reported local remediation and non-deployment notice; treated as historical context only and re-verified independently in this audit.
- `firestore.rules` (1,072 lines) and `firestore.owner-admin.proposed.rules` — inspected line by line and diffed.
- `storage.rules` (127 lines) — inspected line by line.
- `firebase.json` (30 lines) — targets named Firestore database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`, `firestore.rules`, `firestore.indexes.json`, and `storage.rules`; emulator ports `firestore: 8085`, `auth: 9099`, `storage: 9199`.
- `.firebaserc` — **absent** (`ls -la .firebaserc*` returned `No such file or directory`). Deployments require explicit `--project gen-lang-client-0431487205` or creating `.firebaserc`.
- `firebase-blueprint.json` (348 lines) — schema reference; note documentation drift (documents `/activity_posts` and omits `/activities`, `/verification_requests`, and `/admin_*`).
- `app/google-services.json`, `app/src/main/res/values/firebase_applet_config.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `app/src/main/java/com/example/SogoApplication.kt`, `.env.example`.
- Core Kotlin & Admin tooling inspected: `FirebaseAuthListener.kt`, `AuthComponents.kt`, `MainActivity.kt`, `SogoRepository.kt`, `OwnerAdminRepository.kt`, `OwnerAdminViewModel.kt`, `CommunitySafetyGuidelinesDialog.kt`, `admin-tools/owner-admin-sync.mjs`, `admin-tools/owner-security.mjs`, `admin-tools/README.md`.
- Security rule & unit test suites inspected: `firestore.test.js`, `storage.test.js`, `firestore.owner-admin.test.js`, `SogoRepositoryRuleTest.kt`, `FirestoreEmulatorTestBase.kt`, `AuthComponentsTest.kt`, `FirebaseAuthListenerTest.kt`.

---

## 3. Line-by-Line Firestore Rules Security Review (`firestore.rules`)

### 3.1 Complete Collection & Subcollection Matrix

| Rule Path | Lines | `get` / `list` | `create` | `update` | `delete` | Assessment |
|---|---|---|---|---|---|---|
| `/{document=**}` | `6-8` | `false` | `false` | `false` | `false` | **PASS** — Global default deny. |
| `/users/{userId}` | `81-130` | `isSignedIn()` | `isOwner(userId)` + schema & `accountStatus == 'ACTIVE'` & `role == 'member'` | Owner profile fields (`role == 'member'`) OR `isVerifiedOwner()` (`accountStatus`, `updatedAt` only) | `isOwner(userId)` | **PASS** *(Low perf note on `L124`; see §3.2 #4)*. |
| `/users/{userId}/activity_reactions/{postId}` | `147-159` | `isOwner(userId)` | `isOwner(userId)` + schema + `isExistingNonBlockedActivityPost(postId)` | Same as `create` | `isOwner(userId)` | **PASS** — Checks `/activities/$(postId)` and `/activity_posts/$(postId)` + `!isBlockedBetween`. |
| `/users/{userId}/gallery_photos/{photoId}` | `170-181` | `isSignedIn()` | `isOwner(userId)` + schema (`<= 65000`) | *Denied (default deny)* | `isOwner(userId) \|\| isVerifiedOwner()` | **PASS**. |
| `/user_presence/{userId}` | `192-202` | `isSignedIn()` | `isOwner(userId)` + timestamp `<= request.time` | Same as `create` | `isOwner(userId)` | **PASS**. |
| `/conversations/{conversationId}` | `218-252` | `isSignedIn()` (`resource == null \|\| participant`) | `participant` + `!isBlockedBetween` + sender check | `participant` + immutable participants + `!isBlockedBetween` + Action 1/2 | `false` | **PASS** *(Note `resource == null` `get` pattern; see §3.2 #6)*. |
| `/conversations/{id}/messages/{messageId}` | `279-340` | `participant` of non-blocked parent conversation | `senderId == uid` + `isRead == false` + `!isBlockedBetween` | Receiver `isRead: false -> true` OR participant own reaction map update | `senderId == uid` | **PASS**. |
| `/user_blocks/{blockId}` & `/blocks/{blockId}` | `351-399` | `blockerId == uid \|\| blockedId == uid` | `blockerId == uid` & `blockedId != uid` | `blockerId == uid`, `blockedId` immutable | `blockerId == uid` | **PASS** — Supports both legacy/active block paths queried by `isBlockedBetween`. |
| `/user_reports/{reportId}` & `/reports/{reportId}` | `412-440` | `isVerifiedOwner() \|\| reporterId == uid` | `reporterId == uid`, `reportedId != uid`, `status == 'PENDING'` | `false` | `false` | **PASS**. |
| `/content_reports/{reportId}` & `/activity_reports/{reportId}` | `457-514` | `isVerifiedOwner() \|\| reporterId == uid` | `reporterId == uid`, target `!= uid`, `status == 'PENDING'` | `isVerifiedOwner()` (`PENDING -> REVIEWED\|DISMISSED`, `status` only) | `false` | **PASS**. |
| `/notifications/{notificationId}` | `528-558` | `userId == uid` | `isRead == false`; self OR whitelisted cross-user types with `senderId == uid` & `!isBlockedBetween` (`SYSTEM` blocked cross-user) | Recipient `isRead: false -> true` only | `userId == uid` | **PASS**. |
| `/friend_requests/{requestId}` | `574-606` | `resource == null \|\| participant` | `requesterId == uid`, `status == 'PENDING'`, `!isBlockedBetween` | `recipientId == uid`, `PENDING -> ACCEPTED`, `!isBlockedBetween` | `participant` | **PASS**. |
| `/user_follows/{followId}` | `609-625` | `resource == null \|\| follower \|\| following` | `followerId == uid`, `followingId != uid`, `!isBlockedBetween` | *Denied* | `followerId == uid` | **PASS**. |
| `/user_favorites/{favoriteId}` | `627-642` | `resource == null \|\| userId == uid` | `userId == uid`, `targetUserId != uid`, `!isBlockedBetween` | *Denied* | `userId == uid` | **PASS**. |
| `/profile_likes/{likeId}` | `644-662` | `resource == null \|\| liker \|\| likedUser` | `likerId == uid`, `likedUserId != uid`, `!isBlockedBetween` | *Denied* | `likerId == uid` | **PASS**. |
| `/profile_visits/{visitId}` | `664-683` | `resource == null \|\| visitor \|\| visitedUser` | `visitorId == uid`, `visitedUserId != uid`, `!isBlockedBetween` | **Same as `create` (`existing().visitorId` NOT checked)** | `visitor \|\| visitedUser` | **FAIL (Medium)** — See §3.2 #1. |
| `/call_sessions/{callId}` | `706-749` | `resource == null \|\| participant` | `callerId == uid`, `status == 'RINGING'`, `!isBlockedBetween` | `participant`, `!ENDED/!DECLINED`, callee `ANSWERED` or participant `ENDED/DECLINED`, `!isBlockedBetween` | `participant` | **PASS**. |
| `/call_sessions/{callId}/candidates/{candidateId}` | `760-777` | `resource == null \|\| participant` | Call `participant` + `senderId == uid` + `participantUids` match parent | `participant` (any call participant) | `participant` (any call participant) | **PASS** *(Low note §3.2 #5)*. |
| `/activity_posts/{postId}` & `/comments/{commentId}` | `800-889` | `isSignedIn()` | Author `== uid`, zero counters; comment `!isBlockedBetween(uid, parent.authorId)` | Author edits OR non-blocked member reaction/comment/share counters | Author or `isVerifiedOwner()` (or parent author for comments) | **PASS** *(Low Spark counter note §3.2 #7)*. |
| `/activities/{postId}` & `/comments/{commentId}` | `933-1034` | `isSignedIn()` | `userId == uid && authorId == uid`, empty `likes`/`commentIds`, zero counters; comment `!isBlockedBetween` | Author edits OR non-blocked member: Action 2 (`isValidActivityLikesToggle`), Action 3 (emoji counters), Action 4 (`commentIds`/`commentCount`/`shareCount`) | Author or `isVerifiedOwner()` (or parent author for comments) | **PASS** *(Low Spark counter note §3.2 #7)*. |
| `/verification_requests/{requestId}` | `1038-1041` | `isVerifiedOwner()` | `false` | `false` | `false` | **PASS** — Frozen for audit preservation. |
| `/admin_dashboard`, `/admin_usage_daily`, `/admin_registration_history` | `1044-1057` | `isVerifiedOwner()` | `false` | `false` | `false` | **PASS** — Admin SDK only. |
| `/admin_audit_logs/{logId}` | `1059-1069` | `isVerifiedOwner()` | `isVerifiedOwner()` + `adminUid == uid` + schema | `false` | `false` | **PASS** — Append-only audit log. |

### 3.2 Security & Defense-in-Depth Findings

1. **[MEDIUM] Cross-User Overwrite Allowed on `/profile_visits/{visitId}` Update (`firestore.rules:669-679`, `firestore.owner-admin.proposed.rules`)**
   - **Evidence:** `allow create, update:` only validates `incoming().visitId == visitId`, `incoming().visitorId == request.auth.uid`, and `incoming().visitedUserId != request.auth.uid`. It does **not** verify `existing().visitorId == request.auth.uid`, `incoming().visitedUserId == existing().visitedUserId`, or deterministic ID binding `visitId == 'visit_' + request.auth.uid + '_' + incoming().visitedUserId`.
   - **Risk:** `SogoRepository.recordProfileVisit()` (`SogoRepository.kt:1222`) uses predictable document IDs `visit_${uid}_${targetUid}`. Any authenticated, non-blocked third party (Charlie) who knows or guesses `visit_${ALICE_UID}_${BOB_UID}` can send an `update` replacing Alice's visit record with Charlie's `visitorId`/`visitorName`/`visitorPhotoUrl` (or even redirect `visitedUserId` to a fourth member), corrupting or hijacking existing profile visit history (`firestore.test.js:1233-1240` only tests `set` + `get`, not cross-user `update`).
   - **Recommendation:** Split `allow create` and `allow update` on `/profile_visits/{visitId}`, require `existing().visitorId == request.auth.uid` and `incoming().visitorId == existing().visitorId && incoming().visitedUserId == existing().visitedUserId` on `update`, and optionally bind `visitId == 'visit_' + request.auth.uid + '_' + incoming().visitedUserId`.

2. **[MEDIUM / CONDITIONAL] `storage.rules` Cross-Service `(default)` Lookup Mismatch (`storage.rules:52-74`)**
   - **Status:** **`NOT VERIFIED — PRODUCTION VALIDATION REQUIRED`**
   - **Evidence:** `storage.rules:52-74` references `/databases/(default)/documents/user_blocks`, `/databases/(default)/documents/blocks`, and `/databases/(default)/documents/conversations`, whereas `firebase.json:4` and `firebase_applet_config.xml:3` configure named Firestore database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`.
   - **Impact:** The current APK does not include `firebase-storage` (`app/build.gradle.kts`) and stores images as compressed `<= 60,000`-char `data:image/jpeg;base64,...` URIs inside Firestore. However, `firebase.json:9-11` still targets `storage.rules` for deployment; if `storage.rules` is deployed and Storage is accessed directly with user credentials, `!isBlockedBetween(request.auth.uid, userId)` on `/profile_photos/{userId}/{fileName}` (`storage.rules:81-84`) evaluates against an empty `(default)` Firestore database and would fail to enforce user blocks.

3. **[LOW] `isSignedIn()` Does Not Enforce `sign_in_provider == 'google.com'` at the Rules Layer (`firestore.rules:11-13`)**
   - **Evidence:** `function isSignedIn() { return request.auth != null; }` permits any Firebase Auth provider token (`email/password`, `anonymous`, etc.) if enabled in Firebase Console. Likewise `isVerifiedOwner()` (`L19-25`) checks `email`, `email_verified`, `owner`, `admin`, relying on Admin SDK provisioning + Console provider settings rather than `request.auth.token.firebase.sign_in_provider == 'google.com'`.
   - **Risk:** Safe **only** while Google is the sole enabled Sign-In provider in live Firebase Console (`gen-lang-client-0431487205`), which is **`NOT VERIFIED`**. Note that `FirestoreEmulatorTestBase.kt:89-93` and `@firebase/rules-unit-testing` `authenticatedContext` default tokens omit `firebase.sign_in_provider == 'google.com'`, so tightening this later requires updating test helpers.

4. **[LOW] `/users/{userId}` Owner Moderation `update` Evaluates Full `isValidUserProfile` Before Key Diff (`firestore.rules:119-128`)**
   - **Evidence:** During Phase 2.8 execution of `firestore.test.js:1134` (where `ownerDb` attempts an unauthorized update of `isVerifiedMember`/`verificationStatus` on Alice's user doc), the Firestore emulator logged: `PERMISSION_DENIED: ... Unable to evaluate the expression as the maximum of 1000 expressions to evaluate has been reached. for 'update' @ L102`.
   - **Risk:** **No bypass** — Firestore fails closed with `PERMISSION_DENIED`, and legitimate Owner `accountStatus` suspension updates (`firestore.test.js:1141`) succeed cleanly. Moving `incoming().diff(existing()).affectedKeys().hasOnly(['accountStatus', 'updatedAt'])` ahead of `isValidUserProfile(incoming(), userId)` at `L124-127` would short-circuit unauthorized Owner writes in <10 expressions instead of exhausting the 1,000-expression budget.

5. **[LOW] ICE Candidate Update/Delete Allows Either Call Participant (`firestore.rules:775-776`)**
   - **Evidence:** `allow update, delete:` checks `request.auth.uid in existing().participantUids` rather than `existing().senderId == request.auth.uid`.
   - **Risk:** Only the two call participants have access (isolated from outsiders), so risk is low, though restricting `update, delete` to `existing().senderId == request.auth.uid` (while retaining participant cleanup or call deletion) is cleaner.

6. **[LOW / DESIGN] `resource == null` Permitted on Deterministic Document `get` Rules**
   - **Evidence:** `conversations` (`L219-220`), `messages` (`L284`), `friend_requests` (`L575-576`), `user_follows` (`L610-611`), `user_favorites` (`L628`), `profile_likes` (`L645-646`), `profile_visits` (`L665-666`), `call_sessions` (`L707-708`), and `candidates` (`L761-762`) include `resource == null || ...` on `allow get`.
   - **Risk:** Safe — when a document exists, the participant/owner check strictly denies unauthorized reads; when absent, Firestore returns a non-existent snapshot without leaking any data, enabling client existence checks (`ensureConversationExists`, deterministic social IDs). `list` queries never include `resource == null`.

7. **[LOW / SPARK ARCHITECTURE] Client-Driven Aggregate Counters & Activity Lists (`L849-855`, `L993-1000`)**
   - **Evidence:** Because the project operates on the Spark plan without Cloud Functions triggers, `reactionCounts`, `totalReactions`, `commentCount`, `shareCount`, and `commentIds` are updated by non-blocked signed-in members via `affectedKeys().hasOnly(...)` rather than strict `+1`/`-1` delta validation (by contrast, `likes` on `/activities` IS strictly validated single-UID toggle via `isValidActivityLikesToggle` at `L917-931`). Additionally, `match /users/{userId}/activity_reactions/{postId}` `allow delete` (`L158`) removes only the caller's own reaction marker without forcing parent counter updates.
   - **Risk:** A malicious authenticated non-blocked member could inflate or zero-out `reactionCounts`/`totalReactions`/`commentCount`/`shareCount` on another user's post (cannot tamper with post text, author, `likes`, or blocked authors). Block enforcement (`!isBlockedBetween`) still blocks all mutations from blocked users.

8. **Block vs Mute Verification:** Mutual blocking (`user_blocks` and `blocks`) via `isBlockedBetween` is enforced across chats, messages, notifications, friend requests, follows, favorites, profile likes, profile visits, calls, activity likes/reactions/comments/shares, and activity reaction subcollections. There is no separate `mute` feature or collection in the codebase or rules.

9. **Legacy Compatibility & `/verification_requests` Protection:**
   - Transitional tolerances in `isValidUserProfile` (`L70-71`), `isValidActivityPost` (`L789`), and `isValidActivityRecord` (`L903`) preserve read/update compatibility for legacy production documents without allowing any client (or Owner) to set `isVerifiedMember`, `verificationStatus`, or `authorVerified` in `create`/`update` allowlists.
   - `/verification_requests/{requestId}` (`L1038-1041`) allows `get, list` solely for `isVerifiedOwner()` and denies all `create, update, delete` (`if false`).

---

## 4. Owner / Admin Authorization Review & Functional Rules Diff

1. **Multi-Layer Owner Verification Verified:**
   - **Client Entry Gate (`SogoRepository.kt:183-191`):** `isOwnerCandidateSignedIn()` checks `user.isEmailVerified && hasGoogleProvider && user.email.equals("gptgamestudioph@gmail.com", ignoreCase = true)`. No Firestore user field is trusted.
   - **Repository & Claims Gate (`OwnerAdminRepository.kt:53-87, 260-281`):** `verifyOwnerAccess(true)` refreshes the Firebase ID token and verifies `isGoogleProvider == true`, `email == "gptgamestudioph@gmail.com"`, `emailVerified == true`, `claims["owner"] == true`, and `claims["admin"] == true`.
   - **Server Claims Provisioning (`admin-tools/owner-admin-sync.mjs`, `owner-security.mjs`):** Assigns `{ owner: true, admin: true }` only via Firebase Admin SDK after `verifyOwnerEligibility`. Client code cannot write custom claims.
   - **Firestore Server Enforcement (`firestore.rules:19-25`):** `isVerifiedOwner()` checks `request.auth.token.email == 'gptgamestudioph@gmail.com' && request.auth.token.email_verified == true && request.auth.token.owner == true && request.auth.token.admin == true`.
   - **Privilege Escalation & Audit Protection:** Ordinary members are pinned to `role == 'member'` and cannot write `accountStatus` (`L97, 109-117`). `admin_dashboard`, `admin_usage_daily`, and `admin_registration_history` reject all client writes (`if false`, even from Owner). `admin_audit_logs` permits `create` only by `isVerifiedOwner()` with `adminUid == request.auth.uid` and forbids `update, delete` (`L1059-1069`).

2. **Functional Diff (`diff -u firestore.rules firestore.owner-admin.proposed.rules`):**
   - **Exit code `1` with 8 comment-only lines differing** (`firestore.rules` lines `69`, `243`, `246`, `849`, `851`, `988`, `993`, `997`).
   - **Functional parity:** **100% identical** — zero rule logic, match block, helper function, or field allowlist differences exist between `firestore.rules` and `firestore.owner-admin.proposed.rules`.

---

## 5. Storage Rules & Named-Database Configuration Review

- **Path Restrictions (`storage.rules:76-124`):** Allows only `/profile_photos/{userId}/{fileName}` (owner write, JPEG/PNG/WebP `<= 5 MB`, non-blocked signed-in read), `/chat_photos/{conversationId}/{fileName}` (participant upload/read, `<= 5 MB`, `allow delete: if false`), and `/voice_messages/{conversationId}/{fileName}` (participant upload/read, `<= 10 MB`, `allow delete: if false`). All other paths including any verification evidence paths are denied (`L6-8`).
- **Named-Database Cross-Service Issue (`NOT VERIFIED — PRODUCTION VALIDATION REQUIRED`):**
  - `storage.rules:52-74` hardcodes `/databases/(default)/documents/...` in `isBlockedBetween`, `canReadConversationMedia`, and `canUploadConversationMedia`.
  - Production Firestore runs on named database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`.
  - Local `storage.test.js` (`24/24` passed) exercises `@firebase/rules-unit-testing`'s `(default)` Firestore instance only and therefore does **not** prove cross-service Storage enforcement for named-database data.
  - While the current Android client stores compact JPEG data URIs in Firestore without `firebase-storage`, deploying `storage.rules` as listed in `firebase.json:9-11` leaves Storage paths unprotected by live named-DB blocks/conversations if Storage is ever invoked directly.

---

## 6. Firebase Configuration & Secret Safety Review

- **Project & Package Alignment:** `app/google-services.json` defines `project_id: gen-lang-client-0431487205`, `storage_bucket: gen-lang-client-0431487205.firebasestorage.app`, `package_name: com.sogo.chatandmeet` (matching `app/build.gradle.kts:17`), two Android OAuth clients (`client_type: 1`), and one Web OAuth client (`client_type: 3`). Named DB `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` matches across `firebase.json:4`, `firebase_applet_config.xml:3`, `SogoRepository.kt:2144`, and `admin-tools/owner-admin-sync.mjs:29`.
- **Secrets Check:** `.env` is absent; `.env.example` contains only commented-out `# GEMINI_API_KEY=MY_GEMINI_API_KEY`; `app/build.gradle.kts:87` ignores `FIREBASE_APPCHECK_DEBUG_TOKEN`. No API keys, OAuth secrets, or keystore credentials are hardcoded in Kotlin source or exposed in this report.
- **App Check & Backup Hardening:** `SogoApplication.kt:48-65` installs `PlayIntegrityAppCheckProviderFactory` for release builds (`DebugAppCheckProviderFactory` scoped to `debugImplementation` only). `AndroidManifest.xml:16` sets `android:allowBackup="false"`.
- **Live Firebase Console & Missing `.firebaserc` (`NOT VERIFIED`):** `.firebaserc` is absent; Firebase Console Authentication provider restrictions (confirming Email/Password and Anonymous are disabled and only Google is enabled), Play Integrity App Check enforcement mode, and live `{ owner: true, admin: true }` custom claims on `gptgamestudioph@gmail.com` could not be verified in this offline/read-only container.
- **Pre-Login Copy Note (Non-Blocking):** `AuthComponents.kt:562,570` displays `"18+ Adult Verified Community"` / `"zero fake accounts"`, whereas `CommunitySafetyGuidelinesDialog.kt:139` explicitly clarifies that Google Sign-In confirms the user's Google account rather than real-world identity.

---

## 7. Fresh Phase 2.8 Verification & Automated Test Results

> **Environment Toolchain Verified:** `firebase 15.35.0`, `node v22.23.4`, `openjdk 21.0.13` (`2026-10-20 LTS`).  
> **Emulator Scope Disclaimer:** All emulator tests executed locally against `--project demo-no-project`; live production Firebase (`gen-lang-client-0431487205`) was never accessed or modified.

### 7.1 Android Compilation (`compile_applet`)
- **Result:** **PASS** — `Build succeeded - the applet is compiled`.

### 7.2 JS Security Rules & Admin-Tools Suites
- **Command 1:** `firebase emulators:exec --only auth,firestore,storage --project demo-no-project "node --test firestore.test.js && node --test storage.test.js && node --test firestore.owner-admin.test.js" && node --test admin-tools/owner-security.test.js` → **Exit Code `0`**
- **Command 2 (spec confirmation):** `firebase emulators:exec --only auth,firestore --project demo-no-project "node --test --test-reporter=spec firestore.test.js"` → **Exit Code `0`**
- **Verified Counts:**
  - `firestore.test.js`: **32 passed, 0 failed, 0 skipped** (all 32 assertions passed; note `users` Owner-reject test `L1134` failed closed via `PERMISSION_DENIED` while hitting the 1,000-expression limit — see §3.2 #4).
  - `storage.test.js`: **24 passed, 0 failed, 0 skipped** *(against local `(default)` emulator only; cross-service named-DB behavior remains **`NOT VERIFIED — PRODUCTION VALIDATION REQUIRED`**)*.
  - `firestore.owner-admin.test.js`: **4 passed, 0 failed, 0 skipped**.
  - `admin-tools/owner-security.test.js`: **4 passed, 0 failed, 0 skipped**.

### 7.3 Android Unit & Robolectric Suites (`:app:testDebugUnitTest`)
- **SDK-35 & Pure JVM Tests (9 of 12 test classes) — PASS (`48 passed, 0 failed, 0 skipped`):**
  - **Command:** `gradle :app:testDebugUnitTest --tests "com.example.ExampleUnitTest" --tests "com.example.OwnerAdminTest" --tests "com.example.RegressionRepairsTest" --tests "com.example.ads.*" --tests "com.example.data.model.*" --tests "com.example.ui.auth.GoogleAuthOnlyRegressionTest" --tests "com.example.ui.auth.AuthValidatingCardTest" --tests "com.example.ui.safety.*" --tests "com.example.ui.profile.*"` → **Exit Code `0`** (`app/build/test-results/testDebugUnitTest/TEST-*.xml`):
    - `com.example.ExampleUnitTest`: **1 passed, 0 failed, 0 skipped**
    - `com.example.OwnerAdminTest`: **5 passed, 0 failed, 0 skipped**
    - `com.example.RegressionRepairsTest`: **21 passed, 0 failed, 0 skipped**
    - `com.example.ads.AdMobManagerTest`: **5 passed, 0 failed, 0 skipped**
    - `com.example.data.model.ModelsSerializationTest`: **4 passed, 0 failed, 0 skipped**
    - `com.example.ui.auth.AuthValidatingCardTest`: **1 passed, 0 failed, 0 skipped**
    - `com.example.ui.auth.GoogleAuthOnlyRegressionTest`: **7 passed, 0 failed, 0 skipped**
    - `com.example.ui.profile.MemberProfileSetupScreenTest`: **2 passed, 0 failed, 0 skipped**
    - `com.example.ui.safety.CommunitySafetyGuidelinesTest`: **2 passed, 0 failed, 0 skipped**
- **SDK-36 Default Robolectric Tests (3 of 12 test classes) — `BLOCKED` in Phase 2.8 Container:**
  - Affected classes (each uses `@Config(manifest = Config.NONE)` without pinning `sdk = [35]`): `com.example.data.repository.SogoRepositoryRuleTest` (10 tests), `com.example.ui.auth.AuthComponentsTest` (15 tests), and `com.example.ui.auth.FirebaseAuthListenerTest` (2 tests).
  - **Exact Failure:** `java.lang.AssertionError: Failed to fetch maven artifact org.robolectric:android-all-instrumented:16-robolectric-13921718-i7` (`Server returned HTTP response code: 429 for URL: https://repo1.maven.org/maven2/...`). The local `/root/.m2` repository caches only `15-robolectric-13954326-i7`, and Maven Central rate-limited SDK 36 artifact downloads during Phase 2.8.
  - Note: Phase 2.7 (`docs/PHASE_2_7_RULES_REMEDIATION_REPORT.md:122-140`) previously recorded all 75/75 passing on identical rules/code before HTTP 429 rate limiting, and `firestore.test.js` (32/32) + `firestore.owner-admin.test.js` (4/4) re-verified the Firestore emulator rules directly in Phase 2.8.
- **Instrumented Android Tests (`androidTest`) & Live Production Firebase:** **`NOT VERIFIED`** (no Android emulator/ADB or live production credentials/mutations).

---

## 8. Security Regression Checks (9 Attack / Abuse Scenarios)

| # | Attack / Abuse Scenario | Result | Controlling Rule / Mechanism & Verification Evidence |
|---|---|---|---|
| 1 | **Unauthenticated access to member profiles, chats, calls, or admin docs** | **PASS (Blocked)** | Default deny (`L6-8`) + `isSignedIn()` on all collections; verified in `firestore.test.js` & `firestore.owner-admin.test.js` (`L72-76`, `L225`). |
| 2 | **Cross-user profile, presence, or gallery tampering (`uidB` writes `uidA`)** | **PASS (Blocked)** | `isOwner(userId)` + immutable `userId`/`createdAt` on `/users/{uid}` (`L102-118`), `/gallery_photos` (`L173-180`), `/user_presence` (`L195-201`); verified in `firestore.test.js:1191-1199`. |
| 3 | **Chat / call eavesdropping, sender spoofing, or third-party reaction tampering** | **PASS (Blocked)** | `participantUids` checks + `senderId == request.auth.uid` + `preservesOtherReaction` (`L218-340`, `L706-777`); verified in `firestore.test.js` (`32/32`). |
| 4 | **Blocked-user interaction bypass (chat, call, friend request, social graph, activity comments/likes/reactions)** | **PASS (Blocked)** | `!isBlockedBetween(...)` across `/conversations`, `/messages`, `/notifications`, `/friend_requests`, `/user_follows`, `/user_favorites`, `/profile_likes`, `/profile_visits`, `/call_sessions`, `/activity_posts`, `/activities`, `/comments`, and `/activity_reactions`; verified in `firestore.test.js`. *(Storage cross-service block enforcement remains `NOT VERIFIED — PRODUCTION VALIDATION REQUIRED`.)* |
| 5 | **Social graph & profile-visit tampering (spoofing `visitorId`, `likerId`, or overwriting another user's visit record)** | **FAIL (Medium Gap on `profile_visits` `update`)** | `user_follows`, `user_favorites`, `profile_likes`, and `friend_requests` enforce ownership cleanly (`L574-662`). **However**, `/profile_visits/{visitId}` (`L669-679`) omits `existing().visitorId == request.auth.uid` on `update`, permitting cross-user overwrite of predictable `visit_<uidA>_<uidB>` docs (§3.2 #1). |
| 6 | **Activity feed hijacking, mass-likes manipulation, or unblocked counter tampering** | **PASS (Core Isolation) / LOW (Spark Counters)** | Content/author/postId edits restricted to author (`L967-978`); `likes` enforced as strict single-UID toggle (`L917-931`); delete restricted to author or `isVerifiedOwner()`. Non-blocked members can still set aggregate counters (`reactionCounts`/`totalReactions`/`commentCount`/`shareCount`) within type/range bounds due to Spark no-Functions design (§3.2 #7). |
| 7 | **Owner privilege escalation (`role: 'owner'`, spoofed claims, self-unsuspension, client writes to `admin_dashboard`)** | **PASS (Blocked)** | `role == 'member'` locked (`L77, 117`); `accountStatus` locked to `isVerifiedOwner()` (`L97, 125-127`); `isVerifiedOwner()` requires email + `email_verified` + `{ owner: true, admin: true }` custom claims (`L19-25`); `admin_dashboard`/`admin_usage_daily`/`admin_registration_history` `if false` (`L1044-1057`). Verified in `firestore.owner-admin.test.js` (`L77-142`) and `OwnerAdminTest`. |
| 8 | **Tampering with frozen `/verification_requests` or injecting legacy `isVerifiedMember` / `verificationStatus` / `authorVerified`** | **PASS (Blocked)** | `/verification_requests` freezes `create, update, delete: if false;` (`L1038-1041`); all create/update key allowlists omit `isVerifiedMember`, `verificationStatus`, and `authorVerified`. Verified in `firestore.test.js:1091-1172` and `firestore.owner-admin.test.js:206-232`. |
| 9 | **Fake `SYSTEM` notifications, forged moderation status on reports, or tampering with `admin_audit_logs`** | **PASS (Blocked)** | Cross-user `notifications` prohibit `type == 'SYSTEM'` (`L541`); `content_reports`/`activity_reports` force `status == 'PENDING'` on create and restrict updates to `isVerifiedOwner()` (`L457-514`); `admin_audit_logs` rejects non-owner creates and all `update, delete` (`L1059-1069`). Verified in `firestore.test.js:1376-1401` and `firestore.owner-admin.test.js:144-204`. |

---

## 9. Remaining Risks, Limitations & Pre-Deployment Checklist

1. **Fix `/profile_visits/{visitId}` Cross-User Update Gap (Medium):** Restrict `update` in both `firestore.rules:669-679` and `firestore.owner-admin.proposed.rules` to `existing().visitorId == request.auth.uid` (and immutable `visitedUserId`) and add an `assertFails` regression test in `firestore.test.js`.
2. **Resolve / Quarantine `storage.rules` Named-Database Deployment Risk (`NOT VERIFIED — PRODUCTION VALIDATION REQUIRED`):** Because `storage.rules:52-74` references `/databases/(default)/documents/...` and Android currently uses zero `firebase-storage` SDK calls, do **not** deploy `storage.rules` for production Storage uploads against named-DB block/conversation docs; either update/re-architect Storage authorization before enabling Firebase Storage uploads or deploy `firestore:rules` explicitly (without enabling Storage media paths).
3. **Unblock SDK-36 Robolectric Rule Tests:** Pin `@Config(sdk = [35])` (or configure `robolectric.properties` / pre-cache SDK 36) in a follow-up fix phase so `SogoRepositoryRuleTest` (10 tests), `AuthComponentsTest` (15 tests), and `FirebaseAuthListenerTest` (2 tests) re-run cleanly without Maven Central HTTP `429` blocks.
4. **Live Firebase Console Checks (Manual Pre-Deploy):**
   - Bind project target (`.firebaserc` or explicit `--project gen-lang-client-0431487205`).
   - Confirm in Firebase Console Auth that **Google** is the sole enabled provider (Email/Password and Anonymous disabled).
   - Run `admin-tools/owner-admin-sync.mjs --grant-claims-only` against `gen-lang-client-0431487205` to confirm `{ owner: true, admin: true }` custom claims on `gptgamestudioph@gmail.com`.
   - Confirm Play Integrity App Check enforcement matches signed release builds.
5. **Minor Polish (Non-Blocking):** Reorder `affectedKeys().hasOnly(['accountStatus', 'updatedAt'])` before `isValidUserProfile(...)` at `firestore.rules:124-127`, and align `"zero fake accounts"` copy in `AuthComponents.kt:562,570` with `CommunitySafetyGuidelinesDialog.kt:139`.

---

## 10. Final Deployment Recommendation

**Recommendation:** **`NOT READY FOR DEPLOYMENT`**

**Basis:**
1. **Unpatched Medium Firestore Rule Gap (`firestore.rules:669-679`):** `match /profile_visits/{visitId}` permits an authenticated non-blocked member to overwrite another visitor's `visit_<uidA>_<uidB>` record via `update` because `existing().visitorId == request.auth.uid` is not checked.
2. **Storage Cross-Service Named-Database Validation (`NOT VERIFIED — PRODUCTION VALIDATION REQUIRED`):** `storage.rules:52-74` references `(default)` instead of `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`, preventing safe production validation of `storage.rules` if deployed alongside `firebase.json`.
3. **Partial Test & Live-Console Coverage Gaps:** 3 Android test classes (`SogoRepositoryRuleTest`, `AuthComponentsTest`, `FirebaseAuthListenerTest`) are **BLOCKED** by Maven Central HTTP `429` on SDK 36 Robolectric artifacts in Phase 2.8, `.firebaserc` is absent, and live Firebase Console Auth providers / Owner custom claims remain **NOT VERIFIED**.
