const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
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

// 1. Unauthenticated tests
test("Unauthenticated: cannot read or write users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc("user1").set({
    userId: "user1",
    displayName: "Test",
    age: 25,
    gender: "Female",
    country: "Philippines",
    createdAt: new Date(),
    updatedAt: new Date(),
  }));
});

// 2. User Profile tests
test("User: can create and update own profile with valid data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    displayName: "Alice Santos",
    age: 26,
    gender: "Female",
    country: "Philippines",
    city: "Manila",
    aboutMe: "Loves travel, coffee and deep talks.",
    interests: ["Travel", "Coffee", "Music"],
    isOnline: true,
    lastActive: now,
    createdAt: now,
    updatedAt: now,
  }));

  // Update own profile
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).update({
    aboutMe: "Updated bio!",
    updatedAt: new Date(),
  }));
});

test("User: cannot create profile under age 18", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    displayName: "Underage",
    age: 17,
    gender: "Female",
    country: "Philippines",
    createdAt: now,
    updatedAt: now,
  }));
});

test("User: cannot escalate privileges or set role to admin", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    displayName: "Alice Admin",
    age: 25,
    gender: "Female",
    country: "Philippines",
    role: "admin", // Client cannot grant admin role
    createdAt: now,
    updatedAt: now,
  }));
});

test("User: cannot modify another user's profile", async () => {
  // Seed Alice's profile
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const now = new Date();
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      age: 24,
      gender: "Female",
      country: "Philippines",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({
    displayName: "Hacked by Bob",
    updatedAt: new Date(),
  }));
});

// 3. User Presence Tests
test("User Presence: owner can update presence, others cannot modify", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(aliceDb.collection("user_presence").doc(ALICE_UID).set({
    userId: ALICE_UID,
    isOnline: true,
    lastSeen: now,
    updatedAt: now,
  }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Bob can view Alice's presence
  await assertSucceeds(bobDb.collection("user_presence").doc(ALICE_UID).get());
  // Bob cannot modify Alice's presence
  await assertFails(bobDb.collection("user_presence").doc(ALICE_UID).update({
    isOnline: false,
    updatedAt: new Date(),
  }));
});

// 4. Conversations & Messages tests
test("Conversation: participants can access their private conversation", async () => {
  const now = new Date();
  const convId = "conv_alice_bob";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(aliceDb.collection("conversations").doc(convId).set({
    conversationId: convId,
    participantUids: [ALICE_UID, BOB_UID],
    lastMessage: "Hello Bob!",
    lastMessageSenderId: ALICE_UID,
    lastMessageTimestamp: now,
    createdAt: now,
    updatedAt: now,
  }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("conversations").doc(convId).get());

  // Third party Charlie cannot read it
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
});

test("Conversation: distinct participants enforcement ([Alice, Bob] succeeds, [Alice, Alice], 1 participant, 3 participants fail)", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // 1. [Alice, Bob] must succeed
  const validConvId = "conv_distinct_valid";
  await assertSucceeds(aliceDb.collection("conversations").doc(validConvId).set({
    conversationId: validConvId,
    participantUids: [ALICE_UID, BOB_UID],
    lastMessage: "Hello!",
    lastMessageSenderId: ALICE_UID,
    lastMessageTimestamp: now,
    createdAt: now,
    updatedAt: now,
  }));

  // 2. [Alice, Alice] (duplicate participant) must fail
  const duplicateConvId = "conv_duplicate_alice";
  await assertFails(aliceDb.collection("conversations").doc(duplicateConvId).set({
    conversationId: duplicateConvId,
    participantUids: [ALICE_UID, ALICE_UID],
    lastMessage: "Talking to myself",
    lastMessageSenderId: ALICE_UID,
    lastMessageTimestamp: now,
    createdAt: now,
    updatedAt: now,
  }));

  // 3. List with one participant must fail
  const singleConvId = "conv_single_participant";
  await assertFails(aliceDb.collection("conversations").doc(singleConvId).set({
    conversationId: singleConvId,
    participantUids: [ALICE_UID],
    lastMessage: "Solo",
    lastMessageSenderId: ALICE_UID,
    lastMessageTimestamp: now,
    createdAt: now,
    updatedAt: now,
  }));

  // 4. List with three participants must fail
  const trioConvId = "conv_three_participants";
  await assertFails(aliceDb.collection("conversations").doc(trioConvId).set({
    conversationId: trioConvId,
    participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
    lastMessage: "Group chat attempt",
    lastMessageSenderId: ALICE_UID,
    lastMessageTimestamp: now,
    createdAt: now,
    updatedAt: now,
  }));
});

test("Messages: non-participant cannot read or send messages", async () => {
  const now = new Date();
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg1").set({
    messageId: "msg1",
    conversationId: convId,
    senderId: ALICE_UID,
    receiverId: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    content: "Hi Bob!",
    type: "TEXT",
    isRead: false,
    createdAt: now,
  }));

  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg1").get());
  await assertFails(charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg2").set({
    messageId: "msg2",
    conversationId: convId,
    senderId: CHARLIE_UID,
    receiverId: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    content: "Intruder message",
    type: "TEXT",
    createdAt: now,
  }));
});

