package io.github.levlandon.numai_plus;

/**
 * Created by Gleb on 14.12.2025.
 *
 * Minimal Base64 encoder based on public domain Base64 code by Robert Harder
 */
class Base64 {
    private final static char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private final static int[] DECODABET = new int[256];

    static {
        int i;
        for (i = 0; i < DECODABET.length; i++) {
            DECODABET[i] = -1;
        }
        for (i = 0; i < ALPHABET.length; i++) {
            DECODABET[ALPHABET[i]] = i;
        }
        DECODABET['='] = -2;
    }
    static String encode(byte[] source) {
        if (source == null) {
            return null;
        }

        int len = source.length;
        char[] out = new char[((len + 2) / 3) * 4];

        int i = 0;
        int outIdx = 0;

        while (i < len - 2) {
            int b1 = source[i++] & 0xFF;
            int b2 = source[i++] & 0xFF;
            int b3 = source[i++] & 0xFF;

            int val = (b1 << 16) | (b2 << 8) | b3;

            out[outIdx++] = ALPHABET[(val >>> 18) & 0x3F];
            out[outIdx++] = ALPHABET[(val >>> 12) & 0x3F];
            out[outIdx++] = ALPHABET[(val >>> 6) & 0x3F];
            out[outIdx++] = ALPHABET[val & 0x3F];
        }

        if (i < len) {
            int b1 = source[i++] & 0xFF;
            int b2 = (i < len) ? source[i] & 0xFF : 0;

            int val = (b1 << 16) | (b2 << 8);

            out[outIdx++] = ALPHABET[(val >>> 18) & 0x3F];
            out[outIdx++] = ALPHABET[(val >>> 12) & 0x3F];

            out[outIdx++] = (i < len) ? ALPHABET[(val >>> 6) & 0x3F] : '=';
            out[outIdx++] = '=';
        }

        return new String(out);
    }

    static byte[] decode(String source) {
        if (source == null) {
            return null;
        }

        StringBuffer clean = new StringBuffer(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c < DECODABET.length && (DECODABET[c] >= 0 || DECODABET[c] == -2)) {
                clean.append(c);
            }
        }

        int len = clean.length();
        if (len == 0) {
            return new byte[0];
        }
        if (len % 4 != 0) {
            throw new IllegalArgumentException("Invalid Base64 input");
        }

        int padding = 0;
        if (clean.charAt(len - 1) == '=') padding++;
        if (clean.charAt(len - 2) == '=') padding++;

        byte[] out = new byte[(len * 3) / 4 - padding];
        int outIndex = 0;

        for (int i = 0; i < len; i += 4) {
            int c1 = DECODABET[clean.charAt(i)];
            int c2 = DECODABET[clean.charAt(i + 1)];
            int c3 = DECODABET[clean.charAt(i + 2)];
            int c4 = DECODABET[clean.charAt(i + 3)];

            int block = (c1 << 18) | (c2 << 12);
            if (c3 >= 0) {
                block |= c3 << 6;
            }
            if (c4 >= 0) {
                block |= c4;
            }

            out[outIndex++] = (byte) ((block >> 16) & 0xFF);
            if (c3 >= 0 && outIndex < out.length) {
                out[outIndex++] = (byte) ((block >> 8) & 0xFF);
            }
            if (c4 >= 0 && outIndex < out.length) {
                out[outIndex++] = (byte) (block & 0xFF);
            }
        }

        return out;
    }

    private Base64() {}
}
