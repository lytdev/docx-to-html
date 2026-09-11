package cn.p4u.dth.parser;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class OmmlToLatexConverterTest {

    private Element parseOmml(String xml) throws Exception {
        String wrapped = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">" +
            xml + "\n" +
            "</m:oMath>";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new org.xml.sax.EntityResolver() {
            @Override
            public org.xml.sax.InputSource resolveEntity(String publicId, String systemId) {
                return new org.xml.sax.InputSource(new java.io.StringReader(""));
            }
        });
        org.w3c.dom.Document doc = builder.parse(new ByteArrayInputStream(wrapped.getBytes()));
        return doc.getDocumentElement();
    }

    @Test
    void convertsSimpleRun() throws Exception {
        Element el = parseOmml("<m:r><m:t>x</m:t></m:r>");
        assertEquals("x", OmmlToLatexConverter.convert(el));
    }

    @Test
    void convertsFraction() throws Exception {
        Element el = parseOmml(
            "<m:f>\n" +
            "  <m:fPr><m:type m:val=\"bar\"/></m:fPr>\n" +
            "  <m:num><m:r><m:t>a</m:t></m:r></m:num>\n" +
            "  <m:den><m:r><m:t>b</m:t></m:r></m:den>\n" +
            "</m:f>");
        assertEquals("\\frac{a}{b}", OmmlToLatexConverter.convert(el));
    }

    @Test
    void convertsSuperscript() throws Exception {
        Element el = parseOmml(
            "<m:sSup>\n" +
            "  <m:e><m:r><m:t>x</m:t></m:r></m:e>\n" +
            "  <m:sup><m:r><m:t>2</m:t></m:r></m:sup>\n" +
            "</m:sSup>");
        assertEquals("x^{2}", OmmlToLatexConverter.convert(el));
    }

    @Test
    void convertsSubscript() throws Exception {
        Element el = parseOmml(
            "<m:sSub>\n" +
            "  <m:e><m:r><m:t>x</m:t></m:r></m:e>\n" +
            "  <m:sub><m:r><m:t>i</m:t></m:r></m:sub>\n" +
            "</m:sSub>");
        assertEquals("x_{i}", OmmlToLatexConverter.convert(el));
    }

    @Test
    void convertsOverbraceWithAnnotationFromDemoDocument() throws Exception {
        Element el = parseOmml(
            "<m:sSup>" +
            "  <m:e><m:groupChr>" +
            "    <m:groupChrPr><m:chr m:val=\"⏞\"/><m:pos m:val=\"top\"/>" +
            "      <m:vertJc m:val=\"bot\"/></m:groupChrPr>" +
            "    <m:e><m:r><m:t>b×d</m:t></m:r></m:e>" +
            "  </m:groupChr></m:e>" +
            "  <m:sup><m:r><m:t>乘法优先</m:t></m:r></m:sup>" +
            "</m:sSup>");

        assertEquals("{\\overbrace{b×d}}^{乘法优先}", OmmlToLatexConverter.convert(el));
    }

    @Test
    void convertsUnderbrace() throws Exception {
        Element el = parseOmml("<m:groupChr><m:groupChrPr>"
                + "<m:chr m:val=\"⏟\"/><m:pos m:val=\"bot\"/>"
                + "</m:groupChrPr><m:e><m:r><m:t>x+y</m:t></m:r></m:e></m:groupChr>");

        assertEquals("\\underbrace{x+y}", OmmlToLatexConverter.convert(el));
    }

    @Test
    void returnsEmptyForNull() {
        assertEquals("", OmmlToLatexConverter.convert(null));
    }
}
