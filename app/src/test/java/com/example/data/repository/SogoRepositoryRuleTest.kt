package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SogoRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveUserProfile_validAdultProfile_createsSuccessfully() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = SogoRepository(firestore, auth)

    val profile = UserProfile(
      userId = uid,
      displayName = "Alice Santos",
      birthday = "2000-05-15",
      age = 25,
      gender = "Female",
      country = "Philippines",
      city = "Makati",
      aboutMe = "Filipina professional who loves beach trips and good food.",
      personality = "Romantic & Thoughtful",
      interests = listOf("Travel", "Dining", "Fitness"),
      isOnline = true
    )

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveUserProfile(profile)
    }

    val fetched = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeCurrentUserProfile(uid).first { it != null }
    }
    assertNotNull(fetched)
    assertTrue(fetched?.displayName == "Alice Santos")
    assertTrue(fetched?.age == 25)
    assertTrue(fetched?.birthday == "2000-05-15")
    assertTrue(fetched?.personality == "Romantic & Thoughtful")
  }

  @Test
  fun saveUserProfile_under18_failsRules() = runBlocking {
    val uid = signInTestUser("underage_sogo@test.com")
    val repo = SogoRepository(firestore, auth)

    val underageProfile = UserProfile(
      userId = uid,
      displayName = "Underage User",
      birthday = "2010-01-01",
      age = 16,
      gender = "Female",
      country = "Philippines",
      city = "Manila",
      aboutMe = "Underage test",
      personality = "Chill",
      interests = listOf("Gaming"),
      isOnline = true
    )

    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.saveUserProfile(underageProfile)
      }
      fail("Expected PERMISSION_DENIED for age < 18")
    } catch (e: Exception) {
      val fsError = e.unwrapFirestoreException()
      assertTrue(fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
  }

  @Test
  fun conversationAndMessages_participantsCanExchangeMessages() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = SogoRepository(firestore, auth)

    val bobUid = signInTestUser(BOB_EMAIL)
    val bobRepo = SogoRepository(firestore, auth)

    // Alice creates conversation with Bob
    signInTestUser(ALICE_EMAIL)
    val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureConversationExists(bobUid)
    }
    assertTrue(convId.isNotEmpty())

    // Alice sends message to Bob
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.sendMessage(convId, bobUid, "Kumusta Bob! Glad to meet you.", "TEXT")
    }

    // Bob observes the message
    signInTestUser(BOB_EMAIL)
    val messages = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeMessages(convId).first { it.isNotEmpty() }
    }
    assertTrue(messages.any { it.content.contains("Kumusta") })
  }

  @Test
  fun observeMessages_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = SogoRepository(firestore, auth)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeMessages("conv_fake_123").first()
      }
      fail("Expected permission denied exception")
    } catch (e: Exception) {
      val fsError = e.unwrapFirestoreException()
      // Either direct IllegalStateException from requireUserId() or PERMISSION_DENIED
      assertTrue(
        e is IllegalStateException ||
        fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      )
    }
  }

  @Test
  fun blockUser_authenticatedUser_recordsBlockSuccessfully() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = SogoRepository(firestore, auth)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.blockUser("annoying_user_123")
    }

    val blockedIds = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeBlockedUserIds().first { it.isNotEmpty() }
    }
    assertTrue(blockedIds.contains("annoying_user_123"))
  }

  private inline fun <T> runStep(label: String, block: () -> T): T {
    return try {
      block()
    } catch (t: Throwable) {
      throw AssertionError("RuleTest failed at step [$label]: ${t.message}", t)
    }
  }

  @Test
  fun friendRequests_sendAcceptAndRemove_enforcesSecurityRules() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = SogoRepository(firestore, auth)

    val bobUid = signInTestUser(BOB_EMAIL)
    val bobRepo = SogoRepository(firestore, auth)

    // Alice sends friend request to Bob
    signInTestUser(ALICE_EMAIL)
    runStep("alice_sendFriendRequest") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.sendFriendRequest(bobUid)
      }
    }

    val aliceList = runStep("alice_observeFriendRequests") {
      withTimeout(FLOW_TIMEOUT_MS) {
        aliceRepo.observeFriendRequests().first { it.isNotEmpty() }
      }
    }
    assertTrue(aliceList.any { it.recipientId == bobUid && it.status == "PENDING" })
    val reqId = aliceRepo.getFriendRequestIdForUsers(bobUid)

    // Alice cannot self-accept her own sent request
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.acceptFriendRequest(reqId)
      }
      fail("Expected PERMISSION_DENIED when requester tries to accept")
    } catch (e: Exception) {
      val fsError = e.unwrapFirestoreException()
      assertTrue(fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    // Bob accepts the friend request
    signInTestUser(BOB_EMAIL)
    runStep("bob_acceptFriendRequest") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.acceptFriendRequest(reqId)
      }
    }
    val bobAccepted = runStep("bob_observeFriendRequests") {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeFriendRequests().first { list -> list.any { it.requestId == reqId && it.status == "ACCEPTED" } }
      }
    }
    assertTrue(bobAccepted.any { it.status == "ACCEPTED" })

    // Unfriend deletes the record cleanly
    runStep("bob_removeFriendOrRequest") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.removeFriendOrRequest(aliceUid)
      }
    }
  }

  @Test
  fun freeCallSession_createIceAnswerAndEnd_enforcesSecurityRules() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = SogoRepository(firestore, auth)

    val bobUid = signInTestUser(BOB_EMAIL)
    val bobRepo = SogoRepository(firestore, auth)
    val bobProfile = UserProfile(userId = bobUid, displayName = "Bob", age = 26)

    // Alice initiates free VIDEO call session to Bob
    signInTestUser(ALICE_EMAIL)
    val callId = runStep("alice_createCallSession") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.createCallSession(
          callee = bobProfile,
          callerName = "Alice",
          callerPhotoUrl = null,
          callType = "VIDEO",
          offerSdp = "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n"
        )
      }
    }
    assertTrue(callId.isNotEmpty())

    val participants = listOf(aliceUid, bobUid).sorted()
    runStep("alice_addIceCandidate") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.addIceCandidate(
          callId = callId,
          participantUids = participants,
          sdpMid = "0",
          sdpMLineIndex = 0,
          candidate = "candidate:1 1 UDP 2122252543 127.0.0.1 54321 typ host"
        )
      }
    }

    // Bob sees RINGING call and answers it
    signInTestUser(BOB_EMAIL)
    val incomingCalls = runStep("bob_observeCallSessions") {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeCallSessions().first { list -> list.any { it.callId == callId && it.status == "RINGING" } }
      }
    }
    assertTrue(incomingCalls.any { it.callType == "VIDEO" })

    runStep("bob_answerCallSession") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.answerCallSession(callId, "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n")
      }
    }

    val candidates = runStep("bob_observeIceCandidates") {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeIceCandidates(callId).first { it.isNotEmpty() }
      }
    }
    assertTrue(candidates.any { it.senderId == aliceUid })

    // Ending the call succeeds cleanly
    runStep("bob_updateCallStatus_ENDED") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.updateCallStatus(callId, "ENDED")
      }
    }
  }

  @Test
  fun observeFriendRequestsAndCallSessions_unauthenticated_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = SogoRepository(firestore, auth)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeFriendRequests().first()
      }
      fail("Expected unauthenticated error on observeFriendRequests")
    } catch (e: Exception) {
      val fsError = e.unwrapFirestoreException()
      assertTrue(
        e is IllegalStateException ||
        fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      )
    }

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCallSessions().first()
      }
      fail("Expected unauthenticated error on observeCallSessions")
    } catch (e: Exception) {
      val fsError = e.unwrapFirestoreException()
      assertTrue(
        e is IllegalStateException ||
        fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      )
    }
  }

  @Test
  fun activityPosts_createReactToggleAndDelete_enforcesRules() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = SogoRepository(firestore, auth)
    val aliceProfile = UserProfile(
      userId = aliceUid,
      displayName = "Alice Santos",
      age = 25,
      city = "Makati"
    )

    val bobUid = signInTestUser(BOB_EMAIL)
    val bobRepo = SogoRepository(firestore, auth)

    // Alice publishes a daily activity post
    signInTestUser(ALICE_EMAIL)
    val postId = runStep("alice_createActivityPost") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.createActivityPost(
          authorProfile = aliceProfile,
          content = "Morning iced coffee at BGC!",
          moodEmoji = "☕",
          activityTag = "Coffee Time"
        )
      }
    }
    assertTrue(postId.isNotEmpty())

    // Bob reacts with Like 👍, then switches to Heart ❤️
    signInTestUser(BOB_EMAIL)
    runStep("bob_react_like") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.toggleActivityReaction(postId, "👍")
      }
    }
    val myReactionsAfterLike = runStep("bob_observe_like") {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeMyActivityReactions().first { it[postId] == "👍" }
      }
    }
    assertTrue(myReactionsAfterLike[postId] == "👍")

    runStep("bob_switch_to_heart") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.toggleActivityReaction(postId, "❤️")
      }
    }
    val postsAfterSwitch = runStep("bob_observe_posts_after_heart") {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeActivityPosts().first { list ->
          val target = list.firstOrNull { it.postId == postId }
          target != null && target.totalReactions == 1L && (target.reactionCounts["❤️"] ?: 0L) == 1L
        }
      }
    }
    val updatedPost = postsAfterSwitch.first { it.postId == postId }
    assertTrue((updatedPost.reactionCounts["👍"] ?: 0L) == 0L)
    assertTrue(updatedPost.reactionCounts["❤️"] == 1L)

    // Bob cannot delete Alice's activity post
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        bobRepo.deleteActivityPost(postId)
      }
      fail("Expected PERMISSION_DENIED when non-author deletes activity post")
    } catch (e: Exception) {
      val fsError = e.unwrapFirestoreException()
      assertTrue(fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    // Alice deletes her own activity post
    signInTestUser(ALICE_EMAIL)
    runStep("alice_deleteActivityPost") {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.deleteActivityPost(postId)
      }
    }
  }

  @Test
  fun chatMessages_toggleEmojiReaction_enforcesParticipantIsolation() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = SogoRepository(firestore, auth)

    val bobUid = signInTestUser(BOB_EMAIL)
    val bobRepo = SogoRepository(firestore, auth)

    // Alice creates conversation and sends message to Bob
    signInTestUser(ALICE_EMAIL)
    val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureConversationExists(bobUid)
    }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.sendMessage(convId, bobUid, "Tara kape mamaya!", "TEXT")
    }

    val initialMsgs = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeMessages(convId).first { it.isNotEmpty() }
    }
    val msgId = initialMsgs.first().messageId

    // Alice reacts with ☕
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.toggleChatMessageReaction(convId, msgId, "☕")
    }

    // Bob reacts with 😍 while preserving Alice's ☕
    signInTestUser(BOB_EMAIL)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.toggleChatMessageReaction(convId, msgId, "😍")
    }

    val reactedMsgs = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeMessages(convId).first { list ->
        val msg = list.firstOrNull { it.messageId == msgId }
        msg != null && msg.reactions[aliceUid] == "☕" && msg.reactions[bobUid] == "😍"
      }
    }
    val targetMsg = reactedMsgs.first { it.messageId == msgId }
    assertTrue(targetMsg.reactions[aliceUid] == "☕")
    assertTrue(targetMsg.reactions[bobUid] == "😍")
  }

  private companion object {
    const val ALICE_EMAIL = "alice_sogo@test.com"
    const val BOB_EMAIL = "bob_sogo@test.com"
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
