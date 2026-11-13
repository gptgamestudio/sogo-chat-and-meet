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

const [firestoreHost, firestorePortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const firestorePort = parseInt(firestorePortStr, 10);

const [storageHost, storagePortStr] = (process.env.FIREBASE_STORAGE_EMULATOR_HOST || "127.0.0.1:9199").split(":");
const storagePort = parseInt(storagePortStr, 10);

const validJpegBytes = Buffer.from("ffd8ffe000104a46494600010101006000600000ffdb", "hex");
const oversizedBytes = Buffer.alloc(11 * 1024 * 1024, "a"); // 11MB exceeds 5MB and 10MB limits

before(async () => {
  const storageRules = fs.readFileSync("./storage.rules", "utf8");
  const firestoreRules = fs.readFileSync("./firestore.rules", "utf8");

  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    storage: {
      rules: storageRules,
      host: storageHost,
      port: storagePort,
    },
    firestore: {
      rules: firestoreRules,
      host: firestoreHost,
      port: firestorePort,
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
    await testEnv.clearStorage();
  }
});

// =========================================================================
// 1. Profile Photos Tests
// Path: /profile_photos/{userId}/{fileName}
// =========================================================================

test("Profile photos: Unauthenticated read denied", async () => {
  const unauth = testEnv.unauthenticatedContext();
  const ref = unauth.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertFails(ref.getDownloadURL());
});

test("Profile photos: Unauthenticated upload denied", async () => {
  const unauth = testEnv.unauthenticatedContext();
  const ref = unauth.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertFails(ref.put(validJpegBytes, { contentType: "image/jpeg" }));
});

test("Profile photos: Authenticated read allowed", async () => {
  // Seed Alice's avatar via owner upload
  const alice = testEnv.authenticatedContext(ALICE_UID);
  const aliceRef = alice.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertSucceeds(aliceRef.put(validJpegBytes, { contentType: "image/jpeg" }));

  // Bob (authenticated member) can read Alice's profile photo
  const bob = testEnv.authenticatedContext(BOB_UID);
  const bobReadRef = bob.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertSucceeds(bobReadRef.getDownloadURL());
});

test("Profile photos: Owner upload allowed for valid JPEG, PNG, WebP within 5 MB", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID);

  // JPEG
  const jpegRef = alice.storage().ref(`profile_photos/${ALICE_UID}/photo.jpg`);
  await assertSucceeds(jpegRef.put(validJpegBytes, { contentType: "image/jpeg" }));

  // PNG
  const pngRef = alice.storage().ref(`profile_photos/${ALICE_UID}/photo.png`);
  await assertSucceeds(pngRef.put(Buffer.from("fake png data"), { contentType: "image/png" }));

  // WebP
  const webpRef = alice.storage().ref(`profile_photos/${ALICE_UID}/photo.webp`);
  await assertSucceeds(webpRef.put(Buffer.from("fake webp data"), { contentType: "image/webp" }));
});

test("Profile photos: Other user upload denied", async () => {
  const bob = testEnv.authenticatedContext(BOB_UID);
  // Bob tries to upload to Alice's profile photo path
  const ref = bob.storage().ref(`profile_photos/${ALICE_UID}/hacked.jpg`);
  await assertFails(ref.put(validJpegBytes, { contentType: "image/jpeg" }));
});

test("Profile photos: Other user deletion denied", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID);
  const aliceRef = alice.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertSucceeds(aliceRef.put(validJpegBytes, { contentType: "image/jpeg" }));

  const bob = testEnv.authenticatedContext(BOB_UID);
  const bobRef = bob.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertFails(bobRef.delete());
});

test("Profile photos: Owner deletion allowed", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID);
  const ref = alice.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertSucceeds(ref.put(validJpegBytes, { contentType: "image/jpeg" }));
  await assertSucceeds(ref.delete());
});

test("Profile photos: Invalid MIME type denied", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID);
  const ref = alice.storage().ref(`profile_photos/${ALICE_UID}/document.pdf`);
  await assertFails(ref.put(Buffer.from("pdf header"), { contentType: "application/pdf" }));

  const textRef = alice.storage().ref(`profile_photos/${ALICE_UID}/script.sh`);
  await assertFails(textRef.put(Buffer.from("echo hello"), { contentType: "text/plain" }));
});

