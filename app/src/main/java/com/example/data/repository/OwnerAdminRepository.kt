package com.example.data.repository

import android.content.Context
import com.example.data.model.ActivityReportRecord
import com.example.data.model.AdminAuditLog
import com.example.data.model.AdminDailyUsageDoc
import com.example.data.model.AdminDashboardSummaryDoc
import com.example.data.model.AdminRegistrationHistoryDoc
import com.example.data.model.BillingCapabilityStatus
import com.example.data.model.ContentReportRecord
import com.example.data.model.FirestoreDailyUsagePoint
import com.example.data.model.FirestoreMonitoringState
import com.example.data.model.MemberAnalyticsState
import com.example.data.model.MetricOrigin
import com.example.data.model.OwnerAuthStatus
import com.example.data.model.OwnerDashboardSnapshot
import com.example.data.model.QuotaSeverity
import com.example.data.model.QuotaWarningItem
import com.example.data.model.RegistrationTrendPoint
import com.example.data.model.SanitizedRegistrationEntry
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

interface OwnerAdminDataSource {
    suspend fun verifyOwnerAccess(forceTokenRefresh: Boolean = true): OwnerAuthStatus
    suspend fun loadOwnerDashboard(): OwnerDashboardSnapshot
    suspend fun updateContentReportStatus(reportId: String, newStatus: String)
    suspend fun updateActivityReportStatus(reportId: String, newStatus: String) {}
    suspend fun setMemberAccountStatus(targetUid: String, suspended: Boolean) {}
}

object OwnerDashboardAggregator {
    const val OWNER_EMAIL = "gptgamestudioph@gmail.com"
    const val SPARK_DAILY_READS_LIMIT = 50_000L
    const val SPARK_DAILY_WRITES_LIMIT = 20_000L
    const val SPARK_DAILY_DELETES_LIMIT = 20_000L
    const val SPARK_STORAGE_BYTES_LIMIT = 1_073_741_824L // 1 GiB

    fun evaluateOwnerClaims(
        email: String?,
        emailVerified: Boolean,
        claims: Map<String, Any?>,
        isGoogleProvider: Boolean = true
    ): OwnerAuthStatus {
        val normalizedEmail = email?.trim()?.lowercase(Locale.US)
        val hasOwnerClaim = claims["owner"] == true
        val hasAdminClaim = claims["admin"] == true
        val emailMatches = normalizedEmail == OWNER_EMAIL

        val denialReason = when {
            normalizedEmail.isNullOrBlank() ->
                "Owner session requires an authenticated Google account."
            !isGoogleProvider ->
                "Access denied: Owner authentication requires Google Sign-In."
            !emailMatches ->
                "Access denied: Signed-in account does not match the registered Owner identity."
            !emailVerified ->
                "Access denied: Firebase email_verified must be true."
            !hasOwnerClaim || !hasAdminClaim ->
                "Owner custom claims (owner=true, admin=true) are not present on your Firebase ID token. Grant them via Firebase Admin SDK on a trusted server; client-side privilege assignment is disabled."
            else -> null
        }

        return OwnerAuthStatus(
            signedInEmail = email,
            emailVerified = emailVerified,
            hasOwnerClaim = hasOwnerClaim,
            hasAdminClaim = hasAdminClaim,
            expectedOwnerEmail = OWNER_EMAIL,
            isAuthorized = denialReason == null,
            denialReason = denialReason
        )
    }

    fun parseMetricOrigin(raw: String?): MetricOrigin = when (raw?.trim()?.uppercase(Locale.US)) {
        "LIVE_ADMIN_SDK" -> MetricOrigin.LIVE_ADMIN_SDK
        "LIVE_MONITORING" -> MetricOrigin.LIVE_MONITORING
        "FIRESTORE_ESTIMATE" -> MetricOrigin.FIRESTORE_ESTIMATE
        else -> MetricOrigin.UNAVAILABLE
    }

    fun deduplicateProfiles(profiles: List<UserProfile>): Pair<List<UserProfile>, Int> {
        val seenIds = mutableSetOf<String>()
        val unique = mutableListOf<UserProfile>()
        var duplicates = 0
        for (profile in profiles) {
            val key = profile.userId.ifBlank { profile.id }.trim()
            if (key.isBlank()) continue
            if (seenIds.add(key)) {
                unique += profile
            } else {
                duplicates++
            }
        }
        return unique to duplicates
    }

