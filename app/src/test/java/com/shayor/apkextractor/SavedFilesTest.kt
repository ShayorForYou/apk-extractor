package com.shayor.apkextractor

import com.shayor.apkextractor.data.SavedFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedFilesTest {
    @Test
    fun stripsCopySuffix() {
        assertEquals("App_v1.2.apk", SavedFiles.withoutCopySuffix("App_v1.2 (1).apk"))
        assertEquals("App_v1.2.apks", SavedFiles.withoutCopySuffix("App_v1.2 (12).apks"))
        assertEquals("App_v1.2", SavedFiles.withoutCopySuffix("App_v1.2 (3)"))
    }

    @Test
    fun keepsOtherNames() {
        assertEquals("App_v1.2.apk", SavedFiles.withoutCopySuffix("App_v1.2.apk"))
        assertEquals("App (Beta)_v1.apk", SavedFiles.withoutCopySuffix("App (Beta)_v1.apk"))
        assertEquals("App(1).apk", SavedFiles.withoutCopySuffix("App(1).apk"))
        assertEquals(" (1).apk", SavedFiles.withoutCopySuffix(" (1).apk"))
    }
}
