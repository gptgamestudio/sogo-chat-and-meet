package com.example.ui.common

data class EmojiCategory(
    val title: String,
    val icon: String,
    val emojis: List<String>
)

object SogoEmojiCatalog {
    val quickReactionEmojis: List<String> = listOf(
        "👍", "❤️", "😂", "😍", "🔥", "👏", "😢", "🎉"
    )

    val activityMoods: List<Pair<String, String>> = listOf(
        "☕" to "Coffee Time",
        "🍽️" to "Food Trip",
        "💼" to "Work Mode",
        "🏋️" to "Workout",
        "🎵" to "Music & Chill",
        "🚗" to "Road Trip",
        "🌅" to "Good Vibes",
        "🌙" to "Night Owls"
    )

    val categories: List<EmojiCategory> = listOf(
        EmojiCategory(
            title = "Love & Hearts",
            icon = "❤️",
            emojis = listOf(
                "❤️", "🩷", "🧡", "💛", "💚", "💙", "💜", "🤍",
                "💖", "💕", "💞", "💓", "💗", "💘", "💝", "🫶",
                "😍", "🥰", "😘", "😚", "💋", "🌹", "💐", "💍"
            )
        ),
        EmojiCategory(
            title = "Smileys",
            icon = "😊",
            emojis = listOf(
                "😊", "😄", "😁", "😂", "🤣", "😉", "😌", "😇",
                "🥳", "🤩", "😎", "🤗", "🤭", "🥹", "🥺", "😢",
                "😭", "😮", "😲", "😴", "🤔", "😜", "🤪", "🙃"
            )
        ),
        EmojiCategory(
            title = "Gestures & Vibes",
            icon = "👍",
            emojis = listOf(
                "👍", "👎", "👏", "🙌", "🙏", "🤝", "👋", "👌",
                "✌️", "🤞", "🫰", "🤟", "💪", "🔥", "✨", "⭐",
                "💯", "🎉", "🎊", "🏆", "👑", "💎", "⚡", "💥"
            )
        ),
        EmojiCategory(
            title = "Activities",
            icon = "☕",
            emojis = listOf(
                "☕", "🧋", "🍵", "🍲", "🍕", "🍔", "🍰", "🍻",
                "🍷", "🎵", "🎤", "🎧", "🎬", "🎮", "📸", "🚗",
                "✈️", "🏖️", "🌅", "🌙", "💼", "🏋️", "🛍️", "🏠"
            )
        )
    )

    val allEmojis: List<String> = categories.flatMap { it.emojis }.distinct()
}
