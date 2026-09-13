package com.fairhome.support;

/**
 * Canonicalisation and validation for the Aadhaar-style 12 digit national identity number that
 * FairHome uses as the single identity key for de-duplication.
 */
public final class NationalId {

    private NationalId() {
    }

    /** Strips spaces, dashes and any other separator so that "1234 5678 9012" == "123456789012". */
    public static String canonicalise(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static boolean isStructurallyValid(String canonical) {
        if (canonical == null || canonical.length() != 12) {
            return false;
        }
        // Real Aadhaar numbers never begin with 0 or 1.
        if (canonical.charAt(0) == '0' || canonical.charAt(0) == '1') {
            return false;
        }
        return Verhoeff.isChecksumValid(canonical);
    }

    public static String last4(String canonical) {
        if (canonical == null || canonical.length() < 4) {
            return "????";
        }
        return canonical.substring(canonical.length() - 4);
    }

    /** Display form for screens and exports: only the last four digits are ever shown. */
    public static String masked(String canonical) {
        return "XXXX XXXX " + last4(canonical);
    }
}
