package cn.p4u.dth.extractor;

import cn.p4u.dth.util.TestDocxBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocxExtractorTest {

    @TempDir
    Path tempRoot;

    @Test
    void extractsDocxToTempDirectory() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Hello</w:t></w:r></w:p>");
            Path docxPath = builder.build();

            try (InputStream docxStream = Files.newInputStream(docxPath)) {
                Path extracted = DocxExtractor.extract(docxStream);
                try {
                    assertTrue(Files.isDirectory(extracted));
                    assertTrue(Files.exists(extracted.resolve("word/document.xml")));
                    String content = Files.readString(extracted.resolve("word/document.xml"));
                    assertTrue(content.contains("Hello"));
                } finally {
                    DocxExtractor.cleanup(extracted);
                }
            }
        }
    }

    @Test
    void extractsInvalidZipAsEmptyDirectory() {
        Path extracted = DocxExtractor.extract(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        try {
            assertTrue(Files.isDirectory(extracted));
        } finally {
            DocxExtractor.cleanup(extracted);
        }
    }

    @Test
    void extractsToUniqueChildOfSpecifiedDirectory() throws Exception {
        Path marker = tempRoot.resolve("keep.txt");
        Files.writeString(marker, "keep");

        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Custom temp</w:t></w:r></w:p>");

            try (InputStream docxStream = Files.newInputStream(builder.build())) {
                Path extracted = DocxExtractor.extract(docxStream, tempRoot);
                try {
                    assertEquals(tempRoot.toAbsolutePath().normalize(), extracted.getParent());
                    assertTrue(extracted.getFileName().toString().startsWith("docx2html-"));
                    assertTrue(Files.exists(extracted.resolve("word/document.xml")));
                } finally {
                    DocxExtractor.cleanup(extracted);
                }
            }
        }

        assertTrue(Files.exists(tempRoot));
        assertEquals("keep", Files.readString(marker));
    }

    @Test
    void cleanupRemovesDirectory() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Test</w:t></w:r></w:p>");
            Path docxPath = builder.build();

            try (InputStream docxStream = Files.newInputStream(docxPath)) {
                Path extracted = DocxExtractor.extract(docxStream);
                assertTrue(Files.exists(extracted));
                DocxExtractor.cleanup(extracted);
                assertFalse(Files.exists(extracted));
            }
        }
    }

    @Test
    void extractedDocxAutomaticallyCleansDirectoryWhenClosed() throws Exception {
        Path extractedPath;
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Auto close</w:t></w:r></w:p>");

            try (InputStream docxStream = Files.newInputStream(builder.build());
                 ExtractedDocx extracted = DocxExtractor.open(docxStream, tempRoot)) {
                extractedPath = extracted.rootDirectory();
                assertTrue(Files.exists(extractedPath.resolve("word/document.xml")));
            }
        }

        assertFalse(Files.exists(extractedPath));
        assertTrue(Files.exists(tempRoot));
    }
}
