package com.extractor

import com.extractor.data.FileNames
import com.extractor.data.NameFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun buildsReadableNames() {
        assertEquals("WhatsApp_v2.24.1.apk", FileNames.build("WhatsApp", "com.whatsapp", "2.24.1", NameFormat.LABEL_VERSION, "apk"))
        assertEquals("com.whatsapp_v2.24.1.apks", FileNames.build("WhatsApp", "com.whatsapp", "2.24.1", NameFormat.PACKAGE_VERSION, "apks"))
        assertEquals(
            "WhatsApp_com.whatsapp_v2.24.1.apk",
            FileNames.build("WhatsApp", "com.whatsapp", "2.24.1", NameFormat.LABEL_PACKAGE_VERSION, "apk"),
        )
    }

    @Test
    fun doesNotDoubleTheVersionPrefix() {
        assertEquals("App_v1.0.apk", FileNames.build("App", "a.b", "v1.0", NameFormat.LABEL_VERSION, "apk"))
    }

    @Test
    fun replacesIllegalCharacters() {
        val name = FileNames.build("A/B:C*D?\"E<F>G|H\\I", "x.y", "1", NameFormat.LABEL_VERSION, "apk")
        assertFalse(name.any { it in "/\\:*?\"<>|" })
        assertEquals("A_B_C_D__E_F_G_H_I_v1.apk", name)
    }

    @Test
    fun stripsLeadingDotsAndControlCharacters() {
        assertEquals("hidden", FileNames.sanitize("...hidden", "fallback"))
        assertEquals("a_b", FileNames.sanitize("a\u0000b", "fallback"))
        assertEquals("a b", FileNames.sanitize("a \n\t b", "fallback"))
    }

    @Test
    fun fallsBackWhenNameIsEmpty() {
        assertEquals("fallback", FileNames.sanitize("   ...   ", "fallback"))
    }

    @Test
    fun truncatesWithoutBreakingMultiByteCharacters() {
        val emoji = "😀" // 4 bytes in UTF-8
        val long = emoji.repeat(100) // 400 bytes
        val result = FileNames.sanitize(long, "fallback")
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 180)
        assertEquals(0, result.length % 2) // never splits a surrogate pair
        assertTrue(result.all { Character.isSurrogate(it) })
    }

    @Test
    fun finalNameFitsFilesystemLimit() {
        val name = FileNames.build("x".repeat(500), "pkg", "1", NameFormat.LABEL_VERSION, "apks")
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 255)
        assertTrue(name.endsWith(".apks"))
    }
}
