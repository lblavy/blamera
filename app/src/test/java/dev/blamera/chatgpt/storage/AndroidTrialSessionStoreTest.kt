package dev.blamera.chatgpt.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidTrialSessionStoreTest {
    @Test fun androidFactoryUsesPrivateNoBackupStorageAndSurvivesRecreation() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val key = key()
        val first = AndroidTrialSessionStore(context) { key }
        first.clear()
        first.save("synthetic credential record")
        val path = File(context.noBackupFilesDir, SESSION_FILENAME)
        assertEquals(
            "synthetic credential record",
            AndroidTrialSessionStore(context) {
                key
            }.load()
        )
        assertFalse(path.readText().contains("synthetic credential"))
        assertFalse(File(context.filesDir, SESSION_FILENAME).exists())
        first.clear()
        assertNull(first.load())
    }

    @Test fun existingKeystoreKeyIsReusedAndUnexpectedKeyTypeFailsClosed() {
        val expected = key()
        val provider = AndroidSessionKeyProvider({ expected }) {
            throw AssertionError("Existing keys must not be regenerated")
        }
        assertSame(expected, provider.key())
        val other = object : java.security.Key {
            override fun getAlgorithm() = "fixture"
            override fun getFormat() = "fixture"
            override fun getEncoded() = byteArrayOf()
        }
        assertThrows(SessionStorageException::class.java) {
            AndroidSessionKeyProvider({ other }) { expected }.key()
        }
    }

    @Test fun newlyGeneratedKeyRequiresAes256GcmAndFreshRandomNoncesWithoutBiometricPrompt() {
        val expected = key()
        var requested: KeyGenParameterSpec? = null
        val provider = AndroidSessionKeyProvider({ null }) {
            requested = it
            expected
        }
        assertSame(expected, provider.key())
        val spec = checkNotNull(requested)
        assertEquals(256, spec.keySize)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, spec.purposes)
        assertArrayEquals(arrayOf("GCM"), spec.blockModes)
        assertArrayEquals(arrayOf("NoPadding"), spec.encryptionPaddings)
        org.junit.Assert.assertTrue(spec.isRandomizedEncryptionRequired)
        assertFalse(spec.isUserAuthenticationRequired)
    }

    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
