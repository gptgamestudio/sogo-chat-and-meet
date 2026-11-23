import test from "node:test";
import assert from "node:assert/strict";
import {
  EXPECTED_OWNER_EMAIL,
  SPARK_QUOTAS,
  aggregateAuthUsers,
  evaluateBillingStatus,
  evaluateQuotaWarnings,
  maskEmail,
  verifyOwnerEligibility,
} from "./owner-security.mjs";

test("verifyOwnerEligibility: requires exact owner email and emailVerified = true", () => {
  assert.equal(
    verifyOwnerEligibility({
      uid: "owner_1",
      email: "gptgamestudioph@gmail.com",
      emailVerified: true,
    }).eligible,
    true
  );

  // Case-insensitive match still requires emailVerified = true
  assert.equal(
    verifyOwnerEligibility({
      uid: "owner_2",
      email: "GPTGameStudioPH@gmail.com",
      emailVerified: false,
    }).eligible,
    false
  );

  // Wrong email always denied even when verified
  const wrongEmail = verifyOwnerEligibility({
    uid: "attacker_1",
    email: "member@gmail.com",
    emailVerified: true,
  });
  assert.equal(wrongEmail.eligible, false);
  assert.match(wrongEmail.reason, /Email mismatch/);
  assert.equal(EXPECTED_OWNER_EMAIL, "gptgamestudioph@gmail.com");
});

test("aggregateAuthUsers: deduplicates duplicate accounts, computes trends, and strips secrets", () => {
  const nowMs = Date.UTC(2026, 10, 22, 8, 0, 0); // 2026-11-22 16:00 Asia/Manila
  const dayMs = 24 * 60 * 60 * 1000;

  const users = [
    {
      uid: "u1",
      email: "alice@example.com",
      displayName: "Alice Santos",
      emailVerified: true,
      passwordHash: "SECRET_HASH_MUST_NEVER_APPEAR",
      passwordSalt: "SECRET_SALT",
      metadata: { creationTime: new Date(nowMs - 1000).toISOString() },
      providerData: [{ providerId: "google.com" }],
    },
    // Duplicate UID
    {
      uid: "u1",
      email: "alice@example.com",
      displayName: "Alice Duplicate",
      emailVerified: true,
      metadata: { creationTime: new Date(nowMs - 1000).toISOString() },
    },
    // Duplicate email under second UID
    {
      uid: "u1_dup_email",
      email: "ALICE@example.com",
      displayName: "Alice Second UID",
      emailVerified: false,
      metadata: { creationTime: new Date(nowMs - 2 * dayMs).toISOString() },
    },
    {
      uid: "u2",
      email: "bob@example.com",
      displayName: "Bob Reyes",
      emailVerified: true,
      metadata: { creationTime: new Date(nowMs - 3 * dayMs).toISOString() },
      providerData: [{ providerId: "google.com" }],
    },
    {
      uid: "u3",
      email: "carla@example.com",
      displayName: "Carla Cruz",
      emailVerified: true,
      metadata: { creationTime: new Date(nowMs - 15 * dayMs).toISOString() },
      providerData: [{ providerId: "google.com" }],
    },
  ];

  const result = aggregateAuthUsers(users, nowMs, "Asia/Manila");
  assert.equal(result.totalAuthUsers, 3);
  assert.equal(result.verifiedAuthUsers, 3);
  assert.equal(result.duplicatesExcluded, 2);
  assert.equal(result.dailyRegistrations, 1);
  assert.equal(result.weeklyRegistrations, 2);
  assert.equal(result.monthlyRegistrations, 3);
  assert.equal(result.dailyBuckets.length, 14);

  const serialized = JSON.stringify(result.sanitizedHistory);
  assert.equal(serialized.includes("SECRET_HASH"), false);
  assert.equal(serialized.includes("SECRET_SALT"), false);
  assert.equal(serialized.includes("alice@example.com"), false);
  assert.equal(result.sanitizedHistory[0].maskedEmail, maskEmail("alice@example.com"));
});

test("evaluateQuotaWarnings: marks unavailable without fabrication and flags Spark thresholds", () => {
  const unavailable = evaluateQuotaWarnings({});
  assert.equal(unavailable.every((w) => w.severity === "UNAVAILABLE" && w.used === null), true);

  const thresholded = evaluateQuotaWarnings({
    readsToday: Math.round(SPARK_QUOTAS.readsPerDay * 0.92), // CRITICAL >= 90%
    writesToday: Math.round(SPARK_QUOTAS.writesPerDay * 0.8), // WARNING >= 75%
    deletesToday: 100, // OK
    storageBytes: 1024 * 1024, // OK
  });
  assert.equal(thresholded[0].severity, "CRITICAL");
  assert.equal(thresholded[1].severity, "WARNING");
  assert.equal(thresholded[2].severity, "OK");
  assert.equal(thresholded[3].severity, "OK");
});

test("evaluateBillingStatus: preserves Spark plan and blocks automatic Blaze upgrade", () => {
  const billing = evaluateBillingStatus();
  assert.equal(billing.billingPlan, "SPARK");
  assert.equal(billing.autoUpgradeEnabled, false);
  assert.ok(billing.blazeRequiredFeatures.length >= 1);
  assert.match(billing.sparkCompatibleAlternative, /Spark/);
});
