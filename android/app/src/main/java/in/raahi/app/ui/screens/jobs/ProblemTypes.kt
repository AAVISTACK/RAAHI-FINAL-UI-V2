package `in`.raahi.app.ui.screens.jobs

data class ProblemType(val id: String, val label: String, val emoji: String, val suggestedReward: Int)

val PROBLEM_TYPES = listOf(
    ProblemType("puncture", "Puncture", "\uD83D\uDD27", 150),
    ProblemType("fuel", "No Fuel", "\u26FD", 200),
    ProblemType("breakdown", "Breakdown", "\uD83D\uDE97", 300),
    ProblemType("battery", "Dead Battery", "\uD83D\uDD0B", 200),
    ProblemType("accident", "Accident", "\u26A0\uFE0F", 500),
    ProblemType("towing", "Need Towing", "\uD83D\uDE9B", 400),
    ProblemType("other", "Other", "\u2753", 200),
)

val QUICK_PRICES = listOf(50, 100, 150, 200, 300, 500)
