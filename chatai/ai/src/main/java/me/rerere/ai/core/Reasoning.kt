package me.rerere.ai.core

enum class ReasoningLevel(
  val budgetTokens: Int,
  val effort: String
) {
  OFF(0, "low"),
  LOW(1024, "low"),
  MEDIUM(16_000, "medium"),
  HIGH(32_000, "high");

  companion object {
    fun fromBudgetTokens(budgetTokens: Int): ReasoningLevel {
      return entries.minByOrNull { kotlin.math.abs(it.budgetTokens - budgetTokens) } ?: OFF
    }
  }
}