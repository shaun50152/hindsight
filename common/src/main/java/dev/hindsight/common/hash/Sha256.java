package dev.hindsight.common.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable SHA-256 hashing for persisted identities and audit chains.
 * Never use {@link Object#hashCode()} for anything persisted or routed.
 */
public final class Sha256 {

    private static final HexFormat HEX = HexFormat.of();

    private Sha256() {}

    public static String hex(byte[] input) {
        return HEX.formatHex(digest(input));
    }

    public static String hex(String utf8Input) {
        return hex(utf8Input.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
