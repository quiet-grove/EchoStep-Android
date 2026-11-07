package com.echostep.app.net;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates the Sec-MS-GEC v2 token required by Microsoft Edge's Read Aloud
 * WebSocket endpoint. This is a Java port of the token derivation used by
 * edge-tts 7.3.x (the CS64 + SHA-256 "v2" token).
 */
public final class EdgeToken {
    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final long MASK = 0xFFFFFFFFL;

    private static final long[] SHA256_INITIAL = {
            0x6A09E667L, 0xBB67AE85L, 0x3C6EF372L, 0xA54FF53AL,
            0x510E527FL, 0x9B05688CL, 0x1F83D9ABL, 0x5BE0CD19L
    };
    private static final long[] SHA256_STATE = {
            0xFD9FBEA2L, 0x91243CE7L, 0xBE499094L, 0xCD2E1E0AL,
            0x6C006F49L, 0x04BD35ABL, 0xFEB0A7E4L, 0x03EFFBD2L
    };
    private static final long[] SHA256_SCHEDULE = {
            0xEB24BF9EL, 0x1558691FL, 0xA0C09EE2L, 0x0CFA32BAL,
            0xA622EE8CL, 0xD1DD9491L, 0xC0DD25A0L, 0xF9E70AB6L,
            0xF0C31573L, 0x7058CE4EL, 0x7188E606L, 0x00E5AB33L,
            0xD258B060L, 0x72766564L
    };
    private static final long[] SHA256_ROUND = {
            0x428A2F98L, 0x71374491L, 0xB5C0FBCFL, 0xE9B5DBA5L,
            0x3956C25BL, 0x59F111F1L, 0x923F82A4L, 0xAB1C5ED5L,
            0xD807AA98L, 0x12835B01L, 0x243185BEL, 0x550C7DC3L,
            0x72BE5D74L, 0x80DEB1FEL, 0x9BDC06A7L, 0xC19BF174L,
            0xE49B69C1L, 0xEFBE4786L, 0x0FC19DC6L, 0x240CA1CCL,
            0x2DE92C6FL, 0x4A7484AAL, 0x5CB0A9DCL, 0x76F988DAL,
            0x983E5152L, 0xA831C66DL, 0xB00327C8L, 0xBF597FC7L,
            0xC6E00BF3L, 0xD5A79147L, 0x06CA6351L, 0x14292967L,
            0x27B70A85L, 0x2E1B2138L, 0x4D2C6DFCL, 0x53380D13L,
            0x650A7354L, 0x766A0ABBL, 0x81C2C92EL, 0x92722C85L,
            0xA2BFE8A1L, 0xA81A664BL, 0xC24B8B70L, 0xC76C51A3L,
            0xD192E819L, 0xD6990624L, 0xF40E3585L, 0x106AA070L,
            0x19A4C116L, 0x1E376C08L, 0x2748774CL, 0x34B0BCB5L,
            0x391C0CB3L, 0x4ED8AA4AL, 0x5B9CCA4FL, 0x682E6FF3L,
            0x748F82EEL, 0x78A5636FL, 0x84C87814L, 0x8CC70208L,
            0x90BEFFFAL, 0xA4506CEBL, 0xBEF9A3F7L, 0xC67178F2L
    };
    private static final long SHA256_ADJUSTMENT = 0xC9DC06A7L;
    private static final long SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80L;
    private static final long SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0L;
    private static final long[] SHA256_BLOCK = new long[64];
    static {
        System.arraycopy(SHA256_ROUND, 0, SHA256_BLOCK, 0, 64);
        SHA256_BLOCK[14] = SHA256_ADJUSTMENT;
    }

    private static final long[][] CS64_COEFFICIENTS = {
            {0xE82E91B1L, 0xB32700D0L},
            {0x44DE24C6L, 0x88DFF51DL},
            {0x3BEB65BCL, 0x29514038L},
            {0xC89C5933L, 0xB328CBDFL},
            {0x8D1EC211L, 0xD405B4D5L},
            {0x314EF5D5L, 0x26E55F3CL},
            {0xE92491B1L, 0x607801C6L},
            {0xF0A5B9D5L, 0x82B2C1FBL},
            {0x06064CF3L, 0xFA51A3DAL},
            {0xDA79FA60L, 0x207CC464L},
            {0xBED20560L, 0x3C3C74F3L},
            {0x89F9C9F8L, 0xD2D14B61L},
            {0x35FA38C6L, 0xBB04A2C1L},
            {0xCB49E3C6L, 0xC5626167L}
    };
    private static final long CS64_FACTOR = 0x82893933L;
    private static final long CS64_ADJUSTMENT0 = 0xF2F90B1AL;
    private static final long CS64_ADJUSTMENT1 = 0x0B1A0000L;

    private EdgeToken() {
    }

    public static String generate() {
        long epochSeconds = System.currentTimeMillis() / 1000L;
        return generate(epochSeconds);
    }

