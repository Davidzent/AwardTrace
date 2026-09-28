package com.zntsns.awardtrace;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleBoundaryTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(AwardTraceApplication.class).verify();
    }
}
