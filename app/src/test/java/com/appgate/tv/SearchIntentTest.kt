package com.appgate.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIntentTest {
    @Test
    fun parsesPriceAndDeepDescriptionRequirement() {
        val parsed = SearchIntentParser.parse("expedition under 8k with a 3.73 axle")
        assertEquals("expedition", parsed.coreQuery)
        assertEquals(8000, parsed.maxPrice)
        assertEquals(listOf("3.73 axle"), parsed.requiredTerms)
    }

    @Test
    fun parsesMileageAndDollarPrice() {
        val parsed = SearchIntentParser.parse("ford expedition under $8,000 under 150k miles with 3.73 axle")
        assertEquals(8000, parsed.maxPrice)
        assertEquals(150000, parsed.maxMileage)
        assertTrue(parsed.coreQuery.contains("ford expedition"))
    }

    @Test
    fun rejectsSummaryAboveHardPriceCeiling() {
        val parsed = SearchIntentParser.parse("expedition under 8k")
        assertTrue(SearchMatcher.summaryCouldMatch("2003 Ford Expedition Eddie Bauer 198,329 Miles $4,995", parsed))
        assertFalse(SearchMatcher.summaryCouldMatch("2027 Ford Expedition Tremor MSRP $86,660 $86,660", parsed))
    }

    @Test
    fun deepTermsCanMatchWordsSeparatedInDescription() {
        val parsed = SearchIntentParser.parse("expedition under 8k with a 3.73 axle")
        assertTrue(SearchMatcher.deepTextMatches("Factory tow package. Rear axle ratio is 3.73 with limited slip.", parsed))
        assertFalse(SearchMatcher.deepTextMatches("Rear axle ratio 3.31. Clean interior.", parsed))
    }

    @Test
    fun kslUrlCarriesHardPriceFilter() {
        val parsed = SearchIntentParser.parse("expedition under 8k with a 3.73 axle")
        val url = SearchUrlBuilder.build("ksl_cars", "https://cars.ksl.com/search/keyword/{q}", parsed)
        assertTrue(url.contains("keyword/expedition"))
        assertTrue(url.contains("priceTo/8000"))
        assertFalse(url.contains("3.73"))
    }
}
