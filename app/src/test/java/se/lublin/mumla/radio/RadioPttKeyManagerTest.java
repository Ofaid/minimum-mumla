package se.lublin.mumla.radio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public class RadioPttKeyManagerTest {
    @Test
    public void recognizesMediaKeysForScreenOffSessionPath() {
        assertTrue(RadioPttKeyManager.isMediaStyleKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        assertTrue(RadioPttKeyManager.isMediaStyleKey(KeyEvent.KEYCODE_HEADSETHOOK));
        assertFalse(RadioPttKeyManager.isMediaStyleKey(KeyEvent.KEYCODE_F1));
    }

    @Test
    public void recognizesRadioProfilesForAutomaticDefaults() {
        assertTrue(RadioPttKeyManager.isRadioProfile(RadioDeviceProfile.T99));
        assertTrue(RadioPttKeyManager.isRadioProfile(RadioDeviceProfile.T88));
        assertFalse(RadioPttKeyManager.isRadioProfile(RadioDeviceProfile.GENERIC));
    }

    @Test
    public void t99UsesCapturedF1PttAndRejectsPhysicalExitF2() {
        assertTrue(RadioPttKeyManager.isProfileDefaultPttKey(
                RadioDeviceProfile.T99, KeyEvent.KEYCODE_F1));
        assertFalse(RadioPttKeyManager.isProfileDefaultPttKey(
                RadioDeviceProfile.T99, KeyEvent.KEYCODE_F2));
        assertTrue(RadioPttKeyManager.isProfileDefaultPttKey(
                RadioDeviceProfile.T99, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
    }

    @Test
    public void t88KeepsBothFunctionKeysUntilPhysicalCapture() {
        assertTrue(RadioPttKeyManager.isProfileDefaultPttKey(
                RadioDeviceProfile.T88, KeyEvent.KEYCODE_F1));
        assertTrue(RadioPttKeyManager.isProfileDefaultPttKey(
                RadioDeviceProfile.T88, KeyEvent.KEYCODE_F2));
    }
}
