package com.example.ui.safety

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BurgundyContainer
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldContainer
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

enum class SafetyCategory(val label: String) {
    ALL("All"),
    COMMUNITY_STANDARDS("Standards"),
    PRIVATE_INTERACTIONS("Private Chat & Calls"),
    REPORTING_TOOLS("Reporting & Blocking"),
    IN_PERSON_SAFETY("Meetup Tips")
}

enum class SafetyActionTarget {
    PRIVACY_SETTINGS,
    BLOCKED_USERS,
    REPORT_IN_CHAT
}

data class SafetyGuidelineItem(
    val id: String,
    val category: SafetyCategory,
    val badge: String,
    val title: String,
    val summary: String,
    val details: List<String>,
    val accentColor: Color,
    val icon: ImageVector,
    val actionTarget: SafetyActionTarget? = null
)

object CommunitySafetyCatalog {
    val guidelines: List<SafetyGuidelineItem> = listOf(
        SafetyGuidelineItem(
            id = "adults_only",
            category = SafetyCategory.COMMUNITY_STANDARDS,
            badge = "18+ REQUIREMENT",
            title = "Authentic, Adult-Only Community",
            summary = "SOGO CHAT & MEET is strictly for consenting adults aged 18 and older.",
            details = listOf(
                "Accurate birthdates are enforced during onboarding; underage accounts are prohibited.",
                "Represent yourself honestly—impersonation, catfish accounts, or fake photos lead to removal.",
                "Members sign in through Google via Firebase Authentication; always rely on in-app safety tools, as sign-in confirms your Google account rather than real-world identity."
            ),
            accentColor = GoldAccent,
            icon = Icons.Default.VerifiedUser
        ),
        SafetyGuidelineItem(
            id = "respectful_conduct",
            category = SafetyCategory.COMMUNITY_STANDARDS,
            badge = "ZERO TOLERANCE",
            title = "Respectful Conduct & Consent",
            summary = "Harassment, hate speech, threats, and non-consensual content are never tolerated.",
            details = listOf(
                "Respect boundaries immediately if someone declines a message, friend request, or call.",
                "Do not share sexually explicit, violent, or demeaning material in public feeds or unsolicited chats.",
                "Violations reported by members are logged for moderation review and account enforcement."
            ),
            accentColor = AlertRed,
            icon = Icons.Default.Gavel
        ),
        SafetyGuidelineItem(
            id = "private_info_guard",
            category = SafetyCategory.PRIVATE_INTERACTIONS,
            badge = "PRIVATE CHAT TIP",
            title = "Protect Financial & Personal Details",
            summary = "Never share passwords, OTP codes, banking info, or money in private chats.",
            details = listOf(
                "Keep private financial accounts, government IDs, and one-time verification codes strictly confidential.",
                "Treat urgent financial requests, investment pitches, or gift-card appeals as red flags and report them.",
                "Avoid clicking suspicious external links sent by accounts you just met."
            ),
            accentColor = GoldLight,
            icon = Icons.Default.Lock
        ),
        SafetyGuidelineItem(
            id = "stay_on_platform",
            category = SafetyCategory.PRIVATE_INTERACTIONS,
            badge = "CALL & CHAT SAFETY",
            title = "Keep Early Chats & Calls on SOGO",
            summary = "Use built-in messaging, voice, and video calls before sharing external contact info.",
            details = listOf(
                "SOGO private chat and WebRTC calls let you get to know members without exposing your phone number or home address.",
                "During voice or video calls, you can mute your microphone, switch cameras, or end the call at any moment.",
                "Only accept incoming call invitations from members you feel comfortable speaking with."
            ),
            accentColor = OnlineGreen,
            icon = Icons.Default.Videocam
        ),
        SafetyGuidelineItem(
            id = "privacy_controls",
            category = SafetyCategory.PRIVATE_INTERACTIONS,
            badge = "PRIVACY SETTINGS",
            title = "Customize Discovery & Profile Privacy",
            summary = "Use SOGO's live privacy toggles whenever you want more control over who sees you.",
            details = listOf(
                "Enable Hide from Discovery to pause appearing in member search and recommendations.",
                "Toggle Hide Online Status, Hide Age, or Hide Location from your Profile Preferences tab.",
                "Your privacy preferences update immediately across discovery and member cards."
            ),
            accentColor = GoldAccent,
            icon = Icons.Default.VisibilityOff,
            actionTarget = SafetyActionTarget.PRIVACY_SETTINGS
        ),
        SafetyGuidelineItem(
            id = "report_chat_content",
            category = SafetyCategory.REPORTING_TOOLS,
            badge = "MODERATION MECHANISM",
            title = "Flag Private Chat Messages",
            summary = "Report abusive or suspicious messages directly inside any private conversation.",
            details = listOf(
                "Tap the Flag icon beside any received message or tap Report Content in the chat moderation banner or overflow menu.",
                "Select a reason and add optional context; the report is stored in the content moderation queue for review.",
                "Pair reporting with blocking if you want communication stopped immediately."
            ),
            accentColor = AlertRed,
            icon = Icons.Default.Flag,
            actionTarget = SafetyActionTarget.REPORT_IN_CHAT
        ),
        SafetyGuidelineItem(
            id = "report_profiles_activities",
            category = SafetyCategory.REPORTING_TOOLS,
            badge = "PROFILES & FEED",
            title = "Report Profiles & Activity Posts",
            summary = "Hold profiles and social feed posts accountable with dedicated reporting tools.",
            details = listOf(
                "From any member profile or chat header menu, choose Report Member to escalate misconduct or fake accounts.",
                "From the Everyday Activities feed, tap the report control on any post to flag harmful social content.",
                "Submitting a report is private—the reported member is not shown who submitted the flag."
            ),
            accentColor = BurgundyPrimary,
            icon = Icons.Default.ReportProblem
        ),
        SafetyGuidelineItem(
            id = "block_and_restrict",
            category = SafetyCategory.REPORTING_TOOLS,
            badge = "INSTANT PROTECTION",
            title = "Block & Restrict Unwanted Members",
            summary = "Blocking removes a member from discovery and stops incoming chats and calls immediately.",
            details = listOf(
                "Blocked members disappear from Member Discovery, Online Users You May Like, Friend Requests, and the Activity Feed.",
                "Incoming messages and call attempts from blocked accounts are restricted automatically.",
                "Review or unblock members anytime in Blocked Users from your Profile or top-right menu."
            ),
            accentColor = AlertRed,
            icon = Icons.Default.Block,
            actionTarget = SafetyActionTarget.BLOCKED_USERS
        ),
        SafetyGuidelineItem(
            id = "in_person_meetup",
            category = SafetyCategory.IN_PERSON_SAFETY,
            badge = "MEETUP CHECKLIST",
            title = "Meet in Public & Share Your Plans",
            summary = "If you decide to meet offline, prioritize public venues and personal autonomy.",
            details = listOf(
                "Choose a busy, well-lit public location for first meetings and keep your own transportation arranged.",
                "Tell a trusted friend or family member who you are meeting, the venue, and when you plan to check back in.",
                "Leave immediately if someone pressures your boundaries or ignores agreed plans."
            ),
            accentColor = OnlineGreen,
            icon = Icons.Default.Place
        )
    )

