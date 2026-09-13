package com.fairhome.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonicalisation and validation for the Aadhaar-style 12 digit national identity number that
 * FairHome uses as the single identity key for de-duplication.
 */
public final class NationalId {

    private static final Logger log = LoggerFactory.getLogger(NationalId.class);

    private NationalId() {
    }

    /** Strips spaces, dashes and any other separator so that "1234 5678 9012" == "123456789012". */
    public static String canonicalise(String raw) {
        log.debug("FairHome : NationalId : in method canonicalise : START");
        if (raw == null) {
            log.debug("FairHome : NationalId : in method canonicalise : END");
            return null;
        }
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            }
        }
        String canonical = sb.toString();
        log.debug("FairHome : NationalId : in method canonicalise : END");
        return canonical;
    }

    public static boolean isStructurallyValid(String canonical) {
        log.debug("FairHome : NationalId : in method isStructurallyValid : START");
        if (canonical == null || canonical.length() != 12) {
            log.debug("FairHome : NationalId : in method isStructurallyValid : END");
            return false;
        }
        // Real Aadhaar numbers never begin with 0 or 1.
        if (canonical.charAt(0) == '0' || canonical.charAt(0) == '1') {
            log.debug("FairHome : NationalId : in method isStructurallyValid : END");
            return false;
        }
        boolean valid = Verhoeff.isChecksumValid(canonical);
        log.debug("FairHome : NationalId : in method isStructurallyValid : END");
        return valid;
    }

    public static String last4(String canonical) {
        log.debug("FairHome : NationalId : in method last4 : START");
        if (canonical == null || canonical.length() < 4) {
            log.debug("FairHome : NationalId : in method last4 : END");
            return "????";
        }
        String result = canonical.substring(canonical.length() - 4);
        log.debug("FairHome : NationalId : in method last4 : END");
        return result;
    }

    /** Display form for screens and exports: only the last four digits are ever shown. */
    public static String masked(String canonical) {
        log.debug("FairHome : NationalId : in method masked : START");
        String result = "XXXX XXXX " + last4(canonical);
        log.debug("FairHome : NationalId : in method masked : END");
        return result;
    }
}
