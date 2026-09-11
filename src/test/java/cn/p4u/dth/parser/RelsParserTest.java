package cn.p4u.dth.parser;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RelsParserTest {

    @Test
    void parsesRelationships() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
            "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image1.png\"/>\n" +
            "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"https://example.com\" TargetMode=\"External\"/>\n" +
            "</Relationships>";

        Path tempFile = Files.createTempFile("rels", ".xml");
        Files.writeString(tempFile, xml);
        try {
            Map<String, RelsParser.Rel> rels = RelsParser.parse(tempFile);
            assertEquals(2, rels.size());
            assertEquals("media/image1.png", rels.get("rId1").target());
            assertEquals("image", rels.get("rId1").type());
            assertEquals("https://example.com", rels.get("rId2").target());
            assertEquals("hyperlink", rels.get("rId2").type());
        } finally {
            Files.delete(tempFile);
        }
    }

    @Test
    void returnsEmptyMapForMissingFile() throws Exception {
        Map<String, RelsParser.Rel> rels = RelsParser.parse(Paths.get("/nonexistent/rels"));
        assertTrue(rels.isEmpty());
    }
}
