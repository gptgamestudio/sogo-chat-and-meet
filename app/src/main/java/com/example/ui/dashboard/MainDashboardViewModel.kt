package com.example.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.repository.RecentLoginRequiredException
import com.example.data.repository.SogoDataSource
import com.example.data.repository.SogoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardFilter(
    val searchQuery: String = "",
    val genderFilter: String = "ALL", // "ALL", "Female", "Male", "Non-Binary"
    val countryFilter: String = "ALL", // "ALL", "Philippines", "Overseas"
    val ageRange: String = "ALL", // "ALL", "18-24", "25-34", "35+"
    val onlineOnly: Boolean = false
)

data class DashboardUiState(
    val currentUserProfile: UserProfile? = null,
    val members: List<UserProfile> = emptyList(),
    val filteredMembers: List<UserProfile> = emptyList(),
    val actualOnlineCount: Int = 0,
    val filter: DashboardFilter = DashboardFilter(),
    val blockedUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isSavingProfile: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val requiresReauthForDeletion: Boolean = false,
    val errorMessage: String? = null
)

private data class FilteredMembersSnapshot(
    val members: List<UserProfile>,
    val filteredMembers: List<UserProfile>,
    val blockedUserIds: Set<String>,
    val actualOnlineCount: Int
)

private data class StatusFlags(
    val isLoading: Boolean,
    val isSavingProfile: Boolean,
    val isDeletingAccount: Boolean,
    val requiresReauthForDeletion: Boolean,
    val errorMessage: String?
)

