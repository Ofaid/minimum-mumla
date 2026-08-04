package se.lublin.mumla.radio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import se.lublin.humla.util.ConnectionRetryPolicy;
import se.lublin.humla.util.HumlaException;

public class ConnectionRetryPolicyTest {
    @Test
    public void managedRadioRetriesEveryDisconnectError() {
        for (HumlaException.HumlaDisconnectReason reason
                : HumlaException.HumlaDisconnectReason.values()) {
            assertTrue(ConnectionRetryPolicy.shouldRetry(true, true, reason));
        }
    }

    @Test
    public void standardModeRetainsTransportErrorOnlyPolicy() {
        assertTrue(ConnectionRetryPolicy.shouldRetry(true, false,
                HumlaException.HumlaDisconnectReason.CONNECTION_ERROR));
        assertFalse(ConnectionRetryPolicy.shouldRetry(true, false,
                HumlaException.HumlaDisconnectReason.REJECT));
        assertFalse(ConnectionRetryPolicy.shouldRetry(false, true,
                HumlaException.HumlaDisconnectReason.CONNECTION_ERROR));
    }

    @Test
    public void retryDelayBacksOffAndCaps() {
        assertTrue(ConnectionRetryPolicy.retryDelayMs(2000L, 0, 60000L) == 2000L);
        assertTrue(ConnectionRetryPolicy.retryDelayMs(2000L, 1, 60000L) == 4000L);
        assertTrue(ConnectionRetryPolicy.retryDelayMs(2000L, 4, 60000L) == 32000L);
        assertTrue(ConnectionRetryPolicy.retryDelayMs(2000L, 10, 60000L) == 60000L);
    }
}
