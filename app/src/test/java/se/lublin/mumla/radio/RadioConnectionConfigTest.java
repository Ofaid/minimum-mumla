package se.lublin.mumla.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class RadioConnectionConfigTest {
    @Test
    public void parsesConnectionRoomsAndPublicTokens() throws JSONException {
        RadioConnectionConfig config = RadioConnectionConfig.fromJson(new JSONObject(validConfig()));

        assertEquals(7, config.getConfigVersion());
        assertEquals("Minimum Test", config.getServiceName());
        assertEquals("voice.example.org", config.getHost());
        assertEquals(64738, config.getPort());
        assertEquals("E25FGL-T99", config.getUsername());
        assertEquals("AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
                config.getServerCertificateSha256());
        assertTrue(config.isAutoTrustServerCertificate());
        assertTrue(config.acceptsServerCertificate(
                "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899"));
        assertFalse(config.acceptsServerCertificate(
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"));
        assertTrue(config.isAutoConnect());
        assertFalse(config.isAutoReconnect());
        assertEquals(Arrays.asList("PUBLIC-A", "PUBLIC-B"), config.getAccessTokens());
        assertEquals(2, config.getRooms().size());
        assertEquals("main", config.getDefaultRoom().getId());
        assertEquals("/PUBLIC/MAIN", config.getDefaultRoom().getPath());
    }

    @Test
    public void rejectsUnsafeHostAndMissingDefaultRoom() throws JSONException {
        JSONObject unsafeHost = new JSONObject(validConfig());
        unsafeHost.getJSONObject("mumble").put("host", "https://voice.example.org/path");
        assertThrows(JSONException.class, () -> RadioConnectionConfig.fromJson(unsafeHost));

        JSONObject missingDefault = new JSONObject(validConfig());
        missingDefault.getJSONObject("mumble").put("defaultRoom", "missing");
        assertThrows(JSONException.class, () -> RadioConnectionConfig.fromJson(missingDefault));

        JSONObject missingUsername = new JSONObject(validConfig());
        missingUsername.getJSONObject("mumble").remove("username");
        assertThrows(JSONException.class, () -> RadioConnectionConfig.fromJson(missingUsername));

        JSONObject controlCharacterUsername = new JSONObject(validConfig());
        controlCharacterUsername.getJSONObject("mumble").put("username", "BAD\nNAME");
        assertThrows(JSONException.class,
                () -> RadioConnectionConfig.fromJson(controlCharacterUsername));

        JSONObject nonStringUsername = new JSONObject(validConfig());
        nonStringUsername.getJSONObject("mumble").put("username", 25);
        assertThrows(JSONException.class, () -> RadioConnectionConfig.fromJson(nonStringUsername));

        JSONObject legacySchema = new JSONObject(validConfig());
        legacySchema.put("schemaVersion", 1);
        assertThrows(JSONException.class, () -> RadioConnectionConfig.fromJson(legacySchema));
    }

    @Test
    public void normalizesOnlySafeAbsoluteRoomPaths() throws JSONException {
        assertEquals("/PUBLIC/MAIN", RadioConnectionConfig.normalizePath(" /PUBLIC/MAIN/ "));
        assertThrows(JSONException.class, () -> RadioConnectionConfig.normalizePath("PUBLIC/MAIN"));
        assertThrows(JSONException.class, () -> RadioConnectionConfig.normalizePath("/PUBLIC//MAIN"));
        assertEquals("AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899",
                RadioConnectionConfig.normalizeFingerprint(
                        "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:"
                                + "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"));
        assertThrows(JSONException.class,
                () -> RadioConnectionConfig.normalizeFingerprint("not-a-fingerprint"));
    }

    @Test
    public void automaticCertificateTrustDefaultsOnAndCanBeDisabled() throws JSONException {
        JSONObject automatic = new JSONObject(validConfig());
        automatic.getJSONObject("mumble").remove("serverCertificateSha256");
        RadioConnectionConfig automaticConfig = RadioConnectionConfig.fromJson(automatic);
        assertTrue(automaticConfig.isAutoTrustServerCertificate());
        assertTrue(automaticConfig.acceptsServerCertificate(
                "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"));

        automatic.getJSONObject("mumble").put("autoTrustServerCertificate", false);
        RadioConnectionConfig disabledConfig = RadioConnectionConfig.fromJson(automatic);
        assertFalse(disabledConfig.isAutoTrustServerCertificate());
        assertFalse(disabledConfig.acceptsServerCertificate(
                "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"));

        automatic.getJSONObject("mumble").put("autoTrustServerCertificate", "yes");
        assertThrows(JSONException.class, () -> RadioConnectionConfig.fromJson(automatic));
    }

    private static String validConfig() {
        return "{"
                + "\"schemaVersion\":2,\"configVersion\":7,\"deviceId\":\"*\","
                + "\"service\":{\"name\":\"Minimum Test\"},"
                + "\"mumble\":{\"serverId\":\"test\",\"host\":\"voice.example.org\","
                + "\"port\":64738,\"username\":\"E25FGL-T99\",\"defaultRoom\":\"main\","
                + "\"serverCertificateSha256\":"
                + "\"AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:"
                + "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99\","
                + "\"autoConnect\":true,"
                + "\"autoReconnect\":false},"
                + "\"ui\":{\"profile\":\"small-radio\"},"
                + "\"ptt\":{\"maximumTxSeconds\":120,\"releaseOnNetworkLoss\":true},"
                + "\"rooms\":["
                + "{\"id\":\"main\",\"label\":\"Main\",\"path\":\"/PUBLIC/MAIN\","
                + "\"presetKey\":\"P1\",\"access\":{\"mode\":\"public\","
                + "\"token\":\" PUBLIC-A \"}},"
                + "{\"id\":\"other\",\"label\":\"Other\",\"path\":\"/PUBLIC/OTHER\","
                + "\"presetKey\":\"P2\",\"access\":{\"mode\":\"public\","
                + "\"token\":\"PUBLIC-B\"}}],"
                + "\"hardware\":{\"profile\":\"generic-radio\"}"
                + "}";
    }
}
