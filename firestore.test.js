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

test("Messages: reading, listing, updating, and sending must fail when either participant has blocked the other, and restore after unblock", async () => {
  const now = new Date();
  const convId = "conv_alice_bob";
  const blockId = `block_${BOB_UID}_${ALICE_UID}`;
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("conversations").doc(convId).collection("messages").doc("msg_existing").set({
      messageId: "msg_existing",
      conversationId: convId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      content: "Prior message before block",
      type: "TEXT",
      isRead: false,
      createdAt: now,
    });
    // Bob blocks Alice
    await db.collection("user_blocks").doc(blockId).set({
      blockId,
      blockerId: BOB_UID,
      blockedId: ALICE_UID,
      createdAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Neither participant can list or read messages while blocked
  await assertFails(
    aliceDb.collection("conversations").doc(convId).collection("messages")
      .where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("conversations").doc(convId).collection("messages")
      .where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertFails(bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_existing").get());

  // Receiver Bob cannot mark blocked sender's message as read while blocked
  await assertFails(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_existing").update({
      isRead: true,
    })
  );

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

  // Bob unblocks Alice -> messaging and listing resume cleanly
  await assertSucceeds(bobDb.collection("user_blocks").doc(blockId).delete());
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages")
      .where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_after_unblock").set({
    messageId: "msg_after_unblock",
    conversationId: convId,
    senderId: ALICE_UID,
    receiverId: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    content: "Glad we reconnected!",
    type: "TEXT",
    isRead: false,
    createdAt: now,
  }));
});

// 5. Blocks, Reports & Notifications
test("Block: user can block another member and list own blocks, cannot self-block", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(aliceDb.collection("user_blocks").doc(`block_${ALICE_UID}_${ALICE_UID}`).set({
    blockId: `block_${ALICE_UID}_${ALICE_UID}`,
    blockerId: ALICE_UID,
    blockedId: ALICE_UID,
    createdAt: now,
  }));

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

// 7. Friend Requests & Free Video Call Signaling Rules
test("FriendRequests: requester can send, recipient can accept, requester cannot self-accept, blocked/third-party fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date();
  const reqId = `fr_${ALICE_UID}_${BOB_UID}`;

  // Cannot pre-seed ACCEPTED status on creation
  await assertFails(aliceDb.collection("friend_requests").doc(reqId).set({
    requestId: reqId,
    requesterId: ALICE_UID,
    recipientId: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    status: "ACCEPTED",
    createdAt: now,
    updatedAt: now,
  }));

  // Valid PENDING request succeeds
  await assertSucceeds(aliceDb.collection("friend_requests").doc(reqId).set({
    requestId: reqId,
    requesterId: ALICE_UID,
    recipientId: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  }));

  // Requester Alice cannot approve her own sent request
  await assertFails(aliceDb.collection("friend_requests").doc(reqId).update({
    status: "ACCEPTED",
    updatedAt: new Date(),
  }));

  // Third-party Charlie cannot read or accept
  await assertFails(charlieDb.collection("friend_requests").doc(reqId).get());
  await assertFails(charlieDb.collection("friend_requests").doc(reqId).update({
    status: "ACCEPTED",
    updatedAt: new Date(),
  }));

  // Recipient Bob can accept
  await assertSucceeds(bobDb.collection("friend_requests").doc(reqId).update({
    status: "ACCEPTED",
    updatedAt: new Date(),
  }));

  // Either participant can unfriend/delete
  await assertSucceeds(aliceDb.collection("friend_requests").doc(reqId).delete());
});

