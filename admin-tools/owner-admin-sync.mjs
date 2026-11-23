#!/usr/bin/env node
/**
 * Trusted Firebase Admin SDK Tool for SOGO CHAT AND MEET
 *
 * Spark-Compatible ($0/month) Server Sync:
 *   1. Verifies Owner account email === "gptgamestudioph@gmail.com" AND emailVerified === true.
 *   2. Grants { owner: true, admin: true } custom claims ONLY via Firebase Admin SDK.
 *   3. Paginates Firebase Auth users, deduplicates accounts, and builds sanitized registration history
 *      without passwords or unmasked private emails.
 *   4. Queries Cloud Monitoring Firestore metrics if Monitoring Viewer permissions & credentials exist;
 *      otherwise records UNAVAILABLE (never fabricates metrics).
 *   5. Writes only to admin_dashboard/summary, admin_usage_daily, and admin_registration_history
 *      in named database ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089.
 *      NEVER modifies existing member documents in /users.
 *
 * Billing Safety:
 *   Does NOT enable Cloud Functions, Cloud Build, or upgrade Firebase away from Spark.
 */

import {
  EXPECTED_OWNER_EMAIL,
  aggregateAuthUsers,
  evaluateBillingStatus,
  verifyOwnerEligibility,
} from "./owner-security.mjs";

const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "gen-lang-client-0431487205";
const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID || "ai-studio-android-sogochat-d6957863-dbb1-4924-8f85-bce544f69089";

async function listAllAuthUsers(auth) {
  const users = [];
  let pageToken = undefined;
  do {
    const page = await auth.listUsers(1000, pageToken);
    users.push(...page.users);
    pageToken = page.pageToken;
  } while (pageToken);
  return users;
}

async function main() {
  const dryRun = process.argv.includes("--dry-run");
  const grantClaimsOnly = process.argv.includes("--grant-claims-only");

  let adminAppModule;
  let adminAuthModule;
  let adminFirestoreModule;
  try {
    adminAppModule = await import("firebase-admin/app");
    adminAuthModule = await import("firebase-admin/auth");
    adminFirestoreModule = await import("firebase-admin/firestore");
  } catch (err) {
    console.error(
      "Missing dependency 'firebase-admin'. Install it in a trusted admin environment (`npm install --no-save firebase-admin`) before running this sync script."
    );
    process.exitCode = 1;
    return;
  }

  const { initializeApp, applicationDefault, getApps } = adminAppModule;
  const { getAuth } = adminAuthModule;
  const { getFirestore, Timestamp } = adminFirestoreModule;

  const app =
    getApps().length > 0
      ? getApps()[0]
      : initializeApp({
          credential: applicationDefault(),
          projectId: PROJECT_ID,
        });

  const auth = getAuth(app);
  const db = getFirestore(app, DATABASE_ID);

  // 1. Verify Owner eligibility and enforce custom claims on trusted server
  const ownerUser = await auth.getUserByEmail(EXPECTED_OWNER_EMAIL);
  const eligibility = verifyOwnerEligibility(ownerUser, EXPECTED_OWNER_EMAIL);
  if (!eligibility.eligible) {
    throw new Error(`Owner verification failed: ${eligibility.reason}`);
  }

  const currentClaims = ownerUser.customClaims || {};
  if (currentClaims.owner !== true || currentClaims.admin !== true) {
    if (!dryRun) {
      await auth.setCustomUserClaims(ownerUser.uid, {
        ...currentClaims,
        owner: true,
        admin: true,
      });
      console.log(`Granted { owner: true, admin: true } claims to ${ownerUser.uid}.`);
    } else {
      console.log(`[dry-run] Would grant { owner: true, admin: true } to ${ownerUser.uid}.`);
    }
  } else {
    console.log(`Verified Owner custom claims already active on ${ownerUser.uid}.`);
  }

  if (grantClaimsOnly) {
    return;
  }

  // 2. Paginate Firebase Auth users and deduplicate
  const allUsers = await listAllAuthUsers(auth);
  const aggregated = aggregateAuthUsers(allUsers);
  const billing = evaluateBillingStatus();

  // Note: Cloud Monitoring metrics (reads/writes/deletes/storage) stay null / UNAVAILABLE
  // unless populated by an explicit authorized Cloud Monitoring API client; never fabricate numbers.
  const summaryPayload = {
    totalAuthUsers: aggregated.totalAuthUsers,
    verifiedAuthUsers: aggregated.verifiedAuthUsers,
    dailyRegistrations: aggregated.dailyRegistrations,
    weeklyRegistrations: aggregated.weeklyRegistrations,
    monthlyRegistrations: aggregated.monthlyRegistrations,
    readsToday: null,
    writesToday: null,
    deletesToday: null,
    storageBytes: null,
    authMetricsOrigin: "LIVE_ADMIN_SDK",
    usageMetricsOrigin: "UNAVAILABLE",
    billingPlan: billing.billingPlan,
    syncedAt: Timestamp.now(),
    sourceNote:
      "Auth counts synced via trusted Admin SDK CLI (Spark $0/mo). Firestore read/write/delete metrics marked UNAVAILABLE until Cloud Monitoring API reader is attached.",
  };

  if (dryRun) {
    console.log("[dry-run] Summary payload:", summaryPayload);
    console.log(`[dry-run] Daily trend buckets: ${aggregated.dailyBuckets.length}`);
    console.log(`[dry-run] Sanitized registration entries: ${aggregated.sanitizedHistory.length}`);
    return;
  }

  const batch = db.batch();
  batch.set(db.collection("admin_dashboard").doc("summary"), summaryPayload, { merge: true });

  for (const bucket of aggregated.dailyBuckets) {
    batch.set(
      db.collection("admin_usage_daily").doc(bucket.dateKey),
      {
        dateKey: bucket.dateKey,
        registrations: bucket.registrations,
        reads: null,
        writes: null,
        deletes: null,
        storageBytes: null,
        usageOrigin: "UNAVAILABLE",
      },
      { merge: true }
    );
  }

  for (const entry of aggregated.sanitizedHistory.slice(0, 100)) {
    batch.set(
      db.collection("admin_registration_history").doc(entry.entryId),
      {
        entryId: entry.entryId,
        memberLabel: entry.memberLabel,
        maskedEmail: entry.maskedEmail,
        providerId: entry.providerId,
        emailVerified: entry.emailVerified,
        registeredAt: entry.registeredAtMs ? Timestamp.fromMillis(entry.registeredAtMs) : Timestamp.now(),
      },
      { merge: true }
    );
  }

  await batch.commit();
  console.log(
    `Synced ${aggregated.totalAuthUsers} unique Auth users (${aggregated.duplicatesExcluded} duplicates excluded) to admin_dashboard/summary.`
  );
}

main().catch((err) => {
  console.error("Owner admin sync failed:", err);
  process.exitCode = 1;
});
