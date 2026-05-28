package cn.p4u.smart.extractor;

import cn.p4u.smart.DocxConversionException;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipInputStream;

public final class DocxExtractor {

    private DocxExtractor() {}

    public static Path extract(Path docxPath) {
        if (!Files.exists(docxPath)) {
            throw new DocxConversionException(docxPath.toString(),
                    new NoSuchFileException(docxPath.toString()));
        }
        try {
            Path tempDir = Files.createTempDirectory("docx2html-");
            try (var zis = new ZipInputStream(Files.newInputStream(docxPath))) {
                var entry = zis.getNextEntry();
                while (entry != null) {
                    Path target = tempDir.resolve(entry.getName()).normalize();
                    if (!target.startsWith(tempDir)) {
                        throw new DocxConversionException("Zip entry escapes temp dir: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                    entry = zis.getNextEntry();
                }
            }
            return tempDir;
        } catch (IOException e) {
            throw new DocxConversionException(docxPath.toString(), e);
        }
    }

    public static void cleanup(Path extractedDir) {
        try {
            FileUtils.deleteDirectory(extractedDir.toFile());
        } catch (IOException e) {
            // best effort
        }
    }
}
