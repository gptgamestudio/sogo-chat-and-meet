package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FriendStatus
import com.example.data.model.UserProfile
import com.example.ui.auth.requestGoogleIdToken
import com.example.ui.auth.signOut
import com.example.ui.common.MemberPresenceStatusRow
import com.example.ui.common.PresenceDot
import com.example.ui.profile.MemberProfileSetupScreen
import com.example.ui.profile.ProfilePhotoSourceDialog
import com.example.ui.profile.rememberProfilePhotoPickerController
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BurgundyDark
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
    onWatchRewardedAd: () -> Unit = {},
    onOpenOwnerAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val credentialManager = remember { CredentialManager.create(context) }

    var showMenu by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showAllMembersDirectory by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var showPostCreationSheet by remember { mutableStateOf(false) }
    var showConversationsInbox by remember { mutableStateOf(false) }
    var showNotificationsInbox by remember { mutableStateOf(false) }
    var pendingBlockMember by remember { mutableStateOf<UserProfile?>(null) }
    var activeBottomTab by remember { mutableStateOf(SogoBottomTab.HOME) }

    if (activeBottomTab != SogoBottomTab.HOME) {
        BackHandler { activeBottomTab = SogoBottomTab.HOME }
    }

    val membersById = remember(uiState.members) {
        uiState.members.associateBy { it.userId }
    }

    val dashboardPhotoPicker = rememberProfilePhotoPickerController { dataUri ->
        viewModel.updateProfilePhoto(dataUri)
    }

    if (dashboardPhotoPicker.showSourceChooser) {
        ProfilePhotoSourceDialog(
            onDismiss = { dashboardPhotoPicker.dismissChooser() },
            onChooseGallery = { dashboardPhotoPicker.launchGalleryPicker() },
            onTakePhoto = { dashboardPhotoPicker.launchCameraCapture() }
        )
    }

    if (showPostCreationSheet) {
        PostCreationSheet(
            currentUserProfile = uiState.currentUserProfile,
            isPosting = uiState.isPostingActivity,
            onDismiss = { showPostCreationSheet = false },
            onSubmitPost = { content, moodEmoji, activityType, imageUrl, onCleared ->
                viewModel.createActivityPost(
                    content = content,
                    moodEmoji = moodEmoji,
                    activityTag = activityType,
                    imageUrl = imageUrl,
                    onSuccess = {
                        onCleared()
                        showPostCreationSheet = false
                        activeBottomTab = SogoBottomTab.ACTIVITIES
                    }
                )
            }
        )
    }

    if (showConversationsInbox) {
        ConversationsInboxSheet(
            currentUid = uiState.currentUserProfile?.userId,
            conversations = uiState.conversations,
            membersById = membersById,
            onDismiss = { showConversationsInbox = false },
            onSelectPartner = { member ->
                showConversationsInbox = false
                onSelectMember(member)
            }
        )
    }

    if (showNotificationsInbox) {
        NotificationsInboxSheet(
            notifications = uiState.notifications,
            membersById = membersById,
            onDismiss = { showNotificationsInbox = false },
            onMarkRead = { viewModel.markNotificationRead(it) },
            onMarkAllRead = { viewModel.markAllNotificationsRead() },
            onSelectPartner = { member ->
                showNotificationsInbox = false
                onSelectMember(member)
            }
        )
    }

    pendingBlockMember?.let { targetMember ->
        AlertDialog(
            onDismissRequest = { pendingBlockMember = null },
            title = {
                Text(
                    text = "Block ${targetMember.displayName}?",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${targetMember.displayName} will be removed from your member discovery list, online strip, and friend requests. Incoming chat messages and calls from them will also be restricted.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val memberToBlock = targetMember
                        pendingBlockMember = null
                        viewModel.blockUser(
                            targetUid = memberToBlock.userId,
                            displayName = memberToBlock.displayName
                        )
                    },
                    modifier = Modifier.testTag("confirm_block_member_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Block User", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBlockMember = null },
                    modifier = Modifier.testTag("cancel_block_member_button")
                ) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    if (showBlockedUsersDialog) {
        BlockedUsersDialog(
            blockedMembers = uiState.blockedMembers,
            myBlockedUserIds = uiState.myBlockedUserIds,
            onUnblock = { userId, displayName ->
                viewModel.unblockUser(userId, displayName)
            },
            onDismiss = { showBlockedUsersDialog = false }
        )
    }

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
            onBlockMember = { member ->
                pendingBlockMember = member
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
                isUpdatingPhoto = uiState.isUpdatingPhoto,
                photoStatusMessage = uiState.photoStatusMessage,
                onInstantPhotoSelected = { uri ->
                    viewModel.updateProfilePhoto(uri)
                },
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
                        viewModel.clearPhotoStatusMessage()
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
                                onReauthRequired = {},
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

    val handleFriendAction: (UserProfile) -> Unit = { member ->
        when (uiState.friendStatusByUserId[member.userId] ?: FriendStatus.NONE) {
            FriendStatus.NONE -> viewModel.sendFriendRequest(member.userId)
            FriendStatus.REQUEST_RECEIVED -> viewModel.acceptFriendRequest(member.userId)
            FriendStatus.REQUEST_SENT,
            FriendStatus.FRIENDS -> viewModel.removeFriendOrCancel(member.userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SOGO",
                                color = GoldLight,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "CHAT & MEET",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Live Online Members Counter — Tap to open All Online & Offline Members Directory
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
                        onClick = { showNotificationsInbox = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("topbar_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationsCount > 0) {
                                    Badge(containerColor = AlertRed, contentColor = TextWhite) {
                                        Text("${uiState.unreadNotificationsCount.coerceAtMost(99)}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = GoldLight
                            )
                        }
                    }

                    IconButton(
                        onClick = { showConversationsInbox = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("topbar_messages_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadMessagesCount > 0) {
                                    Badge(containerColor = OnlineGreen, contentColor = MatteBlackBackground) {
                                        Text("${uiState.unreadMessagesCount.coerceAtMost(99)}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Messages Inbox",
                                tint = GoldLight
                            )
                        }
                    }

                    val myProfile = uiState.currentUserProfile
                    if (myProfile != null) {
                        IconButton(
                            onClick = { dashboardPhotoPicker.openChooser() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("topbar_update_photo_button")
                        ) {
                            Box(modifier = Modifier.size(32.dp)) {
                                if (!myProfile.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(myProfile.photoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Update My Profile Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .border(1.5.dp, GoldAccent, CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(BurgundyPrimary)
                                            .border(1.5.dp, GoldAccent, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = myProfile.displayName.take(1).uppercase(),
                                            color = GoldLight,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clip(CircleShape)
                                        .background(GoldLight)
                                        .border(1.dp, BurgundyDark, CircleShape)
                                        .align(Alignment.BottomEnd),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = BurgundyDark,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }
                    }

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
                            text = { Text("Change Profile Photo", color = GoldLight) },
                            modifier = Modifier.testTag("menu_change_profile_photo"),
                            onClick = {
                                showMenu = false
                                dashboardPhotoPicker.openChooser()
                            }
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
                            text = {
                                val blockedCount = uiState.myBlockedUserIds.size
                                Text(
                                    text = if (blockedCount > 0) "Blocked Users ($blockedCount)" else "Blocked Users",
                                    color = if (blockedCount > 0) GoldLight else TextWhite
                                )
                            },
                            modifier = Modifier.testTag("menu_blocked_users"),
                            onClick = {
                                showMenu = false
                                showBlockedUsersDialog = true
                            }
                        )
                        if (uiState.isOwnerCandidate) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Owner Admin Dashboard",
                                        color = GoldLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                modifier = Modifier.testTag("menu_owner_admin_dashboard"),
                                onClick = {
                                    showMenu = false
                                    onOpenOwnerAdmin()
                                }
                            )
                        }
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
        bottomBar = {
            SogoBottomNavBar(
                selectedTab = activeBottomTab,
                onSelectTab = { tab -> activeBottomTab = tab },
                onOpenPostSheet = { showPostCreationSheet = true }
            )
        },
        containerColor = MatteBlackBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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

            val dashboardError = uiState.errorMessage ?: dashboardPhotoPicker.errorMessage
            AnimatedVisibility(visible = dashboardPhotoPicker.isProcessing || uiState.isUpdatingPhoto) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("photo_updating_banner"),
                    color = MatteBlackSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = GoldLight,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Saving your photo to Firestore...",
                            color = GoldLight,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            AnimatedVisibility(visible = uiState.photoStatusMessage != null && !showEditProfileDialog) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("photo_saved_banner"),
                    color = Color(0xFF162B1D),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "✓ ${uiState.photoStatusMessage ?: ""}",
                            color = OnlineGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearPhotoStatusMessage() }) {
                            Text("OK", color = TextWhite, fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.blockStatusMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("block_status_banner"),
                    color = MatteBlackSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.blockStatusMessage ?: "",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearBlockStatusMessage() }) {
                            Text("OK", color = TextWhite, fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.actionStatusMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("action_status_banner"),
                    color = BurgundyDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.actionStatusMessage ?: "",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearActionStatusMessage() }) {
                            Text("OK", color = TextWhite, fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = dashboardError != null && !showEditProfileDialog && !showDeleteAccountDialog) {
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
                            text = dashboardError ?: "",
                            color = TextWhite,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            viewModel.clearError()
                            dashboardPhotoPicker.clearError()
                        }) {
                            Text("Dismiss", color = GoldLight, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.members.isEmpty() && uiState.currentUserProfile == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldAccent)
                }
            } else {
                when (activeBottomTab) {
                    SogoBottomTab.MEMBERS -> {
                        MembersDiscoveryTab(
                            uiState = uiState,
                            onUpdateSearchQuery = { viewModel.updateSearchQuery(it) },
                            onSetGenderFilter = { viewModel.setGenderFilter(it) },
                            onSetCountryFilter = { viewModel.setCountryFilter(it) },
                            onSetAgeRange = { viewModel.setAgeRange(it) },
                            onToggleOnlineOnly = { viewModel.toggleOnlineOnly() },
                            onToggleOfflineOnly = { viewModel.toggleOfflineOnly() },
                            onToggleFriendsOnly = { viewModel.toggleFriendsOnly() },
                            onToggleSuggestedOnly = { viewModel.toggleSuggestedOnly() },
                            onTogglePreferredMatchOnly = { viewModel.togglePreferredMatchOnly() },
                            onToggleMutualInterestsOnly = { viewModel.toggleMutualInterestsOnly() },
                            onToggleFollowingOnly = { viewModel.toggleFollowingOnly() },
                            onToggleFavoritesOnly = { viewModel.toggleFavoritesOnly() },
                            onResetFilters = { viewModel.resetMemberFilters() },
                            onLoadMore = { viewModel.loadMoreMembers() },
                            onSelectMember = onSelectMember,
                            onVideoCallMember = onVideoCallMember,
                            onFriendAction = handleFriendAction,
                            onToggleFollow = { viewModel.toggleFollow(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onToggleProfileLike = { viewModel.toggleProfileLike(it) },
                            onSayHi = { viewModel.sendSayHi(it) },
                            onBlockMember = { pendingBlockMember = it }
                        )
                    }

                    SogoBottomTab.ACTIVITIES -> {
                        val activePost = uiState.activeCommentsPostId?.let { targetId ->
                            uiState.activityPosts.firstOrNull { it.resolvedPostId == targetId }
                        }
                        ActivityFeed(
                            currentUserProfile = uiState.currentUserProfile,
                            posts = uiState.filteredActivityPosts,
                            selectedCategory = uiState.activityCategoryFilter,
                            myReactions = uiState.myActivityReactions,
                            membersById = membersById,
                            isPosting = uiState.isPostingActivity,
                            activeCommentsPost = activePost,
                            activeComments = uiState.activePostComments,
                            onSelectCategory = { viewModel.setActivityCategoryFilter(it) },
                            onOpenPostSheet = { showPostCreationSheet = true },
                            onQuickCreatePost = { content, moodEmoji, activityTag, onCleared ->
                                viewModel.createActivityPost(
                                    content = content,
                                    moodEmoji = moodEmoji,
                                    activityTag = activityTag,
                                    onSuccess = onCleared
                                )
                            },
                            onToggleLike = { viewModel.toggleActivityLike(it) },
                            onToggleReaction = { postId, emoji ->
                                viewModel.toggleActivityReaction(postId, emoji)
                            },
                            onOpenComments = { viewModel.openCommentsForPost(it) },
                            onCloseComments = { viewModel.closeComments() },
                            onAddComment = { postId, text, onCleared ->
                                viewModel.addActivityComment(postId, text, onCleared)
                            },
                            onDeleteComment = { postId, commentId ->
                                viewModel.deleteActivityComment(postId, commentId)
                            },
                            onSharePost = { viewModel.shareActivityPost(it) },
                            onDeletePost = { viewModel.deleteActivityPost(it) },
                            onReportPost = { post, reason, details ->
                                viewModel.reportActivityPost(post, reason, details)
                            },
                            onChatMember = onSelectMember,
                            onVideoCallMember = onVideoCallMember
                        )
                    }

                    SogoBottomTab.PROFILE -> {
                        OwnProfileTab(
                            uiState = uiState,
                            rewardCredits = rewardCredits,
                            onEditProfile = { showEditProfileDialog = true },
                            onUpdateAvatarPhoto = { dashboardPhotoPicker.openChooser() },
                            onUpdateCoverPhoto = { viewModel.updateCoverPhoto(it) },
                            onAddGalleryPhoto = { viewModel.addGalleryPhoto(it) },
                            onDeleteGalleryPhoto = { viewModel.deleteGalleryPhoto(it) },
                            onRequestVerification = { viewModel.requestMemberVerification() },
                            onSaveDatingPreferences = { username, hobbies, lookingFor, prefGender, minAge, maxAge, prefCountry, hideDisc, hideOnline, hideAge, hideLoc ->
                                viewModel.updateDatingPreferencesAndPrivacy(
                                    username = username,
                                    hobbies = hobbies,
                                    lookingFor = lookingFor,
                                    preferredGender = prefGender,
                                    preferredMinAge = minAge,
                                    preferredMaxAge = maxAge,
                                    preferredCountry = prefCountry,
                                    hideFromDiscovery = hideDisc,
                                    hideOnlineStatus = hideOnline,
                                    hideAge = hideAge,
                                    hideLocation = hideLoc
                                )
                            },
                            onSelectMember = onSelectMember,
                            onOpenBlockedUsers = { showBlockedUsersDialog = true },
                            onWatchRewardedAd = onWatchRewardedAd,
                            onOpenOwnerAdmin = onOpenOwnerAdmin,
                            onDeleteAccount = { showDeleteAccountDialog = true },
                            onSignOut = {
                                viewModel.setPresence(false)
                                signOut(credentialManager, coroutineScope, onSignOut)
                            }
                        )
                    }

                    SogoBottomTab.HOME,
                    SogoBottomTab.POST -> {
                        HomeTabContent(
                            uiState = uiState,
                            onEditProfile = { showEditProfileDialog = true },
                            onOpenMembersTab = { activeBottomTab = SogoBottomTab.MEMBERS },
                            onOpenActivitiesTab = { activeBottomTab = SogoBottomTab.ACTIVITIES },
                            onOpenProfileTab = { activeBottomTab = SogoBottomTab.PROFILE },
                            onOpenConversationsInbox = { showConversationsInbox = true },
                            onUpdateSearchQuery = { viewModel.updateSearchQuery(it) },
                            onToggleOnlineOnly = { viewModel.toggleOnlineOnly() },
                            onToggleOfflineOnly = { viewModel.toggleOfflineOnly() },
                            onToggleFriendsOnly = { viewModel.toggleFriendsOnly() },
                            onSelectMember = onSelectMember,
                            onVideoCallMember = onVideoCallMember,
                            onSayHiMember = { viewModel.sendSayHi(it) },
                            onFriendAction = handleFriendAction,
                            onAcceptFriendRequest = { viewModel.acceptFriendRequest(it) },
                            onDeclineFriendRequest = { viewModel.removeFriendOrCancel(it) },
                            onReactActivity = { postId, emoji ->
                                viewModel.toggleActivityReaction(postId, emoji)
                            }
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
    onBlockMember: (UserProfile) -> Unit = {},
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
                            1 to "Online ($onlineCount)",
                            2 to "Offline ($offlineCount)"
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    if (idx == 1) {
                                        PresenceDot(isOnline = true, dotSize = 8.dp, showOnlineHalo = false)
                                        Spacer(modifier = Modifier.width(5.dp))
                                    } else if (idx == 2) {
                                        PresenceDot(isOnline = false, dotSize = 8.dp, showOnlineHalo = false)
                                        Spacer(modifier = Modifier.width(5.dp))
                                    }
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
                                        Spacer(modifier = Modifier.height(2.dp))
                                        MemberPresenceStatusRow(
                                            member = member,
                                            dotSize = 8.dp,
                                            fontSize = 11.sp,
                                            showOnlineHalo = true,
                                            modifier = Modifier.testTag("directory_status_${member.userId}")
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

                                    IconButton(
                                        onClick = { onBlockMember(member) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("directory_block_button_${member.userId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = "Block ${member.displayName}",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
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

@Composable
fun BlockedUsersDialog(
    blockedMembers: List<UserProfile>,
    myBlockedUserIds: Set<String>,
    onUnblock: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val profilesById = remember(blockedMembers) { blockedMembers.associateBy { it.userId } }
    val orderedIds = remember(blockedMembers, myBlockedUserIds) {
        (blockedMembers.map { it.userId } + myBlockedUserIds.sorted()).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = AlertRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Blocked Users (${orderedIds.size})",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (orderedIds.isEmpty()) {
                Text(
                    text = "You have not blocked any members. When you block someone, they will no longer appear in member discovery or be able to send you chat messages.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .testTag("blocked_users_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = orderedIds, key = { "blocked_$it" }) { blockedUid ->
                        val profile = profilesById[blockedUid]
                        val displayName = profile?.displayName ?: "Member"
                        val subtitle = profile?.let { "${it.city ?: "Manila"}, ${it.country}" } ?: "Restricted from discovery & chat"

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MatteBlackSurfaceVariant,
                            border = BorderStroke(1.dp, OutlineSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("blocked_user_row_$blockedUid")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(BurgundyDark)
                                        .border(1.dp, AlertRed.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = displayName.take(1).uppercase(),
                                        color = GoldLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        color = TextWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subtitle,
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { onUnblock(blockedUid, displayName) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("unblock_user_button_$blockedUid"),
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    border = BorderStroke(1.dp, GoldAccent)
                                ) {
                                    Text(
                                        text = "Unblock",
                                        color = GoldLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_blocked_users_button")
            ) {
                Text("Done", color = GoldLight, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MatteBlackSurface
    )
}