test("CallSessions & Candidates: participants can signal free video calls, blocked & terminal state bypasses fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date();
  const callId = "call_alice_bob_1";

  // Alice starts a free live video call with Bob
  await assertSucceeds(aliceDb.collection("call_sessions").doc(callId).set({
    callId,
    callerId: ALICE_UID,
    callerName: "Alice",
    calleeId: BOB_UID,
    calleeName: "Bob",
    participantUids: [ALICE_UID, BOB_UID],
    callType: "VIDEO",
    status: "RINGING",
    offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
    createdAt: now,
    updatedAt: now,
  }));

  // Third party Charlie cannot read or join the call
  await assertFails(charlieDb.collection("call_sessions").doc(callId).get());
  await assertFails(charlieDb.collection("call_sessions").doc(callId).collection("candidates").doc("c_charlie").set({
    candidateId: "c_charlie",
    senderId: CHARLIE_UID,
    participantUids: [ALICE_UID, BOB_UID],
    sdpMid: "0",
    sdpMLineIndex: 0,
    candidate: "candidate:fake",
    createdAt: new Date(),
  }));

  // Alice exchanges an ICE candidate
  await assertSucceeds(aliceDb.collection("call_sessions").doc(callId).collection("candidates").doc("cand_1").set({
    candidateId: "cand_1",
    senderId: ALICE_UID,
    participantUids: [ALICE_UID, BOB_UID],
    sdpMid: "0",
    sdpMLineIndex: 0,
    candidate: "candidate:1 1 UDP 2122252543 192.168.1.2 50000 typ host",
    createdAt: new Date(),
  }));

  // Bob answers with answerSdp
  await assertSucceeds(bobDb.collection("call_sessions").doc(callId).update({
    status: "ANSWERED",
    answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
    updatedAt: new Date(),
  }));

  // Ending the call sets ENDED (terminal lock)
  await assertSucceeds(aliceDb.collection("call_sessions").doc(callId).update({
    status: "ENDED",
    updatedAt: new Date(),
  }));

  // Cannot reopen an ENDED call session
  await assertFails(bobDb.collection("call_sessions").doc(callId).update({
    status: "ANSWERED",
    answerSdp: "tampered",
    updatedAt: new Date(),
  }));

  // Blocked users cannot ring one another
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const blockId = `block_${BOB_UID}_${ALICE_UID}`;
    await context.firestore().collection("user_blocks").doc(blockId).set({
      blockId,
      blockerId: BOB_UID,
      blockedId: ALICE_UID,
      createdAt: now,
    });
  });

  await assertFails(aliceDb.collection("call_sessions").doc("call_blocked").set({
    callId: "call_blocked",
    callerId: ALICE_UID,
    callerName: "Alice",
    calleeId: BOB_UID,
    calleeName: "Bob",
    participantUids: [ALICE_UID, BOB_UID],
    callType: "VIDEO",
    status: "RINGING",
    offerSdp: "v=0\r\n",
    createdAt: new Date(),
    updatedAt: new Date(),
  }));
});

// 8. Everyday Activities & Emoji Reactions Tests
test("Activity Posts: signed-in users can post and react with emojis; unauth/spoofing/tampering blocked", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();
  const postId = "post_daily_1";

  // Unauthenticated cannot read or post
  await assertFails(unauthDb.collection("activity_posts").get());
  await assertFails(unauthDb.collection("activity_posts").doc(postId).set({
    postId,
    authorId: ALICE_UID,
    authorName: "Alice",
    content: "Unauth post",
    moodEmoji: "☕",
    activityTag: "Coffee Time",
    reactionCounts: {},
    totalReactions: 0,
    createdAt: now,
    updatedAt: now,
  }));

  // Cannot spoof authorId or inflate initial reactions
  await assertFails(bobDb.collection("activity_posts").doc("post_spoof").set({
    postId: "post_spoof",
    authorId: ALICE_UID,
    authorName: "Alice",
    content: "Fake post",
    moodEmoji: "☕",
    activityTag: "Coffee Time",
    reactionCounts: {},
    totalReactions: 0,
    createdAt: now,
    updatedAt: now,
  }));

  await assertFails(aliceDb.collection("activity_posts").doc("post_fake_count").set({
    postId: "post_fake_count",
    authorId: ALICE_UID,
    authorName: "Alice",
    content: "Inflated likes",
    moodEmoji: "🔥",
    activityTag: "Fitness",
    reactionCounts: { "❤️": 999 },
    totalReactions: 999,
    createdAt: now,
    updatedAt: now,
  }));

  // Alice creates a valid everyday activity post
  await assertSucceeds(aliceDb.collection("activity_posts").doc(postId).set({
    postId,
    authorId: ALICE_UID,
    authorName: "Alice Santos",
    authorCity: "Manila",
    content: "Morning coffee by the bay! Sino gising pa?",
    moodEmoji: "☕",
    activityTag: "Morning Vibe",
    reactionCounts: {},
    totalReactions: 0,
    createdAt: now,
    updatedAt: now,
  }));

  // Bob reads feed and reacts with ❤️ in his own activity_reactions subcollection + post counters
  await assertSucceeds(bobDb.collection("activity_posts").orderBy("createdAt", "desc").limit(50).get());
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("activity_reactions").doc(postId).set({
    userId: BOB_UID,
    postId,
    emoji: "❤️",
    updatedAt: new Date(),
  }));
  await assertSucceeds(bobDb.collection("activity_posts").doc(postId).update({
    reactionCounts: { "❤️": 1 },
    totalReactions: 1,
    updatedAt: new Date(),
  }));

  // Bob cannot write into Alice's personal activity_reactions subcollection
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("activity_reactions").doc(postId).set({
    userId: ALICE_UID,
    postId,
    emoji: "👍",
    updatedAt: new Date(),
  }));

  // Bob cannot edit Alice's activity post content or delete Alice's post
  await assertFails(bobDb.collection("activity_posts").doc(postId).update({
    content: "Hacked post text",
    updatedAt: new Date(),
  }));
  await assertFails(bobDb.collection("activity_posts").doc(postId).delete());

  // Alice can delete her own post
  await assertSucceeds(aliceDb.collection("activity_posts").doc(postId).delete());
});

