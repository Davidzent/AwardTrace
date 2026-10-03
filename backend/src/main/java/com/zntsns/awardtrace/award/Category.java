package com.zntsns.awardtrace.award;

/**
 * One of the thirteen categories an award can carry (doc 09).
 *
 * @param definition the exact text the classifier prompt uses
 */
public record Category(String code, String label, String definition) {
}
