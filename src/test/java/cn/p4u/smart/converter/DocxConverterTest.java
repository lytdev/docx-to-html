package cn.p4u.smart.converter;

import cn.p4u.smart.DocxConversionException;
import cn.p4u.smart.renderer.ImageUriResolver;
import cn.p4u.smart.util.TestDocxBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DocxConverterTest {

    @TempDir
    Path tempRoot;

    @Test
    void convertsInputStreamToHtmlString() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Hello</w:t></w:r></w:p>");
            Path docxPath = builder.build();

            try (InputStream docxStream = Files.newInputStream(docxPath)) {
                String html = DocxConverter.convert(docxStream);
                assertTrue(html.contains("Hello"));
                assertTrue(html.contains("<!DOCTYPE html>"));
            }
        }
    }

    @Test
    void acceptsCustomImageResourceResolver() throws Exception {
        String drawing =
                "<w:p><w:r><w:drawing>" +
                "<wp:inline><wp:extent cx=\"9525\" cy=\"9525\"/>" +
                "<a:graphic><a:graphicData>" +
                "<a:blip r:embed=\"rId1\"/>" +
                "</a:graphicData></a:graphic>" +
                "</wp:inline></w:drawing></w:r></w:p>";
        String rels =
                "<Relationship Id=\"rId1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" " +
                "Target=\"media/image1.png\"/>";

        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocumentRels(rels)
                    .addDocument(drawing).addMedia("image1.png", new byte[]{1, 2, 3});
            Path docxPath = builder.build();
            AtomicBoolean resolved = new AtomicBoolean();
            ImageUriResolver resolver = (imagePath, mimeType) -> {
                resolved.set(true);
                assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(imagePath));
                return new ImageUriResolver.ResolveResult("https://cdn.example/image1.png", mimeType);
            };

            try (InputStream docxStream = Files.newInputStream(docxPath)) {
                String html = DocxConverter.convert(docxStream, resolver);
                assertTrue(resolved.get());
                assertTrue(html.contains("src=\"https://cdn.example/image1.png\""), html);
            }
        }
    }

    @Test
    void doesNotCloseCallerOwnedInputStream() throws Exception {
        byte[] docxBytes;
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocument("<w:p><w:r><w:t>Open</w:t></w:r></w:p>");
            docxBytes = Files.readAllBytes(builder.build());
        }

        AtomicBoolean closed = new AtomicBoolean();
        InputStream stream = new FilterInputStream(new ByteArrayInputStream(docxBytes)) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };

        assertTrue(DocxConverter.convert(stream).contains("Open"));
        assertFalse(closed.get(), "DocxConverter must not close a caller-owned stream");
        stream.close();
        assertTrue(closed.get());
    }

    @Test
    void throwsForInvalidInputStream() {
        assertThrows(DocxConversionException.class,
                () -> DocxConverter.convert(new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }
}
