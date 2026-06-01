package cn.p4u.smart.parser;

import cn.p4u.smart.model.ThemeDef;
import cn.p4u.smart.model.ThemeDef.ColorScheme;
import cn.p4u.smart.model.ThemeDef.FontScheme;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ThemeParserTest {

    private static final String THEME_XML =
        "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Office Theme\">\n" +
        "  <a:themeElements>\n" +
        "    <a:clrScheme name=\"Office\">\n" +
        "      <a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>\n" +
        "      <a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>\n" +
        "      <a:dk2><a:srgbClr val=\"44546A\"/></a:dk2>\n" +
        "      <a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2>\n" +
        "      <a:accent1><a:srgbClr val=\"5B9BD5\"/></a:accent1>\n" +
        "      <a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2>\n" +
        "      <a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3>\n" +
        "      <a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4>\n" +
        "      <a:accent5><a:srgbClr val=\"4472C4\"/></a:accent5>\n" +
        "      <a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6>\n" +
        "      <a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink>\n" +
        "      <a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink>\n" +
        "    </a:clrScheme>\n" +
        "    <a:fontScheme name=\"Office\">\n" +
        "      <a:majorFont>\n" +
        "        <a:latin typeface=\"Calibri Light\"/>\n" +
        "        <a:ea typeface=\"\"/>\n" +
        "        <a:cs typeface=\"\"/>\n" +
        "        <a:font script=\"Hans\" typeface=\"宋体\"/>\n" +
        "      </a:majorFont>\n" +
        "      <a:minorFont>\n" +
        "        <a:latin typeface=\"Calibri\"/>\n" +
        "        <a:ea typeface=\"\"/>\n" +
        "        <a:cs typeface=\"\"/>\n" +
        "        <a:font script=\"Hans\" typeface=\"宋体\"/>\n" +
        "      </a:minorFont>\n" +
        "    </a:fontScheme>\n" +
        "  </a:themeElements>\n" +
        "</a:theme>";

    @Test
    void parsesFullTheme() throws Exception {
        Path tempFile = Files.createTempFile("theme", ".xml");
        cn.p4u.smart.util.Jdk8Helpers.writeString(tempFile, THEME_XML);
        try {
            ThemeDef theme = ThemeParser.parse(tempFile);
            assertNotNull(theme);
            assertNotNull(theme.colors());
            assertNotNull(theme.fonts());

            ColorScheme colors = theme.colors();
            assertEquals("000000", colors.dk1());
            assertEquals("FFFFFF", colors.lt1());
            assertEquals("44546A", colors.dk2());
            assertEquals("5B9BD5", colors.accent1());
            assertEquals("ED7D31", colors.accent2());
            assertEquals("0563C1", colors.hlink());

            FontScheme fonts = theme.fonts();
            assertEquals("Calibri Light", fonts.majorLatin());
            assertEquals("Calibri", fonts.minorLatin());
            assertEquals("宋体", fonts.minorScriptOverrides().get("Hans"));
            assertEquals("宋体", fonts.majorScriptOverrides().get("Hans"));
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    void returnsNullForMissingFile() throws Exception {
        ThemeDef theme = ThemeParser.parse(Paths.get("/nonexistent/theme1.xml"));
        assertNotNull(theme);
        assertNull(theme.colors());
        assertNull(theme.fonts());
    }

    @Test
    void sysClrUsesLastClr() throws Exception {
        // dk1 uses sysClr with lastClr="000000" -- should resolve to "000000"
        Path tempFile = Files.createTempFile("theme", ".xml");
        cn.p4u.smart.util.Jdk8Helpers.writeString(tempFile, THEME_XML);
        try {
            ThemeDef theme = ThemeParser.parse(tempFile);
            assertEquals("000000", theme.colors().dk1());
        } finally {
            Files.delete(tempFile);
        }
    }
}
