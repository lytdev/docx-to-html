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
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
            "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
            "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
            "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
            "</Types>";
        writeEntry("[Content_Types].xml", xml);
        return this;
    }

    public TestDocxBuilder addRels() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
            "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
            "</Relationships>";
        writeEntry("_rels/.rels", xml);
        return this;
    }

    public TestDocxBuilder addDocument(String bodyXml) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"\n" +
            "            xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\"\n" +
            "            xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"\n" +
            "            xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"\n" +
            "            xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"\n" +
            "            xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"\n" +
            "            xmlns:wps=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\"\n" +
            "            xmlns:wpg=\"http://schemas.microsoft.com/office/word/2010/wordprocessingGroup\">\n" +
            "  <w:body>" + bodyXml + "\n" +
            "  </w:body>\n" +
            "</w:document>";
        writeEntry("word/document.xml", xml);
        return this;
    }

    public TestDocxBuilder addDocumentRels(String relsXml) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            relsXml + "\n" +
            "</Relationships>";
        writeEntry("word/_rels/document.xml.rels", xml);
        return this;
    }

    public TestDocxBuilder addStyles(String stylesXml) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            stylesXml + "\n" +
            "</w:styles>";
        writeEntry("word/styles.xml", xml);
        return this;
    }

    public TestDocxBuilder addMedia(String fileName, byte[] data) throws IOException {
        writeEntry("word/media/" + fileName, data);
        return this;
    }

    public TestDocxBuilder addTheme(String themeXml) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Office Theme\">" +
            themeXml + "\n" +
            "</a:theme>";
        writeEntry("word/theme/theme1.xml", xml);
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
