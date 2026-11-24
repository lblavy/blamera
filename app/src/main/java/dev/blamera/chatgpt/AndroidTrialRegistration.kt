package dev.blamera.chatgpt

import android.content.Context
import java.util.UUID

internal class AndroidTrialRegistration(context: Context) : TrialRegistrationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "chatgpt-trial-registration",
        Context.MODE_PRIVATE
    )

    override val hostId: String = preferences.getString("host", null) ?: (
        "urn:uuid:" + UUID.randomUUID().toString()
        ).also { requireTrial(preferences.edit().putString("host", it).commit()) }

    override var issuedClientId: String?
        get() = preferences.getString("client", null)
        set(value) {
            requireTrial(preferences.edit().putString("client", value).commit())
        }

    override var sessionBlocked: Boolean
        get() = preferences.getBoolean("session-blocked", false)
        set(value) {
            requireTrial(
                preferences.edit().putBoolean("session-blocked", value).commit(),
                TrialFailure.STORAGE
            )
        }
}
