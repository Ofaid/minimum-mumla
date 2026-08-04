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
}
