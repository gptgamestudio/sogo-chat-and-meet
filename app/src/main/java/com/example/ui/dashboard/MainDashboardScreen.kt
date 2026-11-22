package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FriendStatus
import com.example.data.model.UserProfile
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.profile.MemberProfileSetupScreen
import com.example.ui.auth.requestGoogleIdToken
import com.example.ui.auth.signOut
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyLight
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackBackground
import com.example.ui.theme.MatteBlackCard
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.MatteBlackSurfaceVariant
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OutlineGold
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: MainDashboardViewModel,
    onSelectMember: (UserProfile) -> Unit,
    onVideoCallMember: (UserProfile) -> Unit = {},
    coroutineScope: CoroutineScope,
    onSignOut: () -> Unit,
    adStatusMessage: String? = null,
    rewardCredits: Int = 0,
    onDismissAdStatus: () -> Unit = {},
    onWatchRewardedAd: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val credentialManager = remember { CredentialManager.create(context) }

    var showMenu by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showAllMembersDirectory by remember { mutableStateOf(false) }
    var selectedDashboardTab by remember { mutableIntStateOf(0) }

    if (showAllMembersDirectory) {
        AllMembersDirectoryDialog(
            members = uiState.members.filter {
                it.userId != uiState.currentUserProfile?.userId &&
                    !uiState.blockedUserIds.contains(it.userId)
            },
            friendStatusByUserId = uiState.friendStatusByUserId,
            onFriendAction = { member ->
                when (uiState.friendStatusByUserId[member.userId] ?: FriendStatus.NONE) {
                    FriendStatus.NONE -> viewModel.sendFriendRequest(member.userId)
                    FriendStatus.REQUEST_RECEIVED -> viewModel.acceptFriendRequest(member.userId)
                    FriendStatus.REQUEST_SENT,
                    FriendStatus.FRIENDS -> viewModel.removeFriendOrCancel(member.userId)
                }
            },
            onSelectMember = { member ->
                showAllMembersDirectory = false
                onSelectMember(member)
            },
            onVideoCallMember = { member ->
                showAllMembersDirectory = false
                onVideoCallMember(member)
            },
            onDismiss = { showAllMembersDirectory = false }
        )
    }

    if (showEditProfileDialog && uiState.currentUserProfile != null) {
        Dialog(
            onDismissRequest = {
                if (!uiState.isSavingProfile) {
                    viewModel.clearError()
                    showEditProfileDialog = false
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            MemberProfileSetupScreen(
                initialProfile = uiState.currentUserProfile,
                isSaving = uiState.isSavingProfile,
                externalErrorMessage = uiState.errorMessage,
                onDismissError = { viewModel.clearError() },
                onSaveProfile = { name, bday, age, gen, country, city, photo, about, personality, interests ->
                    viewModel.createOrUpdateProfile(
                        displayName = name,
                        birthday = bday,
                        age = age,
                        gender = gen,
                        country = country,
                        city = city,
                        photoUrl = photo,
                        aboutMe = about,
                        personality = personality,
                        interests = interests,
                        onSuccess = {
                            showEditProfileDialog = false
                        }
                    )
                },
                onSignOut = {
                    showEditProfileDialog = false
                    signOut(credentialManager, coroutineScope, onSignOut)
                },
                onClose = {
                    if (!uiState.isSavingProfile) {
                        viewModel.clearError()
                        showEditProfileDialog = false
                    }
                }
            )
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeletingAccount) {
                    showDeleteAccountDialog = false
                    viewModel.clearError()
                }
            },
            title = {
                Text(
                    if (uiState.requiresReauthForDeletion) "Confirm With Google" else "Delete Account",
                    color = TextWhite
                )
            },
            text = {
                Column {
                    Text(
                        if (uiState.requiresReauthForDeletion) {
                            "Your session needs fresh Google verification before deleting your account. Your profile and data have not been deleted yet."
                        } else {
                            "This permanently deletes your Firebase Auth login, profile, online presence, your blocks, and your notifications. Shared conversations and immutable safety reports remain governed by backend retention policies until automated server cleanup is deployed."
                        },
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.errorMessage!!,
                            color = AlertRed,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !uiState.isDeletingAccount,
                    onClick = {
                        if (uiState.requiresReauthForDeletion) {
                            coroutineScope.launch {
                                try {
                                    val googleIdToken = requestGoogleIdToken(context, credentialManager)
                                    viewModel.deleteAccount(
                                        googleIdToken = googleIdToken,
                                        onCompleted = {
                                            showDeleteAccountDialog = false
                                            signOut(credentialManager, coroutineScope, onSignOut)
                                        }
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    viewModel.reportError(
                                        e.message ?: "Google reauthentication failed. Your profile was preserved."
                                    )
                                }
                            }
                        } else {
                            viewModel.deleteAccount(
                                onReauthRequired = {
                                    // Keep dialog open so user can tap "Verify & Delete"
                                },
                                onCompleted = {
                                    showDeleteAccountDialog = false
                                    signOut(credentialManager, coroutineScope, onSignOut)
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    if (uiState.isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TextWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (uiState.requiresReauthForDeletion) "Verify & Delete" else "Delete Permanently",
                            color = TextWhite
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isDeletingAccount,
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.clearError()
                    }
                ) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SOGO",
                                color = GoldLight,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CHAT & MEET",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        // Actual Online Members Counter — Tap to open All Online & Offline Members Directory
                        val onlineCount = uiState.actualOnlineCount
                        val onlineLabel = if (onlineCount == 1) {
                            "1 Member Online • Browse All ▾"
                        } else {
                            "$onlineCount Members Online • Browse All ▾"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showAllMembersDirectory = true }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                .testTag("topbar_online_directory_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = onlineLabel,
                                color = OnlineGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("dashboard_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = GoldLight
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MatteBlackSurface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "MY DASHBOARD SETTINGS",
                                    color = GoldAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            },
                            enabled = false,
                            onClick = {}
                        )
                        DropdownMenuItem(
                            text = { Text("Edit My Profile", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                showEditProfileDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                val isOnline = uiState.currentUserProfile?.isOnline == true
                                Text(
                                    if (isOnline) "Set Status: Offline" else "Set Status: Online",
                                    color = if (isOnline) GoldAccent else OnlineGreen
                                )
                            },
                            onClick = {
                                showMenu = false
                                val isOnline = uiState.currentUserProfile?.isOnline == true
                                viewModel.setPresence(!isOnline)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                val shown = uiState.dashboardPreferences.showOnlineStrip
                                Text(
                                    text = if (shown) "Online Strip: Shown" else "Online Strip: Hidden",
                                    color = TextWhite
                                )
                            },
                            modifier = Modifier.testTag("toggle_online_strip_menu_item"),
                            onClick = {
                                showMenu = false
                                viewModel.toggleShowOnlineStrip()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                val shown = uiState.dashboardPreferences.showDailyActivitiesPreview
                                Text(
                                    text = if (shown) "Activities Highlight: Shown" else "Activities Highlight: Hidden",
                                    color = TextWhite
                                )
                            },
                            modifier = Modifier.testTag("toggle_activities_preview_menu_item"),
                            onClick = {
                                showMenu = false
                                viewModel.toggleShowDailyActivitiesPreview()
                            }
                        )
                        HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.5f))
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (rewardCredits > 0) {
                                        "Support SOGO • Watch Ad ($rewardCredits ⭐)"
                                    } else {
                                        "Support SOGO • Watch Ad (+1 ⭐)"
                                    },
                                    color = GoldLight
                                )
                            },
                            modifier = Modifier.testTag("watch_rewarded_ad_menu_item"),
                            onClick = {
                                showMenu = false
                                onWatchRewardedAd()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Account", color = AlertRed) },
                            onClick = {
                                showMenu = false
                                showDeleteAccountDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sign Out", color = TextMuted) },
                            onClick = {
                                showMenu = false
                                viewModel.setPresence(false)
                                signOut(credentialManager, coroutineScope, onSignOut)
                            }
                        )
                        HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.4f))
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "App updates & features are owner-managed.",
                                    color = TextDim,
                                    fontSize = 10.sp
                                )
                            },
                            enabled = false,
                            onClick = {}
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BurgundyDark,
                    titleContentColor = TextWhite
                )
            )
        },
        containerColor = MatteBlackBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dashboard Section Switcher: Members vs Everyday Activities
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MatteBlackSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selectedDashboardTab == 0) BurgundyPrimary else MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, if (selectedDashboardTab == 0) GoldAccent else OutlineSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { selectedDashboardTab = 0 }
                        .testTag("dashboard_tab_members")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "👥 Members",
                            color = if (selectedDashboardTab == 0) GoldLight else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selectedDashboardTab == 1) BurgundyPrimary else MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, if (selectedDashboardTab == 1) GoldAccent else OutlineSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { selectedDashboardTab = 1 }
                        .testTag("dashboard_tab_activities")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "✨ Activities (${uiState.activityPosts.size})",
                            color = if (selectedDashboardTab == 1) GoldLight else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (selectedDashboardTab == 0) {
                // Search & Filter Section (Members Tab)
                DashboardSearchAndFilters(
                    filter = uiState.filter,
                    friendsCount = uiState.friendsCount,
                    onSearchChanged = { viewModel.updateSearchQuery(it) },
                    onGenderSelected = { viewModel.setGenderFilter(it) },
                    onCountrySelected = { viewModel.setCountryFilter(it) },
                    onAgeSelected = { viewModel.setAgeRange(it) },
                    onToggleOnline = { viewModel.toggleOnlineOnly() },
                    onToggleOffline = { viewModel.toggleOfflineOnly() },
                    onToggleFriends = { viewModel.toggleFriendsOnly() }
                )
            }

            AnimatedVisibility(visible = adStatusMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("ad_status_banner"),
                    color = BurgundyDark,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = adStatusMessage ?: "",
                            color = GoldLight,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissAdStatus) {
                            Text("OK", color = TextWhite, fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.errorMessage != null && !showEditProfileDialog && !showDeleteAccountDialog) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("dashboard_error_banner"),
                    color = Color(0xFF42121B),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = TextWhite,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss", color = GoldLight, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Content Area: Members Tab vs Everyday Activities Tab
            if (selectedDashboardTab == 1) {
                val myUserId = uiState.currentUserProfile?.userId
                val membersById = remember(uiState.members) {
                    uiState.members.associateBy { it.userId }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("dashboard_activities_list"),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 84.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "activity_composer") {
                        ActivityComposerCard(
                            currentUserProfile = uiState.currentUserProfile,
                            isPosting = uiState.isPostingActivity,
                            onPostActivity = { content, moodEmoji, activityTag, onCleared ->
                                viewModel.createActivityPost(
                                    content = content,
                                    moodEmoji = moodEmoji,
                                    activityTag = activityTag,
                                    onSuccess = onCleared
                                )
                            }
                        )
                    }

                    if (uiState.activityPosts.isEmpty()) {
                        item(key = "empty_activities_state") {
                            EverydayActivitiesEmptyCard()
                        }
                    } else {
                        items(
                            items = uiState.activityPosts,
                            key = { "activity_${it.postId}" }
                        ) { post ->
                            ActivityPostCard(
                                post = post,
                                isOwnPost = myUserId != null && post.authorId == myUserId,
                                myReaction = uiState.myActivityReactions[post.postId],
                                authorProfile = membersById[post.authorId],
                                onToggleReaction = { emoji ->
                                    viewModel.toggleActivityReaction(post.postId, emoji)
                                },
                                onDeletePost = {
                                    viewModel.deleteActivityPost(post.postId)
                                },
                                onChatAuthor = onSelectMember,
                                onVideoCallAuthor = onVideoCallMember
                            )
                        }
                    }
                }
            } else if (uiState.isLoading && uiState.members.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldAccent)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("dashboard_members_list"),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 84.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Incoming Friend Requests Strip
                    if (uiState.incomingFriendRequests.isNotEmpty()) {
                        item(key = "incoming_friend_requests_section") {
                            IncomingFriendRequestsSection(
                                requests = uiState.incomingFriendRequests,
                                onAccept = { senderUid -> viewModel.acceptFriendRequest(senderUid) },
                                onDecline = { senderUid -> viewModel.removeFriendOrCancel(senderUid) },
                                onOpenProfile = onSelectMember
                            )
                        }
                    }

                    // Active Online Members Place (Quick-Pick Online Strip, user-toggleable)
                    if (uiState.onlineMembers.isNotEmpty() && uiState.dashboardPreferences.showOnlineStrip) {
                        item(key = "online_members_strip") {
                            OnlineMembersQuickStrip(
                                onlineMembers = uiState.onlineMembers,
                                onSelectMember = onSelectMember,
                                onVideoCallMember = onVideoCallMember,
                                onOpenAllDirectory = { showAllMembersDirectory = true }
                            )
                        }
                    }

                    // All Members Directory Header so user immediately sees Online & Offline members + Add Friend
                    item(key = "all_members_directory_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ALL MEMBERS • ONLINE & OFFLINE (${uiState.filteredMembers.size})",
                                color = GoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MatteBlackSurfaceVariant,
                                border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showAllMembersDirectory = true }
                                    .testTag("browse_all_directory_pill")
                            ) {
                                Text(
                                    text = "View List →",
                                    color = GoldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (uiState.filteredMembers.isEmpty()) {
                        item(key = "empty_members_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = OutlineSubtle,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (uiState.filter.friendsOnly) {
                                            "No Friends Matched Yet"
                                        } else {
                                            "No Members Found"
                                        },
                                        color = TextWhite,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (uiState.filter.friendsOnly) {
                                            "Tap Add Friend on any member card to build your SOGO friends list."
                                        } else {
                                            "Try adjusting your filters or search keywords."
                                        },
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = uiState.filteredMembers,
                            key = { it.userId }
                        ) { member ->
                            val friendStatus = uiState.friendStatusByUserId[member.userId] ?: FriendStatus.NONE
                            MemberProfileCard(
                                member = member,
                                friendStatus = friendStatus,
                                onFriendActionClicked = {
                                    when (friendStatus) {
                                        FriendStatus.NONE -> viewModel.sendFriendRequest(member.userId)
                                        FriendStatus.REQUEST_RECEIVED -> viewModel.acceptFriendRequest(member.userId)
                                        FriendStatus.REQUEST_SENT,
                                        FriendStatus.FRIENDS -> viewModel.removeFriendOrCancel(member.userId)
                                    }
                                },
                                onVideoCallClicked = { onVideoCallMember(member) },
                                onChatClicked = { onSelectMember(member) }
                            )
                        }
                    }

                    // Everyday Activities Highlight Card placed after Member Cards so Online/Offline cards are immediately visible
                    if (uiState.dashboardPreferences.showDailyActivitiesPreview) {
                        item(key = "everyday_activities_preview") {
                            EverydayActivitiesHighlightCard(
                                posts = uiState.activityPosts,
                                myReactions = uiState.myActivityReactions,
                                onOpenActivitiesTab = { selectedDashboardTab = 1 },
                                onReact = { postId, emoji ->
                                    viewModel.toggleActivityReaction(postId, emoji)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineMembersQuickStrip(
    onlineMembers: List<UserProfile>,
    onSelectMember: (UserProfile) -> Unit,
    onVideoCallMember: (UserProfile) -> Unit,
    onOpenAllDirectory: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("online_members_place")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ONLINE MEMBERS (${onlineMembers.size})",
                    color = GoldLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BurgundyPrimary.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.7f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenAllDirectory() }
                    .testTag("open_all_members_directory_button")
            ) {
                Text(
                    text = "All Online & Offline →",
                    color = GoldLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = onlineMembers,
                key = { "online_${it.userId}" }
            ) { member ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MatteBlackSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .width(122.dp)
                        .clickable { onSelectMember(member) }
                        .testTag("online_member_chip_${member.userId}")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(54.dp)) {
                            if (!member.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(member.photoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = member.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .border(2.dp, OnlineGreen, CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(BurgundyPrimary)
                                        .border(2.dp, OnlineGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.displayName.take(1).uppercase(),
                                        color = GoldLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                                    .border(2.dp, MatteBlackSurface, CircleShape)
                                    .align(Alignment.BottomEnd)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = member.displayName,
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = member.city ?: "Online",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BurgundyPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp)
                                .clickable { onVideoCallMember(member) }
                                .testTag("online_strip_call_${member.userId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Free Video Call ${member.displayName}",
                                    tint = GoldLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Free Call",
                                    color = GoldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingFriendRequestsSection(
    requests: List<IncomingFriendRequestItem>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onOpenProfile: (UserProfile) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MatteBlackSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineGold.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("incoming_friend_requests_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FRIEND REQUESTS (${requests.size})",
                    color = GoldLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                requests.forEach { item ->
                    val sender = item.senderProfile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MatteBlackSurfaceVariant)
                            .clickable { onOpenProfile(sender) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(BurgundyPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!sender.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = sender.photoUrl,
                                    contentDescription = sender.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = sender.displayName.take(1).uppercase(),
                                    color = GoldLight,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${sender.displayName}, ${sender.age}",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${sender.city ?: "Manila"}, ${sender.country}",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onDecline(sender.userId) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("decline_friend_button_${sender.userId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Decline friend request",
                                tint = TextMuted
                            )
                        }

                        Button(
                            onClick = { onAccept(sender.userId) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("accept_friend_button_${sender.userId}"),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OnlineGreen,
                                contentColor = MatteBlackBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Accept",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardSearchAndFilters(
    filter: DashboardFilter,
    friendsCount: Int = 0,
    onSearchChanged: (String) -> Unit,
    onGenderSelected: (String) -> Unit,
    onCountrySelected: (String) -> Unit,
    onAgeSelected: (String) -> Unit,
    onToggleOnline: () -> Unit,
    onToggleOffline: () -> Unit = {},
    onToggleFriends: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MatteBlackSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = filter.searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Search by name, city, hobbies...", color = TextDim, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = GoldAccent, modifier = Modifier.size(18.dp))
            },
            trailingIcon = if (filter.searchQuery.isNotBlank()) {
                {
                    IconButton(onClick = { onSearchChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("member_search_input"),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = GoldAccent,
                unfocusedBorderColor = OutlineSubtle,
                focusedContainerColor = MatteBlackSurfaceVariant,
                unfocusedContainerColor = MatteBlackSurfaceVariant
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Online Only Toggle
            FilterChip(
                selected = filter.onlineOnly,
                onClick = onToggleOnline,
                label = { Text("🟢 Online Now", fontSize = 12.sp) },
                modifier = Modifier.testTag("online_only_chip"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BurgundyPrimary,
                    selectedLabelColor = GoldLight,
                    containerColor = MatteBlackSurfaceVariant,
                    labelColor = TextMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter.onlineOnly,
                    borderColor = if (filter.onlineOnly) GoldAccent else OutlineSubtle
                )
            )

            // Offline Only Toggle
            FilterChip(
                selected = filter.offlineOnly,
                onClick = onToggleOffline,
                label = { Text("⚪ Offline", fontSize = 12.sp) },
                modifier = Modifier.testTag("offline_only_chip"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BurgundyPrimary,
                    selectedLabelColor = GoldLight,
                    containerColor = MatteBlackSurfaceVariant,
                    labelColor = TextMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter.offlineOnly,
                    borderColor = if (filter.offlineOnly) GoldAccent else OutlineSubtle
                )
            )

            // Friends Only Toggle
            FilterChip(
                selected = filter.friendsOnly,
                onClick = onToggleFriends,
                label = { Text("👥 Friends ($friendsCount)", fontSize = 12.sp) },
                modifier = Modifier.testTag("friends_only_chip"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BurgundyPrimary,
                    selectedLabelColor = GoldLight,
                    containerColor = MatteBlackSurfaceVariant,
                    labelColor = TextMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter.friendsOnly,
                    borderColor = if (filter.friendsOnly) GoldAccent else OutlineSubtle
                )
            )

            // Gender Filter
            listOf("ALL" to "All Genders", "Female" to "Women", "Male" to "Men", "Non-Binary" to "Non-Binary").forEach { (key, label) ->
                FilterChip(
                    selected = filter.genderFilter == key,
                    onClick = { onGenderSelected(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BurgundyPrimary,
                        selectedLabelColor = TextWhite,
                        containerColor = MatteBlackSurfaceVariant,
                        labelColor = TextMuted
                    )
                )
            }

            // Country Filter
            listOf("ALL" to "All Locations", "Philippines" to "Philippines 🇵🇭", "Overseas" to "Overseas 🌏").forEach { (key, label) ->
                FilterChip(
                    selected = filter.countryFilter == key,
                    onClick = { onCountrySelected(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BurgundyPrimary,
                        selectedLabelColor = TextWhite,
                        containerColor = MatteBlackSurfaceVariant,
                        labelColor = TextMuted
                    )
                )
            }

            // Age Filter
            listOf("ALL" to "All Ages", "18-24" to "18-24", "25-34" to "25-34", "35+" to "35+").forEach { (key, label) ->
                FilterChip(
                    selected = filter.ageRange == key,
                    onClick = { onAgeSelected(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BurgundyPrimary,
                        selectedLabelColor = TextWhite,
                        containerColor = MatteBlackSurfaceVariant,
                        labelColor = TextMuted
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberProfileCard(
    member: UserProfile,
    friendStatus: FriendStatus = FriendStatus.NONE,
    onFriendActionClicked: () -> Unit = {},
    onVideoCallClicked: () -> Unit = {},
    onChatClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onChatClicked() }
            .testTag("member_card_${member.userId}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (member.isOnline) {
                Brush.horizontalGradient(listOf(OutlineGold, BurgundyPrimary))
            } else {
                Brush.horizontalGradient(listOf(OutlineSubtle, Color(0xFF1E1C22)))
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Photo & Badges Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (!member.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(member.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${member.displayName}'s photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Stylish gradient fallback with Initials
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(BurgundyPrimary, Color(0xFF1A050B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.displayName.take(1).uppercase(),
                            color = GoldLight,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Gradient Overlay for smooth text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    MatteBlackCard.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                // Online Status Indicator Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MatteBlackSurface.copy(alpha = 0.85f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            if (member.isOnline) listOf(OnlineGreen, Color(0xFF00796B))
                            else listOf(OutlineSubtle, OutlineSubtle)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (member.isOnline) OnlineGreen else TextDim)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (member.isOnline) "Active Now" else "Offline",
                            color = if (member.isOnline) OnlineGreen else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Verified 18+ Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BurgundyDark.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Adult",
                            tint = GoldLight,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "18+ Verified",
                            color = GoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Name, Age and Gender in bottom of photo box
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = member.displayName,
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${member.age}",
                            color = GoldLight,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = GoldAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${member.city ?: "Manila"}, ${member.country}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${member.gender}",
                            color = TextDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // About Me Snippet & Interests
            Column(modifier = Modifier.padding(16.dp)) {
                if (!member.aboutMe.isNullOrBlank()) {
                    Text(
                        text = member.aboutMe,
                        color = TextWhite.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (!member.personality.isNullOrBlank()) {
                    Row(modifier = Modifier.padding(bottom = 8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BurgundyDark.copy(alpha = 0.9f),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(OutlineGold, GoldAccent))
                            )
                        ) {
                            Text(
                                text = "✨ ${member.personality}",
                                color = GoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (member.interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        member.interests.take(4).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MatteBlackSurfaceVariant,
                                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(OutlineSubtle, OutlineGold)))
                            ) {
                                Text(
                                    text = tag,
                                    color = GoldLight,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // 3-Choice Member Action Bar: Add Friend | Free Video Call | Chat Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (friendLabel, friendIcon, friendBg) = when (friendStatus) {
                        FriendStatus.NONE -> Triple("Add Friend", Icons.Default.PersonAdd, MatteBlackSurfaceVariant)
                        FriendStatus.REQUEST_SENT -> Triple("Pending", Icons.Default.HourglassTop, MatteBlackSurfaceVariant)
                        FriendStatus.REQUEST_RECEIVED -> Triple("Accept", Icons.Default.Check, OnlineGreen.copy(alpha = 0.22f))
                        FriendStatus.FRIENDS -> Triple("Friends", Icons.Default.HowToReg, GoldAccent.copy(alpha = 0.2f))
                    }
                    val friendContentColor = when (friendStatus) {
                        FriendStatus.REQUEST_RECEIVED -> OnlineGreen
                        FriendStatus.FRIENDS -> GoldLight
                        else -> TextWhite
                    }

                    OutlinedButton(
                        onClick = onFriendActionClicked,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("add_friend_button_${member.userId}"),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = friendBg,
                            contentColor = friendContentColor
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (friendStatus == FriendStatus.REQUEST_RECEIVED) OnlineGreen else OutlineGold.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            imageVector = friendIcon,
                            contentDescription = friendLabel,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = friendLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Free Video Call (Open to all members - $0)
                    Button(
                        onClick = onVideoCallClicked,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("video_call_card_button_${member.userId}"),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OnlineGreen.copy(alpha = 0.18f),
                            contentColor = OnlineGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Free Video Call",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Video Free",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Chat Now CTA
                    Button(
                        onClick = onChatClicked,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("chat_meet_button_${member.userId}"),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BurgundyPrimary,
                            contentColor = TextWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(listOf(GoldAccent, GoldLight))
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat Now",
                            tint = GoldLight,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Chat Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllMembersDirectoryDialog(
    members: List<UserProfile>,
    friendStatusByUserId: Map<String, FriendStatus>,
    onFriendAction: (UserProfile) -> Unit,
    onSelectMember: (UserProfile) -> Unit,
    onVideoCallMember: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }

        var searchQuery by remember { mutableStateOf("") }
        var statusTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Online, 2 = Offline

        val sortedMembers = remember(members) {
            members.sortedWith(compareByDescending<UserProfile> { it.isOnline }.thenBy { it.displayName.lowercase() })
        }
        val onlineCount = remember(sortedMembers) { sortedMembers.count { it.isOnline } }
        val offlineCount = remember(sortedMembers) { sortedMembers.count { !it.isOnline } }

        val visibleMembers = remember(sortedMembers, searchQuery, statusTab) {
            val q = searchQuery.trim().lowercase()
            sortedMembers.filter { m ->
                val matchesTab = when (statusTab) {
                    1 -> m.isOnline
                    2 -> !m.isOnline
                    else -> true
                }
                val matchesQuery = q.isEmpty() ||
                    m.displayName.lowercase().contains(q) ||
                    (m.city ?: "").lowercase().contains(q) ||
                    m.country.lowercase().contains(q)
                matchesTab && matchesQuery
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("directory_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = GoldLight
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "All Members Directory",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "$onlineCount Online • $offlineCount Offline",
                                color = OnlineGreen,
                                fontSize = 11.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BurgundyDark)
                )
            },
            containerColor = MatteBlackBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MatteBlackSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search online or offline members...", color = TextDim, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = GoldAccent, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = if (searchQuery.isNotBlank()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = OutlineSubtle,
                            focusedContainerColor = MatteBlackSurfaceVariant,
                            unfocusedContainerColor = MatteBlackSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("directory_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "All (${sortedMembers.size})",
                            1 to "🟢 Online ($onlineCount)",
                            2 to "⚪ Offline ($offlineCount)"
                        ).forEach { (idx, label) ->
                            val selected = statusTab == idx
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) BurgundyPrimary else MatteBlackSurfaceVariant,
                                border = BorderStroke(1.dp, if (selected) GoldAccent else OutlineSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { statusTab = idx }
                                    .testTag("directory_tab_$idx")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        text = label,
                                        color = if (selected) GoldLight else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("all_members_directory_list"),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (visibleMembers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No members match this status or search.",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(items = visibleMembers, key = { "dir_${it.userId}" }) { member ->
                            val friendStatus = friendStatusByUserId[member.userId] ?: FriendStatus.NONE
                            val (friendLabel, friendIcon, friendBg) = when (friendStatus) {
                                FriendStatus.NONE -> Triple("Add Friend", Icons.Default.PersonAdd, BurgundyPrimary)
                                FriendStatus.REQUEST_SENT -> Triple("Pending", Icons.Default.HourglassTop, MatteBlackSurfaceVariant)
                                FriendStatus.REQUEST_RECEIVED -> Triple("Accept", Icons.Default.Check, OnlineGreen.copy(alpha = 0.25f))
                                FriendStatus.FRIENDS -> Triple("Friends", Icons.Default.HowToReg, GoldAccent.copy(alpha = 0.22f))
                            }
                            val friendFg = when (friendStatus) {
                                FriendStatus.REQUEST_RECEIVED -> OnlineGreen
                                FriendStatus.FRIENDS -> GoldLight
                                FriendStatus.NONE -> GoldLight
                                else -> TextMuted
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MatteBlackCard,
                                border = BorderStroke(
                                    1.dp,
                                    if (member.isOnline) OnlineGreen.copy(alpha = 0.45f) else OutlineSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSelectMember(member) }
                                    .testTag("directory_member_row_${member.userId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(50.dp)) {
                                        if (!member.photoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(member.photoUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = member.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .border(
                                                        2.dp,
                                                        if (member.isOnline) OnlineGreen else OutlineSubtle,
                                                        CircleShape
                                                    )
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(BurgundyPrimary)
                                                    .border(
                                                        2.dp,
                                                        if (member.isOnline) OnlineGreen else OutlineSubtle,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = member.displayName.take(1).uppercase(),
                                                    color = GoldLight,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(if (member.isOnline) OnlineGreen else TextDim)
                                                .border(2.dp, MatteBlackCard, CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${member.displayName}, ${member.age}",
                                            color = TextWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${member.city ?: "Manila"}, ${member.country}",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (member.isOnline) "🟢 Online Now" else "⚪ Offline",
                                            color = if (member.isOnline) OnlineGreen else TextDim,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    OutlinedButton(
                                        onClick = { onFriendAction(member) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("directory_friend_button_${member.userId}"),
                                        shape = RoundedCornerShape(18.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = friendBg,
                                            contentColor = friendFg
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (friendStatus == FriendStatus.REQUEST_RECEIVED) OnlineGreen else OutlineGold.copy(alpha = 0.7f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = friendIcon,
                                            contentDescription = friendLabel,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = friendLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = { onVideoCallMember(member) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = "Free Video Call ${member.displayName}",
                                            tint = OnlineGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