// 9. Chat Message Emoji Reactions Tests
test("Chat Messages: participants can react with emojis without altering other participant's reaction or content", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const convId = `conv_${ALICE_UID}_${BOB_UID}`;
  const msgId = "msg_emoji_1";
  const now = new Date();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      lastMessage: "Kumusta!",
      lastMessageSenderId: ALICE_UID,
      lastMessageTimestamp: now,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("conversations").doc(convId).collection("messages").doc(msgId).set({
      messageId: msgId,
      conversationId: convId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      content: "Kumusta partner! 😊",
      type: "TEXT",
      isRead: false,
      createdAt: now,
    });
  });

  // Bob reacts with ❤️
  await assertSucceeds(bobDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    reactions: { [BOB_UID]: "❤️" },
  }));

  // Alice adds her own 🔥 reaction while preserving Bob's ❤️
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    reactions: { [BOB_UID]: "❤️", [ALICE_UID]: "🔥" },
  }));

  // Alice CANNOT overwrite or remove Bob's reaction
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    reactions: { [BOB_UID]: "😢", [ALICE_UID]: "🔥" },
  }));
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    reactions: { [ALICE_UID]: "🔥" },
  }));

  // Alice cannot alter message text when adding a reaction
  await assertFails(aliceDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    content: "Edited text",
    reactions: { [BOB_UID]: "❤️", [ALICE_UID]: "👍" },
  }));

  // Non-participant Charlie cannot react
  await assertFails(charlieDb.collection("conversations").doc(convId).collection("messages").doc(msgId).update({
    reactions: { [CHARLIE_UID]: "😂" },
  }));
});

// 10. SOGO PRO Social, Gallery, Account Status, Activities Photo/Comments/Reports & Messaging Tests
test("SOGO PRO: verification fields rejected on client writes, accountStatus owner-only, legacy docs remain updatable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_admin_uid", {
    email: "gptgamestudioph@gmail.com",
    email_verified: true,
    owner: true,
    admin: true,
  }).firestore();
  const past = new Date(Date.now() - 1000);

  // User cannot write legacy isVerifiedMember or verificationStatus fields or set accountStatus=SUSPENDED at create
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    displayName: "Alice",
    age: 25,
    gender: "Female",
    country: "Philippines",
    isVerifiedMember: true,
    createdAt: past,
    updatedAt: past,
  }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    displayName: "Alice",
    age: 25,
    gender: "Female",
    country: "Philippines",
    verificationStatus: "PENDING",
    createdAt: past,
    updatedAt: past,
  }));

  // Valid create without removed verification fields
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    username: "AlicePH25",
    displayName: "Alice",
    age: 25,
    gender: "Female",
    country: "Philippines",
    accountStatus: "ACTIVE",
    createdAt: past,
    updatedAt: past,
  }));

  // Neither user nor owner can write verification fields; user cannot self-suspend
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({
    verificationStatus: "PENDING",
    updatedAt: new Date(Date.now() - 500),
  }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({
    accountStatus: "SUSPENDED",
    updatedAt: new Date(Date.now() - 500),
  }));
  await assertFails(ownerDb.collection("users").doc(ALICE_UID).update({
    isVerifiedMember: true,
    verificationStatus: "VERIFIED",
    updatedAt: new Date(Date.now() - 500),
  }));

  // Verified Owner can update accountStatus (e.g., SUSPENDED -> ACTIVE)
  await assertSucceeds(ownerDb.collection("users").doc(ALICE_UID).update({
    accountStatus: "SUSPENDED",
    updatedAt: new Date(Date.now() - 500),
  }));

  // Verified Owner cannot alter member displayName or bio
  await assertFails(ownerDb.collection("users").doc(ALICE_UID).update({
    displayName: "OwnerTampered",
    updatedAt: new Date(Date.now() - 200),
  }));

  // Existing legacy member document already containing verification fields can still update profile/online state
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob Legacy",
      age: 27,
      gender: "Male",
      country: "Philippines",
      isVerifiedMember: true,
      verificationStatus: "VERIFIED",
      accountStatus: "ACTIVE",
      createdAt: past,
      updatedAt: past,
    });
  });
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).update({
    displayName: "Bob Updated",
    isOnline: true,
    updatedAt: new Date(Date.now() - 100),
  }));
});

