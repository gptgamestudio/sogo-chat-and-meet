# SOGO CHAT & MEET — Phase 2.7: Targeted Firebase Rules Remediation Report

**Remediation Date:** 2026-11-24  
**Mode:** `IMPLEMENT + TEST + DOCUMENT`  
**Scope:** Firestore Security Rules, Storage Rules Investigation, Rules Regression Tests, Verification, and Documentation  
**Target Project:** `gen-lang-client-0431487205` (`com.sogo.chatandmeet`)  
**Configured Named Firestore Database:** `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`  
**Git Branch / Commit Hash:** **NOT VERIFIED** — `.git` directory is absent in the build container (`git rev-parse HEAD` → `fatal: not a git repository (or any of the parent directories): .git`).

---

## Executive Summary & Classifications

| Remediation Area / Verification Criterion | Status | Summary |
|---|---|---|
| **Task 1:** Activity Reaction Authorization (`firestore.rules` `activity_reactions`) | **PASS** | `isExistingNonBlockedActivityPost(postId)` verifies that the target post exists in `/activities/{postId}` (used by `SogoRepository.createActivityPost`) or legacy `/activity_posts/{postId}` and enforces `!isBlockedBetween(request.auth.uid, authorUid)` while preserving ownership and reaction payload validation. |
| **Task 2:** Firestore 1,000-Expression Evaluation Limit (`firestore.rules` `/activities` & `/activity_posts` update) | **PASS** | Extracted `isValidActivityUpdateBase`, `isAuthorActivityUpdate`, and `isMemberActivityInteraction` (plus matching `/activity_posts` helpers), eliminating duplicate `isBlockedBetween()` calls and short-circuiting non-matching branches on `next.likes != prev.likes` and `changed.hasOnly(...)` without weakening any security restriction. |
| **Task 3:** Synchronize `firestore.owner-admin.proposed.rules` | **PASS** | Applied identical Task 1 & Task 2 fixes and synchronized the frozen Owner-only audit read `/verification_requests/{requestId}` block. `diff -u firestore.rules firestore.owner-admin.proposed.rules` shows zero functional differences (comments only). |
| **Task 4:** Storage Named-Database Cross-Service Investigation (`storage.rules:52-74`) | **NOT VERIFIED** | `storage.rules` hardcodes `/databases/(default)/documents/...`, whereas the app uses named Firestore database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`. Cross-service Storage rules for a named DB are blocked by Cloud Storage's `(default)` cross-service restriction and `@firebase/rules-unit-testing`'s `(default)` emulator wiring; Android currently stores compact JPEG data URIs in Firestore without `firebase-storage`. |
| **Task 5a:** Full Unfiltered Android Unit, Robolectric & Firestore Emulator Suite | **PASS** | Unfiltered `gradle :app:testDebugUnitTest --rerun-tasks` against Auth + Firestore emulators ran all **75 tests** to completion: **75 passed, 0 failed, 0 skipped** (including **10/10** in `SogoRepositoryRuleTest`). |
| **Task 5b:** Firestore, Storage, Owner-Admin & Admin-Tools JS Suites | **PASS** | `firestore.test.js` (**32/32**), `storage.test.js` against `(default)` emulator (**24/24**), `firestore.owner-admin.test.js` (**4/4**), and `admin-tools/owner-security.test.js` (**4/4**) all passed. |
| **Task 5c:** Android Compilation (`compile_applet`) | **PASS** | `Build succeeded - the applet is compiled`. |
| Live Production Deployment / Production Data Mutation | **NOT VERIFIED** (Not Executed) | Zero `firebase deploy` or production writes performed, adhering strictly to no-deploy constraints. |

---

## 1. Exact Files Changed

| File | Change Type | Purpose |
|---|---|---|
| `firestore.rules` | Modified | Fixed `/users/{userId}/activity_reactions/{postId}` post lookup + block enforcement; refactored `/activities/{postId}` and `/activity_posts/{postId}` `allow update` rules to stay well under the 1,000-expression evaluation limit. |
| `firestore.owner-admin.proposed.rules` | Modified | Synchronized identically with `firestore.rules` (Tasks 1 & 2 fixes + frozen Owner-only read `/verification_requests/{requestId}` block). |
| `firestore.test.js` | Modified | Extended `/activities` test suite with emoji reaction tests (`/users/{uid}/activity_reactions/{postId}` + `/activities/{postId}` Action 3), nonexistent post denial, field-tamper/`authorVerified` rejection, and block enforcement (`user_blocks`) across reactions, likes, emoji counters, and comment counts. |
| `firestore.owner-admin.test.js` | Modified | Added parity test verifying `/verification_requests` Owner-only reads, frozen writes/updates/deletes, and `/activities` emoji reactions under `firestore.owner-admin.proposed.rules`. |
| `docs/PHASE_2_7_RULES_REMEDIATION_REPORT.md` | Created | Phase 2.7 remediation and verification report. |

**Unchanged by design:** `storage.rules` (see Section 4), `SogoRepository.kt`, `app/build.gradle.kts`, Android UI/auth code, Google Sign-In flow, custom claims logic, and live Firebase configuration.

---

## 2. Root Causes & Exact Rule Fixes

### 2.1 Task 1 — Activity Reaction Authorization (`firestore.rules:132-160`)
- **Confirmed Post Collection:** `SogoRepository.createActivityPost()` (`SogoRepository.kt:1572, 2139`) stores social feed posts in **`/activities/{postId}`** (`ACTIVITIES_COLLECTION = "activities"`).
- **Root Cause:** `match /users/{userId}/activity_reactions/{postId}` previously checked `exists(/databases/$(database)/documents/activity_posts/$(postId))` only. When `SogoRepository.toggleActivityReaction(postId, emoji)` wrote `/users/{uid}/activity_reactions/{postId}` for a post in `/activities/{postId}`, creation failed with `PERMISSION_DENIED: evaluation error at L143:34`. In addition, the `activity_reactions` rule previously did not verify block status against the parent post author directly.
- **Implemented Fix (`firestore.rules:140-159`, mirrored in `firestore.owner-admin.proposed.rules`):**
  ```javascript
  function isExistingNonBlockedActivityPost(postId) {
    return (exists(/databases/$(database)/documents/activities/$(postId)) &&
            !isBlockedBetween(request.auth.uid, get(/databases/$(database)/documents/activities/$(postId)).data.userId)) ||
           (exists(/databases/$(database)/documents/activity_posts/$(postId)) &&
            !isBlockedBetween(request.auth.uid, get(/databases/$(database)/documents/activity_posts/$(postId)).data.authorId));
  }

  match /activity_reactions/{postId} {
    allow get, list: if isOwner(userId);

    allow create, update: if isOwner(userId) &&
                             isValidId(userId) &&
                             isValidId(postId) &&
                             incoming().keys().hasAll(['userId', 'postId', 'emoji', 'updatedAt']) &&
                             incoming().keys().hasOnly(['userId', 'postId', 'emoji', 'updatedAt']) &&
                             isValidActivityReaction(incoming(), postId) &&
                             isExistingNonBlockedActivityPost(postId);

    allow delete: if isOwner(userId) && isValidId(userId) && isValidId(postId);
  }
  ```
- **Preserved Protections:**
  - Authentication & strict ownership (`isOwner(userId)` + `data.userId == request.auth.uid`), valid path IDs, exact 4-key allowlist, emoji size (`1..16`), and server timestamp bound (`<= request.time`).
  - Schema/ownership validation executes *before* `isExistingNonBlockedActivityPost(postId)` so invalid payloads fail immediately without spending `exists()`/`get()` budget.
  - Block enforcement (`!isBlockedBetween(...)`) now blocks reactions in `/users/{userId}/activity_reactions/{postId}` even outside a multi-document transaction.
  - Compatible with both `/activities/{postId}` and legacy `/activity_posts/{postId}` without loosening read/write access on `/activities`.
  - Transaction lookup budget: **6** `exists/get` calls on `activity_reactions` + **4** `exists` calls on `/activities/{postId}` = **10 total** (well below Firestore's 20-call transaction limit).

### 2.2 Task 2 — Firestore 1,000-Expression Evaluation Limit (`firestore.rules:807-856` & `933-996`)
- **Root Cause:** In `match /activities/{postId}` `allow update` (previously `L930-967`), Firestore's expression engine inlined `isValidActivityRecord(incoming())`, repeated `incoming()`/`existing()` dereferences, and **three separate** `!isBlockedBetween(request.auth.uid, existing().userId)` expansions (each expanding 4 `exists()` path builders). During Action 3 (`toggleActivityReaction`, which updates `reactionCounts`, `totalReactions`, and `updatedAt` with unchanged `commentIds`):
  1. Action 1 failed at `existing().userId == request.auth.uid`.
  2. Action 2 passed `username`, `activityType`, `content`, and `commentIds`, **evaluated `!isBlockedBetween(...)` in full**, and only then failed `isValidActivityLikesToggle`.
  3. Action 3 re-checked `username`, `activityType`, `content`, `likes`, `commentIds`, and **evaluated `!isBlockedBetween(...)` a second time**.
  This crossed Firestore's 1,000-expression evaluation limit (`Unable to evaluate the expression as the maximum of 1000 expressions to evaluate has been reached. for 'update' @ L930`).
- **Implemented Fix:**
  - Split `/activities/{postId}` `allow update` into `isValidActivityUpdateBase(postId)`, `isAuthorActivityUpdate()`, and `isMemberActivityInteraction()` using `let` bindings (`next`, `prev`, `changed`).
  - Hoisted `next.username == prev.username`, `next.activityType == prev.activityType`, `next.content == prev.content`, and **`!isBlockedBetween(request.auth.uid, prev.userId)` once** across member Actions 2, 3, and 4 (evaluated only after one of the three sub-actions matches).
  - Placed `next.likes != prev.likes` first in Action 2 so Action 3 and Action 4 short-circuit past `isValidActivityLikesToggle` in 3 expressions.
  - Applied the same refactoring (`isValidActivityPostUpdateBase`, `isAuthorActivityPostUpdate`, `isMemberActivityPostInteraction`) to `/activity_posts/{postId}` (`L807-856`).
- **Preserved Restrictions:** Every field check in `isValidActivityRecord`, immutable field lock (`postId`, `userId`, `authorId`, `timestamp`, `createdAt`), timestamp bound (`updatedAt <= request.time`), `isValidActivityLikesToggle`, `affectedKeys().hasOnly(...)` per action, and `!isBlockedBetween` remain 100% enforced. Legacy verification field `authorVerified` remains disallowed in member update diff allowlists.

### 2.3 Task 3 — Synchronized `firestore.owner-admin.proposed.rules`
- Applied identical Task 1 and Task 2 helper/rule refactors to `firestore.owner-admin.proposed.rules`.
- Added the frozen `/verification_requests/{requestId}` rule block (`allow get, list: if isVerifiedOwner(); allow create, update, delete: if false;`) to `firestore.owner-admin.proposed.rules` so both files preserve Owner-only audit read access while blocking all client writes, updates, and deletes and preventing any manual verification workflow from functioning.
- Verified with `diff -u firestore.rules firestore.owner-admin.proposed.rules`: only comment lines differ.

---

## 3. Task 4 — Storage Named-Database Cross-Service Investigation (`NOT VERIFIED`)

### Findings
1. **Configuration Mismatch:**
   - `firebase.json:4`, `app/src/main/res/values/firebase_applet_config.xml:3`, and `SogoRepository.kt:2143-2144` bind Firestore to the named database **`ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`**.
   - `storage.rules:52-74` references **`/databases/(default)/documents/...`** in `isBlockedBetween`, `canReadConversationMedia`, and `canUploadConversationMedia`.
2. **Current Android Upload Architecture:**
   - Inspected `app/build.gradle.kts` and `app/src/main`: the Android app does **not** include the Firebase Storage SDK (`firebase-storage`) and makes **zero** Firebase Storage uploads/downloads.
   - Images (avatars, cover photos, gallery photos, activity photos, chat images via `ProfilePhotoHelper.kt`) are resized/compressed to compact `data:image/jpeg;base64,...` strings (`<= 65,000` chars) and stored inside Firestore documents in the configured named database.
3. **Why a Safe Named-DB Fix Cannot Be Locally Validated (and Why `(default)` Is Invalid for Named DB Data):**
   - Assuming `(default)` is correct would be **false**: in production (`gen-lang-client-0431487205`), `user_blocks`, `blocks`, and `conversations` live in `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`, not `(default)`.
   - Conversely, inspecting `firebase-tools` (`lib/emulator/storage/rules/runtime.js:332-344`) and `@firebase/rules-unit-testing` (`dist/index.cjs.js:257, 307-315`) plus Firebase Cloud Storage cross-service rules documentation confirmed two hard constraints:
     1. `@firebase/rules-unit-testing` (`ctx.firestore()` and `clearFirestore()`) hardcodes `/databases/(default)/documents` and cannot seed/clear the named Firestore database locally for `storage.test.js`.
     2. Firebase Cloud Storage Security Rules (`firestore.get()` / `firestore.exists()`) in Firebase itself target `(default)` rather than arbitrary named Firestore databases.
   - Per Task 4 instructions, `storage.rules` was **not** loosened, Firebase Storage SDK was **not** added, and cross-service Storage rule behavior against named database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` is formally classified **`NOT VERIFIED`**. If Firebase Storage media uploads are adopted in a future release, chat/block authorization metadata must either live in `(default)` or be enforced via server-minted signed URLs / Cloud Functions rather than cross-service Storage lookups to a named Firestore database.

