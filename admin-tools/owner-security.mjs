export const EXPECTED_OWNER_EMAIL = "gptgamestudioph@gmail.com";

export const SPARK_QUOTAS = Object.freeze({
  readsPerDay: 50_000,
  writesPerDay: 20_000,
  deletesPerDay: 20_000,
  storageBytes: 1_073_741_824, // 1 GiB
});

/**
 * Verifies whether a Firebase Auth user record is eligible to receive server-side
 * Owner custom claims ({ owner: true, admin: true }).
 * Never trusts client-provided roles; always validates email + emailVerified.
 */
export function verifyOwnerEligibility(userRecord, expectedEmail = EXPECTED_OWNER_EMAIL) {
  if (!userRecord || typeof userRecord !== "object") {
    return { eligible: false, reason: "Missing Firebase Auth user record." };
  }
  const normalizedExpected = String(expectedEmail || "").trim().toLowerCase();
  const normalizedUserEmail = String(userRecord.email || "").trim().toLowerCase();
  if (!normalizedUserEmail || normalizedUserEmail !== normalizedExpected) {
    return {
      eligible: false,
      reason: `Email mismatch: expected ${normalizedExpected}, received ${normalizedUserEmail || "<none>"}.`,
    };
  }
  if (userRecord.emailVerified !== true) {
    return {
      eligible: false,
      reason: "Firebase email_verified must be true before granting Owner claims.",
    };
  }
  if (userRecord.disabled === true) {
    return {
      eligible: false,
      reason: "Owner Firebase Auth account is disabled.",
    };
  }
  return { eligible: true, reason: null };
}

/**
 * Masks an email address so private member emails are never exposed in logs or admin history.
 */
export function maskEmail(email) {
  const raw = String(email || "").trim();
  const atIndex = raw.indexOf("@");
  if (atIndex <= 0) return "Google Account";
  const local = raw.slice(0, atIndex);
  const domain = raw.slice(atIndex + 1);
  const prefix = local.length <= 2 ? local[0] + "*" : local.slice(0, 2) + "***";
  return `${prefix}@${domain}`;
}

/**
 * Formats an epoch millis timestamp into YYYY-MM-DD in Asia/Manila (or given offset/UTC).
 */
export function toDateKey(epochMs, timeZone = "Asia/Manila") {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(new Date(epochMs));
}

/**
 * Aggregates Firebase Auth user records with strict deduplication (by uid and canonical email)
 * and strips passwords/sensitive hashes/tokens from registration history.
 */
