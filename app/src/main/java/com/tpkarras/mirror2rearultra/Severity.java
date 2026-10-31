package com.tpkarras.mirror2rearultra;

/**
 * How much a reading deserves a glance: nothing to see, worth noticing, or
 * something to act on. One scale for every widget that has a limit, so the
 * panel warns in the same two colours whether it is the sun, the air, the
 * memory or the phone's own heat.
 */
enum Severity {
    NORMAL,
    NOTICE,
    ALERT;

    /** UV by the WHO band: high and very high are worth noticing, extreme is not to be missed. */
    static Severity forUvBand(int band) {
        return band >= 4 ? ALERT : band >= 2 ? NOTICE : NORMAL;
    }

    /** The European air quality band: moderate is noticed, poor or worse is an alert. */
    static Severity forAirBand(int band) {
        return band >= 3 ? ALERT : band == 2 ? NOTICE : NORMAL;
    }

    /** Memory in use: a phone near its limit starts closing apps. */
    static Severity forUsedPercent(int percent) {
        return percent >= 93 ? ALERT : percent >= 85 ? NOTICE : NORMAL;
    }

    /** Storage still free: the other way round, with the same ends. */
    static Severity forFreePercent(int percent) {
        return percent <= 7 ? ALERT : percent <= 15 ? NOTICE : NORMAL;
    }

    /** The phone's own temperature, in degrees Celsius whatever the panel shows. */
    static Severity forPhoneCelsius(float celsius) {
        return celsius >= 45f ? ALERT : celsius >= 40f ? NOTICE : NORMAL;
    }
}
