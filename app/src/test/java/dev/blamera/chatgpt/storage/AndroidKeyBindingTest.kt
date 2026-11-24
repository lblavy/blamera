package dev.blamera.chatgpt.storage

import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidKeyBindingTest {
    @Test
    fun defaultAndBotanicalBindingsLookupDifferentAliasesAndKeepLegacyCipherCompatibility() {
        val legacy = key()
        val botanical = key()
        val provider =
            ControlledAndroidKeys(mapOf(LEGACY_ALIAS to legacy, PLANT_ALIAS to botanical))
        withProvider(provider) {
            val oldRecord = legacyEnvelope(legacy)
            val defaultBinding = AndroidSessionKeyProvider()
            assertSame(legacy, defaultBinding.key())
            assertEquals("FAKE legacy session", SessionCipher(defaultBinding).decrypt(oldRecord))
            val botanicalBinding = androidSessionKeys(PLANT_ALIAS)
            assertSame(botanical, botanicalBinding.key())
            val botanicalCipher = SessionCipher(PLANT_ALIAS, botanicalBinding)
            val encrypted = botanicalCipher.encrypt("FAKE plant credential")
            assertEquals("FAKE plant credential", botanicalCipher.decrypt(encrypted))
            assertThrows(SessionStorageException::class.java) {
                SessionCipher(defaultBinding).decrypt(encrypted)
            }
            assertEquals(setOf(LEGACY_ALIAS, PLANT_ALIAS), provider.requestedAliases.toSet())
            assertEquals(LEGACY_ALIAS, provider.requestedAliases.first())
        }
    }

    @Test
    fun platformLookupFailureReachesOnlyTheSanitizedProtectedBoundary() {
        val provider = ControlledAndroidKeys(mapOf(PLANT_ALIAS to key())).apply { failLoads() }
        withProvider(provider) {
            val cipher = SessionCipher(PLANT_ALIAS, androidSessionKeys(PLANT_ALIAS))
            val error = assertThrows(SessionStorageException::class.java) {
                cipher.encrypt("FAKE private key")
            }
            assertNull(error.cause)
            assertFalse(error.toString().contains("SECRET"))
            assertTrue(provider.requestedAliases.isEmpty())
        }
    }

    private fun withProvider(provider: ControlledAndroidKeys, action: () -> Unit) {
        assertTrue(Security.insertProviderAt(provider, 1) > 0)
        try {
            assertSame(provider, Security.getProviders("KeyStore.AndroidKeyStore").first())
            action()
        } finally {
            Security.removeProvider(provider.name)
        }
    }

    /** Independent v1 wire fixture: it must not inherit the current SessionCipher defaults. */
    private fun legacyEnvelope(key: SecretKey): ByteArray {
        val nonce = ByteArray(12) { it.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(LEGACY_ALIAS.toByteArray(Charsets.UTF_8))
        return byteArrayOf(1) + nonce + cipher.doFinal("FAKE legacy session".toByteArray())
    }

    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}

private const val LEGACY_ALIAS = "dev.blamera.chatgpt.session.v1"
private const val PLANT_ALIAS = "dev.blamera.plantnet.key.v1"
