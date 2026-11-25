package com.example.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.OwnerAuthStatus
import com.example.data.model.OwnerDashboardSnapshot
import com.example.data.repository.FirebaseOwnerAdminRepository
import com.example.data.repository.OwnerAdminDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OwnerAdminSection(val label: String) {
    MEMBERS("Analytics & History"),
    MONITORING("Firestore & Billing"),
    MODERATION("Moderation")
}

data class OwnerAdminUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isUpdatingModeration: Boolean = false,
    val authStatus: OwnerAuthStatus = OwnerAuthStatus(),
    val snapshot: OwnerDashboardSnapshot = OwnerDashboardSnapshot(),
    val selectedSection: OwnerAdminSection = OwnerAdminSection.MEMBERS,
    val statusBanner: String? = null,
    val errorMessage: String? = null
)

class OwnerAdminViewModel(
    application: Application,
    private val adminRepository: OwnerAdminDataSource
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        FirebaseOwnerAdminRepository(application)
    )

    private val _uiState = MutableStateFlow(OwnerAdminUiState())
    val uiState: StateFlow<OwnerAdminUiState> = _uiState.asStateFlow()

    init {
        refreshDashboard(initialLoad = true)
    }

    fun selectSection(section: OwnerAdminSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun refreshDashboard(initialLoad: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initialLoad,
                    isRefreshing = !initialLoad,
                    errorMessage = null
                )
            }
            try {
                val snapshot = adminRepository.loadOwnerDashboard()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        authStatus = snapshot.authStatus,
                        snapshot = snapshot,
                        statusBanner = if (!snapshot.authStatus.isAuthorized) {
                            snapshot.authStatus.denialReason
                        } else {
                            it.statusBanner
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "Unable to verify owner dashboard state."
                    )
                }
            }
        }
    }

    fun updateReportStatus(reportId: String, newStatus: String) {
        if (reportId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingModeration = true, errorMessage = null) }
            try {
                adminRepository.updateContentReportStatus(reportId, newStatus)
                val updatedReports = _uiState.value.snapshot.pendingContentReports.map { report ->
                    if (report.reportId == reportId) report.copy(status = newStatus.uppercase()) else report
                }
                _uiState.update {
                    it.copy(
                        isUpdatingModeration = false,
                        snapshot = it.snapshot.copy(pendingContentReports = updatedReports),
                        statusBanner = "Content report marked as ${newStatus.uppercase()}."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingModeration = false,
                        errorMessage = e.message ?: "Failed to update moderation report status."
                    )
                }
            }
        }
    }

    fun updateActivityReportStatus(reportId: String, newStatus: String) {
        if (reportId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingModeration = true, errorMessage = null) }
            try {
                adminRepository.updateActivityReportStatus(reportId, newStatus)
                val updated = _uiState.value.snapshot.activityReports.map { report ->
                    if (report.reportId == reportId) report.copy(status = newStatus.uppercase()) else report
                }
                _uiState.update {
                    it.copy(
                        isUpdatingModeration = false,
                        snapshot = it.snapshot.copy(activityReports = updated),
                        statusBanner = "Activity report marked as ${newStatus.uppercase()}."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingModeration = false,
                        errorMessage = e.message ?: "Failed to update activity report status."
                    )
                }
            }
        }
    }

    fun toggleMemberSuspension(targetUid: String, suspended: Boolean) {
        if (targetUid.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingModeration = true, errorMessage = null) }
            try {
                adminRepository.setMemberAccountStatus(targetUid, suspended)
                val nextStatus = if (suspended) "SUSPENDED" else "ACTIVE"
                val updatedMembers = _uiState.value.snapshot.memberDirectory.map { m ->
                    if (m.userId == targetUid) m.copy(accountStatus = nextStatus) else m
                }
                _uiState.update {
                    it.copy(
                        isUpdatingModeration = false,
                        snapshot = it.snapshot.copy(memberDirectory = updatedMembers),
                        statusBanner = if (suspended) "Member account suspended." else "Member account restored."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpdatingModeration = false,
                        errorMessage = e.message ?: "Failed to update member status."
                    )
                }
            }
        }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
