package com.tpkarras.mirror2rearultra;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DeviceHealthPolicyTest {
    @Test
    public void pausesAtTemperatureLimitAndUsesTwoDegreeHysteresis() {
        assertEquals(
                DeviceHealthPolicy.PauseReason.TEMPERATURE,
                evaluate(450, 80, false, DeviceHealthPolicy.PauseReason.NONE)
        );
        assertEquals(
                DeviceHealthPolicy.PauseReason.TEMPERATURE,
                evaluate(431, 80, false, DeviceHealthPolicy.PauseReason.TEMPERATURE)
        );
        assertEquals(
                DeviceHealthPolicy.PauseReason.NONE,
                evaluate(430, 80, false, DeviceHealthPolicy.PauseReason.TEMPERATURE)
        );
    }

    @Test
    public void lowBatteryProtectionIsDisabledWhileCharging() {
        assertEquals(
                DeviceHealthPolicy.PauseReason.LOW_BATTERY,
                evaluate(300, 15, false, DeviceHealthPolicy.PauseReason.NONE)
        );
        assertEquals(
                DeviceHealthPolicy.PauseReason.NONE,
                evaluate(300, 10, true, DeviceHealthPolicy.PauseReason.LOW_BATTERY)
        );
    }

    @Test
    public void lowBatteryRecoveryUsesTwoPercentHysteresis() {
        assertEquals(
                DeviceHealthPolicy.PauseReason.LOW_BATTERY,
                evaluate(300, 17, false, DeviceHealthPolicy.PauseReason.LOW_BATTERY)
        );
        assertEquals(
                DeviceHealthPolicy.PauseReason.NONE,
                evaluate(300, 18, false, DeviceHealthPolicy.PauseReason.LOW_BATTERY)
        );
    }

    @Test
    public void temperatureHasPriorityOverLowBattery() {
        assertEquals(
                DeviceHealthPolicy.PauseReason.TEMPERATURE,
                evaluate(500, 5, false, DeviceHealthPolicy.PauseReason.NONE)
        );
    }

    private static DeviceHealthPolicy.PauseReason evaluate(
            int temperature,
            int battery,
            boolean charging,
            DeviceHealthPolicy.PauseReason previous
    ) {
        return DeviceHealthPolicy.evaluate(
                temperature,
                battery,
                charging,
                true,
                45,
                true,
                15,
                previous
        );
    }
}
