package com.example.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityPost
import com.example.data.model.FriendRequest
import com.example.data.model.FriendStatus
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
    val onlineOnly: Boolean = false,
    val friendsOnly: Boolean = false
)

data class IncomingFriendRequestItem(
    val request: FriendRequest,
    val senderProfile: UserProfile
)

data class DashboardPreferences(
    val showOnlineStrip: Boolean = true,
    val showDailyActivitiesPreview: Boolean = true
)

data class DashboardUiState(
    val currentUserProfile: UserProfile? = null,
    val members: List<UserProfile> = emptyList(),
    val filteredMembers: List<UserProfile> = emptyList(),
    val onlineMembers: List<UserProfile> = emptyList(),
    val actualOnlineCount: Int = 0,
    val friendsCount: Int = 0,
    val friendStatusByUserId: Map<String, FriendStatus> = emptyMap(),
    val incomingFriendRequests: List<IncomingFriendRequestItem> = emptyList(),
    val activityPosts: List<ActivityPost> = emptyList(),
    val myActivityReactions: Map<String, String> = emptyMap(),
    val filter: DashboardFilter = DashboardFilter(),
    val dashboardPreferences: DashboardPreferences = DashboardPreferences(),
    val blockedUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isPostingActivity: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val requiresReauthForDeletion: Boolean = false,
    val errorMessage: String? = null
)

private data class FilteredMembersSnapshot(
    val members: List<UserProfile>,
    val filteredMembers: List<UserProfile>,
    val onlineMembers: List<UserProfile>,
    val blockedUserIds: Set<String>,
    val actualOnlineCount: Int,
    val friendsCount: Int,
    val friendStatusByUserId: Map<String, FriendStatus>,
    val incomingFriendRequests: List<IncomingFriendRequestItem>
)

private data class StatusFlags(
    val isLoading: Boolean,
    val isPostingActivity: Boolean,
    val isSavingProfile: Boolean,
    val isDeletingAccount: Boolean,
    val requiresReauthForDeletion: Boolean,
    val errorMessage: String?
)

