package com.game.community.recommend.util;

import com.game.community.model.message.ArticleBehaviorMessage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;

public final class HotRankPeriodUtils {

    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /** ISO 自然周（周一至周日），与前端 dayjs isoWeek 一致 */
    private static final WeekFields ISO_WEEK = WeekFields.ISO;

    private static final DateTimeFormatter DAILY_PERIOD = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final DateTimeFormatter DAILY_REDIS = DateTimeFormatter.BASIC_ISO_DATE;

    private HotRankPeriodUtils() {
    }

    public static LocalDate today() {
        return LocalDate.now(SHANGHAI);
    }

    public static String dailyPeriodKey() {
        return dailyPeriodKey(today());
    }

    public static String dailyPeriodKey(LocalDate date) {
        return date.format(DAILY_PERIOD);
    }

    public static String dailyRedisSegment() {
        return dailyRedisSegment(today());
    }

    public static String dailyRedisSegment(LocalDate date) {
        return date.format(DAILY_REDIS);
    }

    public static String weeklyPeriodKey() {
        return weeklyPeriodKey(today());
    }

    public static String weeklyPeriodKey(LocalDate date) {
        int week = date.get(ISO_WEEK.weekOfWeekBasedYear());
        int year = date.get(ISO_WEEK.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    /** 当前自然周（仅用于 Redis 累积，不直接展示） */
    public static String currentWeeklyPeriodKey() {
        return weeklyPeriodKey(today());
    }

    /** 周榜默认展示：上一完整自然周（上周一至上周日） */
    public static String defaultWeeklyDisplayPeriodKey() {
        return previousWeeklyPeriodKey();
    }

    public static String weeklyRedisSegment() {
        return currentWeeklyPeriodKey();
    }

    /** 上一完整自然周的 periodKey（上周一所在周） */
    public static String previousWeeklyPeriodKey() {
        LocalDate thisWeekMonday = today().with(ISO_WEEK.dayOfWeek(), 1);
        return weeklyPeriodKey(thisWeekMonday.minusWeeks(1));
    }

    public static boolean isToday(String dailyPeriodKey) {
        return dailyPeriodKey().equals(dailyPeriodKey);
    }

    public static boolean isToday(LocalDate date) {
        return today().equals(date);
    }

    public static boolean isCurrentWeek(String weeklyPeriodKey) {
        return currentWeeklyPeriodKey().equals(weeklyPeriodKey);
    }

    public static boolean isCurrentWeek(LocalDate date) {
        return currentWeeklyPeriodKey().equals(weeklyPeriodKey(date));
    }

    public static long resolveEventTimeMs(ArticleBehaviorMessage message) {
        if (message == null || message.getEventTimeMs() == null) {
            return System.currentTimeMillis();
        }
        return message.getEventTimeMs();
    }

    public static LocalDate toLocalDate(long eventTimeMs) {
        return Instant.ofEpochMilli(eventTimeMs).atZone(SHANGHAI).toLocalDate();
    }

    public static LocalDateTime toLocalDateTime(long eventTimeMs) {
        return Instant.ofEpochMilli(eventTimeMs).atZone(SHANGHAI).toLocalDateTime();
    }

    public static LocalDate eventDate(ArticleBehaviorMessage message) {
        return toLocalDate(resolveEventTimeMs(message));
    }

    public static LocalDate parseDailyPeriodKey(String periodKey) {
        return LocalDate.parse(periodKey, DAILY_PERIOD);
    }

    /** 解析周榜 periodKey（如 2026-W30）为当周周一（ISO） */
    public static LocalDate parseWeeklyPeriodKey(String periodKey) {
        if (periodKey == null || !periodKey.matches("\\d{4}-W\\d{2}")) {
            throw new DateTimeParseException("非法周榜周期", periodKey == null ? "null" : periodKey, 0);
        }
        String[] parts = periodKey.split("-W", 2);
        int year = Integer.parseInt(parts[0]);
        int week = Integer.parseInt(parts[1]);
        int maxWeek = LocalDate.of(year, 12, 28).get(ISO_WEEK.weekOfWeekBasedYear());
        if (week < 1 || week > maxWeek) {
            throw new DateTimeParseException("非法周榜周期", periodKey, 0);
        }
        return LocalDate.of(year, 1, 1)
                .with(ISO_WEEK.weekBasedYear(), year)
                .with(ISO_WEEK.weekOfWeekBasedYear(), week)
                .with(ISO_WEEK.dayOfWeek(), 1);
    }
}
