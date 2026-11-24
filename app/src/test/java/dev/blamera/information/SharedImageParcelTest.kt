package dev.blamera.information

import android.content.Intent
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedImageParcelTest {
    @Test fun clipDataOnlyReadingPreservesLensImageWithoutTextAfterParcelTransfer() {
        val uri = FakeSharedImage().uri
        for (target in ImageShareTarget.entries) {
            val launched = mutableListOf<Intent>()
            val sender = ImageShareSender("dev.blamera.lens-images", { true }, launched::add)
            assertEquals(ImageShareResult.OPENED, sender.send(uri, target))
            val parcel = Parcel.obtain()
            try {
                launched.single().writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                val received = Intent.CREATOR.createFromParcel(parcel)
                val clip = checkNotNull(received.clipData)
                val item = clip.getItemAt(0)
                assertEquals(1, clip.itemCount)
                assertEquals(uri, item.uri)
                assertNull(item.text)
                assertNull(item.htmlText)
                assertNull(item.intent)
                assertEquals(1, clip.description.mimeTypeCount)
                assertEquals("image/jpeg", clip.description.getMimeType(0))
                assertEquals("image/jpeg", received.type)
                assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, received.flags)
                assertNull(received.getStringExtra(Intent.EXTRA_TEXT))
            } finally {
                parcel.recycle()
            }
        }
    }
}
