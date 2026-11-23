package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UserProfile
import com.example.ui.common.AllEmojisPickerDialog
import com.example.ui.common.SogoEmojiCatalog
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
import com.example.ui.theme.OutlineGold
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Modal bottom sheet for creating a new [ActivityPost] inside Firestore's `activities` collection.
 * Supports activity category selection, text/emoji input, and PickVisualMedia/camera image attachment.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostCreationSheet(
    currentUserProfile: UserProfile?,
    isPosting: Boolean,
    onDismiss: () -> Unit,
    onSubmitPost: (
        content: String,
        moodEmoji: String,
        activityType: String,
        imageUrl: String?,
        onSuccess: () -> Unit
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SogoEmojiCatalog.activityMoods.first()) }
    var attachedImageDataUri by remember { mutableStateOf<String?>(null) }
    var showAllEmojisDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberProfilePhotoPickerController { dataUri ->
        attachedImageDataUri = dataUri
    }

    if (imagePicker.showSourceChooser) {
        ProfilePhotoSourceDialog(
            onDismiss = { imagePicker.dismissChooser() },
            onChooseGallery = { imagePicker.launchGalleryPicker() },
            onTakePhoto = { imagePicker.launchCameraCapture() }
        )
    }

    if (showAllEmojisDialog) {
        AllEmojisPickerDialog(
            title = "Insert Emoji",
            selectedEmoji = null,
            onDismiss = { showAllEmojisDialog = false },
            onEmojiSelected = { emoji ->
                if (content.length + emoji.length <= 500) {
                    content += emoji
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MatteBlackSurface,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
                .testTag("post_creation_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BurgundyPrimary)
                            .border(1.5.dp, GoldAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentUserProfile?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentUserProfile?.photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Your avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = (currentUserProfile?.displayName ?: "S").take(1).uppercase(),
                                color = GoldLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Share New Activity",
                            color = GoldLight,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "@${currentUserProfile?.username?.takeIf { it.isNotBlank() } ?: currentUserProfile?.displayName ?: "member"}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close post creator",
                        tint = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ACTIVITY CATEGORY",
                color = GoldAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SogoEmojiCatalog.activityMoods.forEach { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) BurgundyPrimary else MatteBlackSurfaceVariant,
                        border = BorderStroke(1.dp, if (isSelected) GoldLight else OutlineSubtle),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedCategory = category }
                            .testTag("post_category_${category.second.lowercase().replace(" ", "_")}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = category.first, fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = category.second,
                                color = if (isSelected) GoldLight else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 500) content = it },
                label = { Text("What are you doing right now?", color = TextMuted) },
                placeholder = {
                    Text(
                        text = "Sharing ${selectedCategory.first} ${selectedCategory.second} with SOGO...",
                        color = TextDim,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp)
                    .testTag("post_creation_input"),
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = OutlineSubtle,
                    focusedContainerColor = MatteBlackCard,
                    unfocusedContainerColor = MatteBlackCard
                )
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAllEmojisDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("post_creation_emoji_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Add emoji",
                            tint = GoldLight
                        )
                    }
                    listOf("☕", "❤️", "✨", "🔥", "🎉", "🌴").forEach { quick ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MatteBlackSurfaceVariant,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (content.length + quick.length <= 500) content += quick
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = quick, fontSize = 16.sp)
                            }
                        }
                    }
                }
                Text(
                    text = "${content.length}/500",
                    color = TextDim,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Photo Attachment Row / Preview (uses PickVisualMedia via rememberProfilePhotoPickerController)
            if (!attachedImageDataUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, OutlineGold, RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(attachedImageDataUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Selected activity photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = MatteBlackSurface.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .clickable { attachedImageDataUri = null }
                            .testTag("post_creation_remove_photo")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attached photo",
                                tint = AlertRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { imagePicker.openChooser() },
                    enabled = !imagePicker.isProcessing && !isPosting,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("post_creation_add_photo_button"),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, OutlineGold)
                ) {
                    if (imagePicker.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = GoldLight,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach Photo",
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (attachedImageDataUri == null) "Add Photo" else "Change Photo",
                        color = GoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        onSubmitPost(
                            content,
                            selectedCategory.first,
                            selectedCategory.second,
                            attachedImageDataUri
                        ) {
                            onDismiss()
                        }
                    },
                    enabled = content.isNotBlank() && !isPosting && !imagePicker.isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("post_creation_submit_button"),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BurgundyPrimary,
                        contentColor = GoldLight,
                        disabledContainerColor = BurgundyDark.copy(alpha = 0.45f),
                        disabledContentColor = TextDim
                    )
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = GoldLight,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Publish Activity",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
