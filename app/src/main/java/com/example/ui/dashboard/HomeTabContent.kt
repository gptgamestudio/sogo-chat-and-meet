package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.matching.RecommendedMember
import com.example.data.model.FriendStatus
import com.example.data.model.UserProfile
import com.example.ui.common.MemberPresenceFormatter
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyLight
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
 * Primary Home Tab implementing SOGO CHAT & MEET PRO reference layout:
 * - Premium Burgundy-Matte Hero Profile Card w/ 4 real-time Firestore counters
 * - Quick Members / Activities segment toggle (`dashboard_tab_members` / `dashboard_tab_activities`)
 * - Member Search & Filter Chips
 * - 💚 ONLINE USERS YOU MAY LIKE horizontally scrollable portrait carousel
 * - ✨ EVERYDAY ACTIVITIES highlight preview
 * - Incoming Friend Requests (accept / decline)
 * - Recent Conversations shortcut
 */
@Composable
fun HomeTabContent(
    uiState: DashboardUiState,
    onEditProfile: () -> Unit,
    onOpenMembersTab: () -> Unit,
    onOpenActivitiesTab: () -> Unit,
    onOpenProfileTab: () -> Unit,
    onOpenConversationsInbox: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onToggleOnlineOnly: () -> Unit,
    onToggleOfflineOnly: () -> Unit,
    onToggleFriendsOnly: () -> Unit,
    onSelectMember: (UserProfile) -> Unit,
    onVideoCallMember: (UserProfile) -> Unit,
    onSayHiMember: (UserProfile) -> Unit,
    onFriendAction: (UserProfile) -> Unit,
    onAcceptFriendRequest: (String) -> Unit,
    onDeclineFriendRequest: (String) -> Unit,
    onReactActivity: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_tab_content"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Premium Member Profile Hero Card + 4 Live Counters
        item(key = "pro_hero_profile_card") {
            HeroProfileHeaderCard(
                profile = uiState.currentUserProfile,
                friendsCount = uiState.friendsCount,
                onlineNowCount = uiState.actualOnlineCount,
                messagesCount = uiState.unreadMessagesCount,
                likesCount = uiState.receivedLikesCount,
                onEditProfile = onEditProfile,
                onOpenFriends = onOpenMembersTab,
                onOpenOnline = {
                    if (!uiState.filter.onlineOnly) onToggleOnlineOnly()
                    onOpenMembersTab()
                },
                onOpenMessages = onOpenConversationsInbox,
                onOpenLikes = onOpenProfileTab
            )
        }

        // 2. Classic Segmented Pill Switcher preserving existing testTags
        item(key = "home_segment_switcher") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BurgundyPrimary,
                    border = BorderStroke(1.dp, GoldAccent),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenMembersTab() }
                        .testTag("dashboard_tab_members")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            text = "👥 Members (${uiState.members.size})",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenActivitiesTab() }
                        .testTag("dashboard_tab_activities")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            text = "✨ Activities (${uiState.activityPosts.size})",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Search & Quick Presence Chips
        item(key = "home_search_and_chips") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.filter.searchQuery,
                    onValueChange = onUpdateSearchQuery,
                    placeholder = {
                        Text("Search username, name, country, city or interests...", color = TextDim, fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search members", tint = GoldAccent)
                    },
                    trailingIcon = {
                        if (uiState.filter.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onUpdateSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = OutlineSubtle,
                        focusedContainerColor = MatteBlackSurface,
                        unfocusedContainerColor = MatteBlackSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.filter.onlineOnly,
                        onClick = onToggleOnlineOnly,
                        label = { Text("🟢 Online Now (${uiState.actualOnlineCount})", fontSize = 11.sp) },
                        modifier = Modifier.testTag("online_only_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OnlineGreen.copy(alpha = 0.2f),
                            selectedLabelColor = OnlineGreen,
                            containerColor = MatteBlackSurfaceVariant,
                            labelColor = TextMuted
                        )
                    )
                    FilterChip(
                        selected = uiState.filter.offlineOnly,
                        onClick = onToggleOfflineOnly,
                        label = { Text("🌙 Offline", fontSize = 11.sp) },
                        modifier = Modifier.testTag("offline_only_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BurgundyPrimary,
                            selectedLabelColor = GoldLight,
                            containerColor = MatteBlackSurfaceVariant,
                            labelColor = TextMuted
                        )
                    )
                    FilterChip(
                        selected = uiState.filter.friendsOnly,
                        onClick = onToggleFriendsOnly,
                        label = { Text("💛 Friends (${uiState.friendsCount})", fontSize = 11.sp) },
                        modifier = Modifier.testTag("friends_only_chip"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BurgundyPrimary,
                            selectedLabelColor = GoldLight,
                            containerColor = MatteBlackSurfaceVariant,
                            labelColor = TextMuted
                        )
                    )
                }
            }
        }

        // 4. 💚 ONLINE USERS YOU MAY LIKE Carousel
        item(key = "online_users_you_may_like") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💚 ", fontSize = 15.sp)
                        Text(
                            text = "ONLINE USERS YOU MAY LIKE",
                            color = OnlineGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.9.sp
                        )
                    }
                    TextButton(onClick = onOpenMembersTab) {
                        Text("Discover All", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (uiState.onlineMembersYouMayLike.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MatteBlackCard,
                        border = BorderStroke(1.dp, OutlineSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No online recommended members match right now",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Check Discover All or adjust your dating preferences.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("online_recommendations_carousel")
                    ) {
                        items(
                            items = uiState.onlineMembersYouMayLike,
                            key = { "rec_${it.profile.userId}" }
                        ) { rec ->
                            OnlineRecommendationCard(
                                recommended = rec,
                                friendStatus = uiState.friendStatusByUserId[rec.profile.userId] ?: FriendStatus.NONE,
                                onViewProfile = { onSelectMember(rec.profile) },
                                onChatNow = { onSelectMember(rec.profile) },
                                onSayHi = { onSayHiMember(rec.profile) },
                                onAddFriend = { onFriendAction(rec.profile) },
                                onVideoCall = { onVideoCallMember(rec.profile) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Everyday Activities Highlight Preview
        if (uiState.dashboardPreferences.showDailyActivitiesPreview) {
            item(key = "home_activities_preview") {
                EverydayActivitiesHighlightCard(
                    posts = uiState.activityPosts,
                    myReactions = uiState.myActivityReactions,
                    onOpenActivitiesTab = onOpenActivitiesTab,
                    onReact = onReactActivity
                )
            }
        }

        // 6. Incoming Friend Requests
        if (uiState.incomingFriendRequests.isNotEmpty()) {
            item(key = "incoming_friend_requests") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
                    border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "FRIEND REQUESTS (${uiState.incomingFriendRequests.size})",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        uiState.incomingFriendRequests.take(5).forEach { reqItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${reqItem.senderProfile.displayName}, ${reqItem.senderProfile.age}",
                                        color = TextWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${reqItem.senderProfile.city ?: ""}, ${reqItem.senderProfile.country}",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onDeclineFriendRequest(reqItem.senderProfile.userId) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Decline", color = TextMuted, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onAcceptFriendRequest(reqItem.senderProfile.userId) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary)
                                ) {
                                    Text("Accept", color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
private fun HeroProfileHeaderCard(
    profile: UserProfile?,
    friendsCount: Int,
    onlineNowCount: Int,
    messagesCount: Int,
    likesCount: Int,
    onEditProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenOnline: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenLikes: () -> Unit
) {
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "SOGO Member"
    val username = profile?.username?.takeIf { it.isNotBlank() }
    val bio = profile?.aboutMe?.takeIf { it.isNotBlank() } ?: "Say hi and share your everyday vibe on SOGO!"

    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.2.dp, OutlineGold),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_profile_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(BurgundyDark.copy(alpha = 0.92f), MatteBlackCard, MatteBlackSurface)
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(72.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(BurgundyPrimary)
                            .border(2.dp, GoldLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profile?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(profile?.photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Your profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = displayName.take(1).uppercase(),
                                color = GoldLight,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Neon Green Online Dot
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                            .border(2.dp, MatteBlackCard, CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (profile?.isVerifiedMember == true) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified member badge",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (!username.isNullOrBlank()) {
                        Text(
                            text = "@$username",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val location = listOfNotNull(profile?.city, profile?.country).joinToString(", ")
                    Text(
                        text = "${profile?.gender ?: "Member"} • ${profile?.age ?: 18} yrs • $location",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$bio\"",
                        color = TextWhite.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OnlineGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "● Online Now",
                        color = OnlineGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                OutlinedButton(
                    onClick = onEditProfile,
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .testTag("hero_edit_profile_button"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, GoldAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = GoldLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Profile", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Dynamic Live Counters (Friends, Online Now, Unread Messages, Likes Received)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCounterBox(
                    value = friendsCount.toString(),
                    label = "Friends",
                    accentColor = GoldLight,
                    onClick = onOpenFriends,
                    testTag = "stat_friends",
                    modifier = Modifier.weight(1f)
                )
                StatCounterBox(
                    value = onlineNowCount.toString(),
                    label = "Online Now",
                    accentColor = OnlineGreen,
                    onClick = onOpenOnline,
                    testTag = "stat_online",
                    modifier = Modifier.weight(1f)
                )
                StatCounterBox(
                    value = messagesCount.toString(),
                    label = "Unread Msg",
                    accentColor = GoldLight,
                    onClick = onOpenMessages,
                    testTag = "stat_messages",
                    modifier = Modifier.weight(1f)
                )
                StatCounterBox(
                    value = likesCount.toString(),
                    label = "Likes",
                    accentColor = BurgundyLight,
                    onClick = onOpenLikes,
                    testTag = "stat_likes",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCounterBox(
    value: String,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MatteBlackSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                color = accentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OnlineRecommendationCard(
    recommended: RecommendedMember,
    friendStatus: FriendStatus,
    onViewProfile: () -> Unit,
    onChatNow: () -> Unit,
    onSayHi: () -> Unit,
    onAddFriend: () -> Unit,
    onVideoCall: () -> Unit
) {
    val member = recommended.profile
    val showAge = !member.hideAge
    val showLocation = !member.hideLocation
    val showOnline = !member.hideOnlineStatus && MemberPresenceFormatter.isEffectivelyOnline(member)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.55f)),
        modifier = Modifier
            .width(236.dp)
            .clickable { onViewProfile() }
            .testTag("online_recommended_card_${member.userId}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
            ) {
                if (!member.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(member.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${member.displayName}'s portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(BurgundyPrimary, BurgundyDark))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.displayName.take(1).uppercase(),
                            color = GoldLight,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MatteBlackCard.copy(alpha = 0.92f))
                            )
                        )
                )

                // Top Online + Match Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showOnline) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MatteBlackSurface.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ONLINE", color = OnlineGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (recommended.mutualInterests.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BurgundyPrimary.copy(alpha = 0.92f)
                        ) {
                            Text(
                                text = "${recommended.mutualInterests.size} mutual",
                                color = GoldLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (showAge) "${member.displayName}, ${member.age}" else member.displayName,
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (member.isVerifiedMember) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    if (!member.username.isNullOrBlank()) {
                        Text(text = "@${member.username}", color = GoldLight, fontSize = 10.sp)
                    }
                    if (showLocation) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${member.city ?: ""}, ${member.country}".trim(',', ' '),
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = member.aboutMe?.takeIf { it.isNotBlank() } ?: "Ready to chat & meet on SOGO!",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val interestTags = (recommended.mutualInterests + member.interests).distinct().take(3)
                if (interestTags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        interestTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MatteBlackSurfaceVariant
                            ) {
                                Text(
                                    text = tag,
                                    color = GoldLight,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Actions: Say Hi, Add Friend, Video Call, Chat Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onSayHi,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, OutlineGold)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WavingHand,
                            contentDescription = "Say Hi",
                            tint = GoldLight,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Say Hi", color = GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    val (friendLabel, friendIcon) = when (friendStatus) {
                        FriendStatus.NONE -> "Add" to Icons.Default.PersonAdd
                        FriendStatus.REQUEST_SENT -> "Sent" to Icons.Default.HourglassTop
                        FriendStatus.REQUEST_RECEIVED -> "Accept" to Icons.Default.Check
                        FriendStatus.FRIENDS -> "Friend" to Icons.Default.HowToReg
                    }
                    OutlinedButton(
                        onClick = onAddFriend,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, OutlineSubtle)
                    ) {
                        Icon(
                            imageVector = friendIcon,
                            contentDescription = friendLabel,
                            tint = GoldLight,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(friendLabel, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onChatNow,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat Now",
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chat Now", color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onVideoCall,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen.copy(alpha = 0.16f))
                            .border(1.dp, OnlineGreen.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Free Video Call ${member.displayName}",
                            tint = OnlineGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
