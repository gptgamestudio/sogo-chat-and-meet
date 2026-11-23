const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const OWNER_UID = "owner_uid_001";
const OWNER_EMAIL = "gptgamestudioph@gmail.com";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ownerDb(overrides = {}) {
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
      owner: true,
      admin: true,
      ...overrides,
    })
    .firestore();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.owner-admin.proposed.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Owner Admin Security: denies unauthenticated, non-owner, unverified email, and missing custom claims", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admin_dashboard").doc("summary").set({
      totalAuthUsers: 42,
      billingPlan: "SPARK",
    });
    await ctx.firestore().collection("admin_usage_daily").doc("2026-11-22").set({
      dateKey: "2026-11-22",
      registrations: 3,
    });
    await ctx.firestore().collection("admin_registration_history").doc("reg_alice").set({
      entryId: "reg_alice",
      memberLabel: "Alice",
    });
  });

  // 1. Unauthenticated denied
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("admin_dashboard").doc("summary").get());
  await assertFails(unauth.collection("admin_usage_daily").get());
  await assertFails(unauth.collection("admin_registration_history").get());

  // 2. Non-owner with spoofed custom claims denied (email mismatch)
  const attackerWithSpoofedClaims = testEnv
    .authenticatedContext(ALICE_UID, {
      email: "attacker@gmail.com",
      email_verified: true,
      owner: true,
      admin: true,
    })
    .firestore();
  await assertFails(attackerWithSpoofedClaims.collection("admin_dashboard").doc("summary").get());
  await assertFails(attackerWithSpoofedClaims.collection("admin_usage_daily").get());

  // 3. Owner email without email_verified == true denied
  await assertFails(ownerDb({ email_verified: false }).collection("admin_dashboard").doc("summary").get());

  // 4. Owner email without owner claim denied
  await assertFails(ownerDb({ owner: false }).collection("admin_dashboard").doc("summary").get());

  // 5. Owner email without admin claim denied
  await assertFails(ownerDb({ admin: false }).collection("admin_dashboard").doc("summary").get());

  // 6. Verified Owner with email_verified=true AND owner=true AND admin=true succeeds read
  const validOwner = ownerDb();
  await assertSucceeds(validOwner.collection("admin_dashboard").doc("summary").get());
  await assertSucceeds(validOwner.collection("admin_usage_daily").get());
  await assertSucceeds(validOwner.collection("admin_registration_history").get());
});

test("Owner Admin Security: prevents client-side writes to admin collections and client role escalation", async () => {
  const validOwner = ownerDb();
  // Even Owner client SDK cannot tamper directly with Admin SDK analytics docs
  await assertFails(
    validOwner.collection("admin_dashboard").doc("summary").set({
      totalAuthUsers: 9999,
      billingPlan: "SPARK",
    })
  );
  await assertFails(
    validOwner.collection("admin_usage_daily").doc("2026-11-22").set({
      dateKey: "2026-11-22",
      registrations: 999,
    })
  );
  await assertFails(
    validOwner.collection("admin_registration_history").doc("reg_fake").set({
      entryId: "reg_fake",
      memberLabel: "Fake",
    })
  );

  // Regular user cannot self-assign role='owner' or role='admin' in users collection
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      age: 24,
      gender: "Female",
      country: "Philippines",
      role: "owner",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Content Reports: allows reporter to flag chat content, blocks self-report & status tampering, restricts moderation updates to verified Owner", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const validReport = {
    reportId: "creport_1",
    reporterId: ALICE_UID,
    reportedUserId: BOB_UID,
    conversationId: "conv_alice_123_bob_456",
    messageId: "msg_1",
    messageType: "TEXT",
    messageExcerpt: "Spam link",
    reason: "Spam or scam",
    details: "Sent unsolicited promo link",
    status: "PENDING",
    createdAt: now,
  };

  // Alice can submit report for Bob's message
  await assertSucceeds(alice.collection("content_reports").doc("creport_1").set(validReport));

  // Self-report rejected
  await assertFails(
    alice.collection("content_reports").doc("creport_self").set({
      ...validReport,
      reportId: "creport_self",
      reportedUserId: ALICE_UID,
    })
  );

  // Submitting pre-approved status rejected
  await assertFails(
    alice.collection("content_reports").doc("creport_bad_status").set({
      ...validReport,
      reportId: "creport_bad_status",
      status: "REVIEWED",
    })
  );

  // Alice can read own report, Bob cannot read Alice's report
  await assertSucceeds(alice.collection("content_reports").doc("creport_1").get());
  await assertFails(bob.collection("content_reports").doc("creport_1").get());

  // Non-owner cannot moderate report status or delete report
  await assertFails(
    alice.collection("content_reports").doc("creport_1").update({ status: "REVIEWED" })
  );
  await assertFails(
    bob.collection("content_reports").doc("creport_1").update({ status: "DISMISSED" })
  );

  // Verified Owner can list content_reports and update status from PENDING -> REVIEWED
  const validOwner = ownerDb();
  await assertSucceeds(validOwner.collection("content_reports").get());
  await assertSucceeds(
    validOwner.collection("content_reports").doc("creport_1").update({ status: "REVIEWED" })
  );
  // Report cannot be deleted even by Owner (audit preservation)
  await assertFails(validOwner.collection("content_reports").doc("creport_1").delete());
});
