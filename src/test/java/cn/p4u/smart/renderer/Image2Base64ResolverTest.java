package cn.p4u.smart.renderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class Image2Base64ResolverTest {

    private final Image2Base64Resolver resolver = new Image2Base64Resolver();

    @TempDir
    Path tempDir;

    @Test
    void resolvesImageToBase64DataUri() throws IOException {
        Path imgFile = tempDir.resolve("test.png");
        Files.write(imgFile, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        ImageUriResolver.ResolveResult result = resolver.resolve(imgFile, "image/png");
        assertTrue(result.uri().startsWith("data:image/png;base64,"));
        assertTrue(result.uri().length() > "data:image/png;base64,".length());
        assertEquals("image/png", result.mimeType());
    }

    @Test
    void throwsIOExceptionForMissingFile() {
        Path missing = Paths.get("/nonexistent/image.png");
        assertThrows(IOException.class, () -> resolver.resolve(missing, "image/png"));
    }
}
