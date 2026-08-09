package se.lublin.mumla.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DeviceConfigCredentialStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rotatesCredentialAndBuildsBearerHeader() throws Exception {
        File directory = temporaryFolder.newFolder("credential");
        DeviceConfigCredentialStore store = DeviceConfigCredentialStore.forDirectory(directory);

        assertNull(store.getCredential());
        assertNull(store.getAuthorizationHeader());

        store.setCredential("first-token");
        assertEquals("first-token", store.getCredential());
        assertEquals("Bearer first-token", store.getAuthorizationHeader());

        store.setCredential("Bearer second-token");
        assertEquals("second-token", store.getCredential());
        assertEquals("Bearer second-token", store.getAuthorizationHeader());
    }

    @Test
    public void rejectsHeaderInjectionAndOversizedCredentialsWithoutWriting() throws Exception {
        File directory = temporaryFolder.newFolder("invalid-credential");
        DeviceConfigCredentialStore store = DeviceConfigCredentialStore.forDirectory(directory);

        assertThrows(IllegalArgumentException.class, () -> store.setCredential("bad\r\nvalue"));
        assertThrows(IllegalArgumentException.class, () -> store.setCredential("bad value"));
        String oversized = new String(new char[DeviceConfigCredentialStore.MAX_CREDENTIAL_BYTES + 1])
                .replace('\0', 'x');
        assertThrows(IllegalArgumentException.class, () -> store.setCredential(oversized));
        assertNull(store.getCredential());
    }

    @Test
    public void clearRemovesCredentialAndTemporaryFiles() throws Exception {
        File directory = temporaryFolder.newFolder("clear-credential");
        DeviceConfigCredentialStore store = DeviceConfigCredentialStore.forDirectory(directory);

        store.setCredential("token");
        store.clearCredential();

        assertNull(store.getCredential());
        assertEquals(0, directory.listFiles().length);
    }

    @Test
    public void installsCredentialFromBoundedStream() throws Exception {
        File directory = temporaryFolder.newFolder("stream-credential");
        DeviceConfigCredentialStore store = DeviceConfigCredentialStore.forDirectory(directory);

        store.setCredential(new ByteArrayInputStream("stream-token".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII)));

        assertEquals("Bearer stream-token", store.getAuthorizationHeader());
    }

    @Test
    public void malformedPersistedCredentialIsRejected() throws Exception {
        File directory = temporaryFolder.newFolder("malformed");
        File file = new File(directory, "device-config-credential");
        java.nio.file.Files.write(file.toPath(), "bad token".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        DeviceConfigCredentialStore store = DeviceConfigCredentialStore.forDirectory(directory);

        assertThrows(IllegalArgumentException.class, store::getCredential);
    }
}
