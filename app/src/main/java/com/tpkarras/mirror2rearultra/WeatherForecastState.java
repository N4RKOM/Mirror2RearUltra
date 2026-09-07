package com.tpkarras.mirror2rearultra;

final class WeatherForecastState {
    static final class Snapshot {
        final String[] dates;
        final int[] minimums;
        final int[] maximums;
        final int[] codes;

        Snapshot(String[] dates, int[] minimums, int[] maximums, int[] codes) {
            this.dates = dates;
            this.minimums = minimums;
            this.maximums = maximums;
            this.codes = codes;
        }
    }

    private static volatile Snapshot current = new Snapshot(
            new String[0], new int[0], new int[0], new int[0]);

    static Snapshot get() { return current; }

    static void set(String[] dates, int[] minimums, int[] maximums, int[] codes) {
        current = new Snapshot(dates.clone(), minimums.clone(), maximums.clone(), codes.clone());
    }

    private WeatherForecastState() {}
}