test("Messages: forged receiverId must fail", async () => {
  const now = new Date();
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Alice tries to send message with Charlie as receiver in Alice-Bob conversation
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_forged_rcv").set({
    messageId: "msg_forged_rcv",
    conversationId: convId,
    senderId: ALICE_UID,
    receiverId: CHARLIE_UID, // Forged receiver
    participantUids: [ALICE_UID, BOB_UID],
    content: "Forged recipient test",
    type: "TEXT",
    isRead: false,
    createdAt: now,
  }));

  // Alice tries to set herself as receiver (receiverId == senderId)
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_self_rcv").set({
    messageId: "msg_self_rcv",
    conversationId: convId,
    senderId: ALICE_UID,
    receiverId: ALICE_UID, // Invalid receiverId
    participantUids: [ALICE_UID, BOB_UID],
    content: "Self recipient test",
    type: "TEXT",
    isRead: false,
    createdAt: now,
  }));
});

test("Messages: mismatched participantUids must fail", async () => {
  const now = new Date();
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Alice tries to supply participantUids that don't match the parent conversation
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_mismatched_parts").set({
    messageId: "msg_mismatched_parts",
    conversationId: convId,
    senderId: ALICE_UID,
    receiverId: BOB_UID,
    participantUids: [ALICE_UID, CHARLIE_UID], // Does not match parent conversation [ALICE_UID, BOB_UID]
    content: "Mismatched participants test",
    type: "TEXT",
    isRead: false,
    createdAt: now,
  }));
});

test("Messages: read receipt authorization - only receiver can mark as read, sender and wrong participant must fail", async () => {
  const now = new Date();
  const convId = "conv_alice_bob";
  const msgId = "msg_read_receipt_test";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    });
    await context.firestore().collection("conversations").doc(convId).collection("messages").doc(msgId).set({
      messageId: msgId,
      conversationId: convId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      content: "Unread message",
      type: "TEXT",
      isRead: false,
      createdAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // 1. Sender (Alice) trying to mark her own message as read must FAIL
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    isRead: true,
  }));

  // 2. Third-party (Charlie) trying to update read receipt must FAIL
  await assertFails(charlieDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    isRead: true,
  }));

  // 3. Actual receiver (Bob) marking as read must SUCCEED
  await assertSucceeds(bobDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    isRead: true,
  }));

  // 4. Once read, toggling back to false must FAIL
  await assertFails(bobDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    isRead: false,
  }));
});

test("Messages: messaging must fail when either participant has blocked the other", async () => {
  const now = new Date();
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    });
    // Bob blocks Alice
    const blockId = `block_${BOB_UID}_${ALICE_UID}`;
    await context.firestore().collection("user_blocks").doc(blockId).set({
      blockId,
      blockerId: BOB_UID,
      blockedId: ALICE_UID,
      createdAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice cannot send message to Bob because Bob blocked Alice
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_blocked_alice").set({
    messageId: "msg_blocked_alice",
    conversationId: convId,
    senderId: ALICE_UID,
    receiverId: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    content: "Can you hear me?",
    type: "TEXT",
    isRead: false,
    createdAt: now,
  }));

  // Bob cannot send message to Alice while the block is active
  await assertFails(bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_blocked_bob").set({
    messageId: "msg_blocked_bob",
    conversationId: convId,
    senderId: BOB_UID,
    receiverId: ALICE_UID,
    participantUids: [ALICE_UID, BOB_UID],
    content: "Attempting to message blocked user",
    type: "TEXT",
    isRead: false,
    createdAt: now,
  }));
});

