package dev.blamera.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraAccessTest {
    @Test
    fun refusalsAndRevocationHaveDistinctRecoverableStates() {
        val access = CameraAccess(false)
        assertTrue(access.activateOrRequest())
        access.acceptPermission(false, true)
        assertEquals(CameraDenial.DENIED, access.denial)
        assertFalse(access.pending)
        access.activateOrRequest()
        access.acceptPermission(false, false)
        assertEquals(CameraDenial.BLOCKED, access.denial)
        access.refresh(true)
        assertFalse(access.activateOrRequest())
        assertTrue(access.activated)
        access.refresh(false)
        assertEquals(CameraDenial.REVOKED, access.denial)
        assertFalse(access.activated)
    }

    @Test
    fun latePermissionResultsAreIgnoredAfterDisposeOrWithoutRequest() {
        val access = CameraAccess(false)
        access.acceptPermission(true, false)
        assertFalse(access.activated)
        access.activateOrRequest()
        access.dispose()
        access.acceptPermission(true, false)
        assertFalse(access.activated)
        assertFalse(access.pending)
        assertEquals(CameraDenial.REVOKED, CameraAccess(false, true).denial)
        val granted = CameraAccess(false)
        granted.activateOrRequest()
        granted.acceptPermission(true, false)
        assertEquals(CameraDenial.NONE, granted.denial)
        assertTrue(granted.activated)
    }
}
