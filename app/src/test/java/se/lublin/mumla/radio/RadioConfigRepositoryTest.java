package se.lublin.mumla.radio;

import static org.junit.Assert.assertThrows;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class RadioConfigRepositoryTest {
    @Test
    public void rejectsOnlyOlderConfigVersions() throws JSONException {
        JSONObject active = new JSONObject("{\"configVersion\":12}");

        assertThrows(JSONException.class, () -> RadioConfigRepository.rejectDowngrade(
                new JSONObject("{\"configVersion\":11}"), active));
        RadioConfigRepository.rejectDowngrade(
                new JSONObject("{\"configVersion\":12}"), active);
        RadioConfigRepository.rejectDowngrade(
                new JSONObject("{\"configVersion\":13}"), active);
    }
}