    public static String generate(long unixSeconds) {
        long ticks = unixSeconds + 11_644_473_600L;
        ticks -= ticks % 300L;
        ticks *= 10_000_000L;
        byte[] data = (ticks + TRUSTED_CLIENT_TOKEN).getBytes(StandardCharsets.US_ASCII);
        byte[] digest = sha256Digest(data);
        long[] cs = cs64Digest(data, digest);
        byte[] csBytes = new byte[8];
        putLeInt(csBytes, 0, cs[0]);
        putLeInt(csBytes, 4, cs[1]);
        return toHex(csBytes) + "-" + toHex(digest);
    }

    private static long[] sha256Init(byte[] data) {
        if (data.length < 7) {
            throw new IllegalArgumentException("salt must contain at least 7 bytes");
        }
        long[] words = new long[64];
        words[14] = ((long) (data[0] & 0xFF) << 16)
                | ((long) (data[1] & 0xFF) << 8)
                | (data[2] & 0xFFL);
        words[15] = ((long) (data[3] & 0xFF) << 24)
                | ((long) (data[4] & 0xFF) << 16)
                | ((long) (data[5] & 0xFF) << 8)
                | (data[6] & 0xFFL);
        for (int index = 16; index <= 29; index++) {
            long value = SHA256_SCHEDULE[index - 16];
            if (index - 2 >= 14) {
                long sigma = sigma1(words[index - 2]);
                if (index == 16) {
                    sigma ^= SHA256_SIGMA1_ADJUSTMENT;
                }
                value += sigma;
            }
            if (index - 7 >= 14) {
                value += words[index - 7];
            }
            if (index - 15 >= 14) {
                long sigma = sigma0(words[index - 15]);
                if (index == 29) {
                    sigma ^= SHA256_SIGMA0_ADJUSTMENT;
                }
                value += sigma;
            }
            words[index] = value & MASK;
        }
        words[30] = (sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14]
                + SHA256_ADJUSTMENT - SHA256_ROUND[14]) & MASK;
        expandSchedule(words, 31);
        long[] working = sha256Rounds(Arrays.copyOf(SHA256_STATE, 8), words, 14);
        long[] result = new long[8];
        for (int i = 0; i < 8; i++) {
            result[i] = (SHA256_INITIAL[i] + working[i]) & MASK;
        }
        return result;
    }

    private static byte[] sha256Digest(byte[] data) {
        if (data.length < 7) {
            throw new IllegalArgumentException("salt must contain at least 7 bytes");
        }
        long length = 57L + data.length;
        ByteArrayOutputStream tail = new ByteArrayOutputStream();
        tail.write(data, 7, data.length - 7);
        tail.write(0x80);
        int zeros = (int) Math.floorMod(55L - length, 64L);
        for (int i = 0; i < zeros; i++) {
            tail.write(0);
        }
        long bitLength = length * 8L;
        for (int i = 7; i >= 0; i--) {
            tail.write((int) ((bitLength >>> (i * 8)) & 0xFF));
        }
        byte[] padded = tail.toByteArray();
        long[] state = sha256Init(Arrays.copyOf(data, 7));
        for (int offset = 0; offset < padded.length; offset += 64) {
            long[] words = new long[64];
            for (int i = 0; i < 16; i++) {
                int p = offset + i * 4;
                words[i] = ((long) (padded[p] & 0xFF) << 24)
                        | ((long) (padded[p + 1] & 0xFF) << 16)
                        | ((long) (padded[p + 2] & 0xFF) << 8)
                        | (padded[p + 3] & 0xFFL);
            }
            expandSchedule(words, 16);
            long[] working = sha256Rounds(state, words, 0);
            for (int i = 0; i < 8; i++) {
                state[i] = (state[i] + working[i]) & MASK;
            }
        }
        byte[] result = new byte[32];
        for (int i = 0; i < 8; i++) {
            result[i * 4] = (byte) ((state[i] >>> 24) & 0xFF);
            result[i * 4 + 1] = (byte) ((state[i] >>> 16) & 0xFF);
            result[i * 4 + 2] = (byte) ((state[i] >>> 8) & 0xFF);
            result[i * 4 + 3] = (byte) (state[i] & 0xFF);
        }
        return result;
    }

    private static long[] sha256Rounds(long[] state, long[] words, int firstRound) {
        long a = state[0], b = state[1], c = state[2], d = state[3];
        long e = state[4], f = state[5], g = state[6], h = state[7];
        long[] constants = firstRound == 14 ? SHA256_BLOCK : SHA256_ROUND;
        for (int i = firstRound; i < 64; i++) {
            long s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
            long ch = (e & f) ^ ((~e) & g);
            long temp1 = (h + s1 + ch + constants[i] + words[i]) & MASK;
            long s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
            long maj = (a & b) | (c & (a | b));
            long temp2 = (s0 + maj) & MASK;
            h = g;
            g = f;
            f = e;
            e = (d + temp1) & MASK;
            d = c;
            c = b;
            b = a;
            a = (temp1 + temp2) & MASK;
        }
        return new long[]{a, b, c, d, e, f, g, h};
    }

    private static void expandSchedule(long[] words, int firstWord) {
        for (int index = firstWord; index < 64; index++) {
            long x = words[index - 2];
            long y = words[index - 15];
            long sigma1 = (x >>> 17) ^ (x << 15) ^ (x >>> 19) ^ (x << 13) ^ (x >>> 10);
            long sigma0 = (y >>> 7) ^ (y << 25) ^ (y >>> 18) ^ (y << 14) ^ (y >>> 3);
            words[index] = (sigma1 + words[index - 7] + sigma0 + words[index - 16]) & MASK;
        }
    }

    private static long[] cs64Digest(byte[] data, byte[] digest) {
        long keyA = ((long) (digest[0] & 0xFF))
                | ((long) (digest[1] & 0xFF) << 8)
                | ((long) (digest[2] & 0xFF) << 16)
                | ((long) (digest[3] & 0xFF) << 24);
        long keyB = ((long) (digest[4] & 0xFF))
                | ((long) (digest[5] & 0xFF) << 8)
                | ((long) (digest[6] & 0xFF) << 16)
                | ((long) (digest[7] & 0xFF) << 24);
        keyA |= 1;
        keyB |= 1;
        long[] factors = {(keyA * CS64_FACTOR) & MASK, (keyB * CS64_FACTOR) & MASK};
        List<Long> terms = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();
        for (int i = 0; i < CS64_COEFFICIENTS.length; i++) {
            terms.add((CS64_COEFFICIENTS[i][0] * factors[i % 2]) & MASK);
            offsets.add(CS64_COEFFICIENTS[i][1]);
        }
        byte[] padded = new byte[((1 + data.length + 3) / 4) * 4];
        System.arraycopy(data, 0, padded, 1, data.length);
        for (int p = 0; p < padded.length; p += 4) {
            long word = ((long) (padded[p] & 0xFF))
                    | ((long) (padded[p + 1] & 0xFF) << 8)
                    | ((long) (padded[p + 2] & 0xFF) << 16)
                    | ((long) (padded[p + 3] & 0xFF) << 24);
            int index = terms.size() - CS64_COEFFICIENTS.length;
            long term = word * ((index % 2 == 0) ? keyA : keyB);
            long offset = rol16(word) * ((index % 2 == 0) ? 0xEF0569FBL : 0xC31713DBL);
            if (index == 0) {
                term += CS64_ADJUSTMENT0 * factors[0];
                offset += CS64_ADJUSTMENT1;
            }
            terms.add(term & MASK);
            offsets.add(offset & MASK);
        }
        if (terms.size() % 2 != 0) {
            terms.add(0L);
            offsets.add(0L);
        }
        long swapAcc = 0, swapLast = 0, state = 0, acc = 0;
        for (int i = 0; i < terms.size(); i += 2) {
            long x = terms.get(i);
            long y = terms.get(i + 1);
            long t = rol16((offsets.get(i) + x) & MASK);
            long q = (t * 0x76F50F34L) & MASK;
            swapAcc = (swapAcc + ((rol16(q) * 0xC3EFEA97L + q) & MASK)) & MASK;
            t = rol16((offsets.get(i + 1) + y) & MASK);
            q = (t * 0x1881CE3CL) & MASK;
            swapLast = (rol16(q) * 0x35BD1EC9L + q) & MASK;
            swapAcc = (swapAcc + swapLast) & MASK;

            state = (state * keyA + x) & MASK;
            state = (rol16(state) * 0xCF98B111L) & MASK;
            state = (rol16(state) * 0x87085B9FL) & MASK;
            state = (rol16(state) * 0x12CEB96DL) & MASK;
            state = (rol16(state) * 0x257E1D83L) & MASK;
            acc = (acc + state) & MASK;

            t = (state * keyB + y) & MASK;
            t = (rol16(t) * 0xA27416F5L) & MASK;
            t = (rol16(t) * 0xD38396FFL) & MASK;
            state = (rol16((rol16(t) * 0x7C932B89L) & MASK) * 0xBFA49F69L) & MASK;
            acc = (acc + state) & MASK;
        }
        return new long[]{(swapLast ^ state) & MASK, (swapAcc ^ acc) & MASK};
    }

    private static long sigma0(long value) {
        value &= MASK;
        return ((value >>> 7) ^ (value << 25) ^ (value >>> 18) ^ (value << 14) ^ (value >>> 3)) & MASK;
    }

    private static long sigma1(long value) {
        value &= MASK;
        return ((value >>> 17) ^ (value << 15) ^ (value >>> 19) ^ (value << 13) ^ (value >>> 10)) & MASK;
    }

    private static long rotr(long value, int bits) {
        value &= MASK;
        return ((value >>> bits) | (value << (32 - bits))) & MASK;
    }

    private static long rol16(long value) {
        value &= MASK;
        return ((value << 16) | (value >>> 16)) & MASK;
    }

    private static void putLeInt(byte[] out, int offset, long value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        out[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        out[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    private static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) {
            builder.append(String.format("%02X", b & 0xFF));
        }
        return builder.toString();
    }
}
