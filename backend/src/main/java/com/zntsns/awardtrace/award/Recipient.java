package com.zntsns.awardtrace.award;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;

/** A read model; the pipeline projects this table from award transactions (ADR 0012). */
@Entity
@Immutable
public class Recipient {

    @Id
    private String uei;

    private String name;
    private String parentUei;
    private String parentName;
    private String city;
    private String stateCode;
    private String countryCode;

    protected Recipient() {
    }

    public String uei() {
        return uei;
    }

    public String name() {
        return name;
    }

    public String parentUei() {
        return parentUei;
    }

    public String parentName() {
        return parentName;
    }

    public String city() {
        return city;
    }

    public String stateCode() {
        return stateCode;
    }

    public String countryCode() {
        return countryCode;
    }
}
