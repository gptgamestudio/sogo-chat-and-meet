package com.example.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.matching.MatchRecommender
import com.example.data.matching.RecommendedMember
import com.example.data.model.ActivityComment
import com.example.data.model.ActivityPost
import com.example.data.model.Conversation
import com.example.data.model.FriendRequest
import com.example.data.model.FriendStatus
import com.example.data.model.GalleryPhoto
import com.example.data.model.NotificationRecord
import com.example.data.model.ProfileLikeRecord
import com.example.data.model.ProfileVisitorRecord
import com.example.data.model.UserProfile
import com.example.data.repository.RecentLoginRequiredException
import com.example.data.repository.SogoDataSource
import com.example.data.repository.SogoRepository
import com.example.ui.common.MemberPresenceFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val friendsOnly: Boolean = false,
    val offlineOnly: Boolean = false,
    val suggestedOnly: Boolean = false,
    val mutualInterestsOnly: Boolean = false,
    val followingOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val preferredMatchOnly: Boolean = false
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
    val pagedFilteredMembers: List<UserProfile> = emptyList(),
    val hasMoreMembers: Boolean = false,
    val onlineMembers: List<UserProfile> = emptyList(),
    val onlineMembersYouMayLike: List<RecommendedMember> = emptyList(),
    val suggestedMatches: List<RecommendedMember> = emptyList(),
    val blockedMembers: List<UserProfile> = emptyList(),
    val actualOnlineCount: Int = 0,
    val friendsCount: Int = 0,
    val unreadMessagesCount: Int = 0,
    val receivedLikesCount: Int = 0,
    val conversations: List<Conversation> = emptyList(),
    val notifications: List<NotificationRecord> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val followingIds: Set<String> = emptySet(),
    val favoriteIds: Set<String> = emptySet(),
    val profileLikesGivenIds: Set<String> = emptySet(),
    val profileLikesReceived: List<ProfileLikeRecord> = emptyList(),
    val profileVisitors: List<ProfileVisitorRecord> = emptyList(),
    val myGalleryPhotos: List<GalleryPhoto> = emptyList(),
    val friendStatusByUserId: Map<String, FriendStatus> = emptyMap(),
    val incomingFriendRequests: List<IncomingFriendRequestItem> = emptyList(),
    val activityPosts: List<ActivityPost> = emptyList(),
    val filteredActivityPosts: List<ActivityPost> = emptyList(),
    val activityCategoryFilter: String = "ALL",
    val myActivityReactions: Map<String, String> = emptyMap(),
    val activeCommentsPostId: String? = null,
    val activePostComments: List<ActivityComment> = emptyList(),
    val filter: DashboardFilter = DashboardFilter(),
    val dashboardPreferences: DashboardPreferences = DashboardPreferences(),
    val blockedUserIds: Set<String> = emptySet(),
    val myBlockedUserIds: Set<String> = emptySet(),
    val blockStatusMessage: String? = null,
    val actionStatusMessage: String? = null,
    val isLoading: Boolean = true,
    val hasResolvedProfile: Boolean = false,
    val profileLoadError: String? = null,
    val isPostingActivity: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isUpdatingPhoto: Boolean = false,
    val photoStatusMessage: String? = null,
    val isDeletingAccount: Boolean = false,
    val requiresReauthForDeletion: Boolean = false,
    val errorMessage: String? = null,
    val isOwnerCandidate: Boolean = false,
    val knownUsersById: Map<String, UserProfile> = emptyMap(),
    val isRefreshingFeed: Boolean = false,
    val isBoostingProfile: Boolean = false
)

private data class FilteredMembersSnapshot(
    val members: List<UserProfile>,
    val filteredMembers: List<UserProfile>,
    val pagedFilteredMembers: List<UserProfile>,
    val hasMoreMembers: Boolean,
    val onlineMembers: List<UserProfile>,
    val onlineMembersYouMayLike: List<RecommendedMember>,
    val suggestedMatches: List<RecommendedMember>,
    val blockedMembers: List<UserProfile>,
    val blockedUserIds: Set<String>,
    val myBlockedUserIds: Set<String>,
    val actualOnlineCount: Int,
    val friendsCount: Int,
    val friendStatusByUserId: Map<String, FriendStatus>,
    val incomingFriendRequests: List<IncomingFriendRequestItem>
)

private data class SocialGraphSnapshot(
    val conversations: List<Conversation>,
    val unreadMessagesCount: Int,
    val notifications: List<NotificationRecord>,
    val unreadNotificationsCount: Int,
    val followingIds: Set<String>,
    val favoriteIds: Set<String>,
    val profileLikesGivenIds: Set<String>,
    val profileLikesReceived: List<ProfileLikeRecord>,
    val profileVisitors: List<ProfileVisitorRecord>,
    val myGalleryPhotos: List<GalleryPhoto>,
    val extraPartnerProfiles: Map<String, UserProfile> = emptyMap()
)

private data class LoadingFlags(
    val isLoading: Boolean,
    val isPostingActivity: Boolean,
    val isSavingProfile: Boolean,
    val isRefreshingFeed: Boolean,
    val isBoostingProfile: Boolean
)

