# SOGO CHAT AND MEET — Owner Admin Server Tooling (Spark Plan Safe)

## Why This Runs Off-Device Without Enabling Blaze
- Firebase Cloud Functions v2 (scheduled cron jobs / HTTPS triggers) require Artifact Registry and Cloud Build, which require the Firebase **Blaze** (pay-as-you-go) plan.
- SOGO CHAT AND MEET keeps the project strictly on the **Spark ($0/month)** plan and **never upgrades billing automatically**.
- Instead, `owner-admin-sync.mjs` runs with Firebase Admin SDK on your trusted workstation (or a private scheduled runner) using Application Default Credentials.

## Security Model
1. **Owner-Only Identity:** `gptgamestudioph@gmail.com` with `email_verified === true`.
2. **Trusted Custom Claims:** Client code never grants Owner/Admin privileges. Only `owner-admin-sync.mjs` sets `{ owner: true, admin: true }` via `auth.setCustomUserClaims(...)`.
3. **Rule Protection (Pre-Deployment Ready):**
   - Existing production `/firestore.rules` was **not** modified or deployed automatically.
   - Proposed rules live in `/firestore.owner-admin.proposed.rules` and are verified by `/firestore.owner-admin.test.js` against the local Firestore emulator.
   - Once you review and approve them, replace `firestore.rules` or deploy them to database `ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089`.

## Commands
Run Node unit tests (no credentials or billing needed):
```bash
node --test admin-tools/owner-security.test.js
```

Dry-run Admin SDK sync (requires `GOOGLE_APPLICATION_CREDENTIALS` pointing to a service account JSON):
```bash
npm install --no-save firebase-admin
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json node admin-tools/owner-admin-sync.mjs --dry-run
```

Grant Owner custom claims & sync Firebase Auth analytics (Spark-safe):
```bash
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json node admin-tools/owner-admin-sync.mjs
```