    fun guidelinesFor(category: SafetyCategory): List<SafetyGuidelineItem> =
        if (category == SafetyCategory.ALL) guidelines
        else guidelines.filter { it.category == category }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitySafetyGuidelinesDialog(
    onDismiss: () -> Unit,
    onOpenBlockedUsers: (() -> Unit)? = null,
    onOpenPrivacySettings: (() -> Unit)? = null,
    onReportMemberInChat: (() -> Unit)? = null
) {
    var selectedCategory by rememberSaveable { mutableStateOf(SafetyCategory.ALL) }
    var expandedIds by remember {
        mutableStateOf(
            setOf("private_info_guard", "report_chat_content", "block_and_restrict")
        )
    }

    val filteredGuidelines = remember(selectedCategory) {
        CommunitySafetyCatalog.guidelinesFor(selectedCategory)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("community_safety_guidelines_screen"),
            containerColor = MatteBlackBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Community Safety Guidelines",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Private Interactions • Reporting • Respect",
                                color = GoldLight,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("safety_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Community Safety Guidelines",
                                tint = TextWhite
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
                Surface(
                    color = MatteBlackSurface,
                    border = BorderStroke(1.dp, OutlineSubtle)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .heightIn(min = 48.dp)
                                .testTag("safety_acknowledge_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Got It — Keep SOGO Safe",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .testTag("safety_guidelines_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "safety_hero_banner") {
                        SafetyHeroBanner()
                    }

                    item(key = "safety_category_filters") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(SafetyCategory.entries, key = { it.name }) { category ->
                                val selected = selectedCategory == category
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedCategory = category },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("safety_filter_${category.name.lowercase()}"),
                                    label = {
                                        Text(
                                            text = category.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BurgundyPrimary,
                                        selectedLabelColor = GoldLight,
                                        containerColor = MatteBlackSurfaceVariant,
                                        labelColor = TextMuted
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = OutlineSubtle,
                                        selectedBorderColor = GoldAccent
                                    )
                                )
                            }
                        }
                    }

                    items(filteredGuidelines, key = { it.id }) { guideline ->
                        val isExpanded = expandedIds.contains(guideline.id)
                        SafetyGuidelineCard(
                            item = guideline,
                            expanded = isExpanded,
                            onToggleExpand = {
                                expandedIds = if (isExpanded) {
                                    expandedIds - guideline.id
                                } else {
                                    expandedIds + guideline.id
                                }
                            },
                            onOpenPrivacySettings = onOpenPrivacySettings,
                            onOpenBlockedUsers = onOpenBlockedUsers,
                            onReportMemberInChat = onReportMemberInChat
                        )
                    }

                    if (onOpenPrivacySettings != null || onOpenBlockedUsers != null || onReportMemberInChat != null) {
                        item(key = "safety_quick_actions") {
                            SafetyQuickActionsCard(
                                onOpenPrivacySettings = onOpenPrivacySettings,
                                onOpenBlockedUsers = onOpenBlockedUsers,
                                onReportMemberInChat = onReportMemberInChat
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyHeroBanner() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineGold),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(BurgundyContainer, MatteBlackCard, MatteBlackSurface)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(GoldContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Community Safety Shield",
                        tint = GoldLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SAFE CONNECTIONS FIRST",
                        color = GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Private interaction tips & active moderation tools",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "Every member deserves respectful conversations. Review these guidelines for private chats, voice/video calls, and in-app reporting so you stay in control.",
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun SafetyGuidelineCard(
    item: SafetyGuidelineItem,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenPrivacySettings: (() -> Unit)?,
    onOpenBlockedUsers: (() -> Unit)?,
    onReportMemberInChat: (() -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) item.accentColor.copy(alpha = 0.65f) else OutlineSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .testTag("safety_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(
                        role = Role.Button,
                        onClick = onToggleExpand
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(item.accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.badge,
                        color = item.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.7.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.summary,
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse ${item.title}" else "Expand ${item.title}",
                    tint = TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.6f))
                    item.details.forEach { bullet ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                color = item.accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = bullet,
                                color = TextWhite.copy(alpha = 0.92f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    when (item.actionTarget) {
                        SafetyActionTarget.PRIVACY_SETTINGS -> {
                            if (onOpenPrivacySettings != null) {
                                OutlinedButton(
                                    onClick = onOpenPrivacySettings,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("safety_card_privacy_action"),
                                    border = BorderStroke(1.dp, OutlineGold)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Profile Privacy Controls", color = GoldLight, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        SafetyActionTarget.BLOCKED_USERS -> {
                            if (onOpenBlockedUsers != null) {
                                OutlinedButton(
                                    onClick = onOpenBlockedUsers,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("safety_card_blocked_users_action"),
                                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.7f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = AlertRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Manage Blocked Users", color = TextWhite, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        SafetyActionTarget.REPORT_IN_CHAT -> {
                            if (onReportMemberInChat != null) {
                                OutlinedButton(
                                    onClick = onReportMemberInChat,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("safety_card_report_chat_action"),
                                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.7f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = AlertRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Report This Conversation", color = GoldLight, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyQuickActionsCard(
    onOpenPrivacySettings: (() -> Unit)?,
    onOpenBlockedUsers: (() -> Unit)?,
    onReportMemberInChat: (() -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackSurface),
        border = BorderStroke(1.dp, OutlineSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "QUICK SAFETY CONTROLS",
                color = GoldAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "Jump straight to SOGO's live safety and privacy tools.",
                color = TextDim,
                fontSize = 11.sp
            )
            if (onOpenPrivacySettings != null) {
                OutlinedButton(
                    onClick = onOpenPrivacySettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("safety_open_privacy_button"),
                    border = BorderStroke(1.dp, OutlineGold)
                ) {
                    Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = null, tint = GoldLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacy Toggles & Discovery Controls", color = GoldLight, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onOpenBlockedUsers != null) {
                OutlinedButton(
                    onClick = onOpenBlockedUsers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("safety_open_blocked_users_button"),
                    border = BorderStroke(1.dp, OutlineSubtle)
                ) {
                    Icon(imageVector = Icons.Default.Block, contentDescription = null, tint = AlertRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Blocked Users", color = TextWhite, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onReportMemberInChat != null) {
                OutlinedButton(
                    onClick = onReportMemberInChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("safety_report_member_button"),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.7f))
                ) {
                    Icon(imageVector = Icons.Default.Flag, contentDescription = null, tint = AlertRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Report Member or Conversation", color = GoldLight, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
