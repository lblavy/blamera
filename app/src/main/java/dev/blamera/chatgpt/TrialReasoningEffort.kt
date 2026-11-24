package dev.blamera.chatgpt

private val STANDARD_TRIAL_EFFORTS = listOf("none", "low", "medium", "high", "xhigh", "max")
private val REASONING_TRIAL_EFFORTS = STANDARD_TRIAL_EFFORTS.drop(1)

/** Exact documented profiles; future variants must not inherit guessed capabilities. */
internal fun trialReasoningEfforts(slug: String): List<String> = when (slug) {
    "gpt-6.1-sol", "gpt-6-astra" -> REASONING_TRIAL_EFFORTS
    "gpt-6.1-terra", "gpt-6-sol", "gpt-6-luna", "gpt-5.6-sol",
    "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.6" -> STANDARD_TRIAL_EFFORTS
    else -> emptyList()
}

internal fun validTrialEffort(effort: String?): Boolean =
    effort == null || effort in STANDARD_TRIAL_EFFORTS

internal fun validateTrialEffort(model: String, effort: String?) {
    requireTrial(effort == null || effort in trialReasoningEfforts(model), TrialFailure.INPUT)
}
