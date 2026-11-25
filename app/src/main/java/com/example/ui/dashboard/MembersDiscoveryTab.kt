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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import com.example.data.matching.MatchRecommender
import com.example.data.model.FriendStatus
import com.example.data.model.UserProfile
import com.example.ui.common.MemberPresenceFormatter
import com.example.ui.common.MemberPresenceStatusRow
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

@Composable
fun MembersDiscoveryTab(
    uiState: DashboardUiState,
    onUpdateSearchQuery: (String) -> Unit,
    onSetGenderFilter: (String) -> Unit,
    onSetCountryFilter: (String) -> Unit,
    onSetAgeRange: (String) -> Unit,
    onToggleOnlineOnly: () -> Unit,
    onToggleOfflineOnly: () -> Unit,
    onToggleFriendsOnly: () -> Unit,
    onToggleSuggestedOnly: () -> Unit,
    onTogglePreferredMatchOnly: () -> Unit,
    onToggleMutualInterestsOnly: () -> Unit,
    onToggleFollowingOnly: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onResetFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onSelectMember: (UserProfile) -> Unit,
    onVideoCallMember: (UserProfile) -> Unit,
    onFriendAction: (UserProfile) -> Unit,
    onToggleFollow: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleProfileLike: (UserProfile) -> Unit,
    onSayHi: (UserProfile) -> Unit,
    onBlockMember: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_members_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "discovery_header_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DISCOVER MEMBERS",
                            color = GoldLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${uiState.filteredMembers.size} eligible members matching filters",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = onResetFilters) {
                        Text("Reset Filters", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = uiState.filter.searchQuery,
                    onValueChange = onUpdateSearchQuery,
                    placeholder = {
                        Text("Search username, name, city, country, hobbies...", color = TextDim, fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = GoldAccent)
                    },
                    trailingIcon = {
                        if (uiState.filter.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onUpdateSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
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
                        .testTag("discovery_search_input")
                )

                // Presence & Social Graph Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickFilterChip("🟢 Online", uiState.filter.onlineOnly, onToggleOnlineOnly, "filter_online_chip")
                    QuickFilterChip("🌙 Offline", uiState.filter.offlineOnly, onToggleOfflineOnly, "filter_offline_chip")
                    QuickFilterChip("✨ Suggested", uiState.filter.suggestedOnly, onToggleSuggestedOnly, "filter_suggested_chip")
                    QuickFilterChip("💘 My Prefs", uiState.filter.preferredMatchOnly, onTogglePreferredMatchOnly, "filter_preferred_chip")
                    QuickFilterChip("🎯 Mutual", uiState.filter.mutualInterestsOnly, onToggleMutualInterestsOnly, "filter_mutual_chip")
                    QuickFilterChip("💛 Friends", uiState.filter.friendsOnly, onToggleFriendsOnly, "filter_friends_chip")
                    QuickFilterChip("➕ Following", uiState.filter.followingOnly, onToggleFollowingOnly, "filter_following_chip")
                    QuickFilterChip("⭐ Favorites", uiState.filter.favoritesOnly, onToggleFavoritesOnly, "filter_favorites_chip")
                }

                // Gender, Country, Age Range Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL" to "All Genders", "Female" to "Female", "Male" to "Male", "Non-Binary" to "Non-Binary").forEach { (value, label) ->
                        QuickFilterChip(label, uiState.filter.genderFilter == value, { onSetGenderFilter(value) })
                    }
                    listOf("ALL" to "All Countries", "Philippines" to "Philippines", "Overseas" to "Overseas").forEach { (value, label) ->
                        QuickFilterChip(label, uiState.filter.countryFilter == value, { onSetCountryFilter(value) })
                    }
                    listOf("ALL" to "All Ages", "18-24" to "18-24", "25-34" to "25-34", "35+" to "35+").forEach { (value, label) ->
                        QuickFilterChip(label, uiState.filter.ageRange == value, { onSetAgeRange(value) })
                    }
                }
            }
        }

        if (uiState.pagedFilteredMembers.isEmpty()) {
            item(key = "discovery_empty_card") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MatteBlackCard,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No matching members found", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try clearing filters or broadening your age, gender, or country search.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = onResetFilters, border = BorderStroke(1.dp, GoldAccent)) {
                            Text("Clear Filters", color = GoldLight)
                        }
                    }
                }
            }
        } else {
            items(
                items = uiState.pagedFilteredMembers,
                key = { "member_row_${it.userId}" }
            ) { member ->
                val mutualInterests = MatchRecommender.computeMutualInterests(uiState.currentUserProfile, member)
                ProMemberDiscoveryCard(
                    member = member,
                    mutualInterests = mutualInterests,
                    friendStatus = uiState.friendStatusByUserId[member.userId] ?: FriendStatus.NONE,
                    isFollowing = uiState.followingIds.contains(member.userId),
                    isFavorited = uiState.favoriteIds.contains(member.userId),
                    isLiked = uiState.profileLikesGivenIds.contains(member.userId),
                    onSelect = { onSelectMember(member) },
                    onVideoCall = { onVideoCallMember(member) },
                    onFriendAction = { onFriendAction(member) },
                    onToggleFollow = { onToggleFollow(member.userId) },
                    onToggleFavorite = { onToggleFavorite(member.userId) },
                    onToggleLike = { onToggleProfileLike(member) },
                    onSayHi = { onSayHi(member) },
                    onBlock = { onBlockMember(member) }
                )
            }

            if (uiState.hasMoreMembers) {
                item(key = "load_more_members_btn") {
                    OutlinedButton(
                        onClick = onLoadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("load_more_members_button"),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, GoldAccent)
                    ) {
                        Text("Load More Members", color = GoldLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BurgundyPrimary,
            selectedLabelColor = GoldLight,
            containerColor = MatteBlackSurfaceVariant,
            labelColor = TextMuted
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProMemberDiscoveryCard(
    member: UserProfile,
    mutualInterests: List<String>,
    friendStatus: FriendStatus,
    isFollowing: Boolean,
    isFavorited: Boolean,
    isLiked: Boolean,
    onSelect: () -> Unit,
    onVideoCall: () -> Unit,
    onFriendAction: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLike: () -> Unit,
    onSayHi: () -> Unit,
    onBlock: () -> Unit
) {
    val isOnline = !member.hideOnlineStatus && MemberPresenceFormatter.isEffectivelyOnline(member)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(
            1.dp,
            if (isOnline) OnlineGreen.copy(alpha = 0.45f) else OutlineSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("member_card_${member.userId}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(62.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(BurgundyPrimary)
                            .border(1.5.dp, GoldAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!member.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(member.photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${member.displayName} avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = member.displayName.take(1).uppercase(),
                                color = GoldLight,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (!member.hideOnlineStatus) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) OnlineGreen else TextDim)
                                .border(2.dp, MatteBlackCard, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val titleText = if (member.hideAge) {
                            member.displayName
                        } else {
                            "${member.displayName}, ${member.age}"
                        }
                        Text(
                            text = titleText,
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (!member.username.isNullOrBlank()) {
                        Text(text = "@${member.username}", color = GoldLight, fontSize = 11.sp)
                    }
                    if (!member.hideLocation) {
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
                    MemberPresenceStatusRow(
                        member = member,
                        dotSize = 8.dp,
                        fontSize = 11.sp,
                        showOnlineHalo = true
                    )
                }

                IconButton(
                    onClick = onBlock,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("block_member_card_button_${member.userId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Block ${member.displayName}",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!member.aboutMe.isNullOrBlank()) {
                Text(
                    text = member.aboutMe,
                    color = TextWhite.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val tagsToShow = (mutualInterests + member.interests + member.hobbies).distinct().take(5)
            if (tagsToShow.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tagsToShow.forEach { tag ->
                        val isMutual = mutualInterests.contains(tag)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMutual) BurgundyDark else MatteBlackSurfaceVariant,
                            border = BorderStroke(1.dp, if (isMutual) GoldAccent else OutlineSubtle)
                        ) {
                            Text(
                                text = if (isMutual) "★ $tag" else tag,
                                color = if (isMutual) GoldLight else TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Secondary Social Row: Follow, Star/Favorite, Heart/Like, Say Hi
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isLiked) BurgundyPrimary else MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, if (isLiked) BurgundyLight else OutlineSubtle),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleLike() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like profile",
                            tint = if (isLiked) GoldLight else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isLiked) "Liked" else "Like", color = TextWhite, fontSize = 11.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isFavorited) GoldAccent.copy(alpha = 0.2f) else MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, if (isFavorited) GoldAccent else OutlineSubtle),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleFavorite() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isFavorited) "Favorited" else "Favorite", color = GoldLight, fontSize = 11.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isFollowing) BurgundyDark else MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, if (isFollowing) GoldAccent else OutlineSubtle),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onToggleFollow() }
                ) {
                    Text(
                        text = if (isFollowing) "✓ Following" else "+ Follow",
                        color = if (isFollowing) GoldLight else TextWhite,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, OutlineGold),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSayHi() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WavingHand,
                            contentDescription = "Say Hi",
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Say Hi", color = GoldLight, fontSize = 11.sp)
                    }
                }
            }

            // Primary Connect Row (preserves existing testTags)
            val (friendLabel, friendIcon) = when (friendStatus) {
                FriendStatus.NONE -> "Add Friend" to Icons.Default.PersonAdd
                FriendStatus.REQUEST_SENT -> "Sent" to Icons.Default.HourglassTop
                FriendStatus.REQUEST_RECEIVED -> "Accept" to Icons.Default.Check
                FriendStatus.FRIENDS -> "Friends" to Icons.Default.HowToReg
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onFriendAction,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("add_friend_button_${member.userId}"),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (friendStatus == FriendStatus.REQUEST_RECEIVED) OnlineGreen else OutlineGold
                    )
                ) {
                    Icon(
                        imageVector = friendIcon,
                        contentDescription = friendLabel,
                        tint = if (friendStatus == FriendStatus.REQUEST_RECEIVED) OnlineGreen else GoldLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = friendLabel,
                        color = if (friendStatus == FriendStatus.REQUEST_RECEIVED) OnlineGreen else GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onVideoCall,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("video_call_card_button_${member.userId}"),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OnlineGreen.copy(alpha = 0.2f),
                        contentColor = OnlineGreen
                    ),
                    border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video Call",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSelect,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("chat_meet_button_${member.userId}"),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Chat & Meet",
                        tint = GoldLight,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chat", color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
