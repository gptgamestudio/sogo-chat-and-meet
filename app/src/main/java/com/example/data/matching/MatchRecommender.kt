package com.example.data.matching

import com.example.data.model.UserProfile
import com.example.ui.common.MemberPresenceFormatter

data class RecommendedMember(
    val profile: UserProfile,
    val matchScore: Int,
    val mutualInterests: List<String>,
    val isNewMember: Boolean
)

/**
 * Computes real-time member recommendations ("Online Users You May Like" and "Suggested Matches")
 * strictly using actual Firestore member profiles, dating preferences, shared interests,
 * and recent activity—never fabricating online profiles.
 */
object MatchRecommender {

    private const val NEW_MEMBER_WINDOW_MS = 7L * 24 * 60 * 60 * 1000L

    fun isDiscoverableMember(
        candidate: UserProfile,
        currentUid: String?,
        blockedUserIds: Set<String>
    ): Boolean {
        if (candidate.userId.isBlank()) return false
        if (currentUid != null && candidate.userId == currentUid) return false
        if (blockedUserIds.contains(candidate.userId)) return false
        if (candidate.hideFromDiscovery) return false
        if (candidate.accountStatus.equals("SUSPENDED", ignoreCase = true)) return false
        if (candidate.age < 18) return false
        return true
    }

    fun computeMutualInterests(me: UserProfile?, other: UserProfile): List<String> {
        if (me == null) return emptyList()
        val mySet = (me.interests + me.hobbies)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()
        if (mySet.isEmpty()) return emptyList()
        return (other.interests + other.hobbies)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .filter { mySet.contains(it.lowercase()) }
    }

    fun recommendOnlineMembersYouMayLike(
        currentUser: UserProfile?,
        allMembers: List<UserProfile>,
        blockedUserIds: Set<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<RecommendedMember> {
        val currentUid = currentUser?.userId
        return allMembers
            .asSequence()
            .filter { isDiscoverableMember(it, currentUid, blockedUserIds) }
            .filter { MemberPresenceFormatter.isEffectivelyOnline(it, nowMs) }
            .map { candidate -> scoreCandidate(currentUser, candidate, nowMs) }
            .sortedWith(
                compareByDescending<RecommendedMember> { it.matchScore }
                    .thenByDescending { it.mutualInterests.size }
                    .thenByDescending { it.profile.lastActive?.toDate()?.time ?: 0L }
            )
            .toList()
    }

    fun recommendSuggestedMatches(
        currentUser: UserProfile?,
        allMembers: List<UserProfile>,
        blockedUserIds: Set<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<RecommendedMember> {
        val currentUid = currentUser?.userId
        return allMembers
            .asSequence()
            .filter { isDiscoverableMember(it, currentUid, blockedUserIds) }
            .map { candidate -> scoreCandidate(currentUser, candidate, nowMs) }
            .sortedWith(
                compareByDescending<RecommendedMember> {
                    MemberPresenceFormatter.isEffectivelyOnline(it.profile, nowMs)
                }
                    .thenByDescending { it.matchScore }
                    .thenByDescending { it.mutualInterests.size }
            )
            .toList()
    }

    fun scoreCandidate(
        me: UserProfile?,
        candidate: UserProfile,
        nowMs: Long = System.currentTimeMillis()
    ): RecommendedMember {
        var score = 0
        val mutual = computeMutualInterests(me, candidate)
        score += mutual.size * 20

        if (me != null) {
            val prefGender = me.preferredGender.trim()
            if (prefGender == "ALL" || prefGender.isBlank() ||
                candidate.gender.equals(prefGender, ignoreCase = true)
            ) {
                score += 25
            } else {
                score -= 20
            }

            val minAge = me.preferredMinAge.coerceAtLeast(18)
            val maxAge = me.preferredMaxAge.coerceAtLeast(minAge)
            if (candidate.age in minAge..maxAge) {
                score += 20
            } else {
                score -= 15
            }

            val prefCountry = me.preferredCountry.trim()
            if (prefCountry == "ALL" || prefCountry.isBlank() ||
                candidate.country.contains(prefCountry, ignoreCase = true)
            ) {
                score += 15
            }

            if (!me.city.isNullOrBlank() && !candidate.city.isNullOrBlank() &&
                me.city.equals(candidate.city, ignoreCase = true)
            ) {
                score += 10
            }

            if (!me.lookingFor.isNullOrBlank() && !candidate.lookingFor.isNullOrBlank() &&
                me.lookingFor.equals(candidate.lookingFor, ignoreCase = true)
            ) {
                score += 12
            }
        }

        if (MemberPresenceFormatter.isEffectivelyOnline(candidate, nowMs)) {
            score += 15
        }
        if (!candidate.photoUrl.isNullOrBlank()) {
            score += 6
        }

        val createdMs = candidate.createdAt?.toDate()?.time
        val isNew = createdMs != null && (nowMs - createdMs) <= NEW_MEMBER_WINDOW_MS
        if (isNew) {
            score += 5
        }

        return RecommendedMember(
            profile = candidate,
            matchScore = score,
            mutualInterests = mutual,
            isNewMember = isNew
        )
    }
}
