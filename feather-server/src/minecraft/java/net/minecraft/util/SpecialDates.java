package net.minecraft.util;

import java.time.Month;
import java.time.MonthDay;
import java.time.ZonedDateTime;
import java.util.List;

public class SpecialDates {
    public static final MonthDay HALLOWEEN = MonthDay.of(Month.OCTOBER, 31);
    public static final List<MonthDay> CHRISTMAS_RANGE = List.of(
        MonthDay.of(Month.DECEMBER, 24), MonthDay.of(Month.DECEMBER, 25), MonthDay.of(Month.DECEMBER, 26)
    );
    public static final MonthDay CHRISTMAS = MonthDay.of(Month.DECEMBER, 24);
    public static final MonthDay NEW_YEAR = MonthDay.of(Month.JANUARY, 1);

    // Gale start - predict Halloween
    /**
     * The 1-indexed month of the year that Halloween starts (inclusive).
     */
    private static final int halloweenMonthOfYear = 10;

    /**
     * The 1-indexed day of the month that Halloween starts (inclusive).
     */
    private static final int halloweenDayOfMonth = 31;

    /**
     * The next start of Halloween, given as milliseconds since the Unix epoch.
     * Will be 0 while not computed yet.
     */
    private static long nextHalloweenStart = 0;

    /**
     * The next end of Halloween, given as milliseconds since the Unix epoch.
     * Will be 0 while not computed yet.
     */
    private static long nextHalloweenEnd = 0;
    // Gale end - predict Halloween

    public static MonthDay dayNow() {
        return MonthDay.from(ZonedDateTime.now());
    }

    public static boolean isHalloween() {
        // Gale start - predict Halloween
        long currentEpochMillis = System.currentTimeMillis();

        // Update predicate
        if (nextHalloweenEnd == 0 || currentEpochMillis >= nextHalloweenEnd) {
            java.time.OffsetDateTime currentDate = java.time.OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(currentEpochMillis), java.time.ZoneId.systemDefault())
                .withHour(0).withMinute(0).withSecond(0).withNano(0); // Adjust to directly start or end at zero o'clock

            java.time.OffsetDateTime thisHalloweenStart = currentDate.withMonth(halloweenMonthOfYear).withDayOfMonth(halloweenDayOfMonth);
            java.time.OffsetDateTime thisHalloweenEnd = thisHalloweenStart.plusDays(1);

            // Move to next year date if current passed
            if (!currentDate.isBefore(thisHalloweenEnd)) {
                thisHalloweenStart = thisHalloweenStart.plusYears(1);
                thisHalloweenEnd = thisHalloweenEnd.plusYears(1);
            }

            nextHalloweenStart = thisHalloweenStart.toInstant().toEpochMilli();
            nextHalloweenEnd = thisHalloweenEnd.toInstant().toEpochMilli();
        }

        return currentEpochMillis >= nextHalloweenStart && currentEpochMillis < nextHalloweenEnd;
        // Gale end - predict Halloween
    }

    public static boolean isExtendedChristmas() {
        return CHRISTMAS_RANGE.contains(dayNow());
    }
}