---

## 4. Complete Task 5 Regression & Rules Verification Results

> **Emulator Scope Disclaimer:** All emulator runs executed locally against `--project demo-no-project` with `--only auth,firestore` or `--only auth,firestore,storage`. Live production Firebase (`gen-lang-client-0431487205`) was not modified or queried.

### 4.1 Android Compilation (`compile_applet`)
- **Result:** **PASS** — `Build succeeded - the applet is compiled`.

### 4.2 Full Unfiltered Android Unit, Robolectric & Firestore Emulator Suite
- **Command:** `firebase emulators:exec --only auth,firestore --project demo-no-project "gradle :app:testDebugUnitTest --rerun-tasks"`
- **Exit Code:** `0` (`BUILD SUCCESSFUL in 2m 43s`, `Script exited successfully (code 0)`)
- **Aggregated Result (`app/build/test-results/testDebugUnitTest/TEST-*.xml`):** **75 tests, 75 passed, 0 failures, 0 errors, 0 skipped** (note `failFast = true` in `app/build.gradle.kts:75` reached completion with zero failures).
- **Per-Class Counts:**
  - `com.example.ExampleUnitTest`: **1 passed, 0 failed, 0 skipped**
  - `com.example.OwnerAdminTest`: **5 passed, 0 failed, 0 skipped**
  - `com.example.RegressionRepairsTest`: **21 passed, 0 failed, 0 skipped**
  - `com.example.ads.AdMobManagerTest`: **5 passed, 0 failed, 0 skipped**
  - `com.example.data.model.ModelsSerializationTest`: **4 passed, 0 failed, 0 skipped**
  - `com.example.data.repository.SogoRepositoryRuleTest`: **10 passed, 0 failed, 0 skipped**
    - Includes **`activityPosts_createReactToggleAndDelete_enforcesRules` — PASSED** (`createActivityPost` → Bob reacts `👍` → observes `👍` → switches to `❤️` → observes `totalReactions == 1`, `❤️ == 1`, `👍 == 0` → Bob denied delete → Alice deletes)
    - Includes all remaining 9 rule tests (`saveUserProfile_*`, `conversationAndMessages_*`, `observeMessages_unauthenticatedUser_*`, `blockUser_*`, `friendRequests_*`, `freeCallSession_*`, `observeFriendRequestsAndCallSessions_*`, `chatMessages_toggleEmojiReaction_*`) — all **PASSED**.
  - `com.example.ui.auth.AuthComponentsTest`: **15 passed, 0 failed, 0 skipped**
  - `com.example.ui.auth.AuthValidatingCardTest`: **1 passed, 0 failed, 0 skipped**
  - `com.example.ui.auth.FirebaseAuthListenerTest`: **2 passed, 0 failed, 0 skipped**
  - `com.example.ui.auth.GoogleAuthOnlyRegressionTest`: **7 passed, 0 failed, 0 skipped**
  - `com.example.ui.profile.MemberProfileSetupScreenTest`: **2 passed, 0 failed, 0 skipped**
  - `com.example.ui.safety.CommunitySafetyGuidelinesTest`: **2 passed, 0 failed, 0 skipped**

