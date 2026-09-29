package com.zntsns.awardtrace.shared;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** RFC 9562 version 7 UUIDs: a 48-bit millisecond timestamp followed by random bits, so IDs sort by time. */
public final class UuidV7 {

    private UuidV7() {
    }

    public static UUID next() {
        var random = ThreadLocalRandom.current();
        long mostSignificant = (System.currentTimeMillis() << 16) | 0x7000L | (random.nextLong() & 0x0FFFL);
        long leastSignificant = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