    fun buildRegistrationTrendsFromProfiles(
        uniqueProfiles: List<UserProfile>,
        nowMs: Long = System.currentTimeMillis(),
        daysBack: Int = 7,
        timeZone: TimeZone = TimeZone.getTimeZone("Asia/Manila")
    ): Triple<Int, Int, Int> {
        val dayMs = 24L * 60L * 60L * 1000L
        val cal = Calendar.getInstance(timeZone, Locale.US).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val sevenDaysAgo = startOfToday - (6L * dayMs)
        val thirtyDaysAgo = startOfToday - (29L * dayMs)

        var daily = 0
        var weekly = 0
        var monthly = 0

        uniqueProfiles.forEach { profile ->
            val createdMs = profile.createdAt?.toDate()?.time ?: return@forEach
            if (createdMs >= startOfToday) daily++
            if (createdMs >= sevenDaysAgo) weekly++
            if (createdMs >= thirtyDaysAgo) monthly++
        }
        return Triple(daily, weekly, monthly)
    }

    fun buildDailyTrendPointsFromProfiles(
        uniqueProfiles: List<UserProfile>,
        nowMs: Long = System.currentTimeMillis(),
        daysBack: Int = 7,
        timeZone: TimeZone = TimeZone.getTimeZone("Asia/Manila")
    ): List<RegistrationTrendPoint> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        val countsByKey = mutableMapOf<String, Int>()
        uniqueProfiles.forEach { profile ->
            val date = profile.createdAt?.toDate() ?: return@forEach
            val key = formatter.format(date)
            countsByKey[key] = (countsByKey[key] ?: 0) + 1
        }

        val cal = Calendar.getInstance(timeZone, Locale.US).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val points = mutableListOf<RegistrationTrendPoint>()
        for (offset in (daysBack - 1) downTo 0) {
            val dayCal = cal.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, -offset)
            val key = formatter.format(dayCal.time)
            points += RegistrationTrendPoint(
                dateKey = key,
                registrationsCount = countsByKey[key] ?: 0,
                origin = MetricOrigin.FIRESTORE_ESTIMATE
            )
        }
        return points
    }

    fun evaluateSparkQuotaWarnings(
        readsToday: Long?,
        writesToday: Long?,
        deletesToday: Long?,
        storageBytes: Long?,
        origin: MetricOrigin
    ): List<QuotaWarningItem> {
        fun checkQuota(
            label: String,
            used: Long?,
            limit: Long
        ): QuotaWarningItem {
            if (used == null || origin == MetricOrigin.UNAVAILABLE) {
                return QuotaWarningItem(
                    resourceName = label,
                    used = null,
                    sparkDailyLimit = limit,
                    severity = QuotaSeverity.UNAVAILABLE,
                    origin = MetricOrigin.UNAVAILABLE,
                    message = "$label metrics unavailable until Cloud Monitoring / Admin SDK sync runs."
                )
            }
            val ratio = used.toDouble() / limit.toDouble()
            val (severity, note) = when {
                ratio >= 0.90 -> QuotaSeverity.CRITICAL to "$label at ${(ratio * 100).toInt()}% of Spark daily/storage quota."
                ratio >= 0.75 -> QuotaSeverity.WARNING to "$label approaching Spark limit (${(ratio * 100).toInt()}%)."
                else -> QuotaSeverity.OK to "$label within safe Spark quota (${(ratio * 100).toInt()}%)."
            }
            return QuotaWarningItem(
                resourceName = label,
                used = used,
                sparkDailyLimit = limit,
                severity = severity,
                origin = origin,
                message = note
            )
        }

        return listOf(
            checkQuota("Document Reads", readsToday, SPARK_DAILY_READS_LIMIT),
            checkQuota("Document Writes", writesToday, SPARK_DAILY_WRITES_LIMIT),
            checkQuota("Document Deletes", deletesToday, SPARK_DAILY_DELETES_LIMIT),
            checkQuota("Firestore Storage", storageBytes, SPARK_STORAGE_BYTES_LIMIT)
        )
    }

    fun sanitizeDisplayLabel(profile: UserProfile): String {
        val raw = profile.displayName.trim().ifBlank { "Member" }
        return raw.take(24)
    }

    fun maskUserId(uid: String): String {
        val clean = uid.trim()
        return if (clean.length <= 8) clean else "${clean.take(4)}...${clean.takeLast(4)}"
    }

    fun defaultSparkBillingStatus(): BillingCapabilityStatus = BillingCapabilityStatus(
        activePlan = "Firebase Spark Plan ($0/month — Auto-upgrade disabled)",
        autoUpgradeDisabled = true,
        blazeRequiredCapabilities = listOf(
            "Scheduled Firebase Cloud Functions v2 (Artifact Registry / Cloud Build requires Blaze)",
            "Automated in-project Cloud Monitoring server triggers on schedule"
        ),
        sparkCompatibleAlternatives = listOf(
            "Trusted local/external Admin SDK sync script (admin-tools/owner-admin-sync.mjs) for exact Auth counts & Cloud Monitoring snapshots",
            "On-demand read-only Firestore profile & moderation queries inside verified Owner UI (clearly labeled ESTIMATE)"
        )
    )
}

class FirebaseOwnerAdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : OwnerAdminDataSource {

    constructor(context: Context) : this(
        SogoRepository.getFirestoreInstance(context),
        Firebase.auth
    )

    override suspend fun verifyOwnerAccess(forceTokenRefresh: Boolean): OwnerAuthStatus {
        val user = auth.currentUser
            ?: return OwnerDashboardAggregator.evaluateOwnerClaims(null, false, emptyMap())

        val tokenResult = try {
            user.getIdToken(forceTokenRefresh).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            user.getIdToken(false).await()
        }

        val hasGoogleProvider = user.providerData.any {
            it.providerId == GoogleAuthProvider.PROVIDER_ID
        }
        return OwnerDashboardAggregator.evaluateOwnerClaims(
            email = user.email,
            emailVerified = user.isEmailVerified,
            claims = tokenResult.claims,
            isGoogleProvider = hasGoogleProvider
        )
    }

    override suspend fun loadOwnerDashboard(): OwnerDashboardSnapshot {
        val authStatus = verifyOwnerAccess(forceTokenRefresh = true)
        if (!authStatus.isAuthorized) {
            return OwnerDashboardSnapshot(
                authStatus = authStatus,
                billingStatus = OwnerDashboardAggregator.defaultSparkBillingStatus(),
                statusNotice = authStatus.denialReason
            )
        }

        // 1. Read trusted Admin SDK summary (if present & rules deployed)
        val summaryDoc = try {
            db.collection("admin_dashboard").document("summary")
                .get()
                .await()
                .toObject(AdminDashboardSummaryDoc::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        // 2. Read daily usage docs from Admin SDK sync (if present)
        val dailyDocs = try {
            db.collection("admin_usage_daily")
                .orderBy("dateKey", Query.Direction.DESCENDING)
                .limit(14)
                .get()
                .await()
                .toObjects(AdminDailyUsageDoc::class.java)
                .reversed()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        // 3. Read sanitized registration history written by Admin SDK (if present)
        val adminHistoryDocs = try {
            db.collection("admin_registration_history")
                .orderBy("registeredAt", Query.Direction.DESCENDING)
                .limit(40)
                .get()
                .await()
                .toObjects(AdminRegistrationHistoryDoc::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        // 4. Read pending content & activity moderation reports
        val pendingContentReports = try {
            db.collection("content_reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
                .toObjects(ContentReportRecord::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        val activityReports = try {
            db.collection("activity_reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
                .toObjects(ActivityReportRecord::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        val auditLogs = try {
            db.collection("admin_audit_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40)
                .get()
                .await()
                .toObjects(AdminAuditLog::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        // 5. Read-only inspection of Firestore member profiles for deduplicated profile estimates (NEVER modifies user records)
        val rawProfiles = try {
            db.collection("users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(500)
                .get()
                .await()
                .toObjects(UserProfile::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        val (uniqueProfiles, duplicatesExcluded) = OwnerDashboardAggregator.deduplicateProfiles(rawProfiles)
        val (estDaily, estWeekly, estMonthly) = OwnerDashboardAggregator.buildRegistrationTrendsFromProfiles(uniqueProfiles)
        val estTrendPoints = OwnerDashboardAggregator.buildDailyTrendPointsFromProfiles(uniqueProfiles)

        val authOrigin = OwnerDashboardAggregator.parseMetricOrigin(summaryDoc?.authMetricsOrigin)
        val usageOrigin = OwnerDashboardAggregator.parseMetricOrigin(summaryDoc?.usageMetricsOrigin)

        val trends = if (dailyDocs.isNotEmpty() && authOrigin == MetricOrigin.LIVE_ADMIN_SDK) {
            dailyDocs.map { doc ->
                RegistrationTrendPoint(
                    dateKey = doc.dateKey,
                    registrationsCount = doc.registrations.toInt(),
                    origin = MetricOrigin.LIVE_ADMIN_SDK
                )
            }
        } else {
            estTrendPoints
        }

        val memberAnalytics = MemberAnalyticsState(
            totalAuthUsers = summaryDoc?.totalAuthUsers,
            authMetricsOrigin = authOrigin,
            observedProfileUsers = uniqueProfiles.size,
            profileMetricsOrigin = if (uniqueProfiles.isNotEmpty()) MetricOrigin.FIRESTORE_ESTIMATE else MetricOrigin.UNAVAILABLE,
            dailyRegistrations = summaryDoc?.dailyRegistrations?.toInt() ?: estDaily,
            weeklyRegistrations = summaryDoc?.weeklyRegistrations?.toInt() ?: estWeekly,
            monthlyRegistrations = summaryDoc?.monthlyRegistrations?.toInt() ?: estMonthly,
            duplicatesExcluded = duplicatesExcluded,
            registrationTrends = trends
        )

        val observableCounts = buildMap {
            put("users", uniqueProfiles.size)
            put("content_reports", pendingContentReports.size)
            put("activity_reports", activityReports.size)
        }

        val usageTrends = dailyDocs.map { doc ->
            FirestoreDailyUsagePoint(
                dateKey = doc.dateKey,
                reads = doc.reads,
                writes = doc.writes,
                deletes = doc.deletes,
                origin = OwnerDashboardAggregator.parseMetricOrigin(doc.usageOrigin)
            )
        }

        val quotaWarnings = OwnerDashboardAggregator.evaluateSparkQuotaWarnings(
            readsToday = summaryDoc?.readsToday,
            writesToday = summaryDoc?.writesToday,
            deletesToday = summaryDoc?.deletesToday,
            storageBytes = summaryDoc?.storageBytes,
            origin = usageOrigin
        )

        val monitoringState = FirestoreMonitoringState(
            readsToday = summaryDoc?.readsToday,
            writesToday = summaryDoc?.writesToday,
            deletesToday = summaryDoc?.deletesToday,
            storageBytes = summaryDoc?.storageBytes,
            observableDocCounts = observableCounts,
            usageOrigin = usageOrigin,
            dailyUsageTrends = usageTrends,
            quotaWarnings = quotaWarnings
        )

        val historyItems = if (adminHistoryDocs.isNotEmpty()) {
            adminHistoryDocs.map { doc ->
                SanitizedRegistrationEntry(
                    entryId = doc.entryId.ifBlank { doc.id },
                    memberLabel = doc.memberLabel.ifBlank { "Member" },
                    maskedIdentity = doc.maskedEmail ?: "Google Account",
                    providerLabel = doc.providerId,
                    emailVerified = doc.emailVerified,
                    registeredAtMillis = doc.registeredAt?.toDate()?.time,
                    origin = MetricOrigin.LIVE_ADMIN_SDK
                )
            }
        } else {
            uniqueProfiles.take(40).map { profile ->
                SanitizedRegistrationEntry(
                    entryId = profile.userId,
                    memberLabel = OwnerDashboardAggregator.sanitizeDisplayLabel(profile),
                    maskedIdentity = "UID ${OwnerDashboardAggregator.maskUserId(profile.userId)}",
                    providerLabel = "Firestore Profile",
                    emailVerified = false,
                    registeredAtMillis = profile.createdAt?.toDate()?.time,
                    origin = MetricOrigin.FIRESTORE_ESTIMATE
                )
            }
        }

        val notice = when {
            summaryDoc == null ->
                "Showing read-only Firestore profile estimates. Run admin-tools/owner-admin-sync.mjs and deploy proposed security rules (on approval) for live Auth user totals, Cloud Monitoring usage, and content moderation updates."
            usageOrigin == MetricOrigin.UNAVAILABLE ->
                "Member analytics synced via Admin SDK; live Firestore Cloud Monitoring read/write/delete metrics require Cloud Monitoring API sync."
            else -> summaryDoc.sourceNote
        }

        return OwnerDashboardSnapshot(
            authStatus = authStatus,
            memberAnalytics = memberAnalytics,
            firestoreMonitoring = monitoringState,
            registrationHistory = historyItems,
            pendingContentReports = pendingContentReports,
            activityReports = activityReports,
            memberDirectory = uniqueProfiles.take(100),
            auditLogs = auditLogs,
            billingStatus = OwnerDashboardAggregator.defaultSparkBillingStatus(),
            lastSyncedAtMillis = summaryDoc?.syncedAt?.toDate()?.time,
            statusNotice = notice
        )
    }

    private suspend fun writeAuditLog(action: String, targetUid: String?, details: String) {
        val adminUid = auth.currentUser?.uid ?: return
        val logId = "alog_${UUID.randomUUID().toString().replace("-", "")}"
        val payload = mutableMapOf<String, Any?>(
            "logId" to logId,
            "adminUid" to adminUid,
            "action" to action.take(80),
            "details" to details.take(1000),
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!targetUid.isNullOrBlank()) {
            payload["targetUid"] = targetUid
        }
        try {
            db.collection("admin_audit_logs").document(logId).set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    override suspend fun updateContentReportStatus(reportId: String, newStatus: String) {
        val authStatus = verifyOwnerAccess(forceTokenRefresh = true)
        if (!authStatus.isAuthorized) {
            throw SecurityException(authStatus.denialReason ?: "Verified Owner claims required.")
        }
        val normalized = newStatus.trim().uppercase(Locale.US)
        if (normalized !in setOf("REVIEWED", "DISMISSED")) {
            throw IllegalArgumentException("Unsupported moderation status: $newStatus")
        }
        db.collection("content_reports").document(reportId)
            .update("status", normalized)
            .await()
        writeAuditLog("CONTENT_REPORT_$normalized", null, "Content report $reportId marked $normalized")
    }

    override suspend fun updateActivityReportStatus(reportId: String, newStatus: String) {
        val authStatus = verifyOwnerAccess(forceTokenRefresh = true)
        if (!authStatus.isAuthorized) {
            throw SecurityException(authStatus.denialReason ?: "Verified Owner claims required.")
        }
        val normalized = newStatus.trim().uppercase(Locale.US)
        if (normalized !in setOf("REVIEWED", "DISMISSED")) {
            throw IllegalArgumentException("Unsupported activity report status: $newStatus")
        }
        db.collection("activity_reports").document(reportId)
            .update("status", normalized)
            .await()
        writeAuditLog("ACTIVITY_REPORT_$normalized", null, "Activity report $reportId marked $normalized")
    }

    override suspend fun setMemberAccountStatus(targetUid: String, suspended: Boolean) {
        val authStatus = verifyOwnerAccess(forceTokenRefresh = true)
        if (!authStatus.isAuthorized) {
            throw SecurityException(authStatus.denialReason ?: "Verified Owner claims required.")
        }
        if (targetUid.isBlank()) return
        val nextStatus = if (suspended) "SUSPENDED" else "ACTIVE"
        db.collection("users").document(targetUid)
            .update(
                mapOf(
                    "accountStatus" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        writeAuditLog(
            action = if (suspended) "SUSPEND_MEMBER" else "RESTORE_MEMBER",
            targetUid = targetUid,
            details = "Member accountStatus updated to $nextStatus"
        )
    }
}
