package dev.blamera.chatgpt

/** Runtime hooks keep OAuth-return and later refresh presence budgets independent. */
internal class TrialGatewayEnvironment(
    val nowSeconds: () -> Long = { System.currentTimeMillis() / TrialLimits.MILLIS_PER_SECOND },
    val random: () -> String = ::trialRandomValue,
    val awaitAppForeground: suspend () -> Unit = { },
    val awaitRefreshForeground: suspend () -> Unit = { }
)
