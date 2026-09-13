package com.fairhome.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
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

    private static final Logger log = LoggerFactory.getLogger(Display.class);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_SECONDS =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ENGLISH);

    private Display() {
    }

    public static String date(Instant instant) {
        log.debug("FairHome : Display : in method date : START");
        String result = instant == null ? "-" : DATE.format(instant.atZone(ZoneId.systemDefault()));
        log.debug("FairHome : Display : in method date : END");
        return result;
    }

    public static String date(LocalDate date) {
        log.debug("FairHome : Display : in method date : START");
        String result = date == null ? "-" : DATE.format(date);
        log.debug("FairHome : Display : in method date : END");
        return result;
    }

    public static String dateTime(Instant instant) {
        log.debug("FairHome : Display : in method dateTime : START");
        String result = instant == null ? "-" : DATE_TIME.format(instant.atZone(ZoneId.systemDefault()));
        log.debug("FairHome : Display : in method dateTime : END");
        return result;
    }

    public static String timestamp(Instant instant) {
        log.debug("FairHome : Display : in method timestamp : START");
        String result = instant == null ? "-" : DATE_TIME_SECONDS.format(instant.atZone(ZoneId.systemDefault()));
        log.debug("FairHome : Display : in method timestamp : END");
        return result;
    }

    public static String rupees(BigDecimal amount) {
        log.debug("FairHome : Display : in method rupees : START");
        if (amount == null) {
            log.debug("FairHome : Display : in method rupees : END");
            return "—";
        }
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.ENGLISH);
        format.setGroupingUsed(true);
        format.setMaximumFractionDigits(0);
        String result = "₹" + format.format(amount.setScale(0, RoundingMode.HALF_UP));
        log.debug("FairHome : Display : in method rupees : END");
        return result;
    }
}
