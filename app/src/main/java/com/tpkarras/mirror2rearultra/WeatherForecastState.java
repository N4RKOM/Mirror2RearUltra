package com.tpkarras.mirror2rearultra;

final class WeatherForecastState {
    static final class Snapshot {
        final String[] dates;
        final int[] minimums;
        final int[] maximums;
        final int[] codes;
        /** The chance of rain, as a percentage, for each of the days above. */
        final int[] rainChances;

        Snapshot(String[] dates, int[] minimums, int[] maximums, int[] codes,
                int[] rainChances) {
            this.dates = dates;
            this.minimums = minimums;
            this.maximums = maximums;
            this.codes = codes;
            this.rainChances = rainChances;
        }
    }

    private static volatile Snapshot current = new Snapshot(
            new String[0], new int[0], new int[0], new int[0], new int[0]);

    static Snapshot get() { return current; }

    static void set(String[] dates, int[] minimums, int[] maximums, int[] codes,
            int[] rainChances) {
        current = new Snapshot(dates.clone(), minimums.clone(), maximums.clone(),
                codes.clone(), rainChances.clone());
    }

    private WeatherForecastState() {}
}
