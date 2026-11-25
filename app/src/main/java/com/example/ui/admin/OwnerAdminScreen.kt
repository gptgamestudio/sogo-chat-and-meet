package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.BillingCapabilityStatus
import com.example.data.model.ContentReportRecord
import com.example.data.model.FirestoreMonitoringState
import com.example.data.model.MemberAnalyticsState
import com.example.data.model.MetricOrigin
import com.example.data.model.OwnerAuthStatus
import com.example.data.model.QuotaSeverity
import com.example.data.model.QuotaWarningItem
import com.example.data.model.RegistrationTrendPoint
import com.example.data.model.SanitizedRegistrationEntry
import com.example.data.repository.OwnerDashboardAggregator
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerAdminScreen(
    onBack: () -> Unit,
    viewModel: OwnerAdminViewModel = viewModel()
) {
    BackHandler { onBack() }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OWNER ADMIN",
                                color = GoldLight,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Text(
                            text = "Spark Plan Safe • Verified Claims Only",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("owner_admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshDashboard(initialLoad = false) },
                        enabled = !uiState.isRefreshing && !uiState.isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("owner_admin_refresh_button")
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = GoldLight,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Owner Metrics",
                                tint = GoldLight
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
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Surface(
                    color = Color(0xFF42121B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("owner_admin_error_banner")
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

            AnimatedVisibility(visible = uiState.statusBanner != null && uiState.authStatus.isAuthorized) {
                Surface(
                    color = MatteBlackSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("owner_admin_status_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.statusBanner ?: "",
                            color = GoldLight,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearBanner() }) {
                            Text("OK", color = TextWhite, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = GoldLight,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("owner_admin_loading")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Verifying server custom claims & email status...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
                return@Scaffold
            }

            if (!uiState.authStatus.isAuthorized) {
                OwnerVerificationLockedView(
                    authStatus = uiState.authStatus,
                    onRefreshToken = { viewModel.refreshDashboard(initialLoad = false) },
                    onBack = onBack
                )
                return@Scaffold
            }

            OwnerSectionTabRow(
                selectedSection = uiState.selectedSection,
                pendingReportsCount = uiState.snapshot.pendingContentReports.count { it.status == "PENDING" } +
                    uiState.snapshot.activityReports.count { it.status == "PENDING" },
                onSelectSection = { viewModel.selectSection(it) }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("owner_admin_content_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 84.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "security_verified_header") {
                    OwnerSecurityVerifiedBanner(
                        authStatus = uiState.authStatus,
                        lastSyncedAtMillis = uiState.snapshot.lastSyncedAtMillis,
                        notice = uiState.snapshot.statusNotice
                    )
                }

                when (uiState.selectedSection) {
                    OwnerAdminSection.MEMBERS -> {
                        item(key = "member_analytics_card") {
                            MemberAnalyticsCard(uiState.snapshot.memberAnalytics)
                        }
                        item(key = "registration_trends_card") {
                            RegistrationTrendsCard(uiState.snapshot.memberAnalytics.registrationTrends)
                        }
                        item(key = "registration_history_header") {
                            Text(
                                text = "SANITIZED REGISTRATION HISTORY",
                                color = GoldLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (uiState.snapshot.registrationHistory.isEmpty()) {
                            item(key = "empty_history_card") {
                                EmptyAdminInfoCard(
                                    title = "No Registration Records Found",
                                    body = "Run admin-tools/owner-admin-sync.mjs or inspect existing Firestore profiles to populate sanitized registration timestamps."
                                )
                            }
                        } else {
                            items(
                                items = uiState.snapshot.registrationHistory,
                                key = { "reg_${it.entryId}" }
                            ) { entry ->
                                RegistrationHistoryRow(entry)
                            }
                        }
                    }

                    OwnerAdminSection.MONITORING -> {
                        item(key = "firestore_usage_card") {
                            FirestoreUsageCard(uiState.snapshot.firestoreMonitoring)
                        }
                        item(key = "quota_warnings_card") {
                            QuotaWarningsCard(uiState.snapshot.firestoreMonitoring.quotaWarnings)
                        }
                        item(key = "billing_safety_card") {
                            BillingSafetyCard(uiState.snapshot.billingStatus)
                        }
                    }

                    OwnerAdminSection.MODERATION -> {
                        val reports = uiState.snapshot.pendingContentReports
                        val activityReports = uiState.snapshot.activityReports
                        val directoryMembers = uiState.snapshot.memberDirectory

                        item(key = "content_reports_header") {
                            Text(
                                text = "PRIVATE CHAT REPORTS (${reports.size})",
                                color = GoldLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        if (reports.isEmpty()) {
                            item(key = "empty_moderation_card") {
                                EmptyAdminInfoCard(
                                    title = "No Content Reports",
                                    body = "When members tap 'Report Content' inside a private chat, flagged messages appear in content_reports here for review."
                                )
                            }
                        } else {
                            items(
                                items = reports,
                                key = { "report_${it.reportId}" }
                            ) { report ->
                                ModerationReportCard(
                                    report = report,
                                    isUpdating = uiState.isUpdatingModeration,
                                    onMarkReviewed = { viewModel.updateReportStatus(report.reportId, "REVIEWED") },
                                    onDismissReport = { viewModel.updateReportStatus(report.reportId, "DISMISSED") }
                                )
                            }
                        }

                        item(key = "activity_reports_header") {
                            Text(
                                text = "ACTIVITY POST REPORTS (${activityReports.size})",
                                color = GoldLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        if (activityReports.isEmpty()) {
                            item(key = "empty_activity_reports_card") {
                                EmptyAdminInfoCard(
                                    title = "No Activity Reports",
                                    body = "Flagged activity posts submitted by members appear here for moderation review."
                                )
                            }
                        } else {
                            items(
                                items = activityReports,
                                key = { "act_report_${it.reportId}" }
                            ) { actReport ->
                                ActivityModerationReportCard(
                                    report = actReport,
                                    isUpdating = uiState.isUpdatingModeration,
                                    onMarkReviewed = { viewModel.updateActivityReportStatus(actReport.reportId, "REVIEWED") },
                                    onDismissReport = { viewModel.updateActivityReportStatus(actReport.reportId, "DISMISSED") }
                                )
                            }
                        }

                        item(key = "member_moderation_header") {
                            Text(
                                text = "MEMBER ACCOUNT CONTROLS (${directoryMembers.size})",
                                color = GoldLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        if (directoryMembers.isEmpty()) {
                            item(key = "empty_member_directory_card") {
                                EmptyAdminInfoCard(
                                    title = "No Member Profiles Loaded",
                                    body = "Registered member profiles will appear here for account status and suspension management."
                                )
                            }
                        } else {
                            items(
                                items = directoryMembers,
                                key = { "mod_member_${it.userId}" }
                            ) { member ->
                                MemberAdminControlCard(
                                    member = member,
                                    isUpdating = uiState.isUpdatingModeration,
                                    onToggleSuspended = {
                                        val currentlySuspended = member.accountStatus.equals("SUSPENDED", ignoreCase = true)
                                        viewModel.toggleMemberSuspension(member.userId, !currentlySuspended)
                                    }
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
private fun OwnerVerificationLockedView(
    authStatus: OwnerAuthStatus,
    onRefreshToken: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("owner_admin_locked_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Admin Access Locked",
                            tint = AlertRed,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Owner Verification Required",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = authStatus.denialReason
                            ?: "Administrative data is locked unless server-verified Owner custom claims are present.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    VerificationCheckRow("Signed-in Email", authStatus.signedInEmail ?: "None", authStatus.signedInEmail.equals(authStatus.expectedOwnerEmail, ignoreCase = true))
                    VerificationCheckRow("Firebase email_verified", if (authStatus.emailVerified) "true" else "false", authStatus.emailVerified)
                    VerificationCheckRow("Server Claim: owner", if (authStatus.hasOwnerClaim) "true" else "missing", authStatus.hasOwnerClaim)
                    VerificationCheckRow("Server Claim: admin", if (authStatus.hasAdminClaim) "true" else "missing", authStatus.hasAdminClaim)

                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = MatteBlackSurfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Zero Client-Side Escalation: Client apps never assign owner/admin privileges. Grant claims on a trusted machine with admin-tools/owner-admin-sync.mjs using Firebase Admin SDK, then tap Refresh Token.",
                            color = GoldLight,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Back", color = TextWhite)
                        }
                        Button(
                            onClick = onRefreshToken,
                            colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("owner_admin_retry_claims_button")
                        ) {
                            Text("Refresh Token", color = GoldLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationCheckRow(label: String, value: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (passed) OnlineGreen else AlertRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                color = if (passed) OnlineGreen else AlertRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun OwnerSectionTabRow(
    selectedSection: OwnerAdminSection,
    pendingReportsCount: Int,
    onSelectSection: (OwnerAdminSection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MatteBlackSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OwnerAdminSection.entries.forEach { section ->
            val selected = section == selectedSection
            val title = if (section == OwnerAdminSection.MODERATION && pendingReportsCount > 0) {
                "${section.label} ($pendingReportsCount)"
            } else {
                section.label
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (selected) BurgundyPrimary else MatteBlackSurfaceVariant,
                border = BorderStroke(1.dp, if (selected) GoldAccent else OutlineSubtle),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelectSection(section) }
                    .testTag("owner_admin_tab_${section.name.lowercase()}")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(
                        text = title,
                        color = if (selected) GoldLight else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnerSecurityVerifiedBanner(
    authStatus: OwnerAuthStatus,
    lastSyncedAtMillis: Long?,
    notice: String?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16271C)),
        border = BorderStroke(1.dp, OnlineGreen.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Owner Claims Verified",
                    tint = OnlineGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Owner Verified: ${authStatus.signedInEmail ?: ""}",
                    color = OnlineGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (lastSyncedAtMillis != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last Admin SDK sync: ${formatTimestamp(lastSyncedAtMillis)}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            if (!notice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notice,
                    color = GoldLight,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun MemberAnalyticsCard(analytics: MemberAnalyticsState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Member Analytics",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                MetricOriginBadge(analytics.authMetricsOrigin)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Firebase Auth Users",
                    value = analytics.totalAuthUsers?.toString() ?: "Unavailable",
                    origin = analytics.authMetricsOrigin,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Firestore Profiles",
                    value = analytics.observedProfileUsers.toString(),
                    origin = analytics.profileMetricsOrigin,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val periodOrigin = if (analytics.authMetricsOrigin == MetricOrigin.LIVE_ADMIN_SDK) {
                    MetricOrigin.LIVE_ADMIN_SDK
                } else {
                    analytics.profileMetricsOrigin
                }
                MetricTile(
                    label = "Daily",
                    value = analytics.dailyRegistrations.toString(),
                    origin = periodOrigin,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Weekly (7d)",
                    value = analytics.weeklyRegistrations.toString(),
                    origin = periodOrigin,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Monthly (30d)",
                    value = analytics.monthlyRegistrations.toString(),
                    origin = periodOrigin,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Duplicate account records excluded: ${analytics.duplicatesExcluded}",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RegistrationTrendsCard(points: List<RegistrationTrendPoint>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registration Trends",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                MetricOriginBadge(points.firstOrNull()?.origin ?: MetricOrigin.UNAVAILABLE)
            }
            Spacer(modifier = Modifier.height(12.dp))
            val maxVal = (points.maxOfOrNull { it.registrationsCount } ?: 1).coerceAtLeast(1)
            points.forEach { point ->
                val fraction = point.registrationsCount.toFloat() / maxVal.toFloat()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = point.dateKey.takeLast(5),
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.width(44.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MatteBlackSurfaceVariant)
                    ) {
                        if (point.registrationsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction.coerceIn(0.06f, 1f))
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(GoldAccent)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = point.registrationsCount.toString(),
                        color = GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RegistrationHistoryRow(entry: SanitizedRegistrationEntry) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.memberLabel,
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (entry.emailVerified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Email Verified",
                            tint = OnlineGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${entry.maskedIdentity} • ${entry.providerLabel}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                entry.registeredAtMillis?.let { millis ->
                    Text(
                        text = formatTimestamp(millis),
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }
            MetricOriginBadge(entry.origin)
        }
    }
}

@Composable
private fun FirestoreUsageCard(monitoring: FirestoreMonitoringState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Firestore Resource Usage",
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                MetricOriginBadge(monitoring.usageOrigin)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "Reads (24h)",
                    value = monitoring.readsToday?.toString() ?: "Unavailable",
                    origin = monitoring.usageOrigin,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Writes (24h)",
                    value = monitoring.writesToday?.toString() ?: "Unavailable",
                    origin = monitoring.usageOrigin,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "Deletes (24h)",
                    value = monitoring.deletesToday?.toString() ?: "Unavailable",
                    origin = monitoring.usageOrigin,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Storage",
                    value = monitoring.storageBytes?.let { formatBytes(it) } ?: "Unavailable",
                    origin = monitoring.usageOrigin,
                    modifier = Modifier.weight(1f)
                )
            }
            if (monitoring.observableDocCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                val countsSummary = monitoring.observableDocCounts.entries
                    .joinToString(" • ") { "${it.key}: ${it.value}" }
                Text(
                    text = "Observable Firestore sample counts ($countsSummary)",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            if (monitoring.dailyUsageTrends.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Daily Usage History",
                    color = GoldLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                monitoring.dailyUsageTrends.takeLast(7).forEach { day ->
                    Text(
                        text = "${day.dateKey}: R=${day.reads ?: "--"} W=${day.writes ?: "--"} D=${day.deletes ?: "--"}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaWarningsCard(warnings: List<QuotaWarningItem>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Spark Quota Guards",
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            warnings.forEach { warning ->
                val tint = when (warning.severity) {
                    QuotaSeverity.CRITICAL -> AlertRed
                    QuotaSeverity.WARNING -> GoldLight
                    QuotaSeverity.OK -> OnlineGreen
                    QuotaSeverity.UNAVAILABLE -> TextMuted
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (warning.severity == QuotaSeverity.OK) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = warning.message,
                        color = tint,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BillingSafetyCard(billing: BillingCapabilityStatus) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Billing Safety & Spark Mode",
                color = GoldLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = billing.activePlan,
                color = OnlineGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Requires Blaze Plan (Not Auto-Enabled):",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            billing.blazeRequiredCapabilities.forEach { item ->
                Text(text = "• $item", color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Spark-Compatible Alternatives:",
                color = GoldLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            billing.sparkCompatibleAlternatives.forEach { item ->
                Text(text = "• $item", color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun ModerationReportCard(
    report: ContentReportRecord,
    isUpdating: Boolean,
    onMarkReviewed: () -> Unit,
    onDismissReport: () -> Unit
) {
    val statusColor = when (report.status.uppercase(Locale.US)) {
        "REVIEWED" -> OnlineGreen
        "DISMISSED" -> TextMuted
        else -> GoldLight
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.reason,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MatteBlackSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = report.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Flagged ${report.messageType}: \"${report.messageExcerpt}\"",
                color = GoldLight,
                fontSize = 12.sp
            )
            if (!report.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Notes: ${report.details}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reporter ${OwnerDashboardAggregator.maskUserId(report.reporterId)} • Reported ${OwnerDashboardAggregator.maskUserId(report.reportedUserId)}",
                color = TextDim,
                fontSize = 11.sp
            )
            if (report.status == "PENDING") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismissReport,
                        enabled = !isUpdating,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("dismiss_report_${report.reportId}")
                    ) {
                        Text("Dismiss", color = TextMuted)
                    }
                    Button(
                        onClick = onMarkReviewed,
                        enabled = !isUpdating,
                        colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("review_report_${report.reportId}")
                    ) {
                        Text("Mark Reviewed", color = GoldLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityModerationReportCard(
    report: com.example.data.model.ActivityReportRecord,
    isUpdating: Boolean,
    onMarkReviewed: () -> Unit,
    onDismissReport: () -> Unit
) {
    val statusColor = when (report.status.uppercase(Locale.US)) {
        "REVIEWED" -> OnlineGreen
        "DISMISSED" -> TextMuted
        else -> GoldLight
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.reason,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(color = MatteBlackSurfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = report.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Post: \"${report.postExcerpt}\"",
                color = GoldLight,
                fontSize = 12.sp
            )
            if (!report.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Notes: ${report.details}", color = TextMuted, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reporter ${OwnerDashboardAggregator.maskUserId(report.reporterId)} • Author ${OwnerDashboardAggregator.maskUserId(report.authorId)}",
                color = TextDim,
                fontSize = 11.sp
            )
            if (report.status == "PENDING") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismissReport,
                        enabled = !isUpdating,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("dismiss_activity_report_${report.reportId}")
                    ) {
                        Text("Dismiss", color = TextMuted)
                    }
                    Button(
                        onClick = onMarkReviewed,
                        enabled = !isUpdating,
                        colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("review_activity_report_${report.reportId}")
                    ) {
                        Text("Mark Reviewed", color = GoldLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberAdminControlCard(
    member: com.example.data.model.UserProfile,
    isUpdating: Boolean,
    onToggleSuspended: () -> Unit
) {
    val isSuspended = member.accountStatus.equals("SUSPENDED", ignoreCase = true)
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, if (isSuspended) AlertRed.copy(alpha = 0.6f) else OutlineSubtle),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.testTag("admin_member_control_${member.userId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${member.displayName}, ${member.age}",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${OwnerDashboardAggregator.maskUserId(member.userId)} • ${member.country}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = MatteBlackSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isSuspended) "SUSPENDED" else "ACTIVE",
                        color = if (isSuspended) AlertRed else OnlineGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onToggleSuspended,
                enabled = !isUpdating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSuspended) OnlineGreen else AlertRed
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("admin_suspend_member_${member.userId}")
            ) {
                Text(
                    text = if (isSuspended) "Restore" else "Suspend",
                    color = if (isSuspended) MatteBlackBackground else TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyAdminInfoCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = BorderStroke(1.dp, OutlineSubtle),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    origin: MetricOrigin,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MatteBlackSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, color = TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = if (origin == MetricOrigin.UNAVAILABLE) TextDim else TextWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            MetricOriginBadge(origin)
        }
    }
}

@Composable
private fun MetricOriginBadge(origin: MetricOrigin) {
    val (label, color) = when (origin) {
        MetricOrigin.LIVE_ADMIN_SDK,
        MetricOrigin.LIVE_MONITORING -> "LIVE" to OnlineGreen
        MetricOrigin.FIRESTORE_ESTIMATE -> "ESTIMATE" to GoldLight
        MetricOrigin.UNAVAILABLE -> "UNAVAILABLE" to TextDim
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
    return sdf.format(Date(millis))
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
