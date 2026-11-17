package com.example.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.MatteBlackSurfaceVariant
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllEmojisPickerDialog(
    title: String = "Choose Emoji Reaction",
    selectedEmoji: String? = null,
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val categories = SogoEmojiCatalog.categories
    val activeCategory = categories.getOrElse(selectedCategoryIndex) { categories.first() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MatteBlackSurface,
            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("all_emojis_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = GoldLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_emoji_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close emoji picker",
                            tint = TextMuted
                        )
                    }
                }

                // Quick Popular Emojis Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SogoEmojiCatalog.quickReactionEmojis.forEach { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) BurgundyPrimary else MatteBlackSurfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) GoldAccent else OutlineSubtle
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onEmojiSelected(emoji)
                                    onDismiss()
                                }
                                .testTag("quick_picker_emoji_$emoji")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEachIndexed { index, category ->
                        val isCurrent = index == selectedCategoryIndex
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isCurrent) BurgundyPrimary else MatteBlackSurfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) GoldAccent else OutlineSubtle
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedCategoryIndex = index }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = category.icon, fontSize = 15.sp)
                                Text(
                                    text = category.title,
                                    color = if (isCurrent) GoldLight else TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Full Emoji Grid for Selected Category
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeCategory.emojis.forEach { emoji ->
                            val isSelected = selectedEmoji == emoji
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) BurgundyPrimary else MatteBlackSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) GoldAccent else OutlineSubtle.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onEmojiSelected(emoji)
                                        onDismiss()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
