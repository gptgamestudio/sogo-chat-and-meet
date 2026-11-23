package com.example.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BurgundyDark
import com.example.ui.theme.BurgundyLight
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.OutlineGold
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

enum class SogoBottomTab(val label: String, val tag: String) {
    HOME("Home", "bottom_nav_home"),
    MEMBERS("Members", "bottom_nav_members"),
    POST("Post", "bottom_nav_post"),
    ACTIVITIES("Activities", "bottom_nav_activities"),
    PROFILE("Profile", "bottom_nav_profile")
}

@Composable
fun SogoBottomNavBar(
    selectedTab: SogoBottomTab,
    onSelectTab: (SogoBottomTab) -> Unit,
    onOpenPostSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MatteBlackSurface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTabItem(
                label = SogoBottomTab.HOME.label,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                selected = selectedTab == SogoBottomTab.HOME,
                testTag = SogoBottomTab.HOME.tag,
                onClick = { onSelectTab(SogoBottomTab.HOME) },
                modifier = Modifier.weight(1f)
            )
            BottomTabItem(
                label = SogoBottomTab.MEMBERS.label,
                selectedIcon = Icons.Filled.Group,
                unselectedIcon = Icons.Outlined.Group,
                selected = selectedTab == SogoBottomTab.MEMBERS,
                testTag = SogoBottomTab.MEMBERS.tag,
                onClick = { onSelectTab(SogoBottomTab.MEMBERS) },
                modifier = Modifier.weight(1f)
            )

            // Center Raised Crimson '+' Post Creation Trigger
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenPostSheet() }
                    .testTag(SogoBottomTab.POST.tag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = (-2).dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(BurgundyLight, BurgundyPrimary, BurgundyDark))
                        )
                        .border(1.5.dp, GoldLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Create Activity Post",
                        tint = GoldLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = SogoBottomTab.POST.label,
                    color = GoldLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            BottomTabItem(
                label = SogoBottomTab.ACTIVITIES.label,
                selectedIcon = Icons.Filled.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome,
                selected = selectedTab == SogoBottomTab.ACTIVITIES,
                testTag = SogoBottomTab.ACTIVITIES.tag,
                onClick = { onSelectTab(SogoBottomTab.ACTIVITIES) },
                modifier = Modifier.weight(1f)
            )
            BottomTabItem(
                label = SogoBottomTab.PROFILE.label,
                selectedIcon = Icons.Filled.Person,
                unselectedIcon = Icons.Outlined.Person,
                selected = selectedTab == SogoBottomTab.PROFILE,
                testTag = SogoBottomTab.PROFILE.tag,
                onClick = { onSelectTab(SogoBottomTab.PROFILE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) GoldLight else TextMuted,
        label = "navIconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) GoldLight else TextMuted,
        label = "navTextColor"
    )

    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) BurgundyDark.copy(alpha = 0.55f) else MatteBlackSurface.copy(alpha = 0f)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 2.dp)
                    .clip(CircleShape)
                    .background(GoldAccent)
            )
        }
    }
}
