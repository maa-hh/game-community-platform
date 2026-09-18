package com.game.community.recommend.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HotRankPeriodUtilsTest {

    @Test
    void parsesIsoDailyAndWeeklyPeriods() {
        assertEquals(LocalDate.of(2026, 9, 18),
                HotRankPeriodUtils.parseDailyPeriodKey("2026-09-18"));
        assertEquals("2026-W38",
                HotRankPeriodUtils.weeklyPeriodKey(LocalDate.of(2026, 9, 18)));
        assertEquals(LocalDate.of(2026, 9, 14),
                HotRankPeriodUtils.parseWeeklyPeriodKey("2026-W38"));
    }

    @Test
    void rejectsMalformedPeriodInsteadOfReturningServerErrorLater() {
        assertThrows(RuntimeException.class,
                () -> HotRankPeriodUtils.parseWeeklyPeriodKey("2026-W99"));
        assertThrows(RuntimeException.class,
                () -> HotRankPeriodUtils.parseDailyPeriodKey("2026-9-18"));
    }
}
