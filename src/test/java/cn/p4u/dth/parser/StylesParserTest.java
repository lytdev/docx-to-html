package cn.p4u.dth.parser;

import cn.p4u.dth.model.StyleDef;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StylesParserTest {

    /** Parses styles XML and returns the styles map from StylesResult. */
    private Map<String, StyleDef> parseStyles(String xml) throws Exception {
        Path tempFile = Files.createTempFile("styles", ".xml");
        Files.writeString(tempFile, xml);
        try {
            return StylesParser.parse(tempFile).styles();
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    void parsesStyles() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
            "  <w:style w:type=\"paragraph\" w:styleId=\"Heading1\">\n" +
            "    <w:name w:val=\"heading 1\"/>\n" +
            "    <w:basedOn w:val=\"Normal\"/>\n" +
            "    <w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr>\n" +
            "    <w:rPr><w:sz w:val=\"32\"/></w:rPr>\n" +
            "  </w:style>\n" +
            "  <w:style w:type=\"character\" w:styleId=\"BoldText\">\n" +
            "    <w:name w:val=\"Bold Text\"/>\n" +
            "    <w:rPr><w:b/></w:rPr>\n" +
            "  </w:style>\n" +
            "</w:styles>";

        Map<String, StyleDef> styles = parseStyles(xml);
        assertEquals(2, styles.size());
        assertEquals("heading 1", styles.get("Heading1").name());
        assertEquals("Normal", styles.get("Heading1").basedOn());
        assertEquals(0, styles.get("Heading1").outlineLvl());
        assertEquals("32", styles.get("Heading1").runProps().get("sz"));
        assertTrue(styles.get("BoldText").runProps().containsKey("b"));
        assertNull(styles.get("BoldText").outlineLvl());
    }

    @Test
    void returnsEmptyMapForMissingFile() throws Exception {
        Map<String, StyleDef> styles = StylesParser.parse(Paths.get("/nonexistent/styles")).styles();
        assertTrue(styles.isEmpty());
    }

    @Test
    void parsesRawAttrsForTheme() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
            "  <w:style w:type=\"paragraph\" w:styleId=\"Normal\">\n" +
            "    <w:name w:val=\"Normal\"/>\n" +
            "    <w:rPr>\n" +
            "      <w:rFonts w:asciiTheme=\"minorHAnsi\" w:eastAsiaTheme=\"minorEastAsia\"/>\n" +
            "      <w:color w:themeColor=\"dk1\" w:themeTint=\"FF\"/>\n" +
            "      <w:shd w:themeFill=\"accent1\" w:themeFillTint=\"99\"/>\n" +
            "    </w:rPr>\n" +
            "  </w:style>\n" +
            "</w:styles>";

        Map<String, StyleDef> styles = parseStyles(xml);
        StyleDef normal = styles.get("Normal");
        assertNotNull(normal);
        Map<String, Map<String, String>> rawRun = normal.rawRunAttrs();
        assertTrue(rawRun.containsKey("rFonts"));
        assertEquals("minorHAnsi", rawRun.get("rFonts").get("asciiTheme"));
        assertEquals("minorEastAsia", rawRun.get("rFonts").get("eastAsiaTheme"));
        assertTrue(rawRun.containsKey("color"));
        assertEquals("dk1", rawRun.get("color").get("themeColor"));
        assertTrue(rawRun.containsKey("shd"));
        assertEquals("accent1", rawRun.get("shd").get("themeFill"));
    }

    @Test
    void parsesDocDefaultsRunAttrs() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
            "  <w:docDefaults>\n" +
            "    <w:rPrDefault>\n" +
            "      <w:rPr>\n" +
            "        <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" " +
            "w:eastAsia=\"宋体\" w:cs=\"Times New Roman\"/>\n" +
            "      </w:rPr>\n" +
            "    </w:rPrDefault>\n" +
            "    <w:pPrDefault/>\n" +
            "  </w:docDefaults>\n" +
            "  <w:style w:type=\"paragraph\" w:styleId=\"Normal\">\n" +
            "    <w:name w:val=\"Normal\"/>\n" +
            "  </w:style>\n" +
            "</w:styles>";

        Path tempFile = Files.createTempFile("styles", ".xml");
        Files.writeString(tempFile, xml);
        try {
            StylesParser.StylesResult result = StylesParser.parse(tempFile);
            // styles map should contain the Normal style
            assertEquals(1, result.styles().size());
            assertNotNull(result.styles().get("Normal"));
            // docDefaultRunAttrs should be parsed
            assertNotNull(result.docDefaultRunAttrs());
            Map<String, String> rFonts = result.docDefaultRunAttrs().get("rFonts");
            assertNotNull(rFonts);
            assertEquals("Times New Roman", rFonts.get("ascii"));
            assertEquals("Times New Roman", rFonts.get("hAnsi"));
            assertEquals("宋体", rFonts.get("eastAsia"));
            assertEquals("Times New Roman", rFonts.get("cs"));
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    void docDefaultIsNullWhenAbsent() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n" +
            "  <w:style w:type=\"paragraph\" w:styleId=\"Normal\">\n" +
            "    <w:name w:val=\"Normal\"/>\n" +
            "  </w:style>\n" +
            "</w:styles>";

        Path tempFile = Files.createTempFile("styles", ".xml");
        Files.writeString(tempFile, xml);
        try {
            StylesParser.StylesResult result = StylesParser.parse(tempFile);
            assertNull(result.docDefaultRunAttrs());
        } finally {
            Files.delete(tempFile);
        }
    }
}
