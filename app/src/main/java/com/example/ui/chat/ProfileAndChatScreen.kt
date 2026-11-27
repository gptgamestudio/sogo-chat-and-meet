package com.example.ui.chat

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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.matching.MatchRecommender
import kotlinx.coroutines.launch
import com.example.data.model.ChatMessage
import com.example.data.model.FriendStatus
import com.example.data.model.GalleryPhoto
import com.example.data.model.UserProfile
import com.example.ui.common.AllEmojisPickerDialog
import com.example.ui.common.MemberPresenceFormatter
import com.example.ui.common.MemberPresenceStatusRow
import com.example.ui.common.SogoEmojiCatalog
import com.example.ui.profile.ProfilePhotoSourceDialog
import com.example.ui.profile.rememberProfilePhotoPickerController
import com.example.ui.safety.CommunitySafetyGuidelinesDialog
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
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAndChatScreen(
    partner: UserProfile,
    viewModel: ChatViewModel,
    myProfile: UserProfile? = null,
    friendStatus: FriendStatus = FriendStatus.NONE,
    isFollowing: Boolean = false,
    isFavorited: Boolean = false,
    isLiked: Boolean = false,
    onFriendActionClick: () -> Unit = {},
    onToggleFollow: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onSayHi: () -> Unit = {},
    onStartAudioCall: () -> Unit = {},
    onStartVideoCall: () -> Unit = {},
    onBack: () -> Unit
) {
    // Hardware and gesture Back Handler returning to Main Dashboard
    BackHandler { onBack() }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val partnerIsOnline = MemberPresenceFormatter.isEffectivelyOnline(partner)
    val chatPhotoPicker = rememberProfilePhotoPickerController { dataUri ->
        viewModel.sendImageMessage(dataUri)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEmojiDrawer by remember { mutableStateOf(false) }
    var showIcebreakers by remember { mutableStateOf(false) }
    var lightboxImageUrl by remember { mutableStateOf<String?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showSafetyGuidelinesDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Inappropriate content") }
    var reportDetails by remember { mutableStateOf("") }
    var pendingContentReportMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var contentReportReason by remember { mutableStateOf("Harassment or abusive language") }
    var contentReportDetails by remember { mutableStateOf("") }

    val showScrollToLatest by remember(uiState.messages.size) {
        derivedStateOf {
            val total = uiState.messages.size
            if (total <= 4) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: total
                lastVisible < total - 2
            }
        }
    }

    if (lightboxImageUrl != null) {
        Dialog(
            onDismissRequest = { lightboxImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { lightboxImageUrl = null }
                    .testTag("chat_photo_lightbox"),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = lightboxImageUrl,
                    contentDescription = "Full screen shared photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                IconButton(
                    onClick = { lightboxImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MatteBlackSurface.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close photo",
                        tint = GoldLight
                    )
                }
            }
        }
    }

    if (chatPhotoPicker.showSourceChooser) {
        ProfilePhotoSourceDialog(
            onDismiss = { chatPhotoPicker.dismissChooser() },
            onChooseGallery = { chatPhotoPicker.launchGalleryPicker() },
            onTakePhoto = { chatPhotoPicker.launchCameraCapture() }
        )
    }

    LaunchedEffect(partner.userId, myProfile?.userId) {
        viewModel.initializeChat(partner, myProfile)
    }

    // Scroll to newest message when messages change
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    if (showSafetyGuidelinesDialog) {
        CommunitySafetyGuidelinesDialog(
            onDismiss = { showSafetyGuidelinesDialog = false },
            onReportMemberInChat = {
                showSafetyGuidelinesDialog = false
                showReportDialog = true
            }
        )
    }

    // Block Confirmation Dialog
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text("Block ${partner.displayName}?", color = TextWhite) },
            text = {
                Text(
                    "You will no longer receive messages or see each other's profile on SOGO CHAT. This member will be blocked immediately.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmDialog = false
                        viewModel.blockCurrentMember {
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Block Member", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    // Report Dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report Member", color = TextWhite) },
            text = {
                Column {
                    Text("Why are you reporting this user?", color = TextMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf(
                        "Inappropriate messages or behavior",
                        "Scam or solicitation",
                        "Fake profile or impersonation",
                        "Under 18 years old",
                        "Harassment or hate speech"
                    ).forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportReason = r }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (reportReason == r) Icons.Default.Check else Icons.Default.Report,
                                contentDescription = null,
                                tint = if (reportReason == r) GoldAccent else TextDim,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(r, color = if (reportReason == r) GoldLight else TextWhite, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it },
                        placeholder = { Text("Additional details...", color = TextDim, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
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
                    onClick = {
                        showReportDialog = false
                        viewModel.reportCurrentMember(reportReason, reportDetails)
                        reportDetails = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary)
                ) {
                    Text("Submit Report", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    // Report Confirmation Dialog
    if (uiState.reportSubmitted) {
        AlertDialog(
            onDismissRequest = { viewModel.clearReportSubmitted() },
            title = { Text("Report Submitted", color = GoldLight) },
            text = {
                Text(
                    "Thank you for helping keep SOGO CHAT safe. Your report about ${partner.displayName} has been recorded for review. You can also block this member immediately.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearReportSubmitted()
                        showBlockConfirmDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    modifier = Modifier.testTag("report_submitted_block_button")
                ) {
                    Text("Block Member", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearReportSubmitted() },
                    modifier = Modifier.testTag("report_submitted_done_button")
                ) {
                    Text("Done", color = GoldLight)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    // Report Content (Message Moderation) Dialog
    pendingContentReportMessage?.let { flaggedMessage ->
        val partnerMessages = remember(uiState.messages, partner.userId) {
            uiState.messages.filter { it.senderId == partner.userId }.takeLast(5).reversed()
        }
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSubmittingContentReport) {
                    pendingContentReportMessage = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report Content for Moderation", color = TextWhite, fontSize = 17.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Flagged message (${flaggedMessage.type}):",
                        color = GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MatteBlackSurfaceVariant,
                        border = BorderStroke(1.dp, OutlineSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = flaggedMessage.content.ifBlank { "[${flaggedMessage.type}]" },
                            color = TextWhite,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (partnerMessages.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Switch flagged message:",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            partnerMessages.forEach { candidate ->
                                val isSelected = candidate.messageId == flaggedMessage.messageId
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) BurgundyPrimary else MatteBlackSurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) GoldAccent else OutlineSubtle),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { pendingContentReportMessage = candidate }
                                ) {
                                    Text(
                                        text = candidate.content.take(18).ifBlank { candidate.type },
                                        color = if (isSelected) GoldLight else TextMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Reason for moderation review:", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    listOf(
                        "Harassment or abusive language",
                        "Explicit or unwanted sexual content",
                        "Scam, phishing, or solicitation",
                        "Hate speech or threats",
                        "Spam or impersonation"
                    ).forEach { reasonOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { contentReportReason = reasonOption }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (contentReportReason == reasonOption) Icons.Default.Check else Icons.Default.Flag,
                                contentDescription = null,
                                tint = if (contentReportReason == reasonOption) GoldAccent else TextDim,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = reasonOption,
                                color = if (contentReportReason == reasonOption) GoldLight else TextWhite,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contentReportDetails,
                        onValueChange = { contentReportDetails = it },
                        placeholder = { Text("Optional context for moderation team...", color = TextDim, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .testTag("content_report_details_input"),
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
                    enabled = !uiState.isSubmittingContentReport,
                    onClick = {
                        val targetMsg = flaggedMessage
                        val r = contentReportReason
                        val d = contentReportDetails
                        pendingContentReportMessage = null
                        contentReportDetails = ""
                        viewModel.reportMessageContent(targetMsg, r, d)
                    },
                    modifier = Modifier.testTag("submit_content_report_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary)
                ) {
                    Text("Submit Report", color = GoldLight, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isSubmittingContentReport,
                    onClick = { pendingContentReportMessage = null }
                ) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    if (uiState.contentReportSubmitted) {
        AlertDialog(
            onDismissRequest = { viewModel.clearContentReportSubmitted() },
            title = { Text("Content Flagged for Review", color = GoldLight) },
            text = {
                Text(
                    text = "Thank you for reporting this message. It has been queued in the content_reports collection for review by the moderation team.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearContentReportSubmitted()
                        showBlockConfirmDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    modifier = Modifier.testTag("content_report_block_button")
                ) {
                    Text("Block Member", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearContentReportSubmitted() },
                    modifier = Modifier.testTag("content_report_done_button")
                ) {
                    Text("Done", color = GoldLight)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Main Dashboard",
                            tint = GoldLight
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedTab = if (selectedTab == 0) 1 else 0 }
                    ) {
                        // Partner Avatar
                        Box(modifier = Modifier.size(40.dp)) {
                            if (!partner.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(partner.photoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = partner.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(BurgundyPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        partner.displayName.take(1).uppercase(),
                                        color = GoldLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(if (partnerIsOnline) OnlineGreen else TextDim)
                                    .border(1.5.dp, MatteBlackSurface, CircleShape)
                                    .align(Alignment.BottomEnd)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (partner.hideAge) partner.displayName else "${partner.displayName}, ${partner.age}",
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle Profile",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            MemberPresenceStatusRow(
                                member = partner,
                                dotSize = 7.dp,
                                fontSize = 11.sp,
                                showOnlineHalo = true
                            )
                        }
                    }
                },
                actions = {
                    // Free Audio Call button
                    IconButton(
                        onClick = onStartAudioCall,
                        enabled = !uiState.isBlocked,
                        modifier = Modifier.testTag("audio_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Free Voice Call",
                            tint = if (uiState.isBlocked) TextDim else GoldLight
                        )
                    }

                    // Free Video Call button
                    IconButton(
                        onClick = onStartVideoCall,
                        enabled = !uiState.isBlocked,
                        modifier = Modifier.testTag("video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Free Video Call",
                            tint = if (uiState.isBlocked) TextDim else OnlineGreen
                        )
                    }

                    // Overflow Menu
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("chat_options_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = TextMuted
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(MatteBlackSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Full Profile", color = TextWhite) },
                            onClick = {
                                showOverflowMenu = false
                                selectedTab = 0
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open Private Chat", color = TextWhite) },
                            onClick = {
                                showOverflowMenu = false
                                selectedTab = 1
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Community Safety Guidelines", color = GoldLight) },
                            modifier = Modifier.testTag("chat_menu_safety_guidelines"),
                            onClick = {
                                showOverflowMenu = false
                                showSafetyGuidelinesDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Report Member", color = GoldAccent) },
                            onClick = {
                                showOverflowMenu = false
                                showReportDialog = true
                            }
                        )
                        val latestPartnerMsgForMenu = uiState.messages.lastOrNull { it.senderId == partner.userId }
                        if (latestPartnerMsgForMenu != null) {
                            DropdownMenuItem(
                                text = { Text("Report Content", color = AlertRed) },
                                modifier = Modifier.testTag("menu_report_content"),
                                onClick = {
                                    showOverflowMenu = false
                                    selectedTab = 1
                                    pendingContentReportMessage = latestPartnerMsgForMenu
                                }
                            )
                        }
                        if (uiState.isBlockedByMe) {
                            DropdownMenuItem(
                                text = { Text("Unblock Member", color = OnlineGreen) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.unblockCurrentMember()
                                }
                            )
                        } else if (!uiState.isBlocked) {
                            DropdownMenuItem(
                                text = { Text("Block Member", color = AlertRed) },
                                onClick = {
                                    showOverflowMenu = false
                                    showBlockConfirmDialog = true
                                }
                            )
                        }
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
                .imePadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Screen 2 Segmented Navigation: Full Profile vs Private Chat
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MatteBlackSurface,
                contentColor = GoldLight,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = GoldLight
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "👤 Member Profile",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) GoldLight else TextMuted,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "💬 Private Chat",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) GoldLight else TextMuted,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                MemberProfileDetailsView(
                    partner = partner,
                    myProfile = myProfile,
                    partnerGalleryPhotos = uiState.partnerGalleryPhotos,
                    friendStatus = friendStatus,
                    isFollowing = isFollowing,
                    isFavorited = isFavorited,
                    isLiked = isLiked,
                    isBlocked = uiState.isBlocked,
                    isBlockedByMe = uiState.isBlockedByMe,
                    onFriendActionClick = onFriendActionClick,
                    onToggleFollow = onToggleFollow,
                    onToggleFavorite = onToggleFavorite,
                    onToggleLike = onToggleLike,
                    onSayHi = onSayHi,
                    onStartVideoCall = onStartVideoCall,
                    onStartChat = { selectedTab = 1 },
                    onBlock = { showBlockConfirmDialog = true },
                    onUnblock = { viewModel.unblockCurrentMember() },
                    onReport = { showReportDialog = true }
                )
            } else {

            AnimatedVisibility(visible = uiState.isBlocked) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("blocked_chat_banner"),
                    color = Color(0xFF3A1219),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Blocked",
                            tint = AlertRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Incoming messages from ${partner.displayName} are restricted while blocked.",
                            color = TextWhite,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.isBlockedByMe) {
                            TextButton(
                                onClick = { viewModel.unblockCurrentMember() },
                                modifier = Modifier.testTag("chat_unblock_button")
                            ) {
                                Text("Unblock", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.errorMessage != null && !uiState.isBlocked) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("chat_error_banner"),
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

            val latestPartnerMsg = remember(uiState.messages, partner.userId) {
                uiState.messages.lastOrNull { it.senderId == partner.userId }
            }
            if (!uiState.isBlocked) {
                Surface(
                    color = MatteBlackSurfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable { showSafetyGuidelinesDialog = true }
                                .padding(end = 8.dp)
                                .testTag("chat_safety_tips_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "View Community Safety Guidelines",
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Moderation protection active",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap for Safety Guidelines & Tips",
                                    color = GoldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (latestPartnerMsg != null) {
                                    pendingContentReportMessage = latestPartnerMsg
                                } else {
                                    showReportDialog = true
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("chat_report_content_button"),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Report Content",
                                tint = AlertRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Report Content",
                                color = GoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            val vibeMatch = remember(myProfile, partner) {
                MatchRecommender.scoreCandidate(myProfile, partner)
            }
            if (!uiState.isBlocked && myProfile != null) {
                val vibePercent = ((vibeMatch.matchScore.coerceAtLeast(15) * 100) / 100).coerceIn(55, 99)
                val mutualLabel = if (vibeMatch.mutualInterests.isNotEmpty()) {
                    "Shared: ${vibeMatch.mutualInterests.take(3).joinToString(", ")}"
                } else {
                    partner.lookingFor?.takeIf { it.isNotBlank() } ?: "Say hi to spark a conversation"
                }
                Surface(
                    color = BurgundyDark.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("chat_compatibility_strip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "💘 $vibePercent% Match • $mutualLabel",
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (showIcebreakers) BurgundyPrimary else MatteBlackSurfaceVariant,
                            border = BorderStroke(1.dp, OutlineGold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showIcebreakers = !showIcebreakers }
                                .testTag("chat_icebreakers_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Dating Icebreakers",
                                    tint = GoldLight,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Icebreakers",
                                    color = GoldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time Messages Feed with Scroll-to-Latest FAB
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.isBlocked) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp)
                                    .testTag("blocked_chat_empty_state"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF3A1219),
                                        modifier = Modifier.size(60.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = null,
                                            tint = AlertRed,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "User Blocked",
                                        color = TextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Incoming chat messages and profile discovery for ${partner.displayName} are blocked in Firestore.",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        if (uiState.messages.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MatteBlackSurfaceVariant,
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = GoldAccent,
                                                modifier = Modifier.padding(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Private End-to-End Chat",
                                            color = TextWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Say kumusta to ${partner.displayName}! Messages are private and securely stored.",
                                            color = TextMuted,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        val currentUid = viewModel.getCurrentUserId()
                        items(items = uiState.messages, key = { it.messageId }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                isFromMe = msg.senderId != partner.userId,
                                currentUserId = currentUid,
                                onImageClick = { url -> lightboxImageUrl = url },
                                onToggleReaction = { emoji ->
                                    viewModel.toggleMessageReaction(msg.messageId, emoji)
                                },
                                onReportContent = { flagged ->
                                    pendingContentReportMessage = flagged
                                }
                            )
                        }
                    }
                }

                if (showScrollToLatest && !uiState.isBlocked && uiState.messages.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = BurgundyPrimary,
                        border = BorderStroke(1.dp, GoldAccent),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem((uiState.messages.size - 1).coerceAtLeast(0))
                                }
                            }
                            .testTag("chat_scroll_to_latest_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to latest messages",
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Latest",
                                color = GoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (uiState.isBlocked) {
                Surface(
                    color = MatteBlackSurface,
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_chat_footer")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Messaging is disabled while this user is blocked.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                AnimatedVisibility(visible = uiState.isPartnerTyping) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("chat_typing_indicator"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MatteBlackSurfaceVariant,
                            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${partner.displayName} is typing...",
                                    color = GoldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showIcebreakers) {
                    DatingIcebreakersBar(
                        partnerName = partner.displayName,
                        onSelectPrompt = { prompt ->
                            viewModel.onInputTextChanged(prompt)
                            showIcebreakers = false
                        }
                    )
                }

                // Quick Dating Stickers & Emojis Bar
                QuickStickersBar(
                    onStickerSelected = { sticker -> viewModel.sendSticker(sticker) }
                )

                // Chat Input Bar with Emoji Picker Toggle & Photo Attachment
                ChatInputBar(
                    inputText = uiState.inputText,
                    isEmojiDrawerOpen = showEmojiDrawer,
                    isSending = uiState.isSending,
                    onToggleEmojiDrawer = { showEmojiDrawer = !showEmojiDrawer },
                    onAttachPhoto = { chatPhotoPicker.openChooser() },
                    onTextChanged = { viewModel.onInputTextChanged(it) },
                    onSend = {
                        showEmojiDrawer = false
                        viewModel.sendTextMessage()
                    }
                )

                AnimatedVisibility(visible = showEmojiDrawer) {
                    ChatEmojiPickerDrawer(
                        onEmojiSelected = { emoji ->
                            viewModel.onInputTextChanged(uiState.inputText + emoji)
                        }
                    )
                }
            }

            val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            if (!imeOpen) {
                // Keep Chat Input Bar & Emoji Drawer clear above MovableBottomAdBanner
                Spacer(modifier = Modifier.height(78.dp))
            }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberProfileDetailsView(
    partner: UserProfile,
    myProfile: UserProfile? = null,
    partnerGalleryPhotos: List<GalleryPhoto> = emptyList(),
    friendStatus: FriendStatus = FriendStatus.NONE,
    isFollowing: Boolean = false,
    isFavorited: Boolean = false,
    isLiked: Boolean = false,
    isBlocked: Boolean = false,
    isBlockedByMe: Boolean = false,
    onFriendActionClick: () -> Unit = {},
    onToggleFollow: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onSayHi: () -> Unit = {},
    onStartVideoCall: () -> Unit = {},
    onStartChat: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit = {},
    onReport: () -> Unit
) {
    val partnerIsOnline = MemberPresenceFormatter.isEffectivelyOnline(partner)
    val mutualInterests = remember(myProfile, partner) {
        MatchRecommender.computeMutualInterests(myProfile, partner)
    }
    val allTags = remember(partner) {
        (partner.interests + partner.hobbies).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Photo Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(OutlineGold, BurgundyPrimary))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!partner.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(partner.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${partner.displayName}'s photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(listOf(BurgundyPrimary, Color(0xFF1A050B)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = partner.displayName.take(1).uppercase(),
                            color = GoldLight,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, MatteBlackCard.copy(alpha = 0.95f))
                            )
                        )
                )

                // Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BurgundyDark.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "18+ COMMUNITY",
                                color = GoldLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MatteBlackSurface.copy(alpha = 0.9f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(
                                if (partnerIsOnline) listOf(OnlineGreen, Color(0xFF00796B))
                                else listOf(OutlineSubtle, OutlineSubtle)
                            )
                        )
                    ) {
                        MemberPresenceStatusRow(
                            member = partner,
                            dotSize = 9.dp,
                            fontSize = 11.sp,
                            showOnlineHalo = true,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Name, Username, Looking For & Location Banner
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = partner.displayName,
                            color = TextWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!partner.hideAge) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${partner.age}",
                                color = GoldLight,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (!partner.username.isNullOrBlank()) {
                        Text(
                            text = "@${partner.username.removePrefix("@")}",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (partner.hideLocation) "Location hidden" else "${partner.city ?: "Manila"}, ${partner.country}",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${partner.gender}",
                            color = TextDim,
                            fontSize = 13.sp
                        )
                    }
                    if (!partner.lookingFor.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Looking for: ${partner.lookingFor}",
                            color = GoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions Row: Add Friend & Free Video Call
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val (friendLabel, friendIcon) = when (friendStatus) {
                FriendStatus.NONE -> "Add Friend" to Icons.Default.PersonAdd
                FriendStatus.REQUEST_SENT -> "Request Sent" to Icons.Default.HourglassTop
                FriendStatus.REQUEST_RECEIVED -> "Accept Friend" to Icons.Default.Check
                FriendStatus.FRIENDS -> "Friends" to Icons.Default.HowToReg
            }
            OutlinedButton(
                onClick = onFriendActionClick,
                enabled = !isBlocked,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("profile_add_friend_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MatteBlackSurface,
                    contentColor = if (friendStatus == FriendStatus.REQUEST_RECEIVED) OnlineGreen else GoldLight,
                    disabledContainerColor = MatteBlackSurfaceVariant,
                    disabledContentColor = TextDim
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        isBlocked -> OutlineSubtle
                        friendStatus == FriendStatus.REQUEST_RECEIVED -> OnlineGreen
                        else -> OutlineGold
                    }
                )
            ) {
                Icon(imageVector = friendIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (isBlocked) "Blocked" else friendLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onStartVideoCall,
                enabled = !isBlocked,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("profile_video_call_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnlineGreen.copy(alpha = 0.2f),
                    contentColor = OnlineGreen,
                    disabledContainerColor = MatteBlackSurfaceVariant,
                    disabledContentColor = TextDim
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBlocked) OutlineSubtle else OnlineGreen
                )
            ) {
                Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Free Video Call", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Social Engagement Row: Like, Follow, Favorite, Say Hi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onToggleLike,
                enabled = !isBlocked,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("profile_like_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isLiked) BurgundyPrimary else MatteBlackSurface,
                    contentColor = if (isLiked) GoldLight else TextWhite
                ),
                border = BorderStroke(1.dp, if (isLiked) GoldAccent else OutlineSubtle)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like profile",
                    tint = if (isLiked) Color(0xFFFF4D6D) else GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isLiked) "Liked" else "Like", fontSize = 11.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = onToggleFollow,
                enabled = !isBlocked,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("profile_follow_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isFollowing) BurgundyDark else MatteBlackSurface,
                    contentColor = if (isFollowing) GoldLight else TextWhite
                ),
                border = BorderStroke(1.dp, if (isFollowing) GoldAccent else OutlineSubtle)
            ) {
                Icon(
                    imageVector = if (isFollowing) Icons.Default.HowToReg else Icons.Default.PersonOutline,
                    contentDescription = "Follow member",
                    tint = GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isFollowing) "Following" else "Follow", fontSize = 11.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = onToggleFavorite,
                enabled = !isBlocked,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("profile_favorite_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isFavorited) BurgundyDark else MatteBlackSurface,
                    contentColor = if (isFavorited) GoldLight else TextWhite
                ),
                border = BorderStroke(1.dp, if (isFavorited) GoldAccent else OutlineSubtle)
            ) {
                Icon(
                    imageVector = if (isFavorited) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Favorite member",
                    tint = GoldLight,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isFavorited) "Saved" else "Fav", fontSize = 11.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = onSayHi,
                enabled = !isBlocked,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("profile_say_hi_button"),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MatteBlackSurface,
                    contentColor = OnlineGreen
                ),
                border = BorderStroke(1.dp, if (isBlocked) OutlineSubtle else OnlineGreen.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = Icons.Default.WavingHand,
                    contentDescription = "Say Hi",
                    tint = if (isBlocked) TextDim else OnlineGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hi!", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Start Private Conversation Button (Requirement 5)
        Button(
            onClick = onStartChat,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_private_conversation_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBlocked) Color(0xFF3A1219) else BurgundyPrimary,
                contentColor = TextWhite
            ),
            shape = RoundedCornerShape(26.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(
                    if (isBlocked) listOf(AlertRed, AlertRed) else listOf(GoldAccent, GoldLight)
                )
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.Chat,
                    contentDescription = null,
                    tint = if (isBlocked) AlertRed else GoldLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isBlocked) "Chat Restricted (Blocked)" else "Chat Now",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }
        }

        if (partnerGalleryPhotos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MatteBlackSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PHOTO GALLERY (${partnerGalleryPhotos.size})",
                        color = GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items = partnerGalleryPhotos, key = { it.photoId.ifBlank { it.id } }) { item ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(item.photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.caption ?: "Member gallery photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, OutlineGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Personality Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MatteBlackSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PERSONALITY",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BurgundyDark.copy(alpha = 0.8f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(OutlineGold, GoldAccent))
                    )
                ) {
                    Text(
                        text = "✨ ${partner.personality?.ifBlank { "Romantic & Friendly" } ?: "Romantic & Friendly"}",
                        color = GoldLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // About Me Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MatteBlackSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ABOUT ME",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (!partner.aboutMe.isNullOrBlank()) partner.aboutMe else "No bio provided yet.",
                    color = TextWhite,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Interests, Hobbies & Mutual Matches Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MatteBlackSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "INTERESTS, HOBBIES & PASSIONS",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (mutualInterests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "✨ ${mutualInterests.size} Mutual Interest(s): ${mutualInterests.joinToString(", ")}",
                        color = OnlineGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (allTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allTags.forEach { tag ->
                            val isMutual = mutualInterests.any { it.equals(tag, ignoreCase = true) }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isMutual) OnlineGreen.copy(alpha = 0.14f) else MatteBlackSurfaceVariant,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.horizontalGradient(
                                        if (isMutual) listOf(OnlineGreen, GoldAccent)
                                        else listOf(OutlineSubtle, OutlineGold)
                                    )
                                )
                            ) {
                                Text(
                                    text = tag,
                                    color = if (isMutual) OnlineGreen else TextWhite,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No interests listed.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Safety note & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReport) {
                Text("Report Member", color = GoldAccent, fontSize = 12.sp)
            }
            if (isBlockedByMe) {
                TextButton(
                    onClick = onUnblock,
                    modifier = Modifier.testTag("profile_unblock_button")
                ) {
                    Text("Unblock Member", color = OnlineGreen, fontSize = 12.sp)
                }
            } else if (!isBlocked) {
                TextButton(
                    onClick = onBlock,
                    modifier = Modifier.testTag("profile_block_button")
                ) {
                    Text("Block Member", color = AlertRed, fontSize = 12.sp)
                }
            } else {
                Text("Blocked", color = AlertRed, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isFromMe: Boolean,
    currentUserId: String? = null,
    onImageClick: (String) -> Unit = {},
    onToggleReaction: (String) -> Unit = {},
    onReportContent: (ChatMessage) -> Unit = {}
) {
    val bubbleColor = if (isFromMe) BurgundyPrimary else MatteBlackCard
    val shape = if (isFromMe) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = message.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Just now"
    var showReactionBar by remember { mutableStateOf(false) }
    var showAllReactionsDialog by remember { mutableStateOf(false) }

    val myReaction = if (!currentUserId.isNullOrBlank()) message.reactions[currentUserId] else null

    if (showAllReactionsDialog) {
        AllEmojisPickerDialog(
            title = "React to Message",
            selectedEmoji = myReaction,
            onDismiss = { showAllReactionsDialog = false },
            onEmojiSelected = { emoji ->
                showReactionBar = false
                onToggleReaction(emoji)
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
        ) {
            if (isFromMe) {
                IconButton(
                    onClick = { showReactionBar = !showReactionBar },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("react_message_button_${message.messageId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddReaction,
                        contentDescription = "React with emoji",
                        tint = if (myReaction != null) GoldAccent else TextDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                shape = shape,
                color = bubbleColor,
                border = if (isFromMe) CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(BurgundyLight, GoldAccent))) else CardDefaults.outlinedCardBorder(),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clickable { showReactionBar = !showReactionBar }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    when (message.type) {
                        "IMAGE" -> {
                            val imageUrl = message.mediaUrl
                            if (!imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Shared image (tap to view full screen)",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onImageClick(imageUrl) },
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            if (message.content.isNotBlank()) {
                                Text(text = message.content, color = TextWhite, fontSize = 14.sp)
                            }
                        }
                        "VOICE" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isFromMe) GoldAccent else BurgundyPrimary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play voice note",
                                        tint = MatteBlackBackground,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    // Waveform bars visual
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(14, 22, 10, 28, 18, 24, 12, 20, 16).forEach { h ->
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(h.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (isFromMe) GoldLight else BurgundyLight)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = message.content,
                                        color = if (isFromMe) GoldLight else TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        "STICKER" -> {
                            Text(
                                text = message.content,
                                fontSize = 24.sp,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = message.content,
                                color = TextWhite,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Timestamp & Read Receipt Checkmarks
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedTime,
                            color = if (isFromMe) GoldLight.copy(alpha = 0.8f) else TextDim,
                            fontSize = 10.sp
                        )
                        if (isFromMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Check,
                                contentDescription = if (message.isRead) "Read" else "Sent",
                                tint = if (message.isRead) GoldLight else TextDim,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            if (!isFromMe) {
                IconButton(
                    onClick = { showReactionBar = !showReactionBar },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("react_message_button_${message.messageId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddReaction,
                        contentDescription = "React with emoji",
                        tint = if (myReaction != null) GoldAccent else TextDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onReportContent(message) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("report_message_button_${message.messageId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Report Content",
                        tint = AlertRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Displayed Message Reactions Row
        if (message.reactions.isNotEmpty()) {
            val grouped = message.reactions.values.groupingBy { it }.eachCount()
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (emoji, count) ->
                    val isMine = myReaction == emoji
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMine) BurgundyPrimary else MatteBlackSurfaceVariant,
                        border = BorderStroke(1.dp, if (isMine) GoldAccent else OutlineSubtle),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleReaction(emoji) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = emoji, fontSize = 13.sp)
                            if (count > 1) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$count",
                                    color = GoldLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expandable Quick Reaction Selector Bar
        AnimatedVisibility(visible = showReactionBar) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MatteBlackSurface,
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.55f)),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("👍", "❤️", "😂", "😍", "🔥", "😢").forEach { emoji ->
                        val isMine = myReaction == emoji
                        Surface(
                            shape = CircleShape,
                            color = if (isMine) BurgundyPrimary else MatteBlackSurfaceVariant,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable {
                                    showReactionBar = false
                                    onToggleReaction(emoji)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MatteBlackSurfaceVariant,
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showAllReactionsDialog = true }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "+ All", color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!isFromMe) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF3A1219),
                            border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    showReactionBar = false
                                    onReportContent(message)
                                }
                                .testTag("report_content_chip_${message.messageId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = AlertRed,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Report Content",
                                    color = TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
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
fun DatingIcebreakersBar(
    partnerName: String,
    onSelectPrompt: (String) -> Unit
) {
    val firstName = partnerName.trim().substringBefore(" ").ifBlank { "there" }
    val prompts = listOf(
        "Kumusta $firstName! Coffee or milktea tayo soon? ☕",
        "What's your ideal weekend date? ✨",
        "Anong paborito mong pagkain or hangout spot? 🍽️",
        "May free time ka ba mamaya for a quick video call? 📹",
        "Favorite song or movie mo lately? 🎬"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurgundyDark.copy(alpha = 0.55f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("chat_icebreakers_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEach { prompt ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MatteBlackSurfaceVariant,
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.65f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectPrompt(prompt) }
            ) {
                Text(
                    text = prompt,
                    color = GoldLight,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun QuickStickersBar(
    onStickerSelected: (String) -> Unit
) {
    val datingStickers = listOf(
        "Kumusta! 👋",
        "Tara kape? ☕",
        "Ingat lagi ❤️",
        "Ganda mo ✨",
        "Kain tayo! 🍲",
        "Good night 🌙",
        "You make me smile 😊",
        "👍",
        "❤️",
        "😂",
        "😍",
        "🔥",
        "🎉"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MatteBlackSurface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        datingStickers.forEach { sticker ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onStickerSelected(sticker) },
                shape = RoundedCornerShape(16.dp),
                color = MatteBlackSurfaceVariant,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(OutlineSubtle, OutlineGold)))
            ) {
                Text(
                    text = sticker,
                    color = GoldLight,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    isEmojiDrawerOpen: Boolean = false,
    isSending: Boolean = false,
    onToggleEmojiDrawer: () -> Unit = {},
    onAttachPhoto: () -> Unit = {},
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MatteBlackSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleEmojiDrawer,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_emoji_picker_button")
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEmotions,
                    contentDescription = if (isEmojiDrawerOpen) "Hide emojis" else "Open emoji picker",
                    tint = if (isEmojiDrawerOpen) GoldLight else GoldAccent
                )
            }

            IconButton(
                onClick = onAttachPhoto,
                enabled = !isSending,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_attach_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Send Photo",
                    tint = if (isSending) TextDim else GoldAccent
                )
            }

            // Text Input Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = { Text("Type a private message...", color = TextDim, fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("chat_message_input"),
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

            Spacer(modifier = Modifier.width(8.dp))

            // Send Button
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = inputText.isNotBlank() && !isSending, onClick = onSend)
                    .testTag("chat_send_button"),
                color = if (inputText.isNotBlank() && !isSending) BurgundyPrimary else Color(0xFF2C2A33),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = if (inputText.isNotBlank() && !isSending) GoldLight else TextDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatEmojiPickerDrawer(
    onEmojiSelected: (String) -> Unit
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = SogoEmojiCatalog.categories
    val activeCategory = categories.getOrElse(selectedCategory) { categories.first() }

    Surface(
        color = MatteBlackSurface,
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_emoji_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { index, category ->
                    val isCurrent = index == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) BurgundyPrimary else MatteBlackSurfaceVariant,
                        border = BorderStroke(1.dp, if (isCurrent) GoldAccent else OutlineSubtle),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedCategory = index }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = category.icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = category.title,
                                color = if (isCurrent) GoldLight else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeCategory.emojis.forEach { emoji ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MatteBlackSurfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onEmojiSelected(emoji) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