test("SOGO PRO Gallery & Social Graph: gallery_photos, user_follows, user_favorites, profile_likes, profile_visits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const past = new Date(Date.now() - 1000);

  // Gallery photos: owner creates/deletes, signed-in members read, third-party denied
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("gallery_photos").doc("photo_1").set({
      photoId: "photo_1",
      userId: ALICE_UID,
      photoUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ==",
      caption: "BGC coffee",
      createdAt: past,
    })
  );
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).collection("gallery_photos").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("gallery_photos").doc("photo_bob").set({
      photoId: "photo_bob",
      userId: BOB_UID,
      photoUrl: "data:image/jpeg;base64,/9j/4AAQ",
      createdAt: past,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("gallery_photos").doc("photo_1").delete());

  // Follows & Favorites: own-write enforced, anti-self-target enforced
  await assertSucceeds(aliceDb.collection("user_follows").doc(`follow_${ALICE_UID}_${BOB_UID}`).set({
    followId: `follow_${ALICE_UID}_${BOB_UID}`,
    followerId: ALICE_UID,
    followingId: BOB_UID,
    createdAt: past,
  }));
  await assertFails(aliceDb.collection("user_follows").doc(`follow_${ALICE_UID}_${ALICE_UID}`).set({
    followId: `follow_${ALICE_UID}_${ALICE_UID}`,
    followerId: ALICE_UID,
    followingId: ALICE_UID,
    createdAt: past,
  }));

  await assertSucceeds(aliceDb.collection("user_favorites").doc(`fav_${ALICE_UID}_${BOB_UID}`).set({
    favoriteId: `fav_${ALICE_UID}_${BOB_UID}`,
    userId: ALICE_UID,
    targetUserId: BOB_UID,
    createdAt: past,
  }));

  // Profile likes & visits: recipient can read, sender creates, third-party denied
  await assertSucceeds(aliceDb.collection("profile_likes").doc(`like_${ALICE_UID}_${BOB_UID}`).set({
    likeId: `like_${ALICE_UID}_${BOB_UID}`,
    likerId: ALICE_UID,
    likedUserId: BOB_UID,
    likerName: "Alice",
    createdAt: past,
  }));
  await assertSucceeds(bobDb.collection("profile_likes").doc(`like_${ALICE_UID}_${BOB_UID}`).get());
  await assertFails(charlieDb.collection("profile_likes").doc(`like_${ALICE_UID}_${BOB_UID}`).get());

  await assertSucceeds(aliceDb.collection("profile_visits").doc(`visit_${ALICE_UID}_${BOB_UID}`).set({
    visitId: `visit_${ALICE_UID}_${BOB_UID}`,
    visitorId: ALICE_UID,
    visitedUserId: BOB_UID,
    visitorName: "Alice",
    visitedAt: past,
  }));
  await assertSucceeds(bobDb.collection("profile_visits").doc(`visit_${ALICE_UID}_${BOB_UID}`).get());

  // Clean up own records
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("gallery_photos").doc("photo_1").delete());
  await assertSucceeds(aliceDb.collection("user_follows").doc(`follow_${ALICE_UID}_${BOB_UID}`).delete());
  await assertSucceeds(aliceDb.collection("user_favorites").doc(`fav_${ALICE_UID}_${BOB_UID}`).delete());
  await assertSucceeds(aliceDb.collection("profile_likes").doc(`like_${ALICE_UID}_${BOB_UID}`).delete());
});

