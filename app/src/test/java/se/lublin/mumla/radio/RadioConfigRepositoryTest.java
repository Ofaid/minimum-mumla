package se.lublin.mumla.radio;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RadioConfigRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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

    @Test
    public void promotesPendingAndCanExplicitlyRollback() throws IOException {
        File directory = temporaryFolder.newFolder("radio-config");
        File active = write(directory, "active-config.json", "active-v1");
        File previous = write(directory, "previous-config.json", "obsolete");
        File pending = write(directory, "pending-config.json", "candidate-v2");

        RadioConfigRepository.promotePendingFiles(directory);

        assertEquals("candidate-v2", read(active));
        assertEquals("active-v1", read(previous));
        assertFalse(pending.exists());
        assertFalse(new File(directory, "previous-config.backup").exists());

        RadioConfigRepository.rollbackFiles(directory);

        assertEquals("active-v1", read(active));
        assertFalse(previous.exists());
        assertFalse(new File(directory, "rollback-config.tmp").exists());
    }

    @Test
    public void promotionWithoutPendingCannotDamageActive() throws IOException {
        File directory = temporaryFolder.newFolder("missing-pending");
        File active = write(directory, "active-config.json", "stable");

        assertThrows(IOException.class,
                () -> RadioConfigRepository.promotePendingFiles(directory));
        assertTrue(active.isFile());
        assertEquals("stable", read(active));
    }

    private static File write(File directory, String name, String value) throws IOException {
        File file = new File(directory, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static String read(File file) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
