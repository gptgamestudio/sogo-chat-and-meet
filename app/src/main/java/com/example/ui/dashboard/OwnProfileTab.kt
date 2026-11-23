package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserProfile
import com.example.ui.profile.ProfilePhotoSourceDialog
import com.example.ui.profile.rememberProfilePhotoPickerController
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackCard
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.MatteBlackSurfaceVariant
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OutlineGold
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnProfileTab(
    uiState: DashboardUiState,
    rewardCredits: Int,
    onEditProfile: () -> Unit,
    onUpdateAvatarPhoto: () -> Unit,
    onUpdateCoverPhoto: (String) -> Unit,
    onAddGalleryPhoto: (String) -> Unit,
    onDeleteGalleryPhoto: (String) -> Unit,
    onRequestVerification: () -> Unit,
    onSaveDatingPreferences: (
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
        hideLocation: Boolean
    ) -> Unit,
    onSelectMember: (UserProfile) -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onWatchRewardedAd: () -> Unit,
    onOpenOwnerAdmin: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = uiState.currentUserProfile
    val coverPicker = rememberProfilePhotoPickerController { dataUri ->
        onUpdateCoverPhoto(dataUri)
    }
    val galleryPicker = rememberProfilePhotoPickerController { dataUri ->
        onAddGalleryPhoto(dataUri)
    }

    if (coverPicker.showSourceChooser) {
        ProfilePhotoSourceDialog(
            onDismiss = { coverPicker.dismissChooser() },
            onChooseGallery = { coverPicker.launchGalleryPicker() },
            onTakePhoto = { coverPicker.launchCameraCapture() }
        )
    }
    if (galleryPicker.showSourceChooser) {
        ProfilePhotoSourceDialog(
            onDismiss = { galleryPicker.dismissChooser() },
            onChooseGallery = { galleryPicker.launchGalleryPicker() },
            onTakePhoto = { galleryPicker.launchCameraCapture() }
        )
    }

    var usernameInput by remember(profile?.username) { mutableStateOf(profile?.username ?: "") }
    var lookingForInput by remember(profile?.lookingFor) { mutableStateOf(profile?.lookingFor ?: "Serious Dating & Friendship") }
    var preferredGender by remember(profile?.preferredGender) { mutableStateOf(profile?.preferredGender ?: "ALL") }
    var preferredCountry by remember(profile?.preferredCountry) { mutableStateOf(profile?.preferredCountry ?: "ALL") }
    var preferredMinAgeText by remember(profile?.preferredMinAge) { mutableStateOf((profile?.preferredMinAge ?: 18).toString()) }
    var preferredMaxAgeText by remember(profile?.preferredMaxAge) { mutableStateOf((profile?.preferredMaxAge ?: 80).toString()) }
    var hideFromDiscovery by remember(profile?.hideFromDiscovery) { mutableStateOf(profile?.hideFromDiscovery ?: false) }
    var hideOnlineStatus by remember(profile?.hideOnlineStatus) { mutableStateOf(profile?.hideOnlineStatus ?: false) }
    var hideAge by remember(profile?.hideAge) { mutableStateOf(profile?.hideAge ?: false) }
    var hideLocation by remember(profile?.hideLocation) { mutableStateOf(profile?.hideLocation ?: false) }

    val membersById = remember(uiState.members) { uiState.members.associateBy { it.userId } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("own_profile_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Cover & Avatar Card
        item(key = "own_cover_avatar") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
                border = BorderStroke(1.dp, OutlineGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                    ) {
                        if (!profile?.coverPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(profile?.coverPhotoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Cover photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(listOf(BurgundyPrimary, BurgundyDark, MatteBlackCard))
                                    )
                            )
                        }
                        OutlinedButton(
                            onClick = { coverPicker.openChooser() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .heightIn(min = 40.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, GoldAccent),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = MatteBlackSurface.copy(alpha = 0.8f))
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Edit Cover", tint = GoldLight, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Cover", color = GoldLight, fontSize = 11.sp)
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(CircleShape)
                                    .background(BurgundyPrimary)
                                    .border(2.dp, GoldLight, CircleShape)
                                    .clickable { onUpdateAvatarPhoto() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!profile?.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = profile?.photoUrl,
                                        contentDescription = "Your photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = (profile?.displayName ?: "S").take(1).uppercase(),
                                        color = GoldLight,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile?.displayName ?: "Member",
                                        color = TextWhite,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    if (profile?.isVerifiedMember == true) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${profile?.age ?: 18} • ${profile?.gender ?: ""} • ${profile?.city ?: ""}, ${profile?.country ?: ""}",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            OutlinedButton(
                                onClick = onEditProfile,
                                border = BorderStroke(1.dp, GoldAccent),
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = GoldLight, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", color = GoldLight, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Verification Status Card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val verStatus = when {
                                profile?.isVerifiedMember == true -> "Verified Member ✓"
                                profile?.verificationStatus == "PENDING" -> "Verification Pending Review"
                                else -> "Unverified Member"
                            }
                            Text(
                                text = verStatus,
                                color = if (profile?.isVerifiedMember == true) OnlineGreen else GoldLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (profile?.isVerifiedMember != true && profile?.verificationStatus != "PENDING") {
                                OutlinedButton(
                                    onClick = onRequestVerification,
                                    modifier = Modifier.heightIn(min = 40.dp),
                                    border = BorderStroke(1.dp, OutlineGold)
                                ) {
                                    Text("Request Verified Badge", color = GoldLight, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Photo Gallery Card (PickVisualMedia)
        item(key = "own_gallery_card") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
                border = BorderStroke(1.dp, OutlineSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MY GALLERY (${uiState.myGalleryPhotos.size})",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        OutlinedButton(
                            onClick = { galleryPicker.openChooser() },
                            border = BorderStroke(1.dp, GoldAccent),
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = GoldLight, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Photo", color = GoldLight, fontSize = 11.sp)
                        }
                    }
                    if (uiState.myGalleryPhotos.isEmpty()) {
                        Text("Upload photos to showcase your lifestyle on your profile.", color = TextMuted, fontSize = 12.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(items = uiState.myGalleryPhotos, key = { it.photoId }) { photo ->
                                Box(
                                    modifier = Modifier
                                        .size(104.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, OutlineSubtle, RoundedCornerShape(14.dp))
                                ) {
                                    AsyncImage(
                                        model = photo.photoUrl,
                                        contentDescription = photo.caption ?: "Gallery photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = MatteBlackSurface.copy(alpha = 0.85f),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(28.dp)
                                            .clickable { onDeleteGalleryPhoto(photo.photoId) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove photo", tint = AlertRed, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Visitors & Likes Received Summary
        item(key = "own_social_insights") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
                border = BorderStroke(1.dp, OutlineSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VISITORS & PROFILE LIKES", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = "Likes Received: ${uiState.profileLikesReceived.size} • Following: ${uiState.followingIds.size} • Favorites: ${uiState.favoriteIds.size}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    if (uiState.profileVisitors.isNotEmpty()) {
                        Text("Recent Profile Visitors:", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(items = uiState.profileVisitors.take(10), key = { it.visitId }) { visit ->
                                val visitorProfile = membersById[visit.visitorId]
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MatteBlackSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(enabled = visitorProfile != null) {
                                            visitorProfile?.let(onSelectMember)
                                        }
                                ) {
                                    Text(
                                        text = visit.visitorName.ifBlank { "Member" },
                                        color = TextWhite,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Dating Preferences & Privacy Controls
        item(key = "own_preferences_privacy") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
                border = BorderStroke(1.dp, OutlineSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DATING PREFERENCES & PRIVACY", color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { if (it.length <= 32) usernameInput = it },
                        label = { Text("Username", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldAccent, unfocusedBorderColor = OutlineSubtle)
                    )
                    OutlinedTextField(
                        value = lookingForInput,
                        onValueChange = { if (it.length <= 60) lookingForInput = it },
                        label = { Text("Looking For", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldAccent, unfocusedBorderColor = OutlineSubtle)
                    )

                    Text("Preferred Match Gender:", color = TextMuted, fontSize = 11.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL", "Female", "Male", "Non-Binary").forEach { g ->
                            FilterChip(
                                selected = preferredGender == g,
                                onClick = { preferredGender = g },
                                label = { Text(g, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BurgundyPrimary, selectedLabelColor = GoldLight)
                            )
                        }
                    }

                    Text("Preferred Country:", color = TextMuted, fontSize = 11.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL", "Philippines", "Overseas").forEach { c ->
                            FilterChip(
                                selected = preferredCountry == c,
                                onClick = { preferredCountry = c },
                                label = { Text(c, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BurgundyPrimary, selectedLabelColor = GoldLight)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = preferredMinAgeText,
                            onValueChange = { preferredMinAgeText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Min Age (18+)", color = TextMuted, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldAccent, unfocusedBorderColor = OutlineSubtle)
                        )
                        OutlinedTextField(
                            value = preferredMaxAgeText,
                            onValueChange = { preferredMaxAgeText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Max Age", color = TextMuted, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldAccent, unfocusedBorderColor = OutlineSubtle)
                        )
                    }

                    HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.4f))
                    PrivacyToggleRow("Hide me from member discovery", hideFromDiscovery) { hideFromDiscovery = it }
                    PrivacyToggleRow("Hide my online status dot", hideOnlineStatus) { hideOnlineStatus = it }
                    PrivacyToggleRow("Hide my age on cards", hideAge) { hideAge = it }
                    PrivacyToggleRow("Hide my city & country", hideLocation) { hideLocation = it }

                    Button(
                        onClick = {
                            val minA = (preferredMinAgeText.toIntOrNull() ?: 18).coerceIn(18, 100)
                            val maxA = (preferredMaxAgeText.toIntOrNull() ?: 80).coerceIn(minA, 120)
                            onSaveDatingPreferences(
                                usernameInput.trim().takeIf { it.isNotBlank() },
                                profile?.hobbies ?: emptyList(),
                                lookingForInput.trim().takeIf { it.isNotBlank() },
                                preferredGender,
                                minA,
                                maxA,
                                preferredCountry,
                                hideFromDiscovery,
                                hideOnlineStatus,
                                hideAge,
                                hideLocation
                            )
                        },
                        enabled = !uiState.isSavingProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_preferences_privacy_button"),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BurgundyPrimary)
                    ) {
                        Text("Save Preferences & Privacy", color = GoldLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Account, Safety & Admin Shortcuts
        item(key = "own_account_actions") {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
                border = BorderStroke(1.dp, OutlineSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.isOwnerCandidate) {
                        Button(
                            onClick = onOpenOwnerAdmin,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BurgundyDark)
                        ) {
                            Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = null, tint = GoldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Owner Admin Dashboard", color = GoldLight, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenBlockedUsers,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, OutlineSubtle)
                    ) {
                        Icon(imageVector = Icons.Default.Block, contentDescription = null, tint = TextMuted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Blocked Users (${uiState.myBlockedUserIds.size})", color = TextWhite)
                    }

                    OutlinedButton(
                        onClick = onWatchRewardedAd,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, OutlineGold)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = GoldLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Watch Ad for Credits ($rewardCredits)", color = GoldLight)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onSignOut) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign Out", color = TextWhite)
                        }
                        TextButton(onClick = onDeleteAccount) {
                            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Account", color = AlertRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = GoldLight, checkedTrackColor = BurgundyPrimary)
        )
    }
}
