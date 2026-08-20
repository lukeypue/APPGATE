package com.appgate.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokNavigationTest {

    @Test
    fun topicSearchBuildsVerifiedTagRoute() {
        assertEquals(
            "https://www.tiktok.com/tag/cats",
            TikTokNavigation.destinationForSearch("cats")
        )
        assertEquals(
            "https://www.tiktok.com/tag/funnycats",
            TikTokNavigation.destinationForSearch("# Funny Cats")
        )
    }

    @Test
    fun atSearchBuildsProfileRoute() {
        assertEquals(
            "https://www.tiktok.com/@creator.name_1",
            TikTokNavigation.destinationForSearch("@creator.name_1")
        )
    }

    @Test
    fun emptySearchDoesNotNavigate() {
        assertNull(TikTokNavigation.destinationForSearch("   #   "))
    }

    @Test
    fun blocksNativeAppAndOneLinkHandoffs() {
        assertTrue(TikTokNavigation.shouldBlock("tiktok://video/123"))
        assertTrue(TikTokNavigation.shouldBlock("snssdk1233://aweme/detail/123"))
        assertTrue(TikTokNavigation.shouldBlock("intent://details#Intent;scheme=tiktok;end"))
        assertTrue(TikTokNavigation.shouldBlock("market://details?id=com.zhiliaoapp.musically"))
        assertTrue(TikTokNavigation.shouldBlock("https://snssdk1233.onelink.me/abc/xyz"))
    }

    @Test
    fun allowsNormalTikTokWebPages() {
        assertFalse(TikTokNavigation.shouldBlock("https://www.tiktok.com/"))
        assertFalse(TikTokNavigation.shouldBlock("https://www.tiktok.com/tag/dogs"))
        assertFalse(TikTokNavigation.shouldBlock("https://www.tiktok.com/@creator/video/123"))
    }
}
