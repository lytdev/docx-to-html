package cn.p4u.smart.parser;

import cn.p4u.smart.model.StyleDef;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StylesParserTest {

    @Test
    void parsesStyles() throws Exception {
        String xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="paragraph" w:styleId="Heading1">
                <w:name w:val="heading 1"/>
                <w:basedOn w:val="Normal"/>
                <w:rPr><w:sz w:val="32"/></w:rPr>
              </w:style>
              <w:style w:type="character" w:styleId="BoldText">
                <w:name w:val="Bold Text"/>
                <w:rPr><w:b/></w:rPr>
              </w:style>
            </w:styles>""";

        Path tempFile = Files.createTempFile("styles", ".xml");
        Files.writeString(tempFile, xml);
        try {
            Map<String, StyleDef> styles = StylesParser.parse(tempFile);
            assertEquals(2, styles.size());
            assertEquals("heading 1", styles.get("Heading1").name());
            assertEquals("Normal", styles.get("Heading1").basedOn());
            assertEquals("32", styles.get("Heading1").runProps().get("sz"));
            assertTrue(styles.get("BoldText").runProps().containsKey("b"));
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    void returnsEmptyMapForMissingFile() throws Exception {
        Map<String, StyleDef> styles = StylesParser.parse(Paths.get("/nonexistent/styles"));
        assertTrue(styles.isEmpty());
    }
}
