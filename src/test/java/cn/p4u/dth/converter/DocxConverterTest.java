package cn.p4u.dth.converter;

import cn.p4u.dth.DocxConversionException;
import cn.p4u.dth.model.ContentBlock;
import cn.p4u.dth.model.ParagraphBlock;
import cn.p4u.dth.model.TableBlock;
import cn.p4u.dth.renderer.ImageUriResolver;
import cn.p4u.dth.util.TestDocxBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

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

    @Test
    void reportsCurrentAndTotalParsedItemsInDocumentOrder() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocument("""
                    <w:p><w:r><w:t>第一段</w:t></w:r></w:p>
                    <w:tbl><w:tr><w:tc><w:p><w:r><w:t>单元格</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
                    <w:p><w:r><w:t>第二段</w:t></w:r></w:p>
                    """);
            var events = new ArrayList<CallBackRecord<ContentBlock>>();
            var counts = new ArrayList<String>();
            var completed = new AtomicInteger(-1);
            FileParseCallback<ContentBlock> callback = new FileParseCallback<>() {
                @Override
                public void onLineParsed(int count, int total, CallBackRecord<ContentBlock> record) {
                    counts.add(count + "/" + total);
                    events.add(record);
                }

                @Override
                public void onComplete(int total, String message) {
                    completed.set(total);
                    assertEquals("文件处理完成", message);
                }
            };

            try (InputStream input = Files.newInputStream(builder.build())) {
                String html = DocxConverter.convert(input, ConversionConfig.defaults(), callback);
                assertTrue(html.contains("第一段"));
            }

            assertEquals(List.of("1/3", "2/3", "3/3"), counts);
            assertEquals(3, completed.get());
            assertEquals(List.of(1, 2, 3), events.stream().map(CallBackRecord::line).toList());
            assertEquals(List.of("paragraph", "table", "paragraph"),
                    events.stream().map(CallBackRecord::type).toList());
            assertInstanceOf(ParagraphBlock.class, events.get(0).data());
            assertInstanceOf(TableBlock.class, events.get(1).data());
        }
    }

    @Test
    void completesAnEmptyDocumentWithZeroTotal() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocument("");
            AtomicInteger completed = new AtomicInteger(-1);
            try (InputStream input = Files.newInputStream(builder.build())) {
                DocxConverter.convert(input, new FileParseCallback<ContentBlock>() {
                    @Override
                    public void onLineParsed(int count, int total, CallBackRecord<ContentBlock> record) {
                        fail("Empty document must not report an item");
                    }

                    @Override
                    public void onComplete(int total, String message) {
                        completed.set(total);
                    }
                });
            }
            assertEquals(0, completed.get());
        }
    }

    @Test
    void reportsExtractionErrorOnceAndDoesNotComplete() {
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        var callback = new FileParseCallback<ContentBlock>() {
            @Override
            public void onLineParsed(int count, int total, CallBackRecord<ContentBlock> record) {}

            @Override
            public void onError(Exception ex, int line) {
                errors.incrementAndGet();
                assertEquals(0, line);
            }

            @Override
            public void onComplete(int total, String message) {
                completed.incrementAndGet();
            }
        };

        assertThrows(DocxConversionException.class, () -> DocxConverter.convert(
                new ByteArrayInputStream(new byte[] {1, 2, 3}), callback));
        assertEquals(1, errors.get());
        assertEquals(0, completed.get());
    }

    @Test
    void reportsTheCurrentItemWhenProgressCallbackFails() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocument(
                    "<w:p/><w:p/><w:p/>");
            AtomicInteger errorLine = new AtomicInteger();
            AtomicBoolean completed = new AtomicBoolean();
            var callback = new FileParseCallback<ContentBlock>() {
                @Override
                public void onLineParsed(int count, int total, CallBackRecord<ContentBlock> record) {
                    if (count == 2) throw new IllegalStateException("callback failed");
                }

                @Override
                public void onError(Exception ex, int line) {
                    errorLine.set(line);
                }

                @Override
                public void onComplete(int total, String message) {
                    completed.set(true);
                }
            };
            try (InputStream input = Files.newInputStream(builder.build())) {
                assertThrows(DocxConversionException.class,
                        () -> DocxConverter.convert(input, ConversionConfig.defaults(), callback));
            }
            assertEquals(2, errorLine.get());
            assertFalse(completed.get());
        }
    }
}
