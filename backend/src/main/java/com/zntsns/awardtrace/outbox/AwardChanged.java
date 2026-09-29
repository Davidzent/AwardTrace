package com.zntsns.awardtrace.outbox;

/**
 * An award changed in a way search must see. It carries no award data on purpose: consumers read the current row,
 * so an event can never deliver stale fields, and several events for one award collapse into one index write.
 *
 * @param changeReason {@code TRANSACTION}, {@code CLASSIFICATION}, or {@code REINDEX}
 */
public record AwardChanged(String awardId, long indexVersion, String changeReason) {
}
