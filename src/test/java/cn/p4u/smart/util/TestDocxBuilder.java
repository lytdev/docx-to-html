package cn.p4u.smart.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class TestDocxBuilder implements AutoCloseable {

    private final Path tempFile;
    private final ZipOutputStream zos;

    public TestDocxBuilder() throws IOException {
        tempFile = Files.createTempFile("test", ".docx");
        zos = new ZipOutputStream(Files.newOutputStream(tempFile));
    }

    public TestDocxBuilder addContentTypes() throws IOException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>""";
        writeEntry("[Content_Types].xml", xml);
        return this;
    }

    public TestDocxBuilder addRels() throws IOException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>""";
        writeEntry("_rels/.rels", xml);
        return this;
    }

    public TestDocxBuilder addDocument(String bodyXml) throws IOException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:body>""" + bodyXml + """
              </w:body>
            </w:document>""";
        writeEntry("word/document.xml", xml);
        return this;
    }

    public TestDocxBuilder addDocumentRels(String relsXml) throws IOException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">"""
            + relsXml + """
            </Relationships>""";
        writeEntry("word/_rels/document.xml.rels", xml);
        return this;
    }

    public TestDocxBuilder addStyles(String stylesXml) throws IOException {
        String xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">"""
            + stylesXml + """
            </w:styles>""";
        writeEntry("word/styles.xml", xml);
        return this;
    }

    public TestDocxBuilder addMedia(String fileName, byte[] data) throws IOException {
        writeEntry("word/media/" + fileName, data);
        return this;
    }

    public Path build() throws IOException {
        zos.close();
        return tempFile;
    }

    private void writeEntry(String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private void writeEntry(String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(tempFile);
    }
}
