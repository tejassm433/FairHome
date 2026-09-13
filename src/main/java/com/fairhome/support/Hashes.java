package com.fairhome.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Hashes {

    private static final Logger log = LoggerFactory.getLogger(Hashes.class);

    private Hashes() {
    }

    public static String sha256Hex(String input) {
        log.debug("FairHome : Hashes : in method sha256Hex : START");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            String hex = sb.toString();
            log.debug("FairHome : Hashes : in method sha256Hex : END");
            return hex;
        } catch (NoSuchAlgorithmException e) {
            log.error("FairHome : Hashes : in method sha256Hex : SHA-256 is required by every JVM", e);
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    public static String shortHash(String hex) {
        log.debug("FairHome : Hashes : in method shortHash : START");
        String result = hex == null || hex.length() < 12 ? hex : hex.substring(0, 12);
        log.debug("FairHome : Hashes : in method shortHash : END");
        return result;
    }
}
