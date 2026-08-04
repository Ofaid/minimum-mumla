package se.lublin.mumla.radio;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AccessTokenResolverTest {
    @Test
    public void trimsTokensAndIgnoresMissingNullAndBlankValues() throws JSONException {
        JSONObject config = configWithRooms(
                roomWithAccess(publicAccess("  Alpha-token  ")),
                roomWithAccess("{\"mode\":\"public\"}"),
                roomWithAccess(publicAccessLiteral("public", "null")),
                roomWithAccess(publicAccess("\\t \\n")));

        assertEquals(Collections.singletonList("Alpha-token"),
                AccessTokenResolver.resolve(config));
        assertEquals(Collections.emptyList(), AccessTokenResolver.resolve(new JSONObject("{}")));
        assertEquals(Collections.emptyList(), AccessTokenResolver.resolve(null));
    }

    @Test
    public void preservesCaseAndRemovesDuplicatesInFirstSeenOrder() throws JSONException {
        JSONObject config = configWithRooms(
                roomWithAccess(publicAccess("  AbC-token  ")),
                roomWithAccess(publicAccess("abc-token")),
                roomWithAccess(publicAccess("AbC-token")),
                roomWithAccess(publicAccess("  Beta-token  ")),
                roomWithAccess(publicAccess("beta-token")));

        assertEquals(Arrays.asList("AbC-token", "abc-token", "Beta-token", "beta-token"),
                AccessTokenResolver.resolve(config));
    }

    @Test
    public void excludesProtectedAndNoneAccessModes() throws JSONException {
        String protectedAccess = "{\"mode\":\"protected\","
                + "\"tokenRef\":\"device-local-reference\","
                + "\"token\":\"protected-token\"}";
        String noneAccess = "{\"mode\":\"none\",\"token\":\"not-public-token\"}";
        String publicAccess = publicAccess(" public-token ");

        JSONObject config = configWithRooms(
                roomWithAccess(protectedAccess),
                roomWithAccess(noneAccess),
                roomWithAccess(publicAccess),
                roomWithAccess("{\"mode\":\"public\","
                        + "\"tokenRef\":\"must-not-be-resolved\"}"));

        assertEquals(Collections.singletonList("public-token"),
                AccessTokenResolver.resolve(config));
    }

    @Test
    public void ignoresMalformedRoomAndAccessEntriesSafely() throws JSONException {
        JSONObject config = configWithRooms(
                "null",
                "\"not-a-room\"",
                "7",
                "{}",
                roomWithAccess("\"not-an-access-object\""),
                roomWithAccess("[]"),
                roomWithAccess("{\"mode\":\"public\",\"token\":7}"),
                roomWithAccess("{\"mode\":7,\"token\":\"wrong-mode-token\"}"),
                roomWithAccess("{\"mode\":\"public\",\"token\":null}"),
                roomWithAccess(publicAccess(" valid-token ")));

        assertEquals(Collections.singletonList("valid-token"),
                AccessTokenResolver.resolve(config));
    }

    private static JSONObject configWithRooms(String... rooms) throws JSONException {
        StringBuilder json = new StringBuilder("{\"rooms\":[");
        for (int index = 0; index < rooms.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(rooms[index]);
        }
        return new JSONObject(json.append("]}").toString());
    }

    private static String roomWithAccess(String access) {
        return "{\"access\":" + access + "}";
    }

    private static String publicAccess(String token) {
        return publicAccessLiteral("public", "\"" + token + "\"");
    }

    private static String publicAccessLiteral(String mode, String token) {
        return "{\"mode\":\"" + mode + "\",\"token\":" + token + "}";
    }
}
