package com.zntsns.awardtrace.award;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/** A subaward reported under a prime award (ADR 0014), as the award detail page lists it. A read model (ADR 0008). */
@Entity
@Immutable
public class Subaward {

    @Id
    private String subawardKey;

    private String primeAwardId;
    private String subRecipientUei;
    private String subRecipientName;
    private BigDecimal amount;
    private LocalDate actionDate;
    private String description;

    protected Subaward() {
    }

    public String subawardKey() {
        return subawardKey;
    }

    /** Null when the subrecipient isn't registered in SAM.gov. */
    public String subRecipientUei() {
        return subRecipientUei;
    }

    public String subRecipientName() {
        return subRecipientName;
    }

    /** Negative for a correction that lowers an earlier report. */
    public BigDecimal amount() {
        return amount;
    }

    public LocalDate actionDate() {
        return actionDate;
    }

    public String description() {
        return description;
    }
}
