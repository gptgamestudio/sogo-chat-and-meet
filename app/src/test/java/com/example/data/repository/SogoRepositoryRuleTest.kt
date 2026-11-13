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

  private companion object {
    const val ALICE_EMAIL = "alice_sogo@test.com"
    const val BOB_EMAIL = "bob_sogo@test.com"
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 5000L
  }
}