test("SOGO PRO Activities: photo upload, comments, share counts, and activity_reports moderation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_admin_uid", {
    email: "gptgamestudioph@gmail.com",
    email_verified: true,
    owner: true,
    admin: true,
  }).firestore();
  const past = new Date(Date.now() - 1000);
  const postId = "post_pro_1";

  await assertSucceeds(aliceDb.collection("activity_posts").doc(postId).set({
    postId,
    authorId: ALICE_UID,
    authorName: "Alice",
    authorUsername: "Alice28",
    authorCountry: "Philippines",
    content: "Coffee time! ☕",
    imageUrl: "data:image/jpeg;base64,/9j/4AAQ",
    moodEmoji: "☕",
    activityTag: "Coffee Time",
    reactionCounts: {},
    totalReactions: 0,
    commentCount: 0,
    shareCount: 0,
    createdAt: past,
    updatedAt: past,
  }));

  // Bob adds a comment and increments commentCount without altering content/reactions
  await assertSucceeds(aliceDb.collection("activity_posts").doc(postId).collection("comments").doc("c_1").get());
  await assertSucceeds(bobDb.collection("activity_posts").doc(postId).collection("comments").doc("c_1").set({
    commentId: "c_1",
    postId,
    authorId: BOB_UID,
    authorName: "Bob",
    content: "Sarap ng kape!",
    createdAt: past,
  }));
  await assertSucceeds(bobDb.collection("activity_posts").doc(postId).update({
    commentCount: 1,
    shareCount: 1,
    updatedAt: new Date(Date.now() - 500),
  }));

  // Bob cannot alter post content along with shareCount
  await assertFails(bobDb.collection("activity_posts").doc(postId).update({
    content: "Altered text",
    shareCount: 2,
    updatedAt: new Date(Date.now() - 300),
  }));

  // Bob reports Alice's post; cannot mark REVIEWED himself, verified owner can
  await assertSucceeds(bobDb.collection("activity_reports").doc("arep_1").set({
    reportId: "arep_1",
    reporterId: BOB_UID,
    postId,
    authorId: ALICE_UID,
    postExcerpt: "Coffee time!",
    reason: "Spam",
    status: "PENDING",
    createdAt: past,
  }));
  await assertFails(bobDb.collection("activity_reports").doc("arep_1").update({
    status: "REVIEWED",
  }));
  await assertSucceeds(ownerDb.collection("activity_reports").doc("arep_1").update({
    status: "REVIEWED",
  }));
});

test("SOGO PRO Chat, Notifications & Audit Logs: IMAGE msgs, typing/unread, SAY_HI notifications, audit logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_admin_uid", {
    email: "gptgamestudioph@gmail.com",
    email_verified: true,
    owner: true,
    admin: true,
  }).firestore();
  const convId = `conv_${ALICE_UID}_${BOB_UID}`;
  const past = new Date(Date.now() - 1000);

  await assertSucceeds(aliceDb.collection("conversations").doc(convId).set({
    conversationId: convId,
    participantUids: [ALICE_UID, BOB_UID],
    lastMessage: "Started a conversation",
    lastMessageSenderId: ALICE_UID,
    unreadCountByUid: { [ALICE_UID]: 0, [BOB_UID]: 0 },
    typingByUid: {},
    lastMessageTimestamp: past,
    createdAt: past,
    updatedAt: past,
  }));

  // Bob updates typingByUid and unreadCountByUid (Action 2) without changing lastMessage
  await assertSucceeds(bobDb.collection("conversations").doc(convId).update({
    typingByUid: { [BOB_UID]: new Date(Date.now() - 500) },
    updatedAt: new Date(Date.now() - 500),
  }));

  // Alice sends an IMAGE message
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).collection("messages").doc("img_1").set({
    messageId: "img_1",
    conversationId: convId,
    senderId: ALICE_UID,
    receiverId: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    content: "Photo",
    type: "IMAGE",
    mediaUrl: "data:image/jpeg;base64,/9j/4AAQ",
    isRead: false,
    createdAt: past,
  }));

  // Cross-user SAY_HI / PROFILE_LIKE notification allowed when senderId == auth.uid; SYSTEM rejected
  await assertSucceeds(aliceDb.collection("notifications").doc("notif_hi_1").set({
    notificationId: "notif_hi_1",
    userId: BOB_UID,
    senderId: ALICE_UID,
    title: "Alice waved at you!",
    body: "Alice said Hi! Tap to chat.",
    type: "SAY_HI",
    isRead: false,
    createdAt: past,
  }));
  await assertFails(aliceDb.collection("notifications").doc("notif_sys_1").set({
    notificationId: "notif_sys_1",
    userId: BOB_UID,
    senderId: ALICE_UID,
    title: "System alert",
    body: "Fake admin alert",
    type: "SYSTEM",
    isRead: false,
    createdAt: past,
  }));

  // Admin audit logs: verified owner creates/reads; regular user fails
  await assertFails(aliceDb.collection("admin_audit_logs").doc("log_fake").set({
    logId: "log_fake",
    adminUid: ALICE_UID,
    action: "VERIFY_MEMBER",
    details: "Escalation",
    createdAt: past,
  }));
  await assertSucceeds(ownerDb.collection("admin_audit_logs").doc("log_real").set({
    logId: "log_real",
    adminUid: "owner_admin_uid",
    action: "VERIFY_MEMBER",
    details: "Verified Alice",
    createdAt: past,
  }));
});

