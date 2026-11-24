package dev.blamera.information

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LensImageSenderTest {
    @Test fun aCompatibleGoogleHandlerReceivesOnlyThePreparedJpegByPublicShare() {
        val launched = mutableListOf<Intent>()
        val uri = Uri.parse("content://dev.blamera.lens-images/images/" + "a".repeat(64) + ".jpg")
        val sender = ImageShareSender(
            "dev.blamera.lens-images",
            { it.`package` == "com.google.android.googlequicksearchbox" },
            launched::add
        )
        assertEquals(ImageShareResult.OPENED, sender.send(uri))
        val intent = launched.single()
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals("com.google.android.googlequicksearchbox", intent.`package`)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertNotNull(intent.clipData)
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertNull(intent.clipData!!.getItemAt(0).text)
        assertNull(intent.clipData!!.getItemAt(0).htmlText)
        assertNull(intent.clipData!!.getItemAt(0).intent)
        assertEquals(1, intent.clipData!!.description.mimeTypeCount)
        assertEquals("image/jpeg", intent.clipData!!.description.getMimeType(0))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
        assertNull(intent.component)
        assertNull(intent.selector)
        assertNull(intent.data)
        assertEquals(setOf(Intent.EXTRA_STREAM), intent.extras!!.keySet())
        assertEquals(1, intent.clipData!!.itemCount)
    }

    @Test fun onlyTheTwoGooglePackagesAreConsideredAndLensIsPreferred() {
        val checked = mutableListOf<String?>()
        val launched = mutableListOf<Intent>()
        val sender = ImageShareSender("dev.blamera.lens-images", {
            checked.add(it.`package`)
            true
        }, launched::add)
        assertEquals(ImageShareResult.OPENED, sender.send(FakeSharedImage().uri))
        assertEquals(listOf("com.google.ar.lens"), checked)
        assertEquals("com.google.ar.lens", launched.single().`package`)
        assertEquals(
            ImageShareResult.UNAVAILABLE,
            ImageShareSender("dev.blamera.lens-images", { false }, { error("No target") })
                .send(FakeSharedImage().uri)
        )
    }

    @Test fun failuresAndForeignOrMalformedContentNeverFallBackToAWebSearch() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            assertEquals(
                ImageShareResult.FAILED,
                ImageShareSender("dev.blamera.lens-images", { true }, { throw failure })
                    .send(FakeSharedImage().uri)
            )
        }
        listOf(
            "https://www.google.com/search?q=cat",
            "content://other/images/" + "a".repeat(64) + ".jpg",
            "content://dev.blamera.lens-images/metadata/a.meta",
            "file:///sdcard/image.jpg"
        ).forEach {
            assertEquals(
                ImageShareResult.FAILED,
                ImageShareSender("dev.blamera.lens-images", { true }, { error("Must not launch") })
                    .send(Uri.parse(it))
            )
        }
    }

    @Test fun actualPackageManagerResolutionRejectsDisabledPrivateOrRestrictedHandlers() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val packageName = "com.google.ar.lens"
        val intent = imageShareIntent(FakeSharedImage().uri, packageName)
        val manager = shadowOf(activity.packageManager)
        fun register(
            exported: Boolean = true,
            enabled: Boolean = true,
            permission: String? = null,
            applicationEnabled: Boolean = true
        ) {
            manager.removeResolveInfosForIntent(intent, packageName)
            val info = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    this.packageName = packageName
                    name = "fixture.PublicImageReceiver"
                    this.exported = exported
                    this.enabled = enabled
                    this.permission = permission
                    applicationInfo = ApplicationInfo().apply { this.enabled = applicationEnabled }
                }
            }
            manager.addResolveInfoForIntent(intent, info)
        }
        register(exported = false)
        assertEquals(
            ImageShareResult.UNAVAILABLE,
            createImageShareSender(activity).send(FakeSharedImage().uri)
        )
        register(enabled = false)
        assertEquals(
            ImageShareResult.UNAVAILABLE,
            createImageShareSender(activity).send(FakeSharedImage().uri)
        )
        register(permission = "fixture.SIGNATURE_PERMISSION")
        assertEquals(
            ImageShareResult.UNAVAILABLE,
            createImageShareSender(activity).send(FakeSharedImage().uri)
        )
        register(applicationEnabled = false)
        assertEquals(
            ImageShareResult.UNAVAILABLE,
            createImageShareSender(activity).send(FakeSharedImage().uri)
        )
        register(permission = "fixture.ALLOWED_PERMISSION")
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions("fixture.ALLOWED_PERMISSION")
        assertEquals(
            ImageShareResult.OPENED,
            createImageShareSender(activity).send(FakeSharedImage().uri)
        )
        assertEquals(Intent.ACTION_SEND, shadowOf(activity).nextStartedActivity.action)
        register()
        assertEquals(
            ImageShareResult.OPENED,
            createImageShareSender(activity).send(FakeSharedImage().uri)
        )
        assertEquals(Intent.ACTION_SEND, shadowOf(activity).nextStartedActivity.action)
    }
}
