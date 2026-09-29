package com.zntsns.awardtrace.award;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;

/** A read model; the pipeline writes this table (ADR 0008). */
@Entity
@Immutable
public class Agency {

    @Id
    private String code;

    private String name;

    protected Agency() {
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }
}
