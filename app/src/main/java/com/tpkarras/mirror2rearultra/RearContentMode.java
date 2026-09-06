package com.tpkarras.mirror2rearultra;

enum RearContentMode {
    MIRROR,
    DASHBOARD,
    HYBRID;

    boolean usesProjection() {
        return this != DASHBOARD;
    }

    boolean showsDashboard() {
        return this != MIRROR;
    }
}
