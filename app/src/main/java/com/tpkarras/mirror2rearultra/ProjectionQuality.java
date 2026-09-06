package com.tpkarras.mirror2rearultra;

enum ProjectionQuality {
    SHARP,
    ECONOMY;

    int bufferDimension(int viewDimension, int zoomPercent) {
        long scaled = (long) Math.max(1, viewDimension) * bufferPercent(zoomPercent);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(scaled / 100f));
    }

    int bufferPercent(int zoomPercent) {
        if (this == ECONOMY || zoomPercent <= 100) {
            return 100;
        }
        return zoomPercent <= 150 ? 150 : 200;
    }
}
