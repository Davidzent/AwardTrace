package com.zntsns.awardtrace.shared;

/** Elasticsearch names shared by the module that writes an index and the modules that query it. */
public final class SearchIndexes {

    /**
     * Every query and live write goes through this alias, which points at one versioned index such as
     * {@code awards-v1}, so a rebuild can swap indexes without downtime.
     */
    public static final String AWARDS = "awards";

    private SearchIndexes() {
    }
}
