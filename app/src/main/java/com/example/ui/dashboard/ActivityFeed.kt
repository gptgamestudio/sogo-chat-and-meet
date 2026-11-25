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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ActivityComment
import com.example.data.model.ActivityPost
import com.example.data.model.UserProfile
import com.example.ui.common.AllEmojisPickerDialog
import com.example.ui.common.MemberPresenceFormatter
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

/**
 * Full real-time Social Activity Feed reading from Firestore `activities` collection,
 * supporting category filtering, toggling Likes (`likes` UID array), emoji reactions,
 * real-time Comments (`activities/{postId}/comments` + `commentIds`), Sharing, Deleting,
 * and Reporting inappropriate posts.
 */
@Composable
fun ActivityFeed(
    currentUserProfile: UserProfile?,
    posts: List<ActivityPost>,
    selectedCategory: String,
    myReactions: Map<String, String>,
    membersById: Map<String, UserProfile>,
    isPosting: Boolean,
    activeCommentsPost: ActivityPost?,
    activeComments: List<ActivityComment>,
    onSelectCategory: (String) -> Unit,
    onOpenPostSheet: () -> Unit,
    onQuickCreatePost: (content: String, moodEmoji: String, activityTag: String, onCleared: () -> Unit) -> Unit,
    onToggleLike: (String) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onOpenComments: (String) -> Unit,
    onCloseComments: () -> Unit,
    onAddComment: (String, String, () -> Unit) -> Unit,
    onDeleteComment: (String, String) -> Unit,
    onSharePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onReportPost: (ActivityPost, String, String?) -> Unit,
    onChatMember: (UserProfile) -> Unit,
    onVideoCallMember: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingReportPost by remember { mutableStateOf<ActivityPost?>(null) }

    pendingReportPost?.let { flaggedPost ->
        ReportActivityDialog(
            post = flaggedPost,
            onDismiss = { pendingReportPost = null },
            onSubmit = { reason, details ->
                pendingReportPost = null
                onReportPost(flaggedPost, reason, details)
            }
        )
    }

    if (activeCommentsPost != null) {
        ActivityCommentsBottomSheet(
            post = activeCommentsPost,
            comments = activeComments,
            currentUid = currentUserProfile?.userId,
            onDismiss = onCloseComments,
            onAddComment = { text, clearFn ->
                onAddComment(activeCommentsPost.resolvedPostId, text, clearFn)
            },
            onDeleteComment = { commentId ->
                onDeleteComment(activeCommentsPost.resolvedPostId, commentId)
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("activity_feed_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "activity_feed_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✨ SOGO ACTIVITIES FEED",
                        color = GoldLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Real-time daily moments • Like, Comment & Connect",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = onOpenPostSheet,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("open_post_creation_sheet_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BurgundyPrimary,
                        contentColor = GoldLight
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Post",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Post", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item(key = "activity_category_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryFilterChip(
                    emoji = "🌟",
                    label = "All",
                    selected = selectedCategory == "ALL",
                    onClick = { onSelectCategory("ALL") }
                )
                SogoEmojiCatalog.activityMoods.forEach { (emoji, tag) ->
                    CategoryFilterChip(
                        emoji = emoji,
                        label = tag,
                        selected = selectedCategory.equals(tag, ignoreCase = true),
                        onClick = { onSelectCategory(tag) }
                    )
                }
            }
        }

        item(key = "activity_inline_composer") {
            ActivityComposerCard(
                currentUserProfile = currentUserProfile,
                isPosting = isPosting,
                onPostActivity = onQuickCreatePost
            )
        }

        if (posts.isEmpty()) {
            item(key = "activity_feed_empty") {
                EverydayActivitiesEmptyCard()
            }
        } else {
            items(
                items = posts,
                key = { "pro_act_${it.resolvedPostId}" }
            ) { post ->
                val currentUid = currentUserProfile?.userId
                val ownerUid = post.resolvedUserId
                val isOwn = currentUid != null && ownerUid == currentUid
                val authorProfile = membersById[ownerUid]
                ProActivityPostCard(
                    post = post,
                    currentUid = currentUid,
                    isOwnPost = isOwn,
                    myReaction = myReactions[post.resolvedPostId],
                    authorProfile = authorProfile,
                    onToggleLike = { onToggleLike(post.resolvedPostId) },
                    onToggleReaction = { emoji -> onToggleReaction(post.resolvedPostId, emoji) },
                    onOpenComments = { onOpenComments(post.resolvedPostId) },
                    onShare = { onSharePost(post.resolvedPostId) },
                    onDeletePost = { onDeletePost(post.resolvedPostId) },
                    onReportPost = { pendingReportPost = post },
                    onChatAuthor = onChatMember,
                    onVideoCallAuthor = onVideoCallMember
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) BurgundyPrimary else MatteBlackSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) GoldAccent else OutlineSubtle),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("activity_filter_${label.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = if (selected) GoldLight else TextMuted,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProActivityPostCard(
    post: ActivityPost,
    currentUid: String?,
    isOwnPost: Boolean,
    myReaction: String?,
    authorProfile: UserProfile?,
    onToggleLike: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onDeletePost: () -> Unit,
    onReportPost: () -> Unit,
    onChatAuthor: (UserProfile) -> Unit,
    onVideoCallAuthor: (UserProfile) -> Unit
) {
    var showAllEmojisDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val postId = post.resolvedPostId
    val displayAuthor = post.resolvedAuthorName
    val displayUsername = post.resolvedUsername
    val displayTag = post.resolvedActivityType
    val isLikedByMe = currentUid != null && post.likes.contains(currentUid)
    val likeCount = post.likes.size
    val totalComments = maxOf(post.commentCount.toInt(), post.commentIds.size)
    val timeText = MemberPresenceFormatter.formatRelativeTimeShort(post.resolvedTimestamp)

    if (showAllEmojisDialog) {
        AllEmojisPickerDialog(
            title = "React to $displayAuthor",
            selectedEmoji = myReaction,
            onDismiss = { showAllEmojisDialog = false },
            onEmojiSelected = onToggleReaction
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Activity Post?", color = TextWhite) },
            text = { Text("This permanently removes your post from SOGO Activities.", color = TextMuted) },
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_post_card_$postId")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Author Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(BurgundyPrimary)
                        .border(1.5.dp, GoldAccent, CircleShape)
                        .clickable(enabled = !isOwnPost && authorProfile != null) {
                            authorProfile?.let(onChatAuthor)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = post.authorPhotoUrl ?: authorProfile?.photoUrl
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "$displayAuthor photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = displayAuthor.take(1).uppercase(),
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
                            text = if (displayUsername.isNotBlank() && !displayUsername.equals(displayAuthor, ignoreCase = true)) {
                                "@$displayUsername • $displayAuthor"
                            } else {
                                displayAuthor
                            },
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = listOfNotNull(
                                post.authorCountry ?: authorProfile?.country,
                                timeText
                            ).joinToString(" • "),
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BurgundyDark,
                    border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "${post.moodEmoji} $displayTag",
                        color = GoldLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (isOwnPost) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_activity_button_$postId")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete activity post",
                            tint = TextMuted
                        )
                    }
                } else {
                    IconButton(
                        onClick = onReportPost,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("report_activity_button_$postId")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Report activity post",
                            tint = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = post.content,
                color = TextWhite,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            if (!post.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(post.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Activity post photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                )
            }

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
                            color = if (selectedByMe) BurgundyPrimary else MatteBlackSurfaceVariant,
                            border = BorderStroke(1.dp, if (selectedByMe) GoldAccent else OutlineSubtle),
                            modifier = Modifier
                                .heightIn(min = 32.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggleReaction(emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 13.sp)
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
            HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(8.dp))

            // Primary Social Interaction Bar: Like (likes[] toggle), Comments (comments subcollection), Share, Emoji React
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isLikedByMe) BurgundyPrimary else MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, if (isLikedByMe) GoldLight else OutlineSubtle),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleLike() }
                        .testTag("activity_like_$postId")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLikedByMe) "Unlike post" else "Like post",
                            tint = if (isLikedByMe) GoldLight else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (likeCount > 0) "Like ($likeCount)" else "Like",
                            color = if (isLikedByMe) GoldLight else TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleReaction("❤️") }
                        .testTag("activity_heart_$postId")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "❤️", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Heart",
                            color = if (myReaction == "❤️") GoldLight else TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenComments() }
                        .testTag("activity_comment_button_$postId")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Comments",
                            tint = GoldLight,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (totalComments > 0) "Comments ($totalComments)" else "Comment",
                            color = TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onShare() }
                        .testTag("activity_share_button_$postId")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share activity",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        if (post.shareCount > 0L) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${post.shareCount}",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showAllEmojisDialog = true }
                        .testTag("activity_all_emojis_$postId")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddReaction,
                            contentDescription = "More emojis",
                            tint = GoldLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

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
                        border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat with ${authorProfile.displayName}",
                            tint = GoldLight,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Chat Now", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onVideoCallAuthor(authorProfile) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OnlineGreen.copy(alpha = 0.18f),
                            contentColor = OnlineGreen
                        ),
                        border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call ${authorProfile.displayName}",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Video Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityCommentsBottomSheet(
    post: ActivityPost,
    comments: List<ActivityComment>,
    currentUid: String?,
    onDismiss: () -> Unit,
    onAddComment: (String, () -> Unit) -> Unit,
    onDeleteComment: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MatteBlackSurface,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("activity_comments_sheet")
        ) {
            Text(
                text = "Comments on ${post.resolvedAuthorName}'s post",
                color = GoldLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${post.moodEmoji} ${post.resolvedActivityType} • ${comments.size} replies",
                color = TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = OutlineSubtle)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Text(
                            text = "No comments yet. Be the first to say hi!",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    }
                } else {
                    items(items = comments, key = { it.commentId }) { comment ->
                        val canDelete = currentUid != null &&
                            (comment.authorId == currentUid || post.resolvedUserId == currentUid)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MatteBlackCard,
                            border = BorderStroke(1.dp, OutlineSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(BurgundyPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!comment.authorPhotoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = comment.authorPhotoUrl,
                                            contentDescription = comment.authorName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = comment.authorName.take(1).uppercase(),
                                            color = GoldLight,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = comment.authorName,
                                        color = GoldLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = comment.content,
                                        color = TextWhite,
                                        fontSize = 13.sp
                                    )
                                }
                                if (canDelete) {
                                    IconButton(
                                        onClick = { onDeleteComment(comment.commentId) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete comment",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { if (it.length <= 500) commentText = it },
                    placeholder = { Text("Write a friendly reply...", color = TextDim, fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("activity_comment_input"),
                    shape = RoundedCornerShape(22.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = OutlineSubtle,
                        focusedContainerColor = MatteBlackCard,
                        unfocusedContainerColor = MatteBlackCard
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = if (commentText.isNotBlank()) BurgundyPrimary else MatteBlackSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = commentText.isNotBlank()) {
                            val toSend = commentText
                            onAddComment(toSend) { commentText = "" }
                        }
                        .testTag("activity_comment_submit_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Comment",
                            tint = if (commentText.isNotBlank()) GoldLight else TextDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportActivityDialog(
    post: ActivityPost,
    onDismiss: () -> Unit,
    onSubmit: (String, String?) -> Unit
) {
    var selectedReason by remember { mutableStateOf("Inappropriate content") }
    var details by remember { mutableStateOf("") }
    val reasons = listOf(
        "Inappropriate content",
        "Harassment or hate speech",
        "Spam or solicitation",
        "Misleading or fake identity"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Report ${post.resolvedAuthorName}'s Post",
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    val selected = selectedReason == reason
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedReason = reason }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) Icons.Default.Check else Icons.Default.Flag,
                            contentDescription = null,
                            tint = if (selected) GoldAccent else TextDim,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reason,
                            color = if (selected) GoldLight else TextWhite,
                            fontSize = 13.sp
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 500) details = it },
                    placeholder = { Text("Optional details for moderation...", color = TextDim, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = OutlineSubtle
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details.takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
            ) {
                Text("Submit Report", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = MatteBlackSurface
    )
}
