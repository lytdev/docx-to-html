package cn.p4u.smart.renderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void base64EncodesImage() throws Exception {
        Path imgFile = tempDir.resolve("test.png");
        Files.write(imgFile, new byte[]{(byte)0x89, 'P', 'N', 'G'});
        String result = ImageHandler.toBase64DataUri(imgFile, "image/png");
        assertTrue(result.startsWith("data:image/png;base64,"));
        assertTrue(result.length() > "data:image/png;base64,".length());
    }

    @Test
    void returnsEmptyForMissingFile() {
        String result = ImageHandler.toBase64DataUri(
                Paths.get("/nonexistent/image.png"), "image/png");
        assertTrue(result.isEmpty());
    }

    @Test
    void copiesImageToOutputDir() throws Exception {
        Path imgFile = tempDir.resolve("src.png");
        Files.write(imgFile, new byte[]{1, 2, 3});
        Path outDir = tempDir.resolve("output");
        Files.createDirectories(outDir);
        Path result = ImageHandler.copyToDir(imgFile, outDir, "media/image1.png");
        assertEquals(outDir.resolve("media/image1.png"), result);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(result));
    }
}
