package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class MetricOrigin {
    LIVE_ADMIN_SDK,
    LIVE_MONITORING,
    FIRESTORE_ESTIMATE,
    UNAVAILABLE
}

enum class QuotaSeverity {
    OK,
    WARNING,
    CRITICAL,
    UNAVAILABLE
}

data class OwnerAuthStatus(
    val signedInEmail: String? = null,
    val emailVerified: Boolean = false,
    val hasOwnerClaim: Boolean = false,
    val hasAdminClaim: Boolean = false,
    val expectedOwnerEmail: String = "gptgamestudioph@gmail.com",
    val isAuthorized: Boolean = false,
    val denialReason: String? = null
)

@Keep
@IgnoreExtraProperties
data class AdminDashboardSummaryDoc(
    @DocumentId val id: String = "",
    val totalAuthUsers: Long? = null,
    val verifiedAuthUsers: Long? = null,
    val deduplicatedProfilesCount: Long? = null,
    val dailyRegistrations: Long? = null,
    val weeklyRegistrations: Long? = null,
    val monthlyRegistrations: Long? = null,
    val readsToday: Long? = null,
    val writesToday: Long? = null,
    val deletesToday: Long? = null,
    val storageBytes: Long? = null,
    val authMetricsOrigin: String = "UNAVAILABLE",
    val usageMetricsOrigin: String = "UNAVAILABLE",
    val billingPlan: String = "SPARK",
    val syncedAt: Timestamp? = null,
    val sourceNote: String? = null
)

@Keep
@IgnoreExtraProperties
data class AdminDailyUsageDoc(
    @DocumentId val id: String = "",
    val dateKey: String = "",
    val registrations: Long = 0L,
    val reads: Long? = null,
    val writes: Long? = null,
    val deletes: Long? = null,
    val storageBytes: Long? = null,
    val usageOrigin: String = "UNAVAILABLE"
)

@Keep
@IgnoreExtraProperties
data class AdminRegistrationHistoryDoc(
    @DocumentId val id: String = "",
    val entryId: String = "",
    val memberLabel: String = "",
    val maskedEmail: String? = null,
    val providerId: String = "google.com",
    val emailVerified: Boolean = false,
    val registeredAt: Timestamp? = null
)

data class RegistrationTrendPoint(
    val dateKey: String,
    val registrationsCount: Int,
    val origin: MetricOrigin
)

data class SanitizedRegistrationEntry(
    val entryId: String,
    val memberLabel: String,
    val maskedIdentity: String,
    val providerLabel: String,
    val emailVerified: Boolean,
    val registeredAtMillis: Long?,
    val origin: MetricOrigin
)

data class FirestoreDailyUsagePoint(
    val dateKey: String,
    val reads: Long?,
    val writes: Long?,
    val deletes: Long?,
    val origin: MetricOrigin
)

data class QuotaWarningItem(
    val resourceName: String,
    val used: Long?,
    val sparkDailyLimit: Long,
    val severity: QuotaSeverity,
    val origin: MetricOrigin,
    val message: String
)

data class MemberAnalyticsState(
    val totalAuthUsers: Long? = null,
    val authMetricsOrigin: MetricOrigin = MetricOrigin.UNAVAILABLE,
    val observedProfileUsers: Int = 0,
    val profileMetricsOrigin: MetricOrigin = MetricOrigin.FIRESTORE_ESTIMATE,
    val dailyRegistrations: Int = 0,
    val weeklyRegistrations: Int = 0,
    val monthlyRegistrations: Int = 0,
    val duplicatesExcluded: Int = 0,
    val registrationTrends: List<RegistrationTrendPoint> = emptyList()
)

data class FirestoreMonitoringState(
    val readsToday: Long? = null,
    val writesToday: Long? = null,
    val deletesToday: Long? = null,
    val storageBytes: Long? = null,
    val observableDocCounts: Map<String, Int> = emptyMap(),
    val usageOrigin: MetricOrigin = MetricOrigin.UNAVAILABLE,
    val dailyUsageTrends: List<FirestoreDailyUsagePoint> = emptyList(),
    val quotaWarnings: List<QuotaWarningItem> = emptyList()
)

data class BillingCapabilityStatus(
    val activePlan: String = "Firebase Spark Plan ($0/month)",
    val autoUpgradeDisabled: Boolean = true,
    val blazeRequiredCapabilities: List<String> = emptyList(),
    val sparkCompatibleAlternatives: List<String> = emptyList()
)

data class OwnerDashboardSnapshot(
    val authStatus: OwnerAuthStatus = OwnerAuthStatus(),
    val memberAnalytics: MemberAnalyticsState = MemberAnalyticsState(),
    val firestoreMonitoring: FirestoreMonitoringState = FirestoreMonitoringState(),
    val registrationHistory: List<SanitizedRegistrationEntry> = emptyList(),
    val pendingContentReports: List<ContentReportRecord> = emptyList(),
    val activityReports: List<ActivityReportRecord> = emptyList(),
    val memberDirectory: List<UserProfile> = emptyList(),
    val auditLogs: List<AdminAuditLog> = emptyList(),
    val billingStatus: BillingCapabilityStatus = BillingCapabilityStatus(),
    val lastSyncedAtMillis: Long? = null,
    val statusNotice: String? = null
)