export function aggregateAuthUsers(userRecords, nowMs = Date.now(), timeZone = "Asia/Manila") {
  const seenUids = new Set();
  const seenEmails = new Set();
  const uniqueUsers = [];
  let duplicatesExcluded = 0;

  for (const user of userRecords || []) {
    if (!user || !user.uid) continue;
    const uid = String(user.uid).trim();
    const emailKey = user.email ? String(user.email).trim().toLowerCase() : null;

    if (seenUids.has(uid) || (emailKey && seenEmails.has(emailKey))) {
      duplicatesExcluded += 1;
      continue;
    }
    seenUids.add(uid);
    if (emailKey) seenEmails.add(emailKey);
    uniqueUsers.push(user);
  }

  const todayKey = toDateKey(nowMs, timeZone);
  const dayMs = 24 * 60 * 60 * 1000;
  const recent7Keys = new Set();
  const recent30Keys = new Set();
  const dailyBucketCounts = new Map();

  for (let i = 0; i < 30; i += 1) {
    const key = toDateKey(nowMs - i * dayMs, timeZone);
    recent30Keys.add(key);
    if (i < 7) recent7Keys.add(key);
    if (i < 14) dailyBucketCounts.set(key, 0);
  }

  let verifiedAuthUsers = 0;
  let dailyRegistrations = 0;
  let weeklyRegistrations = 0;
  let monthlyRegistrations = 0;
  const sanitizedHistory = [];

  const sortedByCreation = [...uniqueUsers].sort((a, b) => {
    const tA = Date.parse(a?.metadata?.creationTime || "") || 0;
    const tB = Date.parse(b?.metadata?.creationTime || "") || 0;
    return tB - tA;
  });

  for (const user of sortedByCreation) {
    if (user.emailVerified === true) verifiedAuthUsers += 1;
    const createdMs = Date.parse(user?.metadata?.creationTime || "");
    const hasValidCreated = Number.isFinite(createdMs) && createdMs > 0;
    if (hasValidCreated) {
      const key = toDateKey(createdMs, timeZone);
      if (key === todayKey) dailyRegistrations += 1;
      if (recent7Keys.has(key)) weeklyRegistrations += 1;
      if (recent30Keys.has(key)) monthlyRegistrations += 1;
      if (dailyBucketCounts.has(key)) {
        dailyBucketCounts.set(key, dailyBucketCounts.get(key) + 1);
      }
    }

    const uid = String(user.uid);
    const providerId =
      Array.isArray(user.providerData) && user.providerData[0]?.providerId
        ? String(user.providerData[0].providerId)
        : "google.com";

    // Strictly omit passwordHash, passwordSalt, phoneNumber, raw tokens, and full email.
    sanitizedHistory.push({
      entryId: `reg_${uid}`,
      memberLabel: String(user.displayName || "Member").trim().slice(0, 24) || "Member",
      maskedEmail: maskEmail(user.email),
      providerId,
      emailVerified: user.emailVerified === true,
      registeredAtMs: hasValidCreated ? createdMs : null,
    });
  }

  const dailyBuckets = Array.from(dailyBucketCounts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([dateKey, registrations]) => ({ dateKey, registrations }));

  return {
    totalAuthUsers: uniqueUsers.length,
    verifiedAuthUsers,
    duplicatesExcluded,
    dailyRegistrations,
    weeklyRegistrations,
    monthlyRegistrations,
    dailyBuckets,
    sanitizedHistory,
  };
}

/**
 * Evaluates Spark free-tier quota warnings strictly when reliable metrics exist;
 * returns UNAVAILABLE when numbers are missing rather than fabricating estimates.
 */
export function evaluateQuotaWarnings(usageMetrics) {
  const checks = [
    { key: "readsToday", label: "Document Reads", limit: SPARK_QUOTAS.readsPerDay },
    { key: "writesToday", label: "Document Writes", limit: SPARK_QUOTAS.writesPerDay },
    { key: "deletesToday", label: "Document Deletes", limit: SPARK_QUOTAS.deletesPerDay },
    { key: "storageBytes", label: "Firestore Storage", limit: SPARK_QUOTAS.storageBytes },
  ];

  return checks.map(({ key, label, limit }) => {
    const val = usageMetrics?.[key];
    if (typeof val !== "number" || !Number.isFinite(val) || val < 0) {
      return {
        resourceName: label,
        used: null,
        limit,
        severity: "UNAVAILABLE",
        message: `${label} live Cloud Monitoring metric unavailable.`,
      };
    }
    const pct = Math.round((val / limit) * 100);
    if (pct >= 90) {
      return {
        resourceName: label,
        used: val,
        limit,
        severity: "CRITICAL",
        message: `${label} at ${pct}% of Spark limit.`,
      };
    }
    if (pct >= 75) {
      return {
        resourceName: label,
        used: val,
        limit,
        severity: "WARNING",
        message: `${label} approaching Spark limit (${pct}%).`,
      };
    }
    return {
      resourceName: label,
      used: val,
      limit,
      severity: "OK",
      message: `${label} within safe Spark quota (${pct}%).`,
    };
  });
}

/**
 * Billing safety inspector: keeps Spark plan active without automatic Blaze upgrade.
 */
export function evaluateBillingStatus() {
  return {
    billingPlan: "SPARK",
    autoUpgradeEnabled: false,
    blazeRequiredFeatures: [
      "Cloud Functions v2 (scheduled cron triggers / Artifact Registry / Cloud Build)",
      "In-project automated server pushes without an external runner",
    ],
    sparkCompatibleAlternative:
      "Run admin-tools/owner-admin-sync.mjs from a trusted admin workstation or GitHub Actions secret workflow to sync Auth totals and Cloud Monitoring snapshots on Spark ($0/month).",
  };
}
