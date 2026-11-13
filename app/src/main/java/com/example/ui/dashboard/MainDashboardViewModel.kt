package com.example.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.repository.SogoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardFilter(
    val searchQuery: String = "",
    val genderFilter: String = "ALL", // "ALL", "Female", "Male"
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
    val errorMessage: String? = null
)

class MainDashboardViewModel(
    application: Application,
    private val repository: SogoRepository = SogoRepository(application)
) : AndroidViewModel(application) {

    private val _filter = MutableStateFlow(DashboardFilter())
    val filter = _filter.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile = _currentUserProfile.asStateFlow()

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())

    private val _rawMembers = MutableStateFlow<List<UserProfile>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
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
        Triple(members, filtered, actualOnline)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _currentUserProfile,
        _filteredMembersData,
        _filter,
        _isLoading
    ) { profile, membersData, filter, loading ->
        val (members, filtered, actualOnline) = membersData
        DashboardUiState(
            currentUserProfile = profile,
            members = members,
            filteredMembers = filtered,
            actualOnlineCount = actualOnline,
            filter = filter,
            blockedUserIds = _blockedUserIds.value,
            isLoading = loading,
            errorMessage = _errorMessage.value
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
        }
    }

    private fun loadUserData(uid: String) {
        viewModelScope.launch {
            try {
                repository.observeCurrentUserProfile(uid).collect { profile ->
                    _currentUserProfile.value = profile
                    // Set online presence
                    if (profile != null) {
                        repository.updatePresence(isOnline = true)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }

        viewModelScope.launch {
            try {
                repository.observeBlockedUserIds().collect { blocked ->
                    _blockedUserIds.value = blocked
                }
            } catch (e: Exception) {
                // Non-fatal
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
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message
            }
        }
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
        onSuccess: () -> Unit = {}
    ) {
        val uid = repository.requireUserId()
        viewModelScope.launch {
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
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun setPresence(isOnline: Boolean) {
        viewModelScope.launch {
            repository.updatePresence(isOnline)
        }
    }

    fun deleteAccount(onCompleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteUserAccount()
                onCompleted()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
}