### 4.3 Firebase Emulator JS Security Rules Suites
- **Command:** `firebase emulators:exec --only auth,firestore,storage --project demo-no-project "node --test firestore.test.js && node --test storage.test.js && node --test firestore.owner-admin.test.js"`
- **Exit Code:** `0` (`Script exited successfully (code 0)`)
- **Counts:** **60 passed, 0 failed, 0 skipped**
  - `firestore.test.js`: **32 passed, 0 failed, 0 skipped** (includes new reaction/block/nonexistent-post/field-tamper assertions for `/activities` and `/users/{uid}/activity_reactions/{postId}`)
  - `storage.test.js`: **24 passed, 0 failed, 0 skipped** *(validates `storage.rules` against the local `(default)` emulator only; cross-service named-DB lookups remain `NOT VERIFIED`)*
  - `firestore.owner-admin.test.js`: **4 passed, 0 failed, 0 skipped** (validates `firestore.owner-admin.proposed.rules`, including `/verification_requests` Owner-only read + write freeze and `/activities` emoji reactions)

### 4.4 Admin-Tools Security Helper Unit Tests
- **Command:** `node --test admin-tools/owner-security.test.js`
- **Exit Code:** `0`
- **Counts:** **4 passed, 0 failed, 0 skipped**

---

## 5. Remaining Limitations & Operational Notes

1. **Named-Database Storage Cross-Service Rules (`NOT VERIFIED`):** As detailed in Section 3, `storage.rules` cross-service lookups target `(default)` and cannot be validated against `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` locally. Safe while the Android app does not use Firebase Storage SDK.
2. **No Emulator / Play Services / Production Access in Container (`NOT VERIFIED`):** Android `androidTest` instrumented tests, live Google Sign-In OAuth consent, live `gptgamestudioph@gmail.com` Owner custom claims on `gen-lang-client-0431487205`, and live Firestore rules deployment remain `NOT VERIFIED` and unexecuted per Phase 2.7 no-deploy restrictions.
3. **Production Deployment Prerequisite:** Prior to deploying to `gen-lang-client-0431487205` (only with explicit approval), ensure `firestore.rules` (updated in this phase) is deployed to named database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089` and `admin-tools/owner-admin-sync.mjs` has provisioned `{ owner: true, admin: true }` custom claims on `gptgamestudioph@gmail.com`.
