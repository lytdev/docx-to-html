package cn.p4u.smart.parser;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmmlToMathmlConverterTest {

    private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    @Test
    void preservesExplicitWordItalicAsMathmlItalic() throws Exception {
        String mathml = convert("<m:r><m:rPr><m:nor/></m:rPr>"
                + "<w:rPr><w:i/></w:rPr><m:t>R</m:t></m:r>");

        assertTrue(mathml.contains("<mi mathvariant=\"normal\""), mathml);
        assertTrue(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void doesNotTreatComplexScriptFlagAsLatinItalic() throws Exception {
        String mathml = convert("<m:r><m:rPr><m:nor/></m:rPr>"
                + "<w:rPr><w:iCs/></w:rPr><m:t>real</m:t></m:r>");

        assertTrue(mathml.contains("mathvariant=\"normal\""), mathml);
        assertFalse(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void preservesOmmlItalicStyle() throws Exception {
        String mathml = convert("<m:r><m:rPr><m:sty m:val=\"i\"/></m:rPr><m:t>x</m:t></m:r>");

        assertTrue(mathml.contains("mathvariant=\"normal\""), mathml);
        assertTrue(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void preservesOmmlPlainStyle() throws Exception {
        String mathml = convert("<m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr><m:t>x</m:t></m:r>");

        assertTrue(mathml.contains("mathvariant=\"normal\""), mathml);
        assertFalse(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void preservesExplicitPlainWordMathRun() throws Exception {
        String mathml = convert("<m:r><m:rPr><m:nor/></m:rPr>"
                + "<w:rPr><w:i w:val=\"0\"/><w:iCs w:val=\"false\"/></w:rPr>"
                + "<m:t>text</m:t></m:r>");

        assertTrue(mathml.contains("mathvariant=\"normal\""), mathml);
        assertFalse(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void keepsUnformattedMathRunUpright() throws Exception {
        String mathml = convert("<m:r><m:t>x</m:t></m:r>");

        assertTrue(mathml.contains("mathvariant=\"normal\""), mathml);
        assertFalse(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void keepsDelimiterObjectCharactersUprightEvenWhenControlPropertiesAreItalic() throws Exception {
        String mathml = convert("<m:d><m:dPr>"
                + "<m:begChr m:val=\"|\"/><m:endChr m:val=\"|\"/>"
                + "<m:ctrlPr><w:rPr><w:i/></w:rPr></m:ctrlPr>"
                + "</m:dPr><m:e><m:r><m:t>x</m:t></m:r></m:e></m:d>");

        assertTrue(mathml.contains("<mo mathvariant=\"normal\">|</mo>"), mathml);
        assertTrue(mathml.endsWith("<mo mathvariant=\"normal\">|</mo></mrow></math>"), mathml);
        assertFalse(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void keepsUnformattedDelimiterCharactersUpright() throws Exception {
        String mathml = convert("<m:d><m:dPr>"
                + "<m:begChr m:val=\"/\"/><m:endChr m:val=\"/\"/>"
                + "<m:ctrlPr><w:rPr><w:iCs/></w:rPr></m:ctrlPr>"
                + "</m:dPr><m:e><m:r><m:t>x</m:t></m:r></m:e></m:d>");

        assertTrue(mathml.contains("<mo mathvariant=\"normal\">/</mo>"), mathml);
        assertFalse(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void keepsBracketsAndSlashesUprightInsideItalicFormulaRun() throws Exception {
        String mathml = convert("<m:r><m:rPr><m:nor/></m:rPr>"
                + "<w:rPr><w:i/></w:rPr><m:t>d[x]/|y</m:t></m:r>");

        assertTrue(mathml.contains("font-style:italic\">d</mi>"), mathml);
        assertTrue(mathml.contains("<mi mathvariant=\"normal\">[</mi>"), mathml);
        assertTrue(mathml.contains("<mi mathvariant=\"normal\">]</mi>"), mathml);
        assertTrue(mathml.contains("<mi mathvariant=\"normal\">/</mi>"), mathml);
        assertTrue(mathml.contains("<mi mathvariant=\"normal\">|</mi>"), mathml);
        assertTrue(mathml.contains("font-style:italic\">y</mi>"), mathml);
    }

    @Test
    void usesComplexScriptFontBeforeEastAsiaForLatinFormulaText() throws Exception {
        String mathml = convert("<m:r><m:rPr><m:nor/></m:rPr><w:rPr>"
                + "<w:rFonts w:cs=\"Times New Roman\" w:eastAsia=\"宋体\"/>"
                + "</w:rPr><m:t>R</m:t></m:r>");

        assertTrue(mathml.contains("font-family:'Times New Roman', '宋体'"), mathml);
    }

    @Test
    void asciiFontHasPriorityOverComplexScriptAndEastAsiaFonts() throws Exception {
        String mathml = convert("<m:r><m:rPr><m:nor/></m:rPr><w:rPr>"
                + "<w:rFonts w:ascii=\"Arial\" w:cs=\"Times New Roman\" w:eastAsia=\"宋体\"/>"
                + "</w:rPr><m:t>R</m:t></m:r>");

        assertTrue(mathml.contains("font-family:'Arial', '宋体'"), mathml);
        assertFalse(mathml.contains("Times New Roman"), mathml);
    }

    private String convert(String body) throws Exception {
        String xml = "<m:oMath xmlns:m=\"" + M + "\" xmlns:w=\"" + W + "\">"
                + body + "</m:oMath>";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) ->
                new org.xml.sax.InputSource(new java.io.StringReader("")));
        Element element = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        return OmmlToMathmlConverter.convert(element);
    }
}
