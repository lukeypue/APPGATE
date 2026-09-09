package com.aibrowser.knowledgehub;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FreshnessTest {
    @Test public void neverChecked() { assertEquals("NEVER CHECKED", Freshness.state(0, 100)); }
    @Test public void freshWithinDay() { long now=10_000_000_000L; assertEquals("FRESH", Freshness.state(now-2*3_600_000L, now)); }
    @Test public void updateAfterDay() { long now=10_000_000_000L; assertEquals("UPDATE", Freshness.state(now-30*3_600_000L, now)); }
    @Test public void staleAfterWeek() { long now=10_000_000_000L; assertEquals("STALE", Freshness.state(now-8*24*3_600_000L, now)); }
}
