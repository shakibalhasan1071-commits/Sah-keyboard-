fun KeyboardSuggestionStrip(
    suggestions: List<String>,
    isDarkTheme: Boolean,
    onSelectSuggestion: (String) -> Unit,
    onOpenAiReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    val bg = if (isDarkTheme) Color(0xFF26282E) else Color(0xFFE5E9EE)
    val textColor = if (isDarkTheme) Color(0xFFF1F2F6) else Color(0xFF1F2937)
    val accentColor = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1A73E8)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(bg)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // AI Reply Engine Button
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { onOpenAiReply() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Auto Reply",
                tint = Color(0xFFA142F4),
                modifier = Modifier.size(18.dp)
            )
        }

        val displaySuggestions = suggestions.take(3)
        displaySuggestions.forEachIndexed { index, suggestion ->
            val isCenter = (index == 1 || (displaySuggestions.size == 1 && index == 0))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectSuggestion(suggestion) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = suggestion,
                    fontSize = if (isCenter) 15.sp else 13.sp,
                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCenter) accentColor else textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (index < displaySuggestions.size - 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(textColor.copy(alpha = 0.2f))
                )
            }
        }
    }
}
