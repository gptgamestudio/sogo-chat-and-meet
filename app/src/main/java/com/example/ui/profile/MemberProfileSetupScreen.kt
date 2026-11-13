package com.example.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.MatteBlackCard
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.OffWhiteText
import com.example.ui.theme.PhilippineGold
import com.example.ui.theme.SecondaryMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar

private val PERSONALITY_OPTIONS = listOf(
    "Romantic & Thoughtful",
    "Ambivert & Friendly",
    "Extrovert & Outgoing",
    "Introvert & Deep Thinker",
    "Adventurous & Spontaneous",
    "Ambitious & Driven",
    "Chill & Laidback",
    "Foodie & Coffee Lover",
    "Creative & Artistic",
    "Family-Oriented"
)

private val INTEREST_OPTIONS = listOf(
    "Coffee & Kape",
    "Travel & Beach",
    "Music & OPM",
    "Foodie & Dining",
    "Fitness & Gym",
    "Art & Photography",
    "Movies & Series",
    "Karaoke & Singing",
    "Gaming & Tech",
    "Hiking & Nature",
    "Nightlife & Events",
    "Cooking & Baking"
)

private val AVATAR_PRESETS = listOf(
    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80",
    "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=600&q=80",
    "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=600&q=80",
    "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=600&q=80",
    "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=600&q=80",
    "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=600&q=80"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemberProfileSetupScreen(
    initialProfile: UserProfile? = null,
    onSaveProfile: (
        displayName: String,
        birthday: String,
        age: Int,
        gender: String,
        country: String,
        city: String,
        photoUrl: String?,
        aboutMe: String,
        personality: String,
        interests: List<String>
    ) -> Unit,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var displayName by remember { mutableStateOf(initialProfile?.displayName ?: "") }
    var photoUrl by remember { mutableStateOf(initialProfile?.photoUrl ?: AVATAR_PRESETS[0]) }
    var gender by remember { mutableStateOf(initialProfile?.gender ?: "Female") }
    var country by remember { mutableStateOf(initialProfile?.country ?: "Philippines") }
    var city by remember { mutableStateOf(initialProfile?.city ?: "Makati") }
    var aboutMe by remember { mutableStateOf(initialProfile?.aboutMe ?: "") }
    var selectedPersonality by remember {
        mutableStateOf(initialProfile?.personality ?: PERSONALITY_OPTIONS[0])
    }
    var selectedInterests by remember {
        mutableStateOf(initialProfile?.interests ?: listOf("Coffee & Kape", "Travel & Beach"))
    }

    // Birthday and Age State (Defaults to 24 years ago today)
    val today = remember { Calendar.getInstance() }
    val defaultBirthYear = today.get(Calendar.YEAR) - 24
    var birthYear by remember { mutableIntStateOf(defaultBirthYear) }
    var birthMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH) + 1) } // 1-12
    var birthDay by remember { mutableIntStateOf(today.get(Calendar.DAY_OF_MONTH)) } // 1-31

    // Automatically Calculate Age from Birthday (Never allow false manual entry)
    val calculatedAge by remember {
        derivedStateOf {
            val now = Calendar.getInstance()
            val curYear = now.get(Calendar.YEAR)
            val curMonth = now.get(Calendar.MONTH) + 1
            val curDay = now.get(Calendar.DAY_OF_MONTH)

            var age = curYear - birthYear
            if (curMonth < birthMonth || (curMonth == birthMonth && curDay < birthDay)) {
                age--
            }
            age
        }
    }

    val isAdultEligible = calculatedAge >= 18
    val birthdayString = "%04d-%02d-%02d".format(birthYear, birthMonth, birthDay)

    var isProcessingPhoto by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Android Photo Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessingPhoto = true
                val compressedDataUrl = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            if (bitmap != null) {
                                // Scale down to reasonable avatar size (max 400x400)
                                val maxDimension = 400
                                val scale = (maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
                                val scaled = Bitmap.createScaledBitmap(
                                    bitmap,
                                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                                    true
                                )
                                val baos = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                                val bytes = baos.toByteArray()
                                "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                            } else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (compressedDataUrl != null) {
                    photoUrl = compressedDataUrl
                }
                isProcessingPhoto = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = PhilippineGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (initialProfile == null) "Member Profile Setup" else "Edit Profile",
                            color = OffWhiteText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    if (initialProfile == null) {
                        OutlinedButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PhilippineGold),
                            modifier = Modifier.testTag("setup_sign_out_button")
                        ) {
                            Text("Sign Out", fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BurgundyDark)
            )
        },
        containerColor = Color(0xFF0F0E11)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MatteBlackSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SOGO CHAT AND MEET",
                        color = PhilippineGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Exclusive 18+ Filipino Dating & Private Communication",
                        color = SecondaryMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Profile Photo Section
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.size(120.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(3.dp, PhilippineGold, CircleShape)
                        .background(MatteBlackCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessingPhoto) {
                        CircularProgressIndicator(color = PhilippineGold, modifier = Modifier.size(36.dp))
                    } else {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PhilippineGold)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("upload_photo_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Upload Photo",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap camera icon to select photo or choose a preset below",
                color = SecondaryMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            // Preset Avatars Row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AVATAR_PRESETS.forEachIndexed { index, presetUrl ->
                    val isSelected = photoUrl == presetUrl
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) PhilippineGold else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { photoUrl = presetUrl }
                    ) {
                        AsyncImage(
                            model = presetUrl,
                            contentDescription = "Preset $index",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display Name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                placeholder = { Text("e.g., Maya Morales") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = PhilippineGold)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PhilippineGold,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = OffWhiteText,
                    unfocusedTextColor = OffWhiteText,
                    focusedLabelColor = PhilippineGold,
                    unfocusedLabelColor = SecondaryMuted
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_display_name_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Birthday and Automatically Calculated Age (Requirement 2)
            Card(
                colors = CardDefaults.cardColors(containerColor = MatteBlackSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cake,
                            contentDescription = null,
                            tint = PhilippineGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Birthday & Age Verification (18+ Mandatory)",
                            color = PhilippineGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Date Selectors: Year, Month, Day
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Month Dropdown
                        MonthDropdown(
                            selectedMonth = birthMonth,
                            onMonthSelected = { birthMonth = it },
                            modifier = Modifier.weight(1.3f)
                        )

                        // Day Dropdown
                        DayDropdown(
                            selectedDay = birthDay,
                            onDaySelected = { birthDay = it },
                            modifier = Modifier.weight(0.9f)
                        )

                        // Year Dropdown
                        YearDropdown(
                            selectedYear = birthYear,
                            onYearSelected = { birthYear = it },
                            modifier = Modifier.weight(1.1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Calculated Age Display Badge (Read-Only)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAdultEligible) Color(0xFF1E2819) else Color(0xFF380E14)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isAdultEligible) Color(0xFF4CAF50) else Color(0xFFE53935),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isAdultEligible) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isAdultEligible) Color(0xFF4CAF50) else Color(0xFFE53935),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Calculated Age: $calculatedAge Years Old",
                                    color = if (isAdultEligible) Color(0xFF81C784) else Color(0xFFFF8A80),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isAdultEligible) {
                                        "Verified 18+ eligibility ($birthdayString)"
                                    } else {
                                        "Ineligible. Must be at least 18 years old to join."
                                    },
                                    color = OffWhiteText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Gender Selection
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Gender",
                    color = PhilippineGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Female", "Male", "Non-Binary").forEach { g ->
                        val isSelected = gender == g
                        FilterChip(
                            selected = isSelected,
                            onClick = { gender = g },
                            label = { Text(g) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PhilippineGold,
                                selectedLabelColor = Color.Black,
                                containerColor = MatteBlackSurface,
                                labelColor = OffWhiteText
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Location: Country & City (No GPS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Country") },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = PhilippineGold)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PhilippineGold,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = OffWhiteText,
                        unfocusedTextColor = OffWhiteText,
                        focusedLabelColor = PhilippineGold,
                        unfocusedLabelColor = SecondaryMuted
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City (Optional)") },
                    placeholder = { Text("e.g. Makati") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PhilippineGold,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = OffWhiteText,
                        unfocusedTextColor = OffWhiteText,
                        focusedLabelColor = PhilippineGold,
                        unfocusedLabelColor = SecondaryMuted
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Personality Archetype
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = PhilippineGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Personality",
                        color = PhilippineGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PERSONALITY_OPTIONS.forEach { trait ->
                        val isSelected = selectedPersonality == trait
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPersonality = trait },
                            label = { Text(trait, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BurgundyDark,
                                selectedLabelColor = PhilippineGold,
                                containerColor = MatteBlackSurface,
                                labelColor = OffWhiteText
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) PhilippineGold else Color.DarkGray
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Interests Chips
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = PhilippineGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Interests & Passions",
                        color = PhilippineGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    INTEREST_OPTIONS.forEach { interest ->
                        val isSelected = selectedInterests.contains(interest)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedInterests = if (isSelected) {
                                    selectedInterests - interest
                                } else {
                                    selectedInterests + interest
                                }
                            },
                            label = { Text(interest, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PhilippineGold,
                                selectedLabelColor = Color.Black,
                                containerColor = MatteBlackSurface,
                                labelColor = OffWhiteText
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // About Me (Bio)
            OutlinedTextField(
                value = aboutMe,
                onValueChange = { if (it.length <= 500) aboutMe = it },
                label = { Text("About Me") },
                placeholder = { Text("Tell members what makes you unique, your passions, or what you're looking for...") },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PhilippineGold,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = OffWhiteText,
                    unfocusedTextColor = OffWhiteText,
                    focusedLabelColor = PhilippineGold,
                    unfocusedLabelColor = SecondaryMuted
                ),
                shape = RoundedCornerShape(16.dp),
                supportingText = {
                    Text(
                        text = "${aboutMe.length}/500",
                        color = SecondaryMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_about_me_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy Assurance Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = PhilippineGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your email address and precise GPS coordinates are never made public.",
                    color = SecondaryMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (isAdultEligible && displayName.isNotBlank()) {
                        isSubmitting = true
                        onSaveProfile(
                            displayName,
                            birthdayString,
                            calculatedAge,
                            gender,
                            country,
                            city,
                            photoUrl,
                            aboutMe,
                            selectedPersonality,
                            selectedInterests
                        )
                    }
                },
                enabled = isAdultEligible && displayName.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhilippineGold,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF333333),
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_profile_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isAdultEligible) "Enter SOGO CHAT AND MEET" else "Must Be 18+ To Join",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDropdown(
    selectedMonth: Int,
    onMonthSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = monthNames.getOrElse(selectedMonth - 1) { "Jan" },
            onValueChange = {},
            readOnly = true,
            label = { Text("Month", fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PhilippineGold,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = OffWhiteText,
                unfocusedTextColor = OffWhiteText
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MatteBlackSurface)
        ) {
            monthNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name, color = OffWhiteText) },
                    onClick = {
                        onMonthSelected(index + 1)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDropdown(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedDay.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Day", fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PhilippineGold,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = OffWhiteText,
                unfocusedTextColor = OffWhiteText
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MatteBlackSurface)
        ) {
            (1..31).forEach { day ->
                DropdownMenuItem(
                    text = { Text(day.toString(), color = OffWhiteText) },
                    onClick = {
                        onDaySelected(day)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearDropdown(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val years = remember { (currentYear - 80..currentYear - 10).toList().reversed() }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Year", fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PhilippineGold,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = OffWhiteText,
                unfocusedTextColor = OffWhiteText
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MatteBlackSurface)
        ) {
            years.forEach { yr ->
                DropdownMenuItem(
                    text = { Text(yr.toString(), color = OffWhiteText) },
                    onClick = {
                        onYearSelected(yr)
                        expanded = false
                    }
                )
            }
        }
    }
}
