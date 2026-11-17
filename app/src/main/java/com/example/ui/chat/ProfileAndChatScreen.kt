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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChatMessage
import com.example.data.model.FriendStatus
import com.example.data.model.UserProfile
import com.example.ui.common.AllEmojisPickerDialog
import com.example.ui.common.SogoEmojiCatalog
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
    friendStatus: FriendStatus = FriendStatus.NONE,
    onFriendActionClick: () -> Unit = {},
    onStartAudioCall: () -> Unit = {},
    onStartVideoCall: () -> Unit = {},
    onBack: () -> Unit
) {
    // Hardware and gesture Back Handler returning to Main Dashboard
    BackHandler { onBack() }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showEmojiDrawer by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Inappropriate content") }
    var reportDetails by remember { mutableStateOf("") }

    LaunchedEffect(partner.userId) {
        viewModel.initializeChat(partner)
    }

    // Scroll to newest message when messages change
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
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
                            if (partner.isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                        .border(1.dp, MatteBlackSurface, CircleShape)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${partner.displayName}, ${partner.age}",
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
                            Text(
                                text = if (partner.isOnline) "Active Now" else "Last seen recently",
                                color = if (partner.isOnline) OnlineGreen else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Free Audio Call button
                    IconButton(
                        onClick = onStartAudioCall,
                        modifier = Modifier.testTag("audio_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Free Voice Call",
                            tint = GoldLight
                        )
                    }

                    // Free Video Call button
                    IconButton(
                        onClick = onStartVideoCall,
                        modifier = Modifier.testTag("video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Free Video Call",
                            tint = OnlineGreen
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
                            text = { Text("Report Member", color = GoldAccent) },
                            onClick = {
                                showOverflowMenu = false
                                showReportDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Block Member", color = AlertRed) },
                            onClick = {
                                showOverflowMenu = false
                                showBlockConfirmDialog = true
                            }
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
                    friendStatus = friendStatus,
                    onFriendActionClick = onFriendActionClick,
                    onStartVideoCall = onStartVideoCall,
                    onStartChat = { selectedTab = 1 },
                    onBlock = { showBlockConfirmDialog = true },
                    onReport = { showReportDialog = true }
                )
            } else {

            AnimatedVisibility(visible = uiState.errorMessage != null) {
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

            // Real-Time Messages Feed
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        onToggleReaction = { emoji ->
                            viewModel.toggleMessageReaction(msg.messageId, emoji)
                        }
                    )
                }
            }

            // Quick Dating Stickers & Emojis Bar
            QuickStickersBar(
                onStickerSelected = { sticker -> viewModel.sendSticker(sticker) }
            )

            // Chat Input Bar with Emoji Picker Toggle
            ChatInputBar(
                inputText = uiState.inputText,
                isEmojiDrawerOpen = showEmojiDrawer,
                onToggleEmojiDrawer = { showEmojiDrawer = !showEmojiDrawer },
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
    friendStatus: FriendStatus = FriendStatus.NONE,
    onFriendActionClick: () -> Unit = {},
    onStartVideoCall: () -> Unit = {},
    onStartChat: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
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
                                text = "18+ VERIFIED",
                                color = GoldLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MatteBlackSurface.copy(alpha = 0.9f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(
                                if (partner.isOnline) listOf(OnlineGreen, Color(0xFF00796B))
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
                                    .background(if (partner.isOnline) OnlineGreen else TextDim)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (partner.isOnline) "Active Now" else "Offline",
                                color = if (partner.isOnline) OnlineGreen else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Name & Location Banner
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = partner.displayName,
                            color = TextWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${partner.age}",
                            color = GoldLight,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
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
                            text = "${partner.city ?: "Manila"}, ${partner.country}",
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
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("profile_add_friend_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MatteBlackSurface,
                    contentColor = if (friendStatus == FriendStatus.REQUEST_RECEIVED) OnlineGreen else GoldLight
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (friendStatus == FriendStatus.REQUEST_RECEIVED) OnlineGreen else OutlineGold
                )
            ) {
                Icon(imageVector = friendIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = friendLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onStartVideoCall,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("profile_video_call_button"),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnlineGreen.copy(alpha = 0.2f),
                    contentColor = OnlineGreen
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen)
            ) {
                Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Free Video Call", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                containerColor = BurgundyPrimary,
                contentColor = TextWhite
            ),
            shape = RoundedCornerShape(26.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(GoldAccent, GoldLight))
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = GoldLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Chat Now",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
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

        // Interests Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MatteBlackSurface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "INTERESTS & PASSIONS",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (partner.interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        partner.interests.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MatteBlackSurfaceVariant,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.horizontalGradient(listOf(OutlineSubtle, OutlineGold))
                                )
                            ) {
                                Text(
                                    text = tag,
                                    color = TextWhite,
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
            TextButton(onClick = onBlock) {
                Text("Block Member", color = AlertRed, fontSize = 12.sp)
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
    onToggleReaction: (String) -> Unit = {}
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
                            if (!message.mediaUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Shared image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp)),
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
                }
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
    onToggleEmojiDrawer: () -> Unit = {},
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                    .clickable(enabled = inputText.isNotBlank(), onClick = onSend)
                    .testTag("chat_send_button"),
                color = if (inputText.isNotBlank()) BurgundyPrimary else Color(0xFF2C2A33),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = if (inputText.isNotBlank()) GoldLight else TextDim,
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
