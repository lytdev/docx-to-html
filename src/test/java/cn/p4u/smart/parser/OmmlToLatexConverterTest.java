package cn.p4u.smart.parser;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class OmmlToLatexConverterTest {

    private Element parseOmml(String xml) throws Exception {
        String wrapped = """
            <?xml version="1.0" encoding="UTF-8"?>
            <m:oMath xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">"""
            + xml + """
            </m:oMath>""";
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
        var doc = builder.parse(new ByteArrayInputStream(wrapped.getBytes()));
        return doc.getDocumentElement();
    }

    @Test
    void convertsSimpleRun() throws Exception {
        var el = parseOmml("<m:r><m:t>x</m:t></m:r>");
        assertEquals("x", OmmlToLatexConverter.convert(el));
    }

    @Test
    void convertsFraction() throws Exception {
        var el = parseOmml("""
            <m:f>
              <m:fPr><m:type m:val="bar"/></m:fPr>
              <m:num><m:r><m:t>a</m:t></m:r></m:num>
              <m:den><m:r><m:t>b</m:t></m:r></m:den>
            </m:f>""");
        assertEquals("\\frac{a}{b}", OmmlToLatexConverter.convert(el));
    }

    @Test
    void convertsSuperscript() throws Exception {
        var el = parseOmml("""
            <m:sSup>
              <m:e><m:r><m:t>x</m:t></m:r></m:e>
              <m:sup><m:r><m:t>2</m:t></m:r></m:sup>
            </m:sSup>""");
        assertEquals("x^{2}", OmmlToLatexConverter.convert(el));
    }

    @Test
    void convertsSubscript() throws Exception {
        var el = parseOmml("""
            <m:sSub>
              <m:e><m:r><m:t>x</m:t></m:r></m:e>
              <m:sub><m:r><m:t>i</m:t></m:r></m:sub>
            </m:sSub>""");
        assertEquals("x_{i}", OmmlToLatexConverter.convert(el));
    }

    @Test
    void returnsEmptyForNull() {
        assertEquals("", OmmlToLatexConverter.convert(null));
    }
}
