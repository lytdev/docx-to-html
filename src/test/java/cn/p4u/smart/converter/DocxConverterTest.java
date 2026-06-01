package cn.p4u.smart.converter;

import cn.p4u.smart.util.TestDocxBuilder;
import cn.p4u.smart.DocxConversionException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class DocxConverterTest {

    @Test
    void convertsMinimalDocx() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Hello</w:t></w:r></w:p>");
            Path docxPath = builder.build();

            ConversionResult result = DocxConverter.convert(docxPath, ConversionConfig.base64Defaults());
            assertTrue(result.html().contains("Hello"));
            assertTrue(result.html().contains("<!DOCTYPE html>"));
            assertFalse(result.extractedDir().isPresent());
        }
    }

    @Test
    void keepsTempDirWhenConfigured() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Temp</w:t></w:r></w:p>");
            Path docxPath = builder.build();

            ConversionConfig config = new ConversionConfig(
                    ConversionConfig.ImageMode.BASE64,
                    Paths.get("images"), null, true);
            ConversionResult result = DocxConverter.convert(docxPath, config);
            assertTrue(result.html().contains("Temp"));
            assertTrue(result.extractedDir().isPresent());
            assertTrue(Files.exists(result.extractedDir().get()));
            cn.p4u.smart.extractor.DocxExtractor.cleanup(result.extractedDir().get());
        }
    }

    @Test
    void throwsForInvalidInput() {
        assertThrows(DocxConversionException.class,
                () -> DocxConverter.convert(Paths.get("/nonexistent.docx"), ConversionConfig.base64Defaults()));
    }
}
