package com.zntsns.awardtrace.award;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/** One modification of an award, as the award detail page lists it. A read model (ADR 0008). */
@Entity
@Immutable
public class AwardTransaction {

    @Id
    private String transactionId;

    private String awardId;
    private String modificationNumber;
    private LocalDate actionDate;
    private BigDecimal federalActionObligation;
    private String description;
    private Instant deletedAt;

    protected AwardTransaction() {
    }

    public String transactionId() {
        return transactionId;
    }

    public String awardId() {
        return awardId;
    }

    public String modificationNumber() {
        return modificationNumber;
    }

    public LocalDate actionDate() {
        return actionDate;
    }

    public BigDecimal federalActionObligation() {
        return federalActionObligation;
    }

    public String description() {
        return description;
    }
}
