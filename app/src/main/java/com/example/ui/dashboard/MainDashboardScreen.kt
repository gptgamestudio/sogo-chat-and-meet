package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserProfile
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.profile.MemberProfileSetupScreen
import com.example.ui.auth.signOut
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyLight
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
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: MainDashboardViewModel,
    onSelectMember: (UserProfile) -> Unit,
    coroutineScope: CoroutineScope,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val credentialManager = remember { CredentialManager.create(context) }

    var showMenu by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    if (showEditProfileDialog && uiState.currentUserProfile != null) {
        Dialog(
            onDismissRequest = { showEditProfileDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            MemberProfileSetupScreen(
                initialProfile = uiState.currentUserProfile,
                onSaveProfile = { name, bday, age, gen, country, city, photo, about, personality, interests ->
                    viewModel.createOrUpdateProfile(
                        displayName = name,
                        birthday = bday,
                        age = age,
                        gender = gen,
                        country = country,
                        city = city,
                        photoUrl = photo,
                        aboutMe = about,
                        personality = personality,
                        interests = interests,
                        onSuccess = {
                            showEditProfileDialog = false
                        }
                    )
                },
                onSignOut = {
                    showEditProfileDialog = false
                    signOut(credentialManager, coroutineScope, onSignOut)
                }
            )
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account", color = TextWhite) },
            text = {
                Text(
                    "Are you sure you want to delete your SOGO CHAT profile? All your data will be permanently removed.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteAccount {
                            signOut(credentialManager, coroutineScope, onSignOut)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Delete Permanently", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = MatteBlackSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SOGO",
                                color = GoldLight,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CHAT & MEET",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        // Actual Online Members Counter (Real Firebase Data)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${uiState.actualOnlineCount} Members Online Now",
                                color = OnlineGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("dashboard_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = GoldLight
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MatteBlackSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit My Profile", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                showEditProfileDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                val isOnline = uiState.currentUserProfile?.isOnline == true
                                Text(
                                    if (isOnline) "Set Status: Offline" else "Set Status: Online",
                                    color = if (isOnline) GoldAccent else OnlineGreen
                                )
                            },
                            onClick = {
                                showMenu = false
                                val isOnline = uiState.currentUserProfile?.isOnline == true
                                viewModel.setPresence(!isOnline)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Account", color = AlertRed) },
                            onClick = {
                                showMenu = false
                                showDeleteAccountDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sign Out", color = TextMuted) },
                            onClick = {
                                showMenu = false
                                viewModel.setPresence(false)
                                signOut(credentialManager, coroutineScope, onSignOut)
                            }
                        )
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
        ) {
            // Search & Filter Section
            DashboardSearchAndFilters(
                filter = uiState.filter,
                onSearchChanged = { viewModel.updateSearchQuery(it) },
                onGenderSelected = { viewModel.setGenderFilter(it) },
                onCountrySelected = { viewModel.setCountryFilter(it) },
                onAgeSelected = { viewModel.setAgeRange(it) },
                onToggleOnline = { viewModel.toggleOnlineOnly() }
            )

            // Loading / Empty / Members List
            if (uiState.isLoading && uiState.members.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldAccent)
                }
            } else if (uiState.filteredMembers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = OutlineSubtle,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Members Found",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try adjusting your filters or search keywords.",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = uiState.filteredMembers,
                        key = { it.userId }
                    ) { member ->
                        MemberProfileCard(
                            member = member,
                            onChatClicked = { onSelectMember(member) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardSearchAndFilters(
    filter: DashboardFilter,
    onSearchChanged: (String) -> Unit,
    onGenderSelected: (String) -> Unit,
    onCountrySelected: (String) -> Unit,
    onAgeSelected: (String) -> Unit,
    onToggleOnline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MatteBlackSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = filter.searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Search by name, city, hobbies...", color = TextDim, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = GoldAccent, modifier = Modifier.size(18.dp))
            },
            trailingIcon = if (filter.searchQuery.isNotBlank()) {
                {
                    IconButton(onClick = { onSearchChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("member_search_input"),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = GoldAccent,
                unfocusedBorderColor = OutlineSubtle,
                focusedContainerColor = MatteBlackSurfaceVariant,
                unfocusedContainerColor = MatteBlackSurfaceVariant
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Online Only Toggle
            FilterChip(
                selected = filter.onlineOnly,
                onClick = onToggleOnline,
                label = { Text("🟢 Online Now", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BurgundyPrimary,
                    selectedLabelColor = GoldLight,
                    containerColor = MatteBlackSurfaceVariant,
                    labelColor = TextMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter.onlineOnly,
                    borderColor = if (filter.onlineOnly) GoldAccent else OutlineSubtle
                )
            )

            // Gender Filter
            listOf("ALL" to "All Genders", "Female" to "Women", "Male" to "Men").forEach { (key, label) ->
                FilterChip(
                    selected = filter.genderFilter == key,
                    onClick = { onGenderSelected(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BurgundyPrimary,
                        selectedLabelColor = TextWhite,
                        containerColor = MatteBlackSurfaceVariant,
                        labelColor = TextMuted
                    )
                )
            }

            // Country Filter
            listOf("ALL" to "All Locations", "Philippines" to "Philippines 🇵🇭", "Overseas" to "Overseas 🌏").forEach { (key, label) ->
                FilterChip(
                    selected = filter.countryFilter == key,
                    onClick = { onCountrySelected(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BurgundyPrimary,
                        selectedLabelColor = TextWhite,
                        containerColor = MatteBlackSurfaceVariant,
                        labelColor = TextMuted
                    )
                )
            }

            // Age Filter
            listOf("ALL" to "All Ages", "18-24" to "18-24", "25-34" to "25-34", "35+" to "35+").forEach { (key, label) ->
                FilterChip(
                    selected = filter.ageRange == key,
                    onClick = { onAgeSelected(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BurgundyPrimary,
                        selectedLabelColor = TextWhite,
                        containerColor = MatteBlackSurfaceVariant,
                        labelColor = TextMuted
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberProfileCard(
    member: UserProfile,
    onChatClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onChatClicked() }
            .testTag("member_card_${member.userId}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MatteBlackCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (member.isOnline) {
                Brush.horizontalGradient(listOf(OutlineGold, BurgundyPrimary))
            } else {
                Brush.horizontalGradient(listOf(OutlineSubtle, Color(0xFF1E1C22)))
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Photo & Badges Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (!member.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(member.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${member.displayName}'s photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Stylish gradient fallback with Initials
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(BurgundyPrimary, Color(0xFF1A050B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.displayName.take(1).uppercase(),
                            color = GoldLight,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Gradient Overlay for smooth text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    MatteBlackCard.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                // Online Status Indicator Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MatteBlackSurface.copy(alpha = 0.85f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            if (member.isOnline) listOf(OnlineGreen, Color(0xFF00796B))
                            else listOf(OutlineSubtle, OutlineSubtle)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (member.isOnline) OnlineGreen else TextDim)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (member.isOnline) "Active Now" else "Offline",
                            color = if (member.isOnline) OnlineGreen else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Verified 18+ Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BurgundyDark.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Adult",
                            tint = GoldLight,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "18+ Verified",
                            color = GoldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Name, Age and Gender in bottom of photo box
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = member.displayName,
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${member.age}",
                            color = GoldLight,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = GoldAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${member.city ?: "Manila"}, ${member.country}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${member.gender}",
                            color = TextDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // About Me Snippet & Interests
            Column(modifier = Modifier.padding(16.dp)) {
                if (!member.aboutMe.isNullOrBlank()) {
                    Text(
                        text = member.aboutMe,
                        color = TextWhite.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (!member.personality.isNullOrBlank()) {
                    Row(modifier = Modifier.padding(bottom = 8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BurgundyDark.copy(alpha = 0.9f),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(OutlineGold, GoldAccent))
                            )
                        ) {
                            Text(
                                text = "✨ ${member.personality}",
                                color = GoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (member.interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        member.interests.take(4).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MatteBlackSurfaceVariant,
                                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(OutlineSubtle, OutlineGold)))
                            ) {
                                Text(
                                    text = tag,
                                    color = GoldLight,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Chat & Meet Action CTA
                Button(
                    onClick = onChatClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("chat_meet_button_${member.userId}"),
                    shape = RoundedCornerShape(23.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BurgundyPrimary,
                        contentColor = TextWhite
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(GoldAccent, GoldLight))
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chat & Meet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextWhite
                        )
                    }
                }
            }
        }
    }
}
