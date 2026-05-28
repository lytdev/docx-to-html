package cn.p4u.smart.renderer;

import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.logging.Logger;

public final class ImageHandler {

    private static final Logger LOG = Logger.getLogger(ImageHandler.class.getName());

    private ImageHandler() {}

    public static String toBase64DataUri(Path imagePath, String mimeType) {
        if (!Files.exists(imagePath)) {
            LOG.warning("Image file not found: " + imagePath);
            return "";
        }
        try {
            byte[] data = Files.readAllBytes(imagePath);
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (IOException e) {
            LOG.warning("Failed to read image: " + imagePath + " - " + e.getMessage());
            return "";
        }
    }

    public static Path copyToDir(Path imagePath, Path outputDir, String relativePath) {
        Path target = outputDir.resolve(relativePath).normalize();
        if (!target.startsWith(outputDir)) {
            LOG.warning("Path traversal blocked: " + relativePath);
            return imagePath;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(imagePath, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            LOG.warning("Failed to copy image: " + imagePath + " - " + e.getMessage());
            return imagePath;
        }
    }
}