private data class ActivityFeedSnapshot(
    val posts: List<ActivityPost>,
    val myReactions: Map<String, String>,
    val preferences: DashboardPreferences
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
    private val _friendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    private val _rawActivityPosts = MutableStateFlow<List<ActivityPost>>(emptyList())
    private val _myActivityReactions = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _dashboardPreferences = MutableStateFlow(DashboardPreferences())
    private val _isLoading = MutableStateFlow(true)
    private val _isPostingActivity = MutableStateFlow(false)
    private val _isSavingProfile = MutableStateFlow(false)
    private val _isDeletingAccount = MutableStateFlow(false)
    private val _requiresReauthForDeletion = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _filteredMembersData = combine(
        _rawMembers,
        _blockedUserIds,
        _friendRequests,
        _filter
    ) { members, blocked, requests, filter ->
        val currentUid = repository.getCurrentUserId()

        val statusMap = mutableMapOf<String, FriendStatus>()
        val incomingItems = mutableListOf<IncomingFriendRequestItem>()
        val membersById = members.associateBy { it.userId }

        requests.forEach { req ->
            val otherUid = req.participantUids.firstOrNull { it != currentUid }
                ?: if (req.requesterId == currentUid) req.recipientId else req.requesterId
            if (otherUid.isBlank() || blocked.contains(otherUid)) return@forEach

            when (req.status) {
                "ACCEPTED" -> statusMap[otherUid] = FriendStatus.FRIENDS
                "PENDING" -> {
                    if (req.requesterId == currentUid) {
                        statusMap[otherUid] = FriendStatus.REQUEST_SENT
                    } else if (req.recipientId == currentUid) {
                        statusMap[otherUid] = FriendStatus.REQUEST_RECEIVED
                        membersById[req.requesterId]?.let { senderProfile ->
                            incomingItems.add(IncomingFriendRequestItem(req, senderProfile))
                        }
                    }
                }
            }
        }

        val onlineList = members.filter {
            it.isOnline && !blocked.contains(it.userId) && it.userId != currentUid
        }
        val friendsTotal = statusMap.values.count { it == FriendStatus.FRIENDS }

        val filtered = members.filter { member ->
            if (member.userId == currentUid) return@filter false
            if (blocked.contains(member.userId)) return@filter false
            if (filter.onlineOnly && !member.isOnline) return@filter false
            if (filter.friendsOnly && statusMap[member.userId] != FriendStatus.FRIENDS) return@filter false
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
            onlineMembers = onlineList,
            blockedUserIds = blocked,
            actualOnlineCount = onlineList.size,
            friendsCount = friendsTotal,
            friendStatusByUserId = statusMap,
            incomingFriendRequests = incomingItems
        )
    }

    private val _loadingFlags = combine(
        _isLoading,
        _isPostingActivity,
        _isSavingProfile
    ) { isLoading, isPostingActivity, isSavingProfile ->
        Triple(isLoading, isPostingActivity, isSavingProfile)
    }

    private val _accountFlags = combine(
        _isDeletingAccount,
        _requiresReauthForDeletion,
        _errorMessage
    ) { isDeletingAccount, requiresReauth, errorMessage ->
        Triple(isDeletingAccount, requiresReauth, errorMessage)
    }

    private val _statusFlags = combine(
        _loadingFlags,
        _accountFlags
    ) { loading, account ->
        StatusFlags(
            isLoading = loading.first,
            isPostingActivity = loading.second,
            isSavingProfile = loading.third,
            isDeletingAccount = account.first,
            requiresReauthForDeletion = account.second,
            errorMessage = account.third
        )
    }

    private val _activityFeedSnapshot = combine(
        _rawActivityPosts,
        _myActivityReactions,
        _blockedUserIds,
        _dashboardPreferences
    ) { posts, myReactions, blocked, prefs ->
        ActivityFeedSnapshot(
            posts = posts.filter { !blocked.contains(it.authorId) },
            myReactions = myReactions,
            preferences = prefs
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _currentUserProfile,
        _filteredMembersData,
        _filter,
        _activityFeedSnapshot,
        _statusFlags
    ) { profile, membersData, filter, activitySnapshot, status ->
        DashboardUiState(
            currentUserProfile = profile,
            members = membersData.members,
            filteredMembers = membersData.filteredMembers,
            onlineMembers = membersData.onlineMembers,
            actualOnlineCount = membersData.actualOnlineCount,
            friendsCount = membersData.friendsCount,
            friendStatusByUserId = membersData.friendStatusByUserId,
            incomingFriendRequests = membersData.incomingFriendRequests,
            activityPosts = activitySnapshot.posts,
            myActivityReactions = activitySnapshot.myReactions,
            filter = filter,
            dashboardPreferences = activitySnapshot.preferences,
            blockedUserIds = membersData.blockedUserIds,
            isLoading = status.isLoading,
            isPostingActivity = status.isPostingActivity,
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
            observeRealtimeFriendRequests()
            observeRealtimeActivities()
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

    private fun observeRealtimeFriendRequests() {
        viewModelScope.launch {
            try {
                repository.observeFriendRequests().collect { list ->
                    _friendRequests.value = list
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to sync friend requests."
            }
        }
    }

    private fun observeRealtimeActivities() {
        viewModelScope.launch {
            try {
                repository.observeActivityPosts().collect { posts ->
                    _rawActivityPosts.value = posts
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load everyday activities."
            }
        }

        viewModelScope.launch {
            try {
                repository.observeMyActivityReactions().collect { reactions ->
                    _myActivityReactions.value = reactions
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to sync your emoji reactions."
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

    fun toggleFriendsOnly() {
        _filter.value = _filter.value.copy(friendsOnly = !_filter.value.friendsOnly)
    }

    fun toggleShowOnlineStrip() {
        _dashboardPreferences.value = _dashboardPreferences.value.copy(
            showOnlineStrip = !_dashboardPreferences.value.showOnlineStrip
        )
    }

    fun toggleShowDailyActivitiesPreview() {
        _dashboardPreferences.value = _dashboardPreferences.value.copy(
            showDailyActivitiesPreview = !_dashboardPreferences.value.showDailyActivitiesPreview
        )
    }

    fun createActivityPost(
        content: String,
        moodEmoji: String,
        activityTag: String,
        onSuccess: () -> Unit = {}
    ) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            _errorMessage.value = "Write a short activity update before posting."
            return
        }
        viewModelScope.launch {
            _isPostingActivity.value = true
            _errorMessage.value = null
            try {
                repository.createActivityPost(
                    authorProfile = _currentUserProfile.value,
                    content = trimmed,
                    moodEmoji = moodEmoji,
                    activityTag = activityTag
                )
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not publish your activity right now."
            } finally {
                _isPostingActivity.value = false
            }
        }
    }

    fun toggleActivityReaction(postId: String, emoji: String) {
        viewModelScope.launch {
            try {
                repository.toggleActivityReaction(postId, emoji)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not update emoji reaction."
            }
        }
    }

    fun deleteActivityPost(postId: String) {
        viewModelScope.launch {
            try {
                repository.deleteActivityPost(postId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not delete activity post."
            }
        }
    }

    fun sendFriendRequest(targetUid: String) {
        viewModelScope.launch {
            try {
                repository.sendFriendRequest(targetUid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not send friend request."
            }
        }
    }

    fun acceptFriendRequest(otherUid: String) {
        val currentUid = repository.getCurrentUserId() ?: return
        val reqId = _friendRequests.value.firstOrNull {
            it.status == "PENDING" && it.requesterId == otherUid && it.recipientId == currentUid
        }?.requestId ?: repository.getFriendRequestIdForUsers(otherUid)

        viewModelScope.launch {
            try {
                repository.acceptFriendRequest(reqId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not accept friend request."
            }
        }
    }

    fun removeFriendOrCancel(otherUid: String) {
        viewModelScope.launch {
            try {
                repository.removeFriendOrRequest(otherUid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not update friend status."
            }
        }
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
