# SOGO CHAT & MEET — Phase 2.13B: Read-Only Owner Claims Verification Report

**Date:** 2026-11-25  
**Mode:** `STRICT READ-ONLY INSPECTION`  
**Target Firebase Project:** `gen-lang-client-0431487205`  
**Target Named Firestore Database:** `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`  
**Redacted Owner Account Reference:** `[REDACTED_OWNER_EMAIL]` (UID: `[REDACTED_OWNER_UID]`)  
**Verification Verdict:** **`NOT VERIFIED`**

> **Strict Safety & Non-Mutation Confirmation:** No custom claims were set, added, removed, or modified. No source code, rules, tests, configuration files, user records, or production data were modified. No Firebase Console settings were changed, no deployments were executed, and no passwords, service-account keys, tokens, full user records, email addresses, or UIDs are exposed in this report.

---

## 1. Required Custom Claims & Supporting Code / Rule References

Both Firestore Security Rules and the Android client application require the exact same claim pair (`owner: true` and `admin: true` as booleans) combined with Google Sign-In provider verification, verified email status, and the configured Owner email identity (`[REDACTED_OWNER_EMAIL]`):

### 1.1 Firestore Security Rules (`firestore.rules` & `firestore.owner-admin.proposed.rules`)

| Check | Exact Requirement | File & Line References |
|---|---|---|
| **Google Provider Gate (`isSignedIn()`)** | `request.auth != null`, `'firebase' in request.auth.token`, `request.auth.token.firebase is map`, `'sign_in_provider' in request.auth.token.firebase`, `request.auth.token.firebase.sign_in_provider == 'google.com'` | `firestore.rules:11-17`<br>`firestore.owner-admin.proposed.rules:11-17` |
| **Owner Identity Match** | `request.auth.token.email == [REDACTED_OWNER_EMAIL]` | `firestore.rules:25`<br>`firestore.owner-admin.proposed.rules:25` |
| **Verified Email Claim** | `request.auth.token.email_verified == true` | `firestore.rules:26`<br>`firestore.owner-admin.proposed.rules:26` |
| **Required Custom Claim 1** | `request.auth.token.owner == true` *(boolean `true`)* | `firestore.rules:27`<br>`firestore.owner-admin.proposed.rules:27` |
| **Required Custom Claim 2** | `request.auth.token.admin == true` *(boolean `true`)* | `firestore.rules:28`<br>`firestore.owner-admin.proposed.rules:28` |

**Protected Rule Paths Depending on `isVerifiedOwner()`:**
- `firestore.rules:123` (`firestore.owner-admin.proposed.rules:122`) — Owner moderation `accountStatus` updates on `/users/{userId}`
- `firestore.rules:184` (`firestore.owner-admin.proposed.rules:183`) — Gallery photo deletion under `/users/{userId}/gallery_photos/{photoId}`
- `firestore.rules:417-418, 432-433, 462-475, 498-511` (`firestore.owner-admin.proposed.rules:414-415, 429-430, 459-472, 495-508`) — `/user_reports`, `/reports`, `/content_reports`, and `/activity_reports` reads and moderation status updates
- `firestore.rules:880, 905, 1025, 1050` (`firestore.owner-admin.proposed.rules:875, 900, 1017, 1042`) — `/activity_posts` and `/activities` (plus `/comments`) moderation deletion
- `firestore.rules:1056` (`firestore.owner-admin.proposed.rules:1048`) — Frozen `/verification_requests/{requestId}` read-only inspection (`get`, `list`)
- `firestore.rules:1062, 1067, 1072, 1077-1078` (`firestore.owner-admin.proposed.rules:1054, 1059, 1064, 1069-1070`) — `/admin_dashboard`, `/admin_usage_daily`, `/admin_registration_history` (`get`, `list`) and `/admin_audit_logs` (`get`, `list`, `create`)

*(Note: `storage.rules` does not check `owner`/`admin` custom claims or `isVerifiedOwner()`.)*

### 1.2 Android Client & Admin Tooling Code

| Component | Exact Requirement | File & Line References |
|---|---|---|
| **Expected Owner Config Constant** | `OwnerDashboardAggregator.OWNER_EMAIL` (`[REDACTED_OWNER_EMAIL]`) and `EXPECTED_OWNER_EMAIL` (`[REDACTED_OWNER_EMAIL]`) | `app/src/main/java/com/example/data/repository/OwnerAdminRepository.kt:47`<br>`admin-tools/owner-security.mjs:1` |
| **Client Entry-Point Candidate Check** | `isOwnerCandidateSignedIn()` checks `user.isEmailVerified`, `GoogleAuthProvider.PROVIDER_ID` in `user.providerData`, and case-insensitive match to `[REDACTED_OWNER_EMAIL]` (gates UI visibility only; does not grant claims) | `app/src/main/java/com/example/data/repository/SogoRepository.kt:183-191`<br>`app/src/main/java/com/example/ui/dashboard/MainDashboardViewModel.kt:208, 620` |
| **Client Token & Claims Gate** | `FirebaseOwnerAdminRepository.verifyOwnerAccess(forceTokenRefresh)` refreshes ID token (`user.getIdToken(forceTokenRefresh)`), validates `GoogleAuthProvider.PROVIDER_ID`, and invokes `OwnerDashboardAggregator.evaluateOwnerClaims(...)` requiring: exact trimmed lowercase email match, `isGoogleProvider == true`, `emailVerified == true`, `claims["owner"] == true`, and `claims["admin"] == true` | `app/src/main/java/com/example/data/repository/OwnerAdminRepository.kt:53-87, 260-281`<br>`app/src/main/java/com/example/ui/admin/OwnerAdminViewModel.kt:65-78` |
| **Trusted Admin SDK Claims Verification / Assignment Logic** | `verifyOwnerEligibility(...)` checks normalized email match, `emailVerified === true`, and `disabled !== true`; `owner-admin-sync.mjs` checks `currentClaims.owner === true && currentClaims.admin === true` (and only mutates claims when run explicitly without `--dry-run` on a trusted server/workstation) | `admin-tools/owner-security.mjs:15-40`<br>`admin-tools/owner-admin-sync.mjs:76-97` |