test("Profile photos: Oversized upload denied (> 5MB)", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID);
  const ref = alice.storage().ref(`profile_photos/${ALICE_UID}/huge.jpg`);
  await assertFails(ref.put(oversizedBytes, { contentType: "image/jpeg" }));
});

// =========================================================================
// 2. Private Chat Photos Tests
// Path: /chat_photos/{conversationId}/{fileName}
// Uses Firestore cross-service auth: request.auth.uid in get(/databases/(default)/...).data.participantUids
// =========================================================================

test("Private chat photos: Conversation participant read and upload allowed", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const bob = testEnv.authenticatedContext(BOB_UID);

  // Participant Alice uploads chat photo
  const aliceRef = alice.storage().ref(`chat_photos/${convId}/photo1.jpg`);
  await assertSucceeds(aliceRef.put(validJpegBytes, { contentType: "image/jpeg" }));

  // Participant Bob reads the chat photo
  const bobRef = bob.storage().ref(`chat_photos/${convId}/photo1.jpg`);
  await assertSucceeds(bobRef.getDownloadURL());
});

test("Private chat photos: Nonparticipant read and upload denied", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const charlie = testEnv.authenticatedContext(CHARLIE_UID);

  // Alice uploads
  const aliceRef = alice.storage().ref(`chat_photos/${convId}/photo1.jpg`);
  await assertSucceeds(aliceRef.put(validJpegBytes, { contentType: "image/jpeg" }));

  // Charlie (non-participant) read denied
  const charlieRef = charlie.storage().ref(`chat_photos/${convId}/photo1.jpg`);
  await assertFails(charlieRef.getDownloadURL());

  // Charlie upload denied
  const charlieUploadRef = charlie.storage().ref(`chat_photos/${convId}/charlie_spy.jpg`);
  await assertFails(charlieUploadRef.put(validJpegBytes, { contentType: "image/jpeg" }));
});

test("Private chat photos: Unauthenticated access denied", async () => {
  const convId = "conv_alice_bob";
  const unauth = testEnv.unauthenticatedContext();
  const ref = unauth.storage().ref(`chat_photos/${convId}/photo.jpg`);
  await assertFails(ref.getDownloadURL());
  await assertFails(ref.put(validJpegBytes, { contentType: "image/jpeg" }));
});

test("Private chat photos: Invalid MIME type denied", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const ref = alice.storage().ref(`chat_photos/${convId}/malware.exe`);
  await assertFails(ref.put(Buffer.from("malware bytes"), { contentType: "application/x-msdownload" }));
});

test("Private chat photos: Oversized upload denied (> 5MB)", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const ref = alice.storage().ref(`chat_photos/${convId}/huge_photo.jpg`);
  await assertFails(ref.put(oversizedBytes, { contentType: "image/jpeg" }));
});

test("Private chat photos: Deletion denied (immutable audit trail)", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const aliceRef = alice.storage().ref(`chat_photos/${convId}/photo1.jpg`);
  await assertSucceeds(aliceRef.put(validJpegBytes, { contentType: "image/jpeg" }));

  // Deletion must fail according to rule: allow delete: if false;
  await assertFails(aliceRef.delete());
});

// =========================================================================
// 3. Voice Messages Tests
// Path: /voice_messages/{conversationId}/{fileName}
// Uses Firestore cross-service auth: request.auth.uid in get(/databases/(default)/...).data.participantUids
// =========================================================================

test("Voice messages: Conversation participant read and upload allowed", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const bob = testEnv.authenticatedContext(BOB_UID);

  // Participant Alice uploads voice note (audio/m4a)
  const voiceBytes = Buffer.from("fake m4a audio stream data");
  const aliceRef = alice.storage().ref(`voice_messages/${convId}/voice1.m4a`);
  await assertSucceeds(aliceRef.put(voiceBytes, { contentType: "audio/m4a" }));

  // Participant Bob listens/downloads voice note
  const bobRef = bob.storage().ref(`voice_messages/${convId}/voice1.m4a`);
  await assertSucceeds(bobRef.getDownloadURL());
});

