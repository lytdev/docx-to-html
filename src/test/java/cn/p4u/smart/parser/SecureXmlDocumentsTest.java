package cn.p4u.smart.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecureXmlDocumentsTest {
  @TempDir Path tempDirectory;

  @Test
  void parsesNormalNamespacedXml() throws Exception {
    Path xml = tempDirectory.resolve("normal.xml");
    Files.writeString(xml, "<root xmlns=\"urn:test\"><value>ok</value></root>");

    assertEquals("ok",
        SecureXmlDocuments.parse(xml)
            .getElementsByTagNameNS("urn:test", "value")
            .item(0)
            .getTextContent());
  }

  @Test
  void rejectsDoctypeDeclarations() throws Exception {
    Path xml = tempDirectory.resolve("doctype.xml");
    Files.writeString(xml, "<!DOCTYPE root [<!ENTITY value 'unsafe'>]><root>&value;</root>");

    assertThrows(Exception.class, () -> SecureXmlDocuments.parse(xml));
  }
}