private data class StatusFlags(
    val isLoading: Boolean,
    val hasResolvedProfile: Boolean,
    val profileLoadError: String?,
    val isPostingActivity: Boolean,
    val isSavingProfile: Boolean,
    val isUpdatingPhoto: Boolean,
    val photoStatusMessage: String?,
    val blockStatusMessage: String?,
    val actionStatusMessage: String?,
    val isDeletingAccount: Boolean,
    val requiresReauthForDeletion: Boolean,
    val errorMessage: String?,
    val isOwnerCandidate: Boolean,
    val isRefreshingFeed: Boolean,
    val isBoostingProfile: Boolean
)

private data class ActivityFeedSnapshot(
    val posts: List<ActivityPost>,
    val filteredPosts: List<ActivityPost>,
    val categoryFilter: String,
    val myReactions: Map<String, String>,
    val activeCommentsPostId: String?,
    val activePostComments: List<ActivityComment>,
    val preferences: DashboardPreferences
)

class MainDashboardViewModel(
    application: Application,
    private val repository: SogoDataSource
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, SogoRepository(application))

    private val _filter = MutableStateFlow(DashboardFilter())
    val filter = _filter.asStateFlow()

    private val _discoveryPageLimit = MutableStateFlow(INITIAL_MEMBERS_PAGE_SIZE)
    private val _activityCategoryFilter = MutableStateFlow("ALL")

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile = _currentUserProfile.asStateFlow()

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    private val _myBlockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    private val _rawMembers = MutableStateFlow<List<UserProfile>>(emptyList())
    private val _friendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    private val _rawActivityPosts = MutableStateFlow<List<ActivityPost>>(emptyList())
    private val _myActivityReactions = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _activeCommentsPostId = MutableStateFlow<String?>(null)
    private val _activePostComments = MutableStateFlow<List<ActivityComment>>(emptyList())
    private val _rawConversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val _rawNotifications = MutableStateFlow<List<NotificationRecord>>(emptyList())
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    private val _profileLikesGivenIds = MutableStateFlow<Set<String>>(emptySet())
    private val _profileLikesReceived = MutableStateFlow<List<ProfileLikeRecord>>(emptyList())
    private val _profileVisitors = MutableStateFlow<List<ProfileVisitorRecord>>(emptyList())
    private val _myGalleryPhotos = MutableStateFlow<List<GalleryPhoto>>(emptyList())
    private val _dashboardPreferences = MutableStateFlow(DashboardPreferences())
    private val _isLoading = MutableStateFlow(true)
    private val _hasResolvedProfile = MutableStateFlow(false)
    private val _profileLoadError = MutableStateFlow<String?>(null)
    private val _isPostingActivity = MutableStateFlow(false)
    private val _isSavingProfile = MutableStateFlow(false)
    private val _isUpdatingPhoto = MutableStateFlow(false)
    private val _photoStatusMessage = MutableStateFlow<String?>(null)
    private val _blockStatusMessage = MutableStateFlow<String?>(null)
    private val _actionStatusMessage = MutableStateFlow<String?>(null)
    private val _isDeletingAccount = MutableStateFlow(false)
    private val _requiresReauthForDeletion = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isOwnerCandidate = MutableStateFlow(repository.isOwnerCandidateSignedIn())
    private val _extraPartnerProfiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    private val _isRefreshingFeed = MutableStateFlow(false)
    private val _isBoostingProfile = MutableStateFlow(false)

    private val _blockedPairIds = combine(_blockedUserIds, _myBlockedUserIds) { allBlocked, myBlocked ->
        allBlocked to myBlocked
    }

    private val _filterControls = combine(
        _filter,
        _discoveryPageLimit,
        _followingIds,
        _favoriteIds
    ) { filter, pageLimit, following, favorites ->
        Triple(filter, pageLimit, following to favorites)
    }

    private val _filteredMembersData = combine(
        _rawMembers,
        _currentUserProfile,
        _blockedPairIds,
        _friendRequests,
        _filterControls
    ) { members, currentProfile, blockedPair, requests, controls ->
        val blocked = blockedPair.first
        val myBlocked = blockedPair.second
        val filter = controls.first
        val pageLimit = controls.second
        val followingIds = controls.third.first
        val favoriteIds = controls.third.second
        val currentUid = repository.getCurrentUserId()

        val statusMap = mutableMapOf<String, FriendStatus>()
        val incomingItems = mutableListOf<IncomingFriendRequestItem>()
        val unblockedMembers = members.filter { member ->
            MatchRecommender.isDiscoverableMember(member, currentUid, blocked)
        }
        val membersById = unblockedMembers.associateBy { it.userId }
        val blockedMembersList = members.filter { it.userId != currentUid && myBlocked.contains(it.userId) }

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

        val nowMs = System.currentTimeMillis()
        val onlineList = unblockedMembers.filter { MemberPresenceFormatter.isEffectivelyOnline(it, nowMs) }
        val onlineRecommendations = MatchRecommender.recommendOnlineMembersYouMayLike(
            currentUser = currentProfile,
            allMembers = unblockedMembers,
            blockedUserIds = blocked,
            nowMs = nowMs
        )
        val suggestedRecommendations = MatchRecommender.recommendSuggestedMatches(
            currentUser = currentProfile,
            allMembers = unblockedMembers,
            blockedUserIds = blocked,
            nowMs = nowMs
        )
        val friendsTotal = statusMap.values.count { it == FriendStatus.FRIENDS }

        val baseFiltered = unblockedMembers.filter { member ->
            val isOnline = MemberPresenceFormatter.isEffectivelyOnline(member, nowMs)
            if (filter.onlineOnly && !isOnline) return@filter false
            if (filter.offlineOnly && isOnline) return@filter false
            if (filter.friendsOnly && statusMap[member.userId] != FriendStatus.FRIENDS) return@filter false
            if (filter.followingOnly && !followingIds.contains(member.userId)) return@filter false
            if (filter.favoritesOnly && !favoriteIds.contains(member.userId)) return@filter false
            if (filter.mutualInterestsOnly && MatchRecommender.computeMutualInterests(currentProfile, member).isEmpty()) {
                return@filter false
            }
            if (filter.preferredMatchOnly && currentProfile != null) {
                val prefGender = currentProfile.preferredGender
                if (prefGender != "ALL" && prefGender.isNotBlank() &&
                    !member.gender.equals(prefGender, ignoreCase = true)
                ) {
                    return@filter false
                }
                val minA = currentProfile.preferredMinAge.coerceAtLeast(18)
                val maxA = currentProfile.preferredMaxAge.coerceAtLeast(minA)
                if (member.age !in minA..maxA) return@filter false
            }
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
                val matchUsername = (member.username ?: "").lowercase().contains(q)
                val matchCity = (member.city ?: "").lowercase().contains(q)
                val matchCountry = member.country.lowercase().contains(q)
                val matchInterests = (member.interests + member.hobbies).any { it.lowercase().contains(q) }
                val matchPersonality = (member.personality ?: "").lowercase().contains(q)
                if (!matchName && !matchUsername && !matchCity && !matchCountry && !matchInterests && !matchPersonality) {
                    return@filter false
                }
            }
            true
        }

        val sortedFiltered = if (filter.suggestedOnly) {
            baseFiltered.sortedByDescending { candidate ->
                MatchRecommender.scoreCandidate(currentProfile, candidate, nowMs).matchScore
            }
        } else {
            baseFiltered
        }

        val paged = sortedFiltered.take(pageLimit)
        FilteredMembersSnapshot(
            members = unblockedMembers,
            filteredMembers = sortedFiltered,
            pagedFilteredMembers = paged,
            hasMoreMembers = sortedFiltered.size > paged.size,
            onlineMembers = onlineList,
            onlineMembersYouMayLike = onlineRecommendations,
            suggestedMatches = suggestedRecommendations,
            blockedMembers = blockedMembersList,
            blockedUserIds = blocked,
            myBlockedUserIds = myBlocked,
            actualOnlineCount = onlineList.size,
            friendsCount = friendsTotal,
            friendStatusByUserId = statusMap,
            incomingFriendRequests = incomingItems
        )
    }

    private val _socialConnectionsFlow = combine(
        _followingIds,
        _favoriteIds,
        _profileLikesGivenIds,
        _profileLikesReceived,
        _profileVisitors
    ) { following, favorites, likesGiven, likesReceived, visitors ->
        listOf(following, favorites, likesGiven, likesReceived, visitors)
    }

    private val _inboxSocialFlow = combine(
        _rawConversations,
        _rawNotifications,
        _extraPartnerProfiles
    ) { conversations, notifications, extraPartners ->
        Triple(conversations, notifications, extraPartners)
    }

    @Suppress("UNCHECKED_CAST")
    private val _socialGraphSnapshot = combine(
        _inboxSocialFlow,
        _blockedUserIds,
        _myGalleryPhotos,
        _socialConnectionsFlow
    ) { inboxTriple, blocked, gallery, connections ->
        val conversations = inboxTriple.first
        val notifications = inboxTriple.second
        val extraPartners = inboxTriple.third
        val uid = repository.getCurrentUserId()
        val unblockedConversations = conversations
            .filter { conv ->
                val partnerId = conv.participantUids.firstOrNull { it != uid }
                partnerId == null || !blocked.contains(partnerId)
            }
            .sortedByDescending { it.lastMessageTimestamp?.toDate()?.time ?: it.updatedAt?.toDate()?.time ?: 0L }

        val unreadMsgs = if (uid.isNullOrBlank()) {
            0
        } else {
            unblockedConversations.sumOf { conv ->
                (conv.unreadCountByUid[uid] ?: 0L).coerceAtLeast(0L).toInt()
            }
        }

        val likesReceived = (connections[3] as List<ProfileLikeRecord>)
            .filter { !blocked.contains(it.likerId) }
        val visitors = (connections[4] as List<ProfileVisitorRecord>)
            .filter { !blocked.contains(it.visitorId) }
        val visibleNotifs = notifications.filter { notif ->
            notif.senderId == null || !blocked.contains(notif.senderId)
        }

        SocialGraphSnapshot(
            conversations = unblockedConversations,
            unreadMessagesCount = unreadMsgs,
            notifications = visibleNotifs,
            unreadNotificationsCount = visibleNotifs.count { !it.isRead },
            followingIds = connections[0] as Set<String>,
            favoriteIds = connections[1] as Set<String>,
            profileLikesGivenIds = connections[2] as Set<String>,
            profileLikesReceived = likesReceived,
            profileVisitors = visitors,
            myGalleryPhotos = gallery,
            extraPartnerProfiles = extraPartners
        )
    }

    private val _loadingFlags = combine(
        _isLoading,
        _isPostingActivity,
        _isSavingProfile,
        _isRefreshingFeed,
        _isBoostingProfile
    ) { isLoading, isPostingActivity, isSavingProfile, isRefreshingFeed, isBoostingProfile ->
        LoadingFlags(
            isLoading = isLoading,
            isPostingActivity = isPostingActivity,
            isSavingProfile = isSavingProfile,
            isRefreshingFeed = isRefreshingFeed,
            isBoostingProfile = isBoostingProfile
        )
    }

    private val _profileLoadFlags = combine(
        _hasResolvedProfile,
        _profileLoadError
    ) { hasResolvedProfile, profileLoadError ->
        hasResolvedProfile to profileLoadError
    }

    private val _accountFlags = combine(
        _isDeletingAccount,
        _requiresReauthForDeletion,
        _errorMessage,
        _isOwnerCandidate
    ) { isDeletingAccount, requiresReauth, errorMessage, isOwnerCandidate ->
        listOf(isDeletingAccount, requiresReauth, errorMessage, isOwnerCandidate)
    }

    private val _photoFlags = combine(
        _isUpdatingPhoto,
        _photoStatusMessage,
        _blockStatusMessage,
        _actionStatusMessage
    ) { isUpdatingPhoto, photoStatusMessage, blockStatusMessage, actionStatusMessage ->
        listOf(isUpdatingPhoto, photoStatusMessage, blockStatusMessage, actionStatusMessage)
    }

    private val _statusFlags = combine(
        _loadingFlags,
        _profileLoadFlags,
        _accountFlags,
        _photoFlags
    ) { loading, profileLoad, account, photo ->
        StatusFlags(
            isLoading = loading.isLoading,
            hasResolvedProfile = profileLoad.first,
            profileLoadError = profileLoad.second,
            isPostingActivity = loading.isPostingActivity,
            isSavingProfile = loading.isSavingProfile,
            isUpdatingPhoto = photo[0] as Boolean,
            photoStatusMessage = photo[1] as String?,
            blockStatusMessage = photo[2] as String?,
            actionStatusMessage = photo[3] as String?,
            isDeletingAccount = account[0] as Boolean,
            requiresReauthForDeletion = account[1] as Boolean,
            errorMessage = account[2] as String?,
            isOwnerCandidate = account[3] as Boolean,
            isRefreshingFeed = loading.isRefreshingFeed,
            isBoostingProfile = loading.isBoostingProfile
        )
    }

    private val _activityCommentPair = combine(
        _activeCommentsPostId,
        _activePostComments,
        _activityCategoryFilter
    ) { postId, comments, categoryFilter ->
        Triple(postId, comments, categoryFilter)
    }

    private val _activityFeedSnapshot = combine(
        _rawActivityPosts,
        _myActivityReactions,
        _blockedUserIds,
        _dashboardPreferences,
        _activityCommentPair
    ) { posts, myReactions, blocked, prefs, commentPair ->
        val unblockedPosts = posts.filter { !blocked.contains(it.resolvedUserId) }
        val category = commentPair.third
        val filtered = if (category == "ALL" || category.isBlank()) {
            unblockedPosts
        } else {
            unblockedPosts.filter { it.resolvedActivityType.equals(category, ignoreCase = true) }
        }
        ActivityFeedSnapshot(
            posts = unblockedPosts,
            filteredPosts = filtered,
            categoryFilter = category,
            myReactions = myReactions,
            activeCommentsPostId = commentPair.first,
            activePostComments = commentPair.second.filter { !blocked.contains(it.authorId) },
            preferences = prefs
        )
    }

    private val _filterAndActivitySnapshot = combine(
        _filter,
        _activityFeedSnapshot
    ) { filter, activitySnapshot ->
        filter to activitySnapshot
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _currentUserProfile,
        _filteredMembersData,
        _filterAndActivitySnapshot,
        _socialGraphSnapshot,
        _statusFlags
    ) { profile, membersData, filterAndActivity, social, status ->
        val filter = filterAndActivity.first
        val activitySnapshot = filterAndActivity.second
        DashboardUiState(
            currentUserProfile = profile,
            members = membersData.members,
            filteredMembers = membersData.filteredMembers,
            pagedFilteredMembers = membersData.pagedFilteredMembers,
            hasMoreMembers = membersData.hasMoreMembers,
            onlineMembers = membersData.onlineMembers,
            onlineMembersYouMayLike = membersData.onlineMembersYouMayLike,
            suggestedMatches = membersData.suggestedMatches,
            blockedMembers = membersData.blockedMembers,
            actualOnlineCount = membersData.actualOnlineCount,
            friendsCount = membersData.friendsCount,
            unreadMessagesCount = social.unreadMessagesCount,
            receivedLikesCount = social.profileLikesReceived.size,
            conversations = social.conversations,
            notifications = social.notifications,
            unreadNotificationsCount = social.unreadNotificationsCount,
            followingIds = social.followingIds,
            favoriteIds = social.favoriteIds,
            profileLikesGivenIds = social.profileLikesGivenIds,
            profileLikesReceived = social.profileLikesReceived,
            profileVisitors = social.profileVisitors,
            myGalleryPhotos = social.myGalleryPhotos,
            friendStatusByUserId = membersData.friendStatusByUserId,
            incomingFriendRequests = membersData.incomingFriendRequests,
            activityPosts = activitySnapshot.posts,
            filteredActivityPosts = activitySnapshot.filteredPosts,
            activityCategoryFilter = activitySnapshot.categoryFilter,
            myActivityReactions = activitySnapshot.myReactions,
            activeCommentsPostId = activitySnapshot.activeCommentsPostId,
            activePostComments = activitySnapshot.activePostComments,
            filter = filter,
            dashboardPreferences = activitySnapshot.preferences,
            blockedUserIds = membersData.blockedUserIds,
            myBlockedUserIds = membersData.myBlockedUserIds,
            blockStatusMessage = status.blockStatusMessage,
            actionStatusMessage = status.actionStatusMessage,
            isLoading = status.isLoading,
            hasResolvedProfile = status.hasResolvedProfile,
            profileLoadError = status.profileLoadError,
            isPostingActivity = status.isPostingActivity,
            isSavingProfile = status.isSavingProfile,
            isUpdatingPhoto = status.isUpdatingPhoto,
            photoStatusMessage = status.photoStatusMessage,
            isDeletingAccount = status.isDeletingAccount,
            requiresReauthForDeletion = status.requiresReauthForDeletion,
            errorMessage = status.errorMessage,
            isOwnerCandidate = status.isOwnerCandidate,
            knownUsersById = social.extraPartnerProfiles + membersData.members.associateBy { it.userId },
            isRefreshingFeed = status.isRefreshingFeed,
            isBoostingProfile = status.isBoostingProfile
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    private var activeObservedUid: String? = null
    private var profileJob: Job? = null
    private var blockedIdsJob: Job? = null
    private var myBlockedIdsJob: Job? = null
    private var membersJob: Job? = null
    private var friendRequestsJob: Job? = null
    private var activityPostsJob: Job? = null
    private var activityReactionsJob: Job? = null
    private var conversationsJob: Job? = null
    private var notificationsJob: Job? = null
    private var followingJob: Job? = null
    private var favoritesJob: Job? = null
    private var likesGivenJob: Job? = null
    private var likesReceivedJob: Job? = null
    private var visitorsJob: Job? = null
    private var myGalleryJob: Job? = null
    private var activeCommentsJob: Job? = null

    init {
        val uid = repository.getCurrentUserId()
        if (uid != null) {
            startSessionObservers(uid)
        } else {
            _isLoading.value = false
        }
    }

    fun ensureSessionStarted(uid: String) {
        if (activeObservedUid != uid || (profileJob?.isActive != true && _currentUserProfile.value == null)) {
            startSessionObservers(uid)
        }
    }

    fun retrySession(uid: String) {
        startSessionObservers(uid, forceRestart = true)
    }

    private fun startSessionObservers(uid: String, forceRestart: Boolean = false) {
        if (!forceRestart && activeObservedUid == uid && profileJob?.isActive == true) {
            return
        }
        activeObservedUid = uid
        _isLoading.value = true
        _hasResolvedProfile.value = false
        _profileLoadError.value = null
        _errorMessage.value = null
        _isOwnerCandidate.value = repository.isOwnerCandidateSignedIn()

        loadUserData(uid)
        observeRealtimeMembers()
        observeRealtimeFriendRequests()
        observeRealtimeActivities()
        observeSocialAndMessaging(uid)
    }

    private fun loadUserData(uid: String) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            try {
                repository.observeCurrentUserProfile(uid).collect { profile ->
                    _currentUserProfile.value = profile
                    _hasResolvedProfile.value = true
                    _profileLoadError.value = null
                    if (profile == null) {
                        _isLoading.value = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isLoading.value = false
                val msg = e.message ?: "Failed to load your profile."
                _profileLoadError.value = msg
                _errorMessage.value = msg
            }
        }

        blockedIdsJob?.cancel()
        blockedIdsJob = viewModelScope.launch {
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

        myBlockedIdsJob?.cancel()
        myBlockedIdsJob = viewModelScope.launch {
            try {
                repository.observeMyBlockedUserIds().collect { myBlocked ->
                    _myBlockedUserIds.value = myBlocked
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun observeRealtimeMembers() {
        membersJob?.cancel()
        membersJob = viewModelScope.launch {
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
        friendRequestsJob?.cancel()
        friendRequestsJob = viewModelScope.launch {
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
        activityPostsJob?.cancel()
        activityPostsJob = viewModelScope.launch {
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

        activityReactionsJob?.cancel()
        activityReactionsJob = viewModelScope.launch {
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

    private fun observeSocialAndMessaging(uid: String) {
        conversationsJob?.cancel()
        conversationsJob = viewModelScope.launch {
            try {
                repository.observeConversations().collect { list ->
                    _rawConversations.value = list
                    val knownIds = _rawMembers.value.map { it.userId }.toSet() + _extraPartnerProfiles.value.keys
                    val missingPartnerIds = list.mapNotNull { conv ->
                        conv.participantUids.firstOrNull { it != uid }
                    }.distinct().filter { it.isNotBlank() && !knownIds.contains(it) }
                    if (missingPartnerIds.isNotEmpty()) {
                        launch {
                            try {
                                val fetched = repository.fetchUsersByIds(missingPartnerIds.take(30))
                                if (fetched.isNotEmpty()) {
                                    _extraPartnerProfiles.value = _extraPartnerProfiles.value + fetched.associateBy { it.userId }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            try {
                repository.observeNotifications().collect { list ->
                    _rawNotifications.value = list
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        followingJob?.cancel()
        followingJob = viewModelScope.launch {
            try {
                repository.observeFollowingIds().collect { ids -> _followingIds.value = ids }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            try {
                repository.observeFavoriteIds().collect { ids -> _favoriteIds.value = ids }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        likesGivenJob?.cancel()
        likesGivenJob = viewModelScope.launch {
            try {
                repository.observeProfileLikesGiven().collect { ids -> _profileLikesGivenIds.value = ids }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        likesReceivedJob?.cancel()
        likesReceivedJob = viewModelScope.launch {
            try {
                repository.observeProfileLikesReceived().collect { likes -> _profileLikesReceived.value = likes }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        visitorsJob?.cancel()
        visitorsJob = viewModelScope.launch {
            try {
                repository.observeProfileVisitors().collect { visits -> _profileVisitors.value = visits }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        myGalleryJob?.cancel()
        myGalleryJob = viewModelScope.launch {
            try {
                repository.observeGalleryPhotos(uid).collect { photos -> _myGalleryPhotos.value = photos }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearPhotoStatusMessage() {
        _photoStatusMessage.value = null
    }

    fun clearBlockStatusMessage() {
        _blockStatusMessage.value = null
    }

    fun clearActionStatusMessage() {
        _actionStatusMessage.value = null
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
        val next = !_filter.value.onlineOnly
        _filter.value = _filter.value.copy(
            onlineOnly = next,
            offlineOnly = if (next) false else _filter.value.offlineOnly
        )
    }

    fun toggleOfflineOnly() {
        val next = !_filter.value.offlineOnly
        _filter.value = _filter.value.copy(
            offlineOnly = next,
            onlineOnly = if (next) false else _filter.value.onlineOnly
        )
    }

    fun toggleFriendsOnly() {
        _filter.value = _filter.value.copy(friendsOnly = !_filter.value.friendsOnly)
    }

    fun toggleSuggestedOnly() {
        _filter.value = _filter.value.copy(suggestedOnly = !_filter.value.suggestedOnly)
    }

    fun toggleMutualInterestsOnly() {
        _filter.value = _filter.value.copy(mutualInterestsOnly = !_filter.value.mutualInterestsOnly)
    }

    fun toggleFollowingOnly() {
        _filter.value = _filter.value.copy(followingOnly = !_filter.value.followingOnly)
    }

    fun toggleFavoritesOnly() {
        _filter.value = _filter.value.copy(favoritesOnly = !_filter.value.favoritesOnly)
    }

    fun togglePreferredMatchOnly() {
        _filter.value = _filter.value.copy(preferredMatchOnly = !_filter.value.preferredMatchOnly)
    }

    fun resetMemberFilters() {
        _filter.value = DashboardFilter()
        _discoveryPageLimit.value = INITIAL_MEMBERS_PAGE_SIZE
    }

    fun loadMoreMembers() {
        val nextLimit = _discoveryPageLimit.value + MEMBERS_PAGE_STEP
        _discoveryPageLimit.value = nextLimit
        if (nextLimit >= _rawMembers.value.size) {
            viewModelScope.launch {
                try {
                    repository.loadMoreMembers()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    fun refreshDashboardFeed(force: Boolean = true) {
        if (_isRefreshingFeed.value) return
        viewModelScope.launch {
            _isRefreshingFeed.value = true
            try {
                repository.refreshMembers(force = force)
                repository.refreshActivityPosts(force = force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not refresh feed right now."
            } finally {
                _isRefreshingFeed.value = false
            }
        }
    }

    fun boostProfile(onSuccess: () -> Unit = {}) {
        if (_isBoostingProfile.value) return
        viewModelScope.launch {
            _isBoostingProfile.value = true
            _errorMessage.value = null
            try {
                repository.boostMyProfile()
                _actionStatusMessage.value = "🚀 Profile boosted! You're bumped near the top of Discover."
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not boost your profile right now."
            } finally {
                _isBoostingProfile.value = false
            }
        }
    }

    fun setActivityCategoryFilter(category: String) {
        _activityCategoryFilter.value = category
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
        createActivityPost(
            content = content,
            moodEmoji = moodEmoji,
            activityTag = activityTag,
            imageUrl = null,
            onSuccess = onSuccess
        )
    }

    fun createActivityPost(
        content: String,
        moodEmoji: String,
        activityTag: String,
        imageUrl: String? = null,
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
                    activityTag = activityTag,
                    imageUrl = imageUrl
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

    fun toggleActivityLike(postId: String) {
        viewModelScope.launch {
            try {
                repository.toggleActivityLike(postId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not update like."
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

    fun openCommentsForPost(postId: String) {
        _activeCommentsPostId.value = postId
        activeCommentsJob?.cancel()
        activeCommentsJob = viewModelScope.launch {
            try {
                repository.observeActivityComments(postId).collect { comments ->
                    _activePostComments.value = comments
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not load comments."
            }
        }
    }

    fun closeComments() {
        activeCommentsJob?.cancel()
        _activeCommentsPostId.value = null
        _activePostComments.value = emptyList()
    }

    fun addActivityComment(postId: String, content: String, onSuccess: () -> Unit = {}) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addActivityComment(postId, _currentUserProfile.value, content)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not post comment."
            }
        }
    }

    fun deleteActivityComment(postId: String, commentId: String) {
        viewModelScope.launch {
            try {
                repository.deleteActivityComment(postId, commentId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not delete comment."
            }
        }
    }

    fun shareActivityPost(postId: String) {
        viewModelScope.launch {
            try {
                repository.shareActivityPost(postId)
                _actionStatusMessage.value = "Activity shared with your SOGO network."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not share activity."
            }
        }
    }

    fun reportActivityPost(post: ActivityPost, reason: String, details: String?) {
        viewModelScope.launch {
            try {
                repository.reportActivityPost(post, reason, details)
                _actionStatusMessage.value = "Activity report submitted for moderation review."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not report post."
            }
        }
    }

    fun toggleFollow(targetUid: String) {
        if (targetUid.isBlank()) return
        val currentlyFollowing = _followingIds.value.contains(targetUid)
        viewModelScope.launch {
            try {
                repository.toggleFollow(targetUid, currentlyFollowing)
                _followingIds.value = if (currentlyFollowing) {
                    _followingIds.value - targetUid
                } else {
                    _followingIds.value + targetUid
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not update follow status."
            }
        }
    }

    fun toggleFavorite(targetUid: String) {
        if (targetUid.isBlank()) return
        val currentlyFavorited = _favoriteIds.value.contains(targetUid)
        viewModelScope.launch {
            try {
                repository.toggleFavorite(targetUid, currentlyFavorited)
                _favoriteIds.value = if (currentlyFavorited) {
                    _favoriteIds.value - targetUid
                } else {
                    _favoriteIds.value + targetUid
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not update favorite status."
            }
        }
    }

    fun toggleProfileLike(targetMember: UserProfile) {
        val targetUid = targetMember.userId
        if (targetUid.isBlank()) return
        val currentlyLiked = _profileLikesGivenIds.value.contains(targetUid)
        viewModelScope.launch {
            try {
                repository.toggleProfileLike(targetMember, _currentUserProfile.value, currentlyLiked)
                _profileLikesGivenIds.value = if (currentlyLiked) {
                    _profileLikesGivenIds.value - targetUid
                } else {
                    _profileLikesGivenIds.value + targetUid
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not update like."
            }
        }
    }

    fun sendSayHi(targetMember: UserProfile, onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.sendSayHi(targetMember, _currentUserProfile.value)
                _actionStatusMessage.value = "👋 Wave sent to ${targetMember.displayName}!"
                onCompleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not send wave right now."
            }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            try {
                repository.markNotificationAsRead(notificationId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            try {
                repository.markAllNotificationsAsRead()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun addGalleryPhoto(photoUri: String, caption: String? = null, onSuccess: () -> Unit = {}) {
        val safeUrl = photoUri.trim()
        if (safeUrl.isBlank() || safeUrl.length > 65000) {
            _errorMessage.value = "Selected gallery photo is too large or invalid."
            return
        }
        viewModelScope.launch {
            _isUpdatingPhoto.value = true
            try {
                repository.addGalleryPhoto(safeUrl, caption)
                _photoStatusMessage.value = "Photo added to your gallery."
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not upload gallery photo."
            } finally {
                _isUpdatingPhoto.value = false
            }
        }
    }

    fun deleteGalleryPhoto(photoId: String) {
        viewModelScope.launch {
            try {
                repository.deleteGalleryPhoto(photoId)
                _photoStatusMessage.value = "Photo removed from gallery."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not delete gallery photo."
            }
        }
    }

    fun updateCoverPhoto(photoUri: String) {
        val safeUrl = photoUri.trim()
        if (safeUrl.isBlank() || safeUrl.length > 65000) {
            _errorMessage.value = "Selected cover photo is too large or invalid."
            return
        }
        viewModelScope.launch {
            _isUpdatingPhoto.value = true
            try {
                repository.updateCoverPhoto(safeUrl)
                _currentUserProfile.value = _currentUserProfile.value?.copy(coverPhotoUrl = safeUrl)
                _photoStatusMessage.value = "Cover photo updated."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not update cover photo."
            } finally {
                _isUpdatingPhoto.value = false
            }
        }
    }

    fun updateDatingPreferencesAndPrivacy(
        username: String?,
        hobbies: List<String>,
        lookingFor: String?,
        preferredGender: String,
        preferredMinAge: Int,
        preferredMaxAge: Int,
        preferredCountry: String,
        hideFromDiscovery: Boolean,
        hideOnlineStatus: Boolean,
        hideAge: Boolean,
        hideLocation: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSavingProfile.value = true
            try {
                repository.updateDatingPreferencesAndPrivacy(
                    username = username,
                    hobbies = hobbies,
                    lookingFor = lookingFor,
                    preferredGender = preferredGender,
                    preferredMinAge = preferredMinAge,
                    preferredMaxAge = preferredMaxAge,
                    preferredCountry = preferredCountry,
                    hideFromDiscovery = hideFromDiscovery,
                    hideOnlineStatus = hideOnlineStatus,
                    hideAge = hideAge,
                    hideLocation = hideLocation
                )
                _currentUserProfile.value = _currentUserProfile.value?.copy(
                    username = username,
                    hobbies = hobbies,
                    lookingFor = lookingFor,
                    preferredGender = preferredGender,
                    preferredMinAge = preferredMinAge,
                    preferredMaxAge = preferredMaxAge,
                    preferredCountry = preferredCountry,
                    hideFromDiscovery = hideFromDiscovery,
                    hideOnlineStatus = hideOnlineStatus,
                    hideAge = hideAge,
                    hideLocation = hideLocation
                )
                _actionStatusMessage.value = "Preferences & privacy settings saved."
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not save preferences."
            } finally {
                _isSavingProfile.value = false
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

    fun blockUser(
        targetUid: String,
        displayName: String? = null,
        onCompleted: () -> Unit = {}
    ) {
        val currentUid = repository.getCurrentUserId()
        if (targetUid.isBlank() || targetUid == currentUid) return
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                repository.blockUser(targetUid)
                _blockedUserIds.value = _blockedUserIds.value + targetUid
                _myBlockedUserIds.value = _myBlockedUserIds.value + targetUid
                val label = displayName?.takeIf { it.isNotBlank() } ?: "Member"
                _blockStatusMessage.value = "$label was blocked and removed from discovery and chat."
                onCompleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not block this user right now."
            }
        }
    }

    fun unblockUser(
        targetUid: String,
        displayName: String? = null,
        onCompleted: () -> Unit = {}
    ) {
        if (targetUid.isBlank()) return
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                repository.unblockUser(targetUid)
                _myBlockedUserIds.value = _myBlockedUserIds.value - targetUid
                val label = displayName?.takeIf { it.isNotBlank() } ?: "Member"
                _blockStatusMessage.value = "$label was unblocked."
                onCompleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Could not unblock this user right now."
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
        username: String? = _currentUserProfile.value?.username,
        hobbies: List<String> = _currentUserProfile.value?.hobbies ?: emptyList(),
        lookingFor: String? = _currentUserProfile.value?.lookingFor,
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
                val existing = _currentUserProfile.value
                val profile = (existing ?: UserProfile(userId = uid)).copy(
                    userId = uid,
                    username = username?.trim()?.replace(Regex("[^a-zA-Z0-9_.]"), "")?.takeIf { it.isNotBlank() }?.take(32),
                    displayName = displayName.trim().ifBlank { "Member" }.take(50),
                    birthday = birthday.trim().take(30),
                    age = age,
                    gender = gender.trim().ifBlank { "Female" }.take(30),
                    country = country.trim().ifBlank { "Philippines" }.take(60),
                    city = city.trim().takeIf { it.isNotBlank() }?.take(60) ?: "Manila",
                    photoUrl = photoUrl?.trim()?.takeIf { it.isNotBlank() && it.length <= 65000 },
                    aboutMe = aboutMe.trim().take(1000),
                    personality = personality.trim().takeIf { it.isNotBlank() }?.take(100),
                    hobbies = hobbies.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25),
                    interests = interests.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25),
                    lookingFor = lookingFor?.trim()?.takeIf { it.isNotBlank() }?.take(60),
                    isOnline = true,
                    role = "member"
                )
                repository.saveUserProfile(profile)
                _currentUserProfile.value = profile
                _hasResolvedProfile.value = true
                _profileLoadError.value = null
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

    fun updateProfilePhoto(
        photoUri: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val safeUrl = photoUri.trim()
        if (safeUrl.isBlank() || safeUrl.length > 65000) {
            val msg = "Selected photo is too large or invalid. Please try another photo."
            _errorMessage.value = msg
            onError(msg)
            return
        }
        viewModelScope.launch {
            _isUpdatingPhoto.value = true
            _errorMessage.value = null
            _photoStatusMessage.value = null
            try {
                repository.updateProfilePhoto(safeUrl)
                _currentUserProfile.value = _currentUserProfile.value?.copy(photoUrl = safeUrl)
                _photoStatusMessage.value = "Profile picture saved to your profile."
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Could not save your profile picture right now."
                _errorMessage.value = msg
                onError(msg)
            } finally {
                _isUpdatingPhoto.value = false
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

    companion object {
        private const val INITIAL_MEMBERS_PAGE_SIZE = 20
        private const val MEMBERS_PAGE_STEP = 20
    }
}
