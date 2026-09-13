package com.fairhome.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

/**
 * Name normalisation and similarity used by the duplicate detector. Paper forms arrive with
 * inconsistent spellings, honorifics and word order, so we compare a canonical token form rather
 * than raw strings.
 */
public final class NameMatching {

    private static final Logger log = LoggerFactory.getLogger(NameMatching.class);

    private static final String[] HONORIFICS = {"mr", "mrs", "ms", "miss", "shri", "smt", "sri",
            "dr", "prof", "kum", "md", "mohd"};

    private NameMatching() {
    }

    /** Lower-cased, accent-stripped, honorific-free, alphabetically ordered tokens. */
    public static String normalise(String name) {
        log.debug("FairHome : NameMatching : in method normalise : START");
        if (name == null) {
            log.debug("FairHome : NameMatching : in method normalise : END");
            return "";
        }
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", " ");
        String[] tokens = Arrays.stream(ascii.trim().split("\\s+"))
                .filter(t -> !t.isBlank())
                .filter(t -> !isHonorific(t))
                .toArray(String[]::new);
        Arrays.sort(tokens);
        String result = String.join(" ", tokens);
        log.debug("FairHome : NameMatching : in method normalise : END");
        return result;
    }

    private static boolean isHonorific(String token) {
        log.debug("FairHome : NameMatching : in method isHonorific : START");
        for (String h : HONORIFICS) {
            if (h.equals(token)) {
                log.debug("FairHome : NameMatching : in method isHonorific : END");
                return true;
            }
        }
        log.debug("FairHome : NameMatching : in method isHonorific : END");
        return false;
    }

    /** Jaro-Winkler similarity in [0,1]; 1.0 means identical. */
    public static double similarity(String a, String b) {
        log.debug("FairHome : NameMatching : in method similarity : START");
        if (a == null || b == null) {
            log.debug("FairHome : NameMatching : in method similarity : END");
            return 0d;
        }
        if (a.equals(b)) {
            log.debug("FairHome : NameMatching : in method similarity : END");
            return 1d;
        }
        if (a.isEmpty() || b.isEmpty()) {
            log.debug("FairHome : NameMatching : in method similarity : END");
            return 0d;
        }
        double jaro = jaro(a, b);
        int prefix = 0;
        int max = Math.min(4, Math.min(a.length(), b.length()));
        while (prefix < max && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        double result = jaro + prefix * 0.1 * (1 - jaro);
        log.debug("FairHome : NameMatching : in method similarity : END");
        return result;
    }

    private static double jaro(String s1, String s2) {
        log.debug("FairHome : NameMatching : in method jaro : START");
        int window = Math.max(0, Math.max(s1.length(), s2.length()) / 2 - 1);
        boolean[] s1Matched = new boolean[s1.length()];
        boolean[] s2Matched = new boolean[s2.length()];

        int matches = 0;
        for (int i = 0; i < s1.length(); i++) {
            int start = Math.max(0, i - window);
            int end = Math.min(i + window + 1, s2.length());
            for (int j = start; j < end; j++) {
                if (s2Matched[j] || s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                s1Matched[i] = true;
                s2Matched[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            log.debug("FairHome : NameMatching : in method jaro : END");
            return 0d;
        }

        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < s1.length(); i++) {
            if (!s1Matched[i]) {
                continue;
            }
            while (!s2Matched[k]) {
                k++;
            }
            if (s1.charAt(i) != s2.charAt(k)) {
                transpositions++;
            }
            k++;
        }

        double m = matches;
        double result = (m / s1.length() + m / s2.length() + (m - transpositions / 2.0) / m) / 3.0;
        log.debug("FairHome : NameMatching : in method jaro : END");
        return result;
    }

    /** Keeps only the trailing 10 digits so "+91 98765 43210" and "09876543210" compare equal. */
    public static String normalisePhone(String phone) {
        log.debug("FairHome : NameMatching : in method normalisePhone : START");
        if (phone == null) {
            log.debug("FairHome : NameMatching : in method normalisePhone : END");
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        String result = digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
        log.debug("FairHome : NameMatching : in method normalisePhone : END");
        return result;
    }

    public static String normaliseEmail(String email) {
        log.debug("FairHome : NameMatching : in method normaliseEmail : START");
        String result = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        log.debug("FairHome : NameMatching : in method normaliseEmail : END");
        return result;
    }
}
