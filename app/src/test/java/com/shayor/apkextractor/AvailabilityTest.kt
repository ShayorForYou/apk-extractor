package com.shayor.apkextractor

import com.shayor.apkextractor.data.Availability
import com.shayor.apkextractor.data.classify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AvailabilityTest {
    private val folder = "content://tree/Archive"

    @Test
    fun presentFileInReachableFolderIsAvailable() {
        assertEquals(Availability.Available, classify(folder, { true }) { true })
    }

    @Test
    fun deletedFileInReachableFolderIsMissing() {
        assertEquals(Availability.Missing, classify(folder, { true }) { false })
    }

    @Test
    fun unreachableFolderIsNeverReportedMissing() {
        // The card is unmounted or access was revoked: the file may still be there.
        assertEquals(Availability.FolderUnreachable, classify(folder, { false }) { false })
    }

    @Test
    fun unreachableFolderDoesNotProbeTheFile() {
        var probed = false
        classify(folder, { false }) {
            probed = true
            false
        }
        assertFalse("the file must not be probed when its folder can't be reached", probed)
    }

    @Test
    fun defaultFolderIsCheckedWithNullTree() {
        var checkedTree: String? = folder
        val result = classify(null, { tree -> checkedTree = tree; true }) { false }
        assertEquals(null, checkedTree)
        assertEquals(Availability.Missing, result)
    }
}
