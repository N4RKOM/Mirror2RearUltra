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

    /** Sunrise and sunset for the forecast's days, as instants, {@link SunTimes#NONE} where there is none. */
    private static volatile long[][] sun = {new long[0], new long[0]};

    static long[] sunrises() { return sun[0]; }

    static long[] sunsets() { return sun[1]; }

    static void setSun(long[] sunrises, long[] sunsets) {
        sun = new long[][]{sunrises.clone(), sunsets.clone()};
    }

    private WeatherForecastState() {}
}
