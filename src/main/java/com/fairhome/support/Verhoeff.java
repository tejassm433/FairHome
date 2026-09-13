package com.fairhome.support;

/**
 * Verhoeff check-digit scheme, the same one used by Aadhaar numbers.
 *
 * <p>Validating the checksum locally catches most transcription errors made while typing paper
 * forms in, long before those errors reach the duplicate review queue.
 */
public final class Verhoeff {

    private static final int[][] D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
            {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
            {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
            {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    private static final int[][] P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
            {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
            {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    private static final int[] INV = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};

    private Verhoeff() {
    }

    public static boolean isChecksumValid(String digits) {
        if (digits == null || digits.isEmpty()) {
            return false;
        }
        int c = 0;
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            char ch = digits.charAt(len - 1 - i);
            if (ch < '0' || ch > '9') {
                return false;
            }
            c = D[c][P[i % 8][ch - '0']];
        }
        return c == 0;
    }

    /** Returns the check digit that makes {@code payload + checkDigit} a valid Verhoeff string. */
    public static int checkDigitFor(String payload) {
        int c = 0;
        int len = payload.length();
        for (int i = 0; i < len; i++) {
            char ch = payload.charAt(len - 1 - i);
            c = D[c][P[(i + 1) % 8][ch - '0']];
        }
        return INV[c];
    }
}
