package se.lublin.mumla.radio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceIdentityManagerTest {
    @Test
    public void acceptsOnlySixUppercaseAlphanumericCharactersWithLetterAndDigit() {
        assertTrue(DeviceIdentityManager.isValidDeviceId("A7K3Q9"));
        assertTrue(DeviceIdentityManager.isValidDeviceId("4FX8LM"));
        assertFalse(DeviceIdentityManager.isValidDeviceId("AAAAAA"));
        assertFalse(DeviceIdentityManager.isValidDeviceId("123456"));
        assertFalse(DeviceIdentityManager.isValidDeviceId("a7K3Q9"));
        assertFalse(DeviceIdentityManager.isValidDeviceId("A7K3Q"));
        assertFalse(DeviceIdentityManager.isValidDeviceId("A7K3Q-"));
    }
}
