package com.appgate.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun parsesUpdateManifest() {
        val info = UpdateChecker.parseManifest(
            """{"versionCode":6,"versionName":"2.2.0","notes":"Improved learning","downloadUrl":"https://github.com/lukeypue/APPGATE/actions"}"""
        )
        assertEquals(6, info.versionCode)
        assertEquals("2.2.0", info.versionName)
        assertEquals("Improved learning", info.notes)
        assertEquals("https://github.com/lukeypue/APPGATE/actions", info.downloadUrl)
    }
}
