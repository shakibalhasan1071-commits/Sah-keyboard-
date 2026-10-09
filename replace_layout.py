import sys

file_path = "app/src/main/java/com/example/moekeyboard/ime/MoeKeyboardUi.kt"

with open(file_path, "r") as f:
    content = f.read()

target = """            } else if (!isResizePanelOpen && !isCustomizerOpen) {
                KeyboardToolbar("""

replacement = """            } else if (!isResizePanelOpen && !isCustomizerOpen) {
                val showSuggestions = (mode == KeyboardMode.BENGALI || mode == KeyboardMode.ENGLISH) && currentSuggestions.isNotEmpty()
                if (showSuggestions) {
                    KeyboardSuggestionStrip(
                        suggestions = currentSuggestions,
                        isDarkTheme = theme.isDark,
                        onSelectSuggestion = { suggestion ->
                            if (onReplacePrefixAndInsert != null) {
                                onReplacePrefixAndInsert(currentWordPrefix, suggestion + " ")
                            } else {
                                onKeyClick(suggestion + " ")
                            }
                            SmartSuggestionEngine.learnWord(suggestion)
                            currentWordPrefix = ""
                        },
                        onOpenAiReply = {
                            val next = !isAiPanelOpen
                            closeAllPanelsExcept("ai")
                            isAiPanelOpen = next
                        },
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                } else {
                    KeyboardToolbar("""

content = content.replace(target, replacement)

target2 = """                )

                // Smart Word Prediction / Suggestion Strip (Gboard Style)
                if ((mode == KeyboardMode.BENGALI || mode == KeyboardMode.ENGLISH) && currentSuggestions.isNotEmpty()) {
                    KeyboardSuggestionStrip(
                        suggestions = currentSuggestions,
                        isDarkTheme = theme.isDark,
                        onSelectSuggestion = { suggestion ->
                            if (onReplacePrefixAndInsert != null) {
                                onReplacePrefixAndInsert(currentWordPrefix, suggestion + " ")
                            } else {
                                onKeyClick(suggestion + " ")
                            }
                            SmartSuggestionEngine.learnWord(suggestion)
                            currentWordPrefix = ""
                        },
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }"""

replacement2 = """                )
                }
            }"""

content = content.replace(target2, replacement2)

with open(file_path, "w") as f:
    f.write(content)