// 5. Blocks, Reports & Notifications
test("Block: user can block another member and list own blocks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(aliceDb.collection("user_blocks").doc("block1").set({
    blockId: "block1",
    blockerId: ALICE_UID,
    blockedId: BOB_UID,
    createdAt: now,
  }));

  // Alice can list her blocks
  await assertSucceeds(aliceDb.collection("user_blocks").where("blockerId", "==", ALICE_UID).get());

  // Charlie (unrelated third party) cannot read the block record
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("user_blocks").doc("block1").get());
});

test("Reports: user can submit a report, but cannot read other users' reports", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(aliceDb.collection("user_reports").doc("rep1").set({
    reportId: "rep1",
    reporterId: ALICE_UID,
    reportedId: BOB_UID,
    reason: "Inappropriate behavior",
    details: "Sent offensive text in chat",
    createdAt: now,
  }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Bob cannot read the report filed against him
  await assertFails(bobDb.collection("user_reports").doc("rep1").get());
});

test("Notifications: user can only read and manage their own notifications", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(aliceDb.collection("notifications").doc("notif1").set({
    notificationId: "notif1",
    userId: ALICE_UID,
    title: "New Match",
    body: "You have a new match!",
    type: "CHAT_MESSAGE",
    isRead: false,
    createdAt: now,
  }));

  // Bob cannot read Alice's notification
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("notifications").doc("notif1").get());

  // Alice can mark her notification as read
  await assertSucceeds(aliceDb.collection("notifications").doc("notif1").update({
    isRead: true,
  }));
});

// 6. Security Hardening Regression Tests
test("Conversation: blocked users cannot create conversation or update conversation summary", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const blockId = `block_${BOB_UID}_${ALICE_UID}`;
    await context.firestore().collection("user_blocks").doc(blockId).set({
      blockId,
      blockerId: BOB_UID,
      blockedId: ALICE_UID,
      createdAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const convId = "conv_blocked_creation";
  await assertFails(aliceDb.collection("conversations").doc(convId).set({
    conversationId: convId,
    participantUids: [ALICE_UID, BOB_UID],
    lastMessage: "Trying to chat despite block",
    lastMessageSenderId: ALICE_UID,
    lastMessageTimestamp: now,
    createdAt: now,
    updatedAt: now,
  }));

  // Existing conversation summary cannot be updated while a block exists
  const existingConvId = "conv_existing_before_block";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc(existingConvId).set({
      conversationId: existingConvId,
      participantUids: [ALICE_UID, BOB_UID],
      lastMessage: "Old message",
      lastMessageSenderId: ALICE_UID,
      lastMessageTimestamp: now,
      createdAt: now,
      updatedAt: now,
    });
  });

  await assertFails(aliceDb.collection("conversations").doc(existingConvId).update({
    lastMessage: "Bypassed update",
    lastMessageSenderId: ALICE_UID,
    lastMessageTimestamp: new Date(),
    updatedAt: new Date(),
  }));
});

test("Conversation: cannot spoof lastMessageSenderId or delete shared conversation", async () => {
  const now = new Date();
  const convId = "conv_alice_bob_integrity";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Spoofing lastMessageSenderId as BOB_UID on create must fail
  await assertFails(aliceDb.collection("conversations").doc(convId).set({
    conversationId: convId,
    participantUids: [ALICE_UID, BOB_UID],
    lastMessage: "Spoofed message",
    lastMessageSenderId: BOB_UID,
    lastMessageTimestamp: now,
    createdAt: now,
    updatedAt: now,
  }));

  // Valid create succeeds
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).set({
    conversationId: convId,
    participantUids: [ALICE_UID, BOB_UID],
    lastMessage: "Hello",
    lastMessageSenderId: ALICE_UID,
    lastMessageTimestamp: now,
    createdAt: now,
    updatedAt: now,
  }));

  // Spoofing lastMessageSenderId on update must fail
  await assertFails(aliceDb.collection("conversations").doc(convId).update({
    lastMessage: "Forged sender",
    lastMessageSenderId: BOB_UID,
    lastMessageTimestamp: new Date(),
    updatedAt: new Date(),
  }));

  // Unilateral conversation deletion must fail
  await assertFails(aliceDb.collection("conversations").doc(convId).delete());
});