test("Voice messages: Nonparticipant read and upload denied", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const charlie = testEnv.authenticatedContext(CHARLIE_UID);

  // Alice uploads voice note
  const aliceRef = alice.storage().ref(`voice_messages/${convId}/secret_voice.m4a`);
  await assertSucceeds(aliceRef.put(Buffer.from("voice data"), { contentType: "audio/m4a" }));

  // Charlie (non-participant) read denied
  const charlieReadRef = charlie.storage().ref(`voice_messages/${convId}/secret_voice.m4a`);
  await assertFails(charlieReadRef.getDownloadURL());

  // Charlie upload denied
  const charlieUploadRef = charlie.storage().ref(`voice_messages/${convId}/intruder_voice.m4a`);
  await assertFails(charlieUploadRef.put(Buffer.from("intruder voice"), { contentType: "audio/m4a" }));
});

test("Voice messages: Unauthenticated access denied", async () => {
  const convId = "conv_alice_bob";
  const unauth = testEnv.unauthenticatedContext();
  const ref = unauth.storage().ref(`voice_messages/${convId}/voice.m4a`);
  await assertFails(ref.getDownloadURL());
  await assertFails(ref.put(Buffer.from("voice audio"), { contentType: "audio/m4a" }));
});

test("Voice messages: Invalid MIME type denied", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const ref = alice.storage().ref(`voice_messages/${convId}/fake_voice.txt`);
  await assertFails(ref.put(Buffer.from("plain text instead of audio"), { contentType: "text/plain" }));
});

test("Voice messages: Oversized upload denied (> 10MB)", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const ref = alice.storage().ref(`voice_messages/${convId}/huge_recording.m4a`);
  await assertFails(ref.put(oversizedBytes, { contentType: "audio/m4a" }));
});

test("Voice messages: Deletion denied (immutable audit trail)", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const ref = alice.storage().ref(`voice_messages/${convId}/voice.m4a`);
  await assertSucceeds(ref.put(Buffer.from("audio"), { contentType: "audio/m4a" }));
  await assertFails(ref.delete());
});

// =========================================================================
// 4. Firestore Cross-Service Authorization Edge Cases
// =========================================================================

test("Cross-service authorization: Nonexistent conversation does not grant access", async () => {
  const nonexistentConvId = "conv_does_not_exist_999";
  const alice = testEnv.authenticatedContext(ALICE_UID);

  // Attempting to upload chat photo to nonexistent conversation must fail
  const photoRef = alice.storage().ref(`chat_photos/${nonexistentConvId}/photo.jpg`);
  await assertFails(photoRef.put(validJpegBytes, { contentType: "image/jpeg" }));

  // Attempting to upload voice message to nonexistent conversation must fail
  const voiceRef = alice.storage().ref(`voice_messages/${nonexistentConvId}/voice.m4a`);
  await assertFails(voiceRef.put(Buffer.from("audio data"), { contentType: "audio/m4a" }));
});

test("Voice messages: application/octet-stream arbitrary binary upload denied", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const binRef = alice.storage().ref(`voice_messages/${convId}/payload.bin`);
  await assertFails(binRef.put(Buffer.from("arbitrary binary"), { contentType: "application/octet-stream" }));
});

test("Block enforcement: blocked users cannot read profile photo or upload chat media", async () => {
  const convId = "conv_alice_bob";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc(convId).set({
      conversationId: convId,
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const alice = testEnv.authenticatedContext(ALICE_UID);
  const bob = testEnv.authenticatedContext(BOB_UID);

  // Alice uploads her avatar before blocking
  const aliceAvatar = alice.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertSucceeds(aliceAvatar.put(validJpegBytes, { contentType: "image/jpeg" }));

  // Alice blocks Bob in Firestore
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const blockId = `block_${ALICE_UID}_${BOB_UID}`;
    await ctx.firestore().collection("user_blocks").doc(blockId).set({
      blockId,
      blockerId: ALICE_UID,
      blockedId: BOB_UID,
      createdAt: new Date(),
    });
  });

  // Bob can no longer read Alice's profile photo
  const bobReadsAliceAvatar = bob.storage().ref(`profile_photos/${ALICE_UID}/avatar.jpg`);
  await assertFails(bobReadsAliceAvatar.getDownloadURL());

  // Neither Bob nor Alice can upload new chat photos or voice notes while blocked
  const bobChatPhoto = bob.storage().ref(`chat_photos/${convId}/blocked_photo.jpg`);
  await assertFails(bobChatPhoto.put(validJpegBytes, { contentType: "image/jpeg" }));

  const aliceVoice = alice.storage().ref(`voice_messages/${convId}/blocked_voice.m4a`);
  await assertFails(aliceVoice.put(Buffer.from("audio"), { contentType: "audio/m4a" }));
});