class MainDashboardViewModel(
    application: Application,
    private val repository: SogoDataSource
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, SogoRepository(application))

    private val _filter = MutableStateFlow(DashboardFilter())
    val filter = _filter.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile = _currentUserProfile.asStateFlow()

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    private val _rawMembers = MutableStateFlow<List<UserProfile>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _isSavingProfile = MutableStateFlow(false)
    private val _isDeletingAccount = MutableStateFlow(false)
    private val _requiresReauthForDeletion = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _filteredMembersData = combine(
        _rawMembers,
        _blockedUserIds,
        _filter
    ) { members, blocked, filter ->
        val currentUid = repository.getCurrentUserId()
        val actualOnline = members.count { it.isOnline && !blocked.contains(it.userId) && it.userId != currentUid }
        val filtered = members.filter { member ->
            if (member.userId == currentUid) return@filter false
            if (blocked.contains(member.userId)) return@filter false
            if (filter.onlineOnly && !member.isOnline) return@filter false
            if (filter.genderFilter != "ALL" && !member.gender.equals(filter.genderFilter, ignoreCase = true)) {
                return@filter false
            }
            if (filter.countryFilter == "Philippines" && !member.country.contains("Philippines", ignoreCase = true)) {
                return@filter false
            } else if (filter.countryFilter == "Overseas" && member.country.contains("Philippines", ignoreCase = true)) {
                return@filter false
            }
            when (filter.ageRange) {
                "18-24" -> if (member.age !in 18..24) return@filter false
                "25-34" -> if (member.age !in 25..34) return@filter false
                "35+" -> if (member.age < 35) return@filter false
            }
            if (filter.searchQuery.isNotBlank()) {
                val q = filter.searchQuery.trim().lowercase()
                val matchName = member.displayName.lowercase().contains(q)
                val matchCity = (member.city ?: "").lowercase().contains(q)
                val matchCountry = member.country.lowercase().contains(q)
                val matchInterests = member.interests.any { it.lowercase().contains(q) }
                val matchPersonality = (member.personality ?: "").lowercase().contains(q)
                if (!matchName && !matchCity && !matchCountry && !matchInterests && !matchPersonality) return@filter false
            }
            true
        }
        FilteredMembersSnapshot(
            members = members,
            filteredMembers = filtered,
            blockedUserIds = blocked,
            actualOnlineCount = actualOnline
        )
    }

    private val _statusFlags = combine(
        _isLoading,
        _isSavingProfile,
        _isDeletingAccount,
        _requiresReauthForDeletion,
        _errorMessage
    ) { loading, saving, deleting, reauthNeeded, error ->
        StatusFlags(
            isLoading = loading,
            isSavingProfile = saving,
            isDeletingAccount = deleting,
            requiresReauthForDeletion = reauthNeeded,
            errorMessage = error
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _currentUserProfile,
        _filteredMembersData,
        _filter,
        _statusFlags
    ) { profile, membersData, filter, status ->
        DashboardUiState(
            currentUserProfile = profile,
            members = membersData.members,
            filteredMembers = membersData.filteredMembers,
            actualOnlineCount = membersData.actualOnlineCount,
            filter = filter,
            blockedUserIds = membersData.blockedUserIds,
            isLoading = status.isLoading,
            isSavingProfile = status.isSavingProfile,
            isDeletingAccount = status.isDeletingAccount,
            requiresReauthForDeletion = status.requiresReauthForDeletion,
            errorMessage = status.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        val uid = repository.getCurrentUserId()
        if (uid != null) {
            loadUserData(uid)
            observeRealtimeMembers()
        } else {
            _isLoading.value = false
        }
    }

    private fun loadUserData(uid: String) {
        viewModelScope.launch {
            try {
                repository.observeCurrentUserProfile(uid).collect { profile ->
                    _currentUserProfile.value = profile
                    if (profile == null) {
                        _isLoading.value = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Failed to load your profile."
            }
        }

        viewModelScope.launch {
            try {
                repository.observeBlockedUserIds().collect { blocked ->
                    _blockedUserIds.value = blocked
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to sync blocked users list."
            }
        }
    }

    private fun observeRealtimeMembers() {
        viewModelScope.launch {
            try {
                repository.observeMembers().collect { list ->
                    _rawMembers.value = list
                    _isLoading.value = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Failed to load community members."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun reportError(message: String) {
        _errorMessage.value = message
    }

    fun updateSearchQuery(query: String) {
        _filter.value = _filter.value.copy(searchQuery = query)
    }

    fun setGenderFilter(gender: String) {
        _filter.value = _filter.value.copy(genderFilter = gender)
    }

    fun setCountryFilter(country: String) {
        _filter.value = _filter.value.copy(countryFilter = country)
    }

    fun setAgeRange(range: String) {
        _filter.value = _filter.value.copy(ageRange = range)
    }

    fun toggleOnlineOnly() {
        _filter.value = _filter.value.copy(onlineOnly = !_filter.value.onlineOnly)
    }

    fun createOrUpdateProfile(
        displayName: String,
        birthday: String,
        age: Int,
        gender: String,
        country: String,
        city: String,
        photoUrl: String?,
        aboutMe: String,
        personality: String,
        interests: List<String>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (age < 18) {
            val msg = "You must be at least 18 years old to join SOGO CHAT AND MEET."
            _errorMessage.value = msg
            onError(msg)
            return
        }

        val uid = try {
            repository.requireUserId()
        } catch (e: Exception) {
            val msg = e.message ?: "You must be signed in before saving your profile."
            _errorMessage.value = msg
            onError(msg)
            return
        }

        viewModelScope.launch {
            _isSavingProfile.value = true
            _errorMessage.value = null
            try {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName.trim(),
                    birthday = birthday,
                    age = age,
                    gender = gender,
                    country = country,
                    city = city.trim().takeIf { it.isNotBlank() } ?: "Manila",
                    photoUrl = photoUrl,
                    aboutMe = aboutMe.trim(),
                    personality = personality,
                    interests = interests,
                    isOnline = true
                )
                repository.saveUserProfile(profile)
                _currentUserProfile.value = profile
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Could not save profile. Please try again."
                _errorMessage.value = msg
                onError(msg)
            } finally {
                _isSavingProfile.value = false
            }
        }
    }

    fun setPresence(isOnline: Boolean, force: Boolean = false) {
        if (_currentUserProfile.value == null && isOnline) return
        viewModelScope.launch {
            repository.updatePresence(isOnline, force = force)
        }
    }

    fun deleteAccount(
        googleIdToken: String? = null,
        onReauthRequired: () -> Unit = {},
        onCompleted: () -> Unit
    ) {
        viewModelScope.launch {
            _isDeletingAccount.value = true
            _requiresReauthForDeletion.value = false
            _errorMessage.value = null
            try {
                repository.deleteUserAccount(googleIdToken)
                onCompleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RecentLoginRequiredException) {
                _requiresReauthForDeletion.value = true
                _errorMessage.value = e.message
                onReauthRequired()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to delete account. Your existing profile was preserved."
            } finally {
                _isDeletingAccount.value = false
            }
        }
    }
}