**Required Combination Summary:**
All of the following must be simultaneously true on the production Owner's Firebase Authentication record and refreshed ID token:
1. Account email equals configured `[REDACTED_OWNER_EMAIL]`
2. `email_verified == true`
3. Sign-in provider includes `google.com` (`request.auth.token.firebase.sign_in_provider == 'google.com'` on the active session token)
4. Custom claims contain `{ "owner": true, "admin": true }` (boolean `true` for both keys)

---

## 2. Verification Method & Access Check

1. **Owner Account Identification:**
   - The intended owner account identifier was resolved strictly from the repository's existing configuration (`OwnerAdminRepository.kt:47`, `firestore.rules:25`, `admin-tools/owner-security.mjs:1`, `admin-tools/README.md:9`) and is represented throughout this report solely as `[REDACTED_OWNER_EMAIL]`.
2. **Read-Only Environment & Credential Check:**
   - Checked whether Firebase Admin SDK access or pre-authorized credentials already existed in the execution environment (without reading or printing credential contents):
     - `GOOGLE_APPLICATION_CREDENTIALS`: **not set**
     - `FIREBASE_TOKEN`: **not set**
     - `/root/.config/gcloud`: **not present**
     - `/root/.config/firebase`: **not present**
     - `firebase-admin` npm package: **not installed**
     - `.firebaserc`: **not present**
3. **Live Verification Attempt Outcome:**
   - Because zero Firebase Admin SDK credentials or authenticated CLI sessions exist in this environment, no live Firebase Authentication lookup could be executed for `[REDACTED_OWNER_EMAIL]`.

---

## 3. Claims Comparison & Verdict

| Criterion | Required State | Observed Production State | Status |
|---|---|---|---|
| **Email Identity Match** | `[REDACTED_OWNER_EMAIL]` | Cannot query live Auth user | **`NOT VERIFIED`** |
| **Email Verified** | `email_verified == true` | Cannot query live Auth user | **`NOT VERIFIED`** |
| **Google Auth Provider** | `google.com` in `providerData` / `firebase.sign_in_provider` | Cannot query live Auth user | **`NOT VERIFIED`** |
| **Custom Claim `owner`** | `true` (`boolean`) | Cannot query live Auth user | **`NOT VERIFIED`** |
| **Custom Claim `admin`** | `true` (`boolean`) | Cannot query live Auth user | **`NOT VERIFIED`** |

**Overall Verdict:** **`NOT VERIFIED`**

---

## 4. Access Limitations & Manual Owner Verification Steps

Verification could not be completed automatically because this execution container holds no GCP/Firebase service-account credentials, no Firebase CLI OAuth session, and no `firebase-admin` installation. **Credentials were not requested and must not be pasted into chat.**

To verify the production account's claims safely in read-only mode on your trusted local workstation:

1. **Read-Only Dry-Run Check Using Existing Repo Tooling (`--dry-run` makes zero claim or Firestore writes):**
   ```bash
   npm install --no-save firebase-admin
   GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json \
     node admin-tools/owner-admin-sync.mjs --dry-run
   ```
   - Look strictly for whether the output reports `"Verified Owner custom claims already active on ..."` (`PASS` candidate) or `"[dry-run] Would grant { owner: true, admin: true } ..."` (missing/incomplete claims -> `FAIL` until granted).
   - Also check `ownerUser.providerData` in Firebase Console (**Authentication → Users**) to confirm the account is linked to **Google** (`google.com`), has `emailVerified == true`, and is enabled.
2. **If Claims Are Missing (`owner !== true` or `admin !== true`) and You Approve Remediation Later:**
   - Explicitly run `admin-tools/owner-admin-sync.mjs --grant-claims-only` (or full sync) on your trusted workstation.
   - Sign out and sign back in with Google in the Android app so Firebase Authentication mints a fresh ID token carrying the updated `{ owner: true, admin: true }` custom claims.

---

## 5. Attestation

- **Custom Claims:** Unchanged (zero claims read remotely, added, removed, or modified).
- **Files / Code / Rules / Config:** Unchanged (only this report `docs/PHASE_2_13B_OWNER_CLAIMS_READ_ONLY_REPORT.md` was created).
- **Firebase Console / Users / Production Data / Deployments:** Unchanged and untouched.
