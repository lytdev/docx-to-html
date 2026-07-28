package cn.p4u.smart.extractor;

import cn.p4u.smart.util.TestDocxBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class DocxExtractorTest {

    @Test
    void extractsDocxToTempDirectory() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Hello</w:t></w:r></w:p>");
            Path docxPath = builder.build();

            Path extracted = DocxExtractor.extract(docxPath);
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

    @Test
    void throwsForInvalidFile() {
        assertThrows(cn.p4u.smart.DocxConversionException.class,
                () -> DocxExtractor.extract(Paths.get("nonexistent.docx")));
    }

    @Test
    void cleanupRemovesDirectory() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Test</w:t></w:r></w:p>");
            Path docxPath = builder.build();

            Path extracted = DocxExtractor.extract(docxPath);
            assertTrue(Files.exists(extracted));
            DocxExtractor.cleanup(extracted);
            assertFalse(Files.exists(extracted));
        }
    }
}
