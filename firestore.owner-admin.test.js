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

const GOOGLE_AUTH_CLAIMS = Object.freeze({
  firebase: {
    sign_in_provider: "google.com",
  },
});

function ownerDb(overrides = {}) {
  const { firebase: firebaseOverride, ...rest } = overrides;
  return testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
      owner: true,
      admin: true,
      firebase: {
        sign_in_provider: "google.com",
        ...(firebaseOverride || {}),
      },
      ...rest,
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
      ...GOOGLE_AUTH_CLAIMS,
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
  const alice = testEnv.authenticatedContext(ALICE_UID, GOOGLE_AUTH_CLAIMS).firestore();
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
  const alice = testEnv.authenticatedContext(ALICE_UID, GOOGLE_AUTH_CLAIMS).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, GOOGLE_AUTH_CLAIMS).firestore();
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

test("Proposed Rules Parity: /verification_requests owner-only read & frozen writes, plus /activities emoji reaction consistency", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  const alice = testEnv.authenticatedContext(ALICE_UID, GOOGLE_AUTH_CLAIMS).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID, GOOGLE_AUTH_CLAIMS).firestore();
  const validOwner = ownerDb();
  const past = new Date(Date.now() - 1000);
  const reqId = `ver_legacy_${ALICE_UID}`;
  const actId = "act_parity_1";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("verification_requests").doc(reqId).set({
      requestId: reqId,
      userId: ALICE_UID,
      status: "PENDING",
      createdAt: past,
    });
  });

  // Legacy verification_requests: owner-only read, all client writes frozen
  await assertFails(unauth.collection("verification_requests").doc(reqId).get());
  await assertFails(alice.collection("verification_requests").doc(reqId).get());
  await assertFails(alice.collection("verification_requests").doc("ver_new").set({ requestId: "ver_new", userId: ALICE_UID }));
  await assertFails(validOwner.collection("verification_requests").doc(reqId).update({ status: "APPROVED" }));
  await assertFails(validOwner.collection("verification_requests").doc(reqId).delete());
  await assertSucceeds(validOwner.collection("verification_requests").doc(reqId).get());
  await assertSucceeds(validOwner.collection("verification_requests").get());

  // Activities emoji reactions work cleanly without expression-limit failures
  await assertSucceeds(alice.collection("activities").doc(actId).set({
    postId: actId,
    userId: ALICE_UID,
    authorId: ALICE_UID,
    username: "AlicePH",
    activityType: "Coffee Time",
    content: "Parity check post",
    likes: [],
    commentIds: [],
    reactionCounts: {},
    totalReactions: 0,
    commentCount: 0,
    shareCount: 0,
    timestamp: past,
    createdAt: past,
    updatedAt: past,
  }));
  await assertSucceeds(bob.collection("users").doc(BOB_UID).collection("activity_reactions").doc(actId).set({
    userId: BOB_UID,
    postId: actId,
    emoji: "❤️",
    updatedAt: new Date(Date.now() - 500),
  }));
  await assertSucceeds(bob.collection("activities").doc(actId).update({
    reactionCounts: { "❤️": 1 },
    totalReactions: 1,
    updatedAt: new Date(Date.now() - 400),
  }));

  // Profile visits: visitor creates & refreshes own visit, third party cannot overwrite
  const visitId = `visit_${ALICE_UID}_${BOB_UID}`;
  const charlie = testEnv.authenticatedContext("charlie_789", GOOGLE_AUTH_CLAIMS).firestore();
  await assertSucceeds(alice.collection("profile_visits").doc(visitId).set({
    visitId,
    visitorId: ALICE_UID,
    visitedUserId: BOB_UID,
    visitorName: "AlicePH",
    visitedAt: past,
  }));
  await assertFails(charlie.collection("profile_visits").doc(visitId).set({
    visitId,
    visitorId: "charlie_789",
    visitedUserId: BOB_UID,
    visitorName: "CharlieHijack",
    visitedAt: new Date(Date.now() - 200),
  }));
});

test("Google Provider Enforcement: rejects Owner with valid claims but non-Google provider and rejects non-Google member tokens", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admin_dashboard").doc("summary").set({
      totalAuthUsers: 42,
      billingPlan: "SPARK",
    });
  });

  // Owner claims paired with non-Google / missing provider claims must fail
  for (const badProviderClaims of [
    { firebase: { sign_in_provider: "password" } },
    { firebase: { sign_in_provider: "anonymous" } },
    { firebase: { sign_in_provider: "custom" } },
    { firebase: {} },
    { firebase: "google.com" },
  ]) {
    const nonGoogleOwner = testEnv
      .authenticatedContext(OWNER_UID, {
        email: OWNER_EMAIL,
        email_verified: true,
        owner: true,
        admin: true,
        ...badProviderClaims,
      })
      .firestore();
    await assertFails(nonGoogleOwner.collection("admin_dashboard").doc("summary").get());
    await assertFails(nonGoogleOwner.collection("verification_requests").get());
  }

  const missingFirebaseOwner = testEnv
    .authenticatedContext(OWNER_UID, {
      email: OWNER_EMAIL,
      email_verified: true,
      owner: true,
      admin: true,
    })
    .firestore();
  await assertFails(missingFirebaseOwner.collection("admin_dashboard").doc("summary").get());

  // Non-Google member tokens cannot write user profiles or access owner collections
  const passwordMember = testEnv
    .authenticatedContext(ALICE_UID, { firebase: { sign_in_provider: "password" } })
    .firestore();
  await assertFails(passwordMember.collection("users").doc(ALICE_UID).get());
  await assertFails(
    passwordMember.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      age: 25,
      gender: "Female",
      country: "Philippines",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
