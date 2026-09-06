package com.tpkarras.mirror2rearultra;

final class DeviceHealthPolicy {
    enum PauseReason {
        NONE,
        TEMPERATURE,
        LOW_BATTERY
    }

    private DeviceHealthPolicy() {
    }

    static PauseReason evaluate(
            int temperatureTenthsCelsius,
            int batteryPercent,
            boolean charging,
            boolean temperatureProtectionEnabled,
            int temperatureThresholdCelsius,
            boolean batteryProtectionEnabled,
            int batteryThresholdPercent,
            PauseReason previousReason
    ) {
        if (temperatureProtectionEnabled && temperatureTenthsCelsius >= 0) {
            int threshold = temperatureThresholdCelsius * 10;
            boolean temperatureUnsafe = previousReason == PauseReason.TEMPERATURE
                    ? temperatureTenthsCelsius > threshold - 20
                    : temperatureTenthsCelsius >= threshold;
            if (temperatureUnsafe) {
                return PauseReason.TEMPERATURE;
            }
        }

        if (batteryProtectionEnabled && batteryPercent >= 0 && !charging) {
            boolean batteryUnsafe = previousReason == PauseReason.LOW_BATTERY
                    ? batteryPercent <= batteryThresholdPercent + 2
                    : batteryPercent <= batteryThresholdPercent;
            if (batteryUnsafe) {
                return PauseReason.LOW_BATTERY;
            }
        }
        return PauseReason.NONE;
    }
}