test("SOGO PRO Activities collection (/activities): create, self-only likes toggle, commentIds & comments subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const past = new Date(Date.now() - 1000);
  const actId = "act_coffee_1";

  // Impersonating another userId on create fails
  await assertFails(bobDb.collection("activities").doc(actId).set({
    postId: actId,
    userId: ALICE_UID,
    authorId: ALICE_UID,
    username: "AlicePH",
    activityType: "Coffee Time",
    content: "Spoofed post",
    likes: [],
    commentIds: [],
    timestamp: past,
    createdAt: past,
    updatedAt: past,
  }));

  // Valid ActivityPost create in /activities
  await assertSucceeds(aliceDb.collection("activities").doc(actId).set({
    postId: actId,
    userId: ALICE_UID,
    authorId: ALICE_UID,
    username: "AlicePH",
    authorName: "Alice",
    activityType: "Coffee Time",
    activityTag: "Coffee Time",
    moodEmoji: "☕",
    content: "Morning brew in BGC!",
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

  // Bob toggles his own UID into likes
  await assertSucceeds(bobDb.collection("activities").doc(actId).update({
    likes: [BOB_UID],
    updatedAt: new Date(Date.now() - 800),
  }));

  // Charlie CANNOT inject ALICE_UID or remove BOB_UID from likes
  await assertFails(charlieDb.collection("activities").doc(actId).update({
    likes: [BOB_UID, ALICE_UID],
    updatedAt: new Date(Date.now() - 700),
  }));
  await assertFails(charlieDb.collection("activities").doc(actId).update({
    likes: [CHARLIE_UID],
    updatedAt: new Date(Date.now() - 600),
  }));

  // Bob toggles his own UID out of likes cleanly
  await assertSucceeds(bobDb.collection("activities").doc(actId).update({
    likes: [],
    updatedAt: new Date(Date.now() - 500),
  }));

  // Bob adds a comment and updates commentIds + commentCount
  await assertSucceeds(bobDb.collection("activities").doc(actId).collection("comments").doc("cmt_1").set({
    commentId: "cmt_1",
    postId: actId,
    authorId: BOB_UID,
    authorName: "Bob",
    content: "See you at BGC!",
    createdAt: past,
  }));
  await assertSucceeds(bobDb.collection("activities").doc(actId).update({
    commentIds: ["cmt_1"],
    commentCount: 1,
    updatedAt: new Date(Date.now() - 400),
  }));

  // Bob writes his emoji reaction in /users/{bob}/activity_reactions/{actId} and updates reactionCounts on /activities/{actId}
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("activity_reactions").doc(actId).set({
    userId: BOB_UID,
    postId: actId,
    emoji: "❤️",
    updatedAt: new Date(Date.now() - 450),
  }));
  await assertSucceeds(bobDb.collection("activities").doc(actId).update({
    reactionCounts: { "❤️": 1 },
    totalReactions: 1,
    updatedAt: new Date(Date.now() - 425),
  }));

  // Bob cannot react to a nonexistent activity post or tamper with post text/authorVerified while reacting
  await assertFails(bobDb.collection("users").doc(BOB_UID).collection("activity_reactions").doc("missing_post_999").set({
    userId: BOB_UID,
    postId: "missing_post_999",
    emoji: "👍",
    updatedAt: new Date(Date.now() - 410),
  }));
  await assertFails(bobDb.collection("activities").doc(actId).update({
    content: "Tampered during reaction",
    reactionCounts: { "❤️": 2 },
    totalReactions: 2,
    updatedAt: new Date(Date.now() - 405),
  }));
  await assertFails(bobDb.collection("activities").doc(actId).update({
    authorVerified: true,
    reactionCounts: { "❤️": 2 },
    totalReactions: 2,
    updatedAt: new Date(Date.now() - 404),
  }));

  // When Alice blocks Bob, Bob cannot write activity_reactions or update likes/reactions/comments on Alice's /activities post
  const blockId = `block_${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(aliceDb.collection("user_blocks").doc(blockId).set({
    blockId,
    blockerId: ALICE_UID,
    blockedId: BOB_UID,
    createdAt: past,
  }));
  await assertFails(bobDb.collection("users").doc(BOB_UID).collection("activity_reactions").doc(actId).set({
    userId: BOB_UID,
    postId: actId,
    emoji: "👍",
    updatedAt: new Date(Date.now() - 390),
  }));
  await assertFails(bobDb.collection("activities").doc(actId).update({
    likes: [BOB_UID],
    updatedAt: new Date(Date.now() - 385),
  }));
  await assertFails(bobDb.collection("activities").doc(actId).update({
    reactionCounts: { "👍": 1 },
    totalReactions: 1,
    updatedAt: new Date(Date.now() - 380),
  }));
  await assertFails(bobDb.collection("activities").doc(actId).update({
    commentIds: ["cmt_1", "cmt_blocked"],
    commentCount: 2,
    updatedAt: new Date(Date.now() - 375),
  }));

  // Non-author Bob cannot delete Alice's activity post, Alice can
  await assertFails(bobDb.collection("activities").doc(actId).delete());
  await assertSucceeds(aliceDb.collection("activities").doc(actId).delete());
});

// --- Legacy Profile Identity & Selfie Verification Requests (Frozen pending manual admin cleanup) ---
test("Verification Requests: frozen for all client creates/updates/deletes; owner-only read for cleanup audit", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_uid", {
    email: "gptgamestudioph@gmail.com",
    email_verified: true,
    owner: true,
    admin: true,
  }).firestore();

  const past = new Date(Date.now() - 1000);
  const legacyReqId = `ver_selfie_${ALICE_UID}`;

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("verification_requests").doc(legacyReqId).set({
      requestId: legacyReqId,
      userId: ALICE_UID,
      memberDisplayName: "Alice Santos",
      method: "FACE_SELFIE",
      evidencePhotoUrl: "data:image/jpeg;base64,/9j/4AAQ",
      status: "PENDING",
      createdAt: past,
    });
  });

  // Unauthenticated and non-owner reads denied
  await assertFails(unauthDb.collection("verification_requests").doc(legacyReqId).get());
  await assertFails(aliceDb.collection("verification_requests").doc(legacyReqId).get());
  await assertFails(bobDb.collection("verification_requests").get());

  // New submissions, updates, and client deletes are completely blocked (even for owner)
  await assertFails(aliceDb.collection("verification_requests").doc(`ver_new_${ALICE_UID}`).set({
    requestId: `ver_new_${ALICE_UID}`,
    userId: ALICE_UID,
    memberDisplayName: "Alice Santos",
    method: "FACE_SELFIE",
    evidencePhotoUrl: "data:image/jpeg;base64,/9j/4AAQ",
    status: "PENDING",
    createdAt: past,
  }));
  await assertFails(ownerDb.collection("verification_requests").doc(legacyReqId).update({
    status: "APPROVED",
    reviewedAt: new Date(Date.now() - 400),
  }));
  await assertFails(ownerDb.collection("verification_requests").doc(legacyReqId).delete());

  // Verified owner can read legacy records for manual audit/export before Admin SDK purge
  await assertSucceeds(ownerDb.collection("verification_requests").doc(legacyReqId).get());
  await assertSucceeds(ownerDb.collection("verification_requests").get());
});

test("Profile Visits Security: ownership, deterministic visitId, immutable identity fields, and anti-overwrite protection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const past1 = new Date(Date.now() - 2000);
  const past2 = new Date(Date.now() - 1000);
  const past3 = new Date(Date.now() - 500);
  const visitId = `visit_${ALICE_UID}_${BOB_UID}`;

  // 1. Unauthenticated read and write attempts are denied
  await assertFails(unauthDb.collection("profile_visits").doc(visitId).get());
  await assertFails(unauthDb.collection("profile_visits").where("visitedUserId", "==", BOB_UID).get());
  await assertFails(unauthDb.collection("profile_visits").doc(visitId).set({
    visitId,
    visitorId: ALICE_UID,
    visitedUserId: BOB_UID,
    visitorName: "Unauth",
    visitedAt: past1,
  }));

  // 2. User cannot create a visit using a manipulated/non-deterministic document ID or another visitor's ID prefix
  await assertFails(charlieDb.collection("profile_visits").doc(`visit_${ALICE_UID}_${BOB_UID}`).set({
    visitId: `visit_${ALICE_UID}_${BOB_UID}`,
    visitorId: CHARLIE_UID,
    visitedUserId: BOB_UID,
    visitorName: "Charlie Spoof",
    visitedAt: past1,
  }));
  await assertFails(charlieDb.collection("profile_visits").doc("visit_arbitrary_123").set({
    visitId: "visit_arbitrary_123",
    visitorId: CHARLIE_UID,
    visitedUserId: BOB_UID,
    visitorName: "Charlie Arbitrary",
    visitedAt: past1,
  }));

  // 3. Valid profile visit creation (matches SogoRepository.recordProfileVisit)
  await assertSucceeds(aliceDb.collection("profile_visits").doc(visitId).set({
    visitId,
    visitorId: ALICE_UID,
    visitedUserId: BOB_UID,
    visitorName: "Alice Santos",
    visitorPhotoUrl: "data:image/jpeg;base64,/9j/4AAQ",
    visitedAt: past1,
  }));

  // 4. Existing valid reads preserved (Alice visitor + Bob visitedUser can read & list; Charlie cannot)
  await assertSucceeds(aliceDb.collection("profile_visits").doc(visitId).get());
  await assertSucceeds(bobDb.collection("profile_visits").doc(visitId).get());
  await assertSucceeds(bobDb.collection("profile_visits").where("visitedUserId", "==", BOB_UID).get());
  await assertFails(charlieDb.collection("profile_visits").doc(visitId).get());

  // 5. Valid authorized updates by original visitor succeed (both SogoRepository .set() refresh and .update())
  await assertSucceeds(aliceDb.collection("profile_visits").doc(visitId).set({
    visitId,
    visitorId: ALICE_UID,
    visitedUserId: BOB_UID,
    visitorName: "Alice S.",
    visitedAt: past2,
  }));
  await assertSucceeds(aliceDb.collection("profile_visits").doc(visitId).update({
    visitorName: "Alice Updated",
    visitorPhotoUrl: "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
    visitedAt: past3,
  }));

  // 6. Cross-user overwrite attempts on Alice's existing visit are denied (both Charlie and Bob)
  await assertFails(charlieDb.collection("profile_visits").doc(visitId).set({
    visitId,
    visitorId: CHARLIE_UID,
    visitedUserId: BOB_UID,
    visitorName: "Charlie Hijack",
    visitedAt: new Date(Date.now() - 200),
  }));
  await assertFails(charlieDb.collection("profile_visits").doc(visitId).update({
    visitorId: CHARLIE_UID,
    visitorName: "Charlie Overwrite",
    visitedAt: new Date(Date.now() - 200),
  }));
  await assertFails(bobDb.collection("profile_visits").doc(visitId).update({
    visitorName: "Tampered By Visited",
    visitedAt: new Date(Date.now() - 200),
  }));

  // 7. Visitor cannot mutate immutable identity fields or inject protected/extra fields on update
  await assertFails(aliceDb.collection("profile_visits").doc(visitId).update({
    visitorId: CHARLIE_UID,
    visitedAt: new Date(Date.now() - 200),
  }));
  await assertFails(aliceDb.collection("profile_visits").doc(visitId).update({
    visitedUserId: CHARLIE_UID,
    visitedAt: new Date(Date.now() - 200),
  }));
  await assertFails(aliceDb.collection("profile_visits").doc(visitId).update({
    isVerifiedMember: true,
    visitedAt: new Date(Date.now() - 200),
  }));

  // 8. Unauthenticated and third-party delete denied; visited user and visitor can delete cleanly
  await assertFails(unauthDb.collection("profile_visits").doc(visitId).delete());
  await assertFails(charlieDb.collection("profile_visits").doc(visitId).delete());
  await assertSucceeds(bobDb.collection("profile_visits").doc(visitId).delete());
});
