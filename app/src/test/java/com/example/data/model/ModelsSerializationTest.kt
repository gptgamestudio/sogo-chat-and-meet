package com.example.data.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelsSerializationTest {

    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "models_test_app" }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId("sogo-test-project")
                    .setApplicationId("1:1234567890:android:abcdef123456")
                    .setApiKey("AIzaSyTestOnlyPlaceholderKey1234567890")
                    .build(),
                "models_test_app"
            )
        firestore = FirebaseFirestore.getInstance(app)
    }

    private fun docRef(path: String): DocumentReference = firestore.document(path)

    @Test
    fun userProfile_isOnline_serializesAndDeserializesWithExactIsOnlineKey() {
        val raw = mapOf(
            "userId" to "uid_123",
            "displayName" to "Maria Clara",
            "age" to 25,
            "isOnline" to true
        )

        val decoded = CustomClassMapper.convertToCustomClass(
            raw,
            UserProfile::class.java,
            docRef("users/uid_123")
        )
        assertEquals("uid_123", decoded.id)
        assertTrue("UserProfile.isOnline must deserialize true from 'isOnline' field", decoded.isOnline)

        val encoded = CustomClassMapper.convertToPlainJavaTypes(decoded) as Map<*, *>
        assertEquals(true, encoded["isOnline"])
        assertFalse("Encoded map must not strip 'is' prefix into 'online'", encoded.containsKey("online"))
    }

    @Test
    fun userPresence_isOnline_serializesAndDeserializesWithExactIsOnlineKey() {
        val raw = mapOf(
            "userId" to "uid_123",
            "isOnline" to true
        )

        val decoded = CustomClassMapper.convertToCustomClass(
            raw,
            UserPresence::class.java,
            docRef("user_presence/uid_123")
        )
        assertTrue("UserPresence.isOnline must deserialize true from 'isOnline' field", decoded.isOnline)

        val encoded = CustomClassMapper.convertToPlainJavaTypes(decoded) as Map<*, *>
        assertEquals(true, encoded["isOnline"])
        assertFalse(encoded.containsKey("online"))
    }

    @Test
    fun chatMessage_isRead_serializesAndDeserializesWithExactIsReadKey() {
        val raw = mapOf(
            "messageId" to "msg_1",
            "conversationId" to "conv_1",
            "senderId" to "uid_a",
            "receiverId" to "uid_b",
            "content" to "Kumusta",
            "isRead" to true
        )

        val decoded = CustomClassMapper.convertToCustomClass(
            raw,
            ChatMessage::class.java,
            docRef("conversations/conv_1/messages/msg_1")
        )
        assertTrue("ChatMessage.isRead must deserialize true from 'isRead' field", decoded.isRead)

        val encoded = CustomClassMapper.convertToPlainJavaTypes(decoded) as Map<*, *>
        assertEquals(true, encoded["isRead"])
        assertFalse("Encoded map must not strip 'is' prefix into 'read'", encoded.containsKey("read"))
    }

    @Test
    fun notificationRecord_isRead_serializesAndDeserializesWithExactIsReadKey() {
        val raw = mapOf(
            "notificationId" to "notif_1",
            "userId" to "uid_a",
            "title" to "New Message",
            "body" to "Hello!",
            "isRead" to true
        )

        val decoded = CustomClassMapper.convertToCustomClass(
            raw,
            NotificationRecord::class.java,
            docRef("notifications/notif_1")
        )
        assertTrue("NotificationRecord.isRead must deserialize true from 'isRead' field", decoded.isRead)

        val encoded = CustomClassMapper.convertToPlainJavaTypes(decoded) as Map<*, *>
        assertEquals(true, encoded["isRead"])
        assertFalse(encoded.containsKey("read"))
    }
}
