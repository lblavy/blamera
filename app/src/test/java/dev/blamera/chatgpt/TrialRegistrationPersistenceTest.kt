package dev.blamera.chatgpt

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrialRegistrationPersistenceTest {
    @Test fun logoutMarkerSurvivesNewAdaptersWhilePublicRegistrationIsPreserved() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("chatgpt-trial-registration", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val initial = AndroidTrialRegistration(context)
        assertFalse(initial.sessionBlocked)
        initial.issuedClientId = "synthetic_client"
        initial.sessionBlocked = true
        val restart = AndroidTrialRegistration(context)
        assertEquals(initial.hostId, restart.hostId)
        assertEquals("synthetic_client", restart.issuedClientId)
        assertTrue(restart.sessionBlocked)
        restart.sessionBlocked = false
        assertFalse(AndroidTrialRegistration(context).sessionBlocked)
        assertEquals("synthetic_client", AndroidTrialRegistration(context).issuedClientId)
    }
}