test("Messages: sender cannot create message pre-marked as isRead=true", async () => {
  const now = new Date();
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_preread").set({
    messageId: "msg_preread",
    conversationId: convId,
    senderId: ALICE_UID,
    receiverId: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    content: "Spoofed read status",
    type: "TEXT",
    isRead: true,
    createdAt: now,
  }));
});

test("Block: blocker can re-block (update), blocked user cannot delete block", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const blockId = `block_${ALICE_UID}_${BOB_UID}`;
  const now = new Date();

  await assertSucceeds(aliceDb.collection("user_blocks").doc(blockId).set({
    blockId,
    blockerId: ALICE_UID,
    blockedId: BOB_UID,
    createdAt: now,
  }));

  // Re-blocking updates createdAt validly
  await assertSucceeds(aliceDb.collection("user_blocks").doc(blockId).set({
    blockId,
    blockerId: ALICE_UID,
    blockedId: BOB_UID,
    createdAt: new Date(),
  }));

  // Blocked user Bob can read the block affecting him, but cannot delete it
  await assertSucceeds(bobDb.collection("user_blocks").doc(blockId).get());
  await assertFails(bobDb.collection("user_blocks").doc(blockId).delete());
});

test("Reports: self-reporting, status tampering, update, and delete are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  // Self-reporting denied
  await assertFails(aliceDb.collection("user_reports").doc("rep_self").set({
    reportId: "rep_self",
    reporterId: ALICE_UID,
    reportedId: ALICE_UID,
    reason: "Self report test",
    status: "PENDING",
    createdAt: now,
  }));

  // Client cannot forge REVIEWED or DISMISSED status on creation
  await assertFails(aliceDb.collection("user_reports").doc("rep_tamper").set({
    reportId: "rep_tamper",
    reporterId: ALICE_UID,
    reportedId: BOB_UID,
    reason: "Spam",
    status: "REVIEWED",
    createdAt: now,
  }));

  // Valid PENDING report succeeds
  await assertSucceeds(aliceDb.collection("user_reports").doc("rep_valid").set({
    reportId: "rep_valid",
    reporterId: ALICE_UID,
    reportedId: BOB_UID,
    reason: "Harassment",
    status: "PENDING",
    createdAt: now,
  }));

  // Reporter cannot update or delete an immutable report
  await assertFails(aliceDb.collection("user_reports").doc("rep_valid").update({
    reason: "Changed reason",
  }));
  await assertFails(aliceDb.collection("user_reports").doc("rep_valid").delete());
});

test("Notifications: cannot spoof for another user, pre-mark isRead, or alter body on update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  // Cannot create notification targeting Bob
  await assertFails(aliceDb.collection("notifications").doc("notif_spoof").set({
    notificationId: "notif_spoof",
    userId: BOB_UID,
    title: "Fake System Alert",
    body: "You won a prize",
    type: "SYSTEM",
    isRead: false,
    createdAt: now,
  }));

  // Cannot create pre-read notification
  await assertFails(aliceDb.collection("notifications").doc("notif_preread").set({
    notificationId: "notif_preread",
    userId: ALICE_UID,
    title: "Test",
    body: "Test body",
    type: "SYSTEM",
    isRead: true,
    createdAt: now,
  }));

  // Valid create, then disallowed body tamper on update
  await assertSucceeds(aliceDb.collection("notifications").doc("notif_own").set({
    notificationId: "notif_own",
    userId: ALICE_UID,
    title: "Welcome",
    body: "Welcome to SOGO",
    type: "SYSTEM",
    isRead: false,
    createdAt: now,
  }));

  await assertFails(aliceDb.collection("notifications").doc("notif_own").update({
    body: "Tampered text",
    isRead: true,
  }));
});

