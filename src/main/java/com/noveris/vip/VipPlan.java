package com.noveris.vip;

import java.util.Arrays;
import java.util.Optional;

enum VipPlan {
    VIAJANTE("viajante"), NOBRE("nobre"), REGENTE("regente"), SOBERANO("soberano");
    final String id;
    VipPlan(String id) { this.id = id; }
    static Optional<VipPlan> from(String value) {
        return Arrays.stream(values()).filter(plan -> plan.id.equalsIgnoreCase(value)).findFirst();
    }
    static String[] names() { return Arrays.stream(values()).map(plan -> plan.id).toArray(String[]::new); }
}
