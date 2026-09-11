package cn.p4u.dth.parser;

import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.converter.DocxConverter;
import cn.p4u.dth.renderer.ImageUriResolver;
import cn.p4u.dth.util.TestDocxBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OleEquationLatexTest {
    @TempDir Path temp;

    // |x̄-μ₀|：包含空上标槽和私有字体括号，防止丢槽或重复输出括号。
    private static final String BAR = """
        03 01 01 03 0A 0A 01 03 04 00 00 01
        32 83 78 00 06 11 00 00 02 86 12 22 02 84 BC 03
        03 0F 01 00 0B 01 02 88 30 00 00 11 00
        00 0A 02 96 07 EC 02 96 08 EC 00 00 00
        """;

    @Test void readsV3SlotsAndEmbellishments() {
        assertEquals("\\left| \\bar{x}-\\mu _{0}\\right|", convert(BAR));
        assertEquals("\\frac{x}{y}", convert("""
            03 01 01 03 00 01 03 0E 00 00
            01 02 83 78 00 00 01 02 83 79 00 00 00 00 00
            """));
    }

    @Test void readsV5SubscriptAndMetadata() {
        assertEquals("x_{0}", convert("""
            05 01 00 06 00 44 53 4D 54 36 00 00
            11 01 41 72 69 61 6C 00 08 01 00
            12 00 01 12 F0 00 01 01 00
            0A 01 00 02 00 83 78 00
            03 00 1B 00 00 01 00 02 00 88 30 00 00 01 01 00 00 00
            """));
    }

    @Test void preservesSumLimitOrder() {
        assertEquals("\\sum\\limits_{i}^{n} A", convert("""
            03 01 01 03 00 01 03 1D 01 00
            01 02 83 41 00 00 01 02 83 6E 00 00 01 02 83 69 00 00
            02 96 11 EC 00 00 00
            """));
        assertEquals("\\sum\\limits_{i}^{n} A", convert("""
            05 01 00 06 00 00 00 01 00 03 00 10 43 00
            01 00 02 00 83 41 00 00 01 00 02 00 83 6E 00 00
            01 00 02 00 83 69 00 00 02 00 96 11 EC 00 00 00
            """));
    }

    @Test void readsRootsAndMatrices() {
        assertEquals("\\sqrt[3]{x}", convert("""
            03 01 01 03 00 01 03 0D 01 00
            01 02 83 78 00 00 01 02 88 33 00 00 00 00 00
            """));
        assertEquals("\\begin{matrix}x&y\\end{matrix}", convert("""
            03 01 01 03 00 01 05 00 00 00 01 02 00 00
            01 02 83 78 00 00 01 02 83 79 00 00 00 00 00
            """));
    }

    @Test void readsV5CharacterEmbellishment() {
        assertEquals("\\bar{x}", convert("""
            05 01 00 06 00 00 00 01 00 02 01 83 78 00 06 00 11 00 00 00
            """));
    }

    @Test void rejectsTruncatedAndUnknownStructures() {
        assertThrows(IllegalArgumentException.class, () -> convert("03 01 01 03 00 01 02"));
        assertThrows(IllegalArgumentException.class, () ->
                convert("03 01 01 03 00 01 03 30 00 00 01 00 00 00 00"));
        assertThrows(IllegalArgumentException.class, () ->
                convert("03 01 01 03 00 01 02 83 01 EC 00 00"));
        assertThrows(IllegalArgumentException.class, () -> convert("04 01 01 03 00 00"));
        assertThrows(IllegalArgumentException.class, () ->
                convert("03 01 01 03 00 01 02 84 61 00 00 00"));
    }

    @Test void limitsRecursion() {
        assertThrows(IllegalArgumentException.class, () -> convert(
                "03 01 01 03 00 " + "01 ".repeat(130) + "00 ".repeat(131)));
    }

    @Test void readsOleNativeStreamAndFallsBackForInvalidFiles() throws Exception {
        Path valid = temp.resolve("valid.bin");
        Files.write(valid, ole(bytes(BAR)));
        assertEquals(convert(BAR), OleEquationLatex.extract(valid));
        Path broken = temp.resolve("broken.bin");
        Files.write(broken, new byte[] {1, 2, 3});
        assertEquals("", OleEquationLatex.extract(broken));
        Files.write(valid, ole(bytes("03 01 01 03 00 01 02")));
        assertEquals("", OleEquationLatex.extract(valid));
        assertEquals("", OleEquationLatex.extract(temp.resolve("missing.bin")));
    }

    @Test void addsLatexToFormulaImageWithoutChangingImageRendering() throws Exception {
        String latex = "\\left| \\bar{x}-\\mu _{0}\\right|";
        assertImage(ole(bytes(BAR)), latex);
        // MTEF 损坏时不输出半截代码，也不影响原来的公式预览图。
        assertImage(ole(bytes("03 01 01 03 00 01 02")), "");
    }

    @Test void escapesLatexAttributeWithoutLosingCharacters() throws Exception {
        assertImage(ole(bytes("03 01 01 03 00 01 02 83 22 00 02 83 26 00 02 83 3C 00 00 00")),
                "\"\\&<");
    }

    private void assertImage(byte[] object, String latex) throws Exception {
        assertImage(object, latex, "eq.png", new byte[] {1, 2, 3});
    }

    @Test void omitsStaleOleLatexButKeepsOriginalPreview() throws Exception {
        byte[] object = ole(bytes(BAR)); // 可编辑对象中只有下标 0。
        assertImage(object, "", "eq.wmf",
                FormulaPreviewConsistencyTest.wmf("5753.62", false, true, "Times New Roman"));
        assertImage(object, convert(BAR), "eq.wmf",
                FormulaPreviewConsistencyTest.wmf("0", false, true, "Times New Roman"));
    }

    private void assertImage(byte[] object, String latex, String imageName, byte[] preview) throws Exception {
        assertImage(object, latex, imageName, preview, "Equation.3");
    }

    @Test void addsLatexToEditableMathTypePreview() throws Exception {
        // MathType 对象使用不同的程序标识，但仍从同一 OLE Native 流重建公式。
        for (String progId : new String[] {"Equation.DSMT4", "MathType.Equation"}) {
            assertImage(ole(bytes(BAR)), convert(BAR), "eq.png", new byte[] {1, 2, 3}, progId);
        }
    }

    private void assertImage(byte[] object, String latex, String imageName, byte[] preview,
                             String progId) throws Exception {
        try (var docx = new TestDocxBuilder()) {
            docx.addContentTypes().addRels().addDocument("""
                <w:p><w:r><w:object xmlns:v="urn:schemas-microsoft-com:vml"
                    xmlns:o="urn:schemas-microsoft-com:office:office">
                  <v:shape style="width:20pt;height:10pt"><v:imagedata r:id="img"/></v:shape>
                  <o:OLEObject Type="Embed" ProgID="%s" r:id="ole"/>
                </w:object></w:r></w:p>
                """.formatted(progId)).addDocumentRels("""
                <Relationship Id="img" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/%s"/>
                <Relationship Id="ole" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject" Target="embeddings/eq.bin"/>
                """.formatted(imageName)).addMedia(imageName, preview).addEmbedding("eq.bin", object);
            var config = ConversionConfig.builder()
                    .wmfStrategy(cn.p4u.dth.renderer.WmfConversionStrategy.NONE)
                    .imageUriResolver((path, mime) -> {
                        assertArrayEquals(preview, Files.readAllBytes(path));
                        return new ImageUriResolver.ResolveResult("memory:equation", mime);
                    }).build();
            try (var input = Files.newInputStream(docx.build())) {
                var img = Jsoup.parse(DocxConverter.convert(input, config)).selectFirst("img");
                assertNotNull(img);
                assertEquals(latex, img.attr("data-latex"));
                assertEquals(!latex.isEmpty(), img.hasAttr("data-latex"));
                assertEquals("formula-item formula-image", img.className());
                assertEquals("formula", img.attr("data-type"));
                assertEquals("memory:equation", img.attr("src"));
                assertFalse(img.hasAttr("width"));
                assertEquals(13, Double.parseDouble(img.attr("height")));
                assertTrue(img.attr("style").contains("max-width: 100%"));
            }
        }
    }

    private static byte[] ole(byte[] mtef) throws Exception {
        var nativeData = ByteBuffer.allocate(28 + mtef.length).order(ByteOrder.LITTLE_ENDIAN);
        nativeData.putShort(0, (short) 28);
        nativeData.putInt(8, mtef.length);
        nativeData.position(28);
        nativeData.put(mtef);
        try (var fs = new POIFSFileSystem(); var out = new ByteArrayOutputStream()) {
            fs.createDocument(new ByteArrayInputStream(nativeData.array()), "Equation Native");
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }

    private static byte[] bytes(String hex) {
        return HexFormat.of().parseHex(hex.replaceAll("\\s+", ""));
    }
    private static String convert(String hex) { return MtefLatexConverter.convert(bytes(hex)); }
}
