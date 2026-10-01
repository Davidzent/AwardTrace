package com.zntsns.awardtrace.search.internal;

import com.zntsns.awardtrace.award.RecipientNetwork;
import com.zntsns.awardtrace.award.RecipientNetwork.Partner;
import java.util.List;

/**
 * The body of {@code GET /api/v1/recipients/{uei}/network} (doc 07).
 *
 * @param dataNote always says the network covers reported subawards only, so no one reads it as every relationship
 */
record RecipientNetworkDetail(List<Partner> primesAbove, List<Partner> subsBelow, String dataNote) {

    static RecipientNetworkDetail of(RecipientNetwork network) {
        return new RecipientNetworkDetail(network.primesAbove(), network.subsBelow(), "Reported subawards only");
    }
}
