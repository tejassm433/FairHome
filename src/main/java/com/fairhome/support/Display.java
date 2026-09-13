package com.fairhome.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Date formatting for the screens.
 *
 * <p>Formatting lives in Java rather than in the templates so the two do not need a Thymeleaf temporal
 * extension on the classpath, and so a date reads the same way everywhere in the app.
 */
public final class Display {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_SECONDS =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ENGLISH);

    private Display() {
    }

    public static String date(Instant instant) {
        return instant == null ? "-" : DATE.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static String date(LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    public static String timestamp(Instant instant) {
        return instant == null ? "-" : DATE_TIME_SECONDS.format(instant.atZone(ZoneId.systemDefault()));
    }
}
