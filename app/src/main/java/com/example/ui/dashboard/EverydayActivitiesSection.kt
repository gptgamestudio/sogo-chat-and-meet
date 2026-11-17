package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ActivityPost
import com.example.data.model.UserProfile
import com.example.ui.common.AllEmojisPickerDialog
import com.example.ui.common.SogoEmojiCatalog
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackCard
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.MatteBlackSurfaceVariant
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OutlineGold
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun EverydayActivitiesHighlightCard(
    posts: List<ActivityPost>,
    myReactions: Map<String, String>,
    onOpenActivitiesTab: () -> Unit,
    onReact: (String, String) -> Unit
) {
    val latestPost = posts.firstOrNull()
    var showEmojiPickerForLatest by remember { mutableStateOf(false) }

    if (showEmojiPickerForLatest && latestPost != null) {
        AllEmojisPickerDialog(
            title = "React to ${latestPost.authorName}'s Activity",
            selectedEmoji = myReactions[latestPost.postId],
            onDismiss = { showEmojiPickerForLatest = false },
            onEmojiSelected = { emoji -> onReact(latestPost.postId, emoji) }
        )
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MatteBlackSurface,
        border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("everyday_activities_highlight_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✨ EVERYDAY ACTIVITIES",
                        color = GoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Share your day • Like, Heart & React",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = onOpenActivitiesTab,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("open_everyday_activities_button"),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BurgundyPrimary,
                        contentColor = GoldLight
                    )
                ) {
                    Text(
                        text = if (posts.isEmpty()) "Post Now" else "View All (${posts.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (latestPost != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val myEmoji = myReactions[latestPost.postId]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MatteBlackSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenActivitiesTab() }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = latestPost.moodEmoji, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = latestPost.authorName,
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = latestPost.activityTag,
                                    color = GoldAccent,
                                    fontSize = 10.sp
                                )
                            }
                            if (latestPost.totalReactions > 0) {
                                Text(
                                    text = "${latestPost.totalReactions} reactions",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = latestPost.content,
                            color = TextWhite.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QuickReactionPill(
                                emoji = "👍",
                                label = "Like",
                                count = latestPost.reactionCounts["👍"] ?: 0L,
                                isSelected = myEmoji == "👍",
                                onClick = { onReact(latestPost.postId, "👍") }
                            )
                            QuickReactionPill(
                                emoji = "❤️",
                                label = "Heart",
                                count = latestPost.reactionCounts["❤️"] ?: 0L,
                                isSelected = myEmoji == "❤️",
                                onClick = { onReact(latestPost.postId, "❤️") }
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MatteBlackCard,
                                border = BorderStroke(1.dp, OutlineSubtle),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showEmojiPickerForLatest = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "😊+", color = GoldLight, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityComposerCard(
    currentUserProfile: UserProfile?,
    isPosting: Boolean,
    onPostActivity: (content: String, moodEmoji: String, activityTag: String, onCleared: () -> Unit) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(SogoEmojiCatalog.activityMoods.first()) }
    var showEmojiInsertDialog by remember { mutableStateOf(false) }

    if (showEmojiInsertDialog) {
        AllEmojisPickerDialog(
            title = "Insert Emoji into Activity",
            selectedEmoji = null,
            onDismiss = { showEmojiInsertDialog = false },
            onEmojiSelected = { emoji ->
                if (content.length + emoji.length <= 500) {
                    content += emoji
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_composer_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BurgundyPrimary)
                        .border(1.5.dp, GoldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentUserProfile?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentUserProfile?.photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Your profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = (currentUserProfile?.displayName ?: "S").take(1).uppercase(),
                            color = GoldLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share Your Everyday Activity",
                        color = GoldLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Open to all SOGO members • Express with emojis",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mood & Activity Tag selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SogoEmojiCatalog.activityMoods.forEach { mood ->
                    val isSelected = selectedMood == mood
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) BurgundyPrimary else MatteBlackSurfaceVariant,
                        border = BorderStroke(1.dp, if (isSelected) GoldAccent else OutlineSubtle),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedMood = mood }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = mood.first, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = mood.second,
                                color = if (isSelected) GoldLight else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 500) content = it },
                placeholder = {
                    Text(
                        text = "What are you up to today? (${selectedMood.first} ${selectedMood.second})...",
                        color = TextDim,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .testTag("activity_post_input"),
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = OutlineSubtle,
                    focusedContainerColor = MatteBlackSurfaceVariant,
                    unfocusedContainerColor = MatteBlackSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Emoji Insert Row + Post Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showEmojiInsertDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("activity_composer_all_emojis_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Open all emojis",
                            tint = GoldLight
                        )
                    }

                    listOf("👍", "❤️", "😂", "🔥", "☕", "✨").forEach { quick ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MatteBlackSurfaceVariant,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (content.length + quick.length <= 500) {
                                        content += quick
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = quick, fontSize = 16.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    enabled = content.isNotBlank() && !isPosting,
                    onClick = {
                        onPostActivity(content, selectedMood.first, selectedMood.second) {
                            content = ""
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("post_activity_button"),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BurgundyPrimary,
                        contentColor = GoldLight,
                        disabledContainerColor = MatteBlackSurfaceVariant,
                        disabledContentColor = TextDim
                    )
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = GoldLight,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Post activity",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Post Activity",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityPostCard(
    post: ActivityPost,
    isOwnPost: Boolean,
    myReaction: String?,
    authorProfile: UserProfile?,
    onToggleReaction: (String) -> Unit,
    onDeletePost: () -> Unit,
    onChatAuthor: (UserProfile) -> Unit,
    onVideoCallAuthor: (UserProfile) -> Unit
) {
    var showAllEmojisDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()) }
    val timestampText = post.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Just now"

    if (showAllEmojisDialog) {
        AllEmojisPickerDialog(
            title = "React to ${post.authorName}",
            selectedEmoji = myReaction,
            onDismiss = { showAllEmojisDialog = false },
            onEmojiSelected = { emoji -> onToggleReaction(emoji) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Activity Post?", color = TextWhite) },
            text = { Text("This will remove your activity post from the feed.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePost()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Delete", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_post_card_${post.postId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Author Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BurgundyPrimary)
                        .border(1.5.dp, GoldAccent.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!post.authorPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(post.authorPhotoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "${post.authorName}'s photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = post.authorName.take(1).uppercase(),
                            color = GoldLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName,
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BurgundyDark.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "${post.moodEmoji} ${post.activityTag}",
                                color = GoldLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = listOfNotNull(post.authorCity, timestampText).joinToString(" • "),
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                if (isOwnPost) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_activity_button_${post.postId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete activity post",
                            tint = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Activity Message Body
            Text(
                text = post.content,
                color = TextWhite,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            // Active Reaction Summary Pills (if any)
            val nonZeroCounts = post.reactionCounts.entries
                .filter { it.value > 0L }
                .sortedByDescending { it.value }
            if (nonZeroCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    nonZeroCounts.forEach { (emoji, count) ->
                        val selectedByMe = myReaction == emoji
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selectedByMe) BurgundyPrimary.copy(alpha = 0.85f) else MatteBlackSurfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (selectedByMe) GoldAccent else OutlineSubtle
                            ),
                            modifier = Modifier
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleReaction(emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$count",
                                    color = if (selectedByMe) GoldLight else TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Reaction Action Bar: Like, Heart, More Quick Emojis, + All Emojis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickReactionPill(
                    emoji = "👍",
                    label = "Like",
                    count = 0L,
                    isSelected = myReaction == "👍",
                    onClick = { onToggleReaction("👍") },
                    modifier = Modifier.testTag("activity_like_${post.postId}")
                )

                QuickReactionPill(
                    emoji = "❤️",
                    label = "Heart",
                    count = 0L,
                    isSelected = myReaction == "❤️",
                    onClick = { onToggleReaction("❤️") },
                    modifier = Modifier.testTag("activity_heart_${post.postId}")
                )

                listOf("😂", "😍", "🔥", "😢").forEach { emoji ->
                    QuickReactionPill(
                        emoji = emoji,
                        label = null,
                        count = 0L,
                        isSelected = myReaction == emoji,
                        onClick = { onToggleReaction(emoji) }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showAllEmojisDialog = true }
                        .testTag("activity_all_emojis_${post.postId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddReaction,
                            contentDescription = "All emojis reaction picker",
                            tint = GoldLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "All",
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Quick Connect Actions (Chat / Free Video Call) if post is from another member
            if (!isOwnPost && authorProfile != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onChatAuthor(authorProfile) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MatteBlackSurface,
                            contentColor = GoldLight
                        ),
                        border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat with ${authorProfile.displayName}",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onVideoCallAuthor(authorProfile) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OnlineGreen.copy(alpha = 0.2f),
                            contentColor = OnlineGreen
                        ),
                        border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Free Video Call ${authorProfile.displayName}",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Free Video", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickReactionPill(
    emoji: String,
    label: String?,
    count: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) BurgundyPrimary else MatteBlackSurfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) GoldAccent else OutlineSubtle),
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 15.sp)
            if (!label.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = if (isSelected) GoldLight else TextWhite,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
            if (count > 0L) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$count",
                    color = if (isSelected) GoldLight else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EverydayActivitiesEmptyCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MatteBlackSurface,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Be the first to share today!",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Post your mood or daily moment above. All members can Like, Heart, or react with any emoji.",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}
