package se.lublin.mumla.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public class RadioKeyActionPolicyTest {
    @Test
    public void t99RequiresFiveSecondHoldForEveryPhysicalExitPath() {
        assertEquals(5_000L, RadioKeyActionPolicy.EXIT_HOLD_MS);
        assertTrue(RadioKeyActionPolicy.isProtectedExitKey(
                RadioDeviceProfile.T99, KeyEvent.KEYCODE_DPAD_CENTER));
        assertTrue(RadioKeyActionPolicy.isProtectedExitKey(
                RadioDeviceProfile.T99, KeyEvent.KEYCODE_F2));
        assertTrue(RadioKeyActionPolicy.isProtectedExitKey(
                RadioDeviceProfile.T99, KeyEvent.KEYCODE_BACK));
        assertFalse(RadioKeyActionPolicy.isProtectedExitKey(
                RadioDeviceProfile.T99, KeyEvent.KEYCODE_MENU));
        assertFalse(RadioKeyActionPolicy.isProtectedExitKey(
                RadioDeviceProfile.T88, KeyEvent.KEYCODE_F2));
    }

    @Test
    public void upAndDownRequireOneSecondAndResolveDirection() {
        assertEquals(1_000L, RadioKeyActionPolicy.ROOM_CHANGE_HOLD_MS);
        assertTrue(RadioKeyActionPolicy.isRoomChangeKey(KeyEvent.KEYCODE_DPAD_UP));
        assertTrue(RadioKeyActionPolicy.isRoomChangeKey(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(-1, RadioKeyActionPolicy.roomDirection(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(1, RadioKeyActionPolicy.roomDirection(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(0, RadioKeyActionPolicy.roomDirection(KeyEvent.KEYCODE_MENU));
    }

    @Test
    public void releaseTimestampCompletesHoldEvenWhenHandlerWasDelayed() {
        assertTrue(RadioKeyActionPolicy.heldLongEnough(10_000L, 15_060L, 5_000L));
        assertFalse(RadioKeyActionPolicy.heldLongEnough(10_000L, 14_999L, 5_000L));
        assertFalse(RadioKeyActionPolicy.heldLongEnough(-1L, 15_060L, 5_000L));
    }
}
