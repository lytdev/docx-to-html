package cn.p4u.dth.parser;

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
    void preservesSummationLimitLocationFromWord() throws Exception {
        String body = "<m:nary><m:naryPr><m:chr m:val='∑'/><m:limLoc m:val='%s'/>"
                + "</m:naryPr><m:sub><m:r><m:t>i=0</m:t></m:r></m:sub>"
                + "<m:sup><m:r><m:t>n</m:t></m:r></m:sup>"
                + "<m:e><m:r><m:t>A</m:t></m:r></m:e></m:nary>";
        String stacked = convert(body.formatted("undOvr"));
        assertTrue(stacked.contains("<mstyle displaystyle=\"true\"><munderover>"), stacked);
        assertTrue(stacked.contains("<mo largeop=\"true\" movablelimits=\"false\">∑</mo>"), stacked);
        assertTrue(stacked.contains("</munderover></mstyle>"), stacked);
        String side = convert(body.formatted("subSup"));
        assertTrue(side.contains("<msubsup>"), side);
        assertFalse(side.contains("munderover"), side);
        assertFalse(side.contains("displaystyle"), side);
    }

    @Test
    void preservesStretchyOverbraceWithRightSuperscriptFromDemoDocument() throws Exception {
        String mathml = convert("<m:sSup><m:e><m:groupChr><m:groupChrPr>"
                + "<m:chr m:val='⏞'/><m:pos m:val='top'/><m:vertJc m:val='bot'/>"
                + "</m:groupChrPr><m:e><m:r><m:t>b×d</m:t></m:r></m:e>"
                + "</m:groupChr></m:e><m:sup><m:r><m:t>乘法优先</m:t></m:r></m:sup></m:sSup>");

        assertTrue(mathml.contains("<msup><mrow><mover accent=\"true\">"), mathml);
        assertTrue(mathml.contains("<mo stretchy=\"true\">⏞</mo></mover>"), mathml);
        assertTrue(mathml.contains(">乘</mi>"), mathml);
        assertTrue(mathml.endsWith("</mrow></msup></math>"), mathml);
    }

    @Test
    void preservesStretchyUnderbrace() throws Exception {
        String mathml = convert("<m:groupChr><m:groupChrPr>"
                + "<m:chr m:val='⏟'/><m:pos m:val='bot'/>"
                + "</m:groupChrPr><m:e><m:r><m:t>x</m:t></m:r></m:e></m:groupChr>");

        assertTrue(mathml.contains("<munder accentunder=\"true\">"), mathml);
        assertTrue(mathml.contains("<mo stretchy=\"true\">⏟</mo></munder>"), mathml);
    }

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
    void usesDefaultItalicForUnformattedMathVariable() throws Exception {
        String mathml = convert("<m:r><m:t>x</m:t></m:r>");

        assertTrue(mathml.contains("mathvariant=\"normal\""), mathml);
        assertTrue(mathml.contains("font-style:italic"), mathml);
    }

    @Test
    void keepsDelimiterObjectCharactersUprightEvenWhenControlPropertiesAreItalic() throws Exception {
        String mathml = convert("<m:d><m:dPr>"
                + "<m:begChr m:val=\"|\"/><m:endChr m:val=\"|\"/>"
                + "<m:ctrlPr><w:rPr><w:i/></w:rPr></m:ctrlPr>"
                + "</m:dPr><m:e><m:r><m:t>x</m:t></m:r></m:e></m:d>");

        assertTrue(mathml.contains("<mo mathvariant=\"normal\">|</mo>"), mathml);
        assertTrue(mathml.endsWith("<mo mathvariant=\"normal\">|</mo></mrow></math>"), mathml);
        assertTrue(mathml.contains("font-style:italic\">x</mi>"), mathml);
    }

    @Test
    void keepsUnformattedDelimiterCharactersUpright() throws Exception {
        String mathml = convert("<m:d><m:dPr>"
                + "<m:begChr m:val=\"/\"/><m:endChr m:val=\"/\"/>"
                + "<m:ctrlPr><w:rPr><w:iCs/></w:rPr></m:ctrlPr>"
                + "</m:dPr><m:e><m:r><m:t>x</m:t></m:r></m:e></m:d>");

        assertTrue(mathml.contains("<mo mathvariant=\"normal\">/</mo>"), mathml);
        assertTrue(mathml.contains("font-style:italic\">x</mi>"), mathml);
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

    @Test
    void defaultItalicDoesNotAffectNumbersOperatorsOrChinese() throws Exception {
        String mathml = convert("<m:r><m:rPr/><m:t>σ2/n+中</m:t></m:r>");
        for (String letter : new String[] {"σ", "n"})
            assertTrue(mathml.contains("font-style:italic\">" + letter + "</mi>"), mathml);
        for (String upright : new String[] {"2", "/", "+", "中"})
            assertTrue(mathml.contains("<mi mathvariant=\"normal\">" + upright + "</mi>"), mathml);
    }

    @Test
    void honorsExplicitItalicOffWithoutNormalTextFlag() throws Exception {
        for (String off : new String[] {"0", "false", "off"}) {
            String mathml = convert("<m:r><w:rPr><w:i w:val='" + off
                    + "'/></w:rPr><m:t>x</m:t></m:r>");
            assertFalse(mathml.contains("font-style:italic"), mathml);
        }
    }

    @Test
    void honorsNormalTextBooleanValue() throws Exception {
        String plain = convert("<m:r><m:rPr><m:nor/></m:rPr><m:t>real</m:t></m:r>");
        assertFalse(plain.contains("font-style:italic"), plain);
        String math = convert("<m:r><m:rPr><m:nor m:val='0'/></m:rPr><m:t>x</m:t></m:r>");
        assertTrue(math.contains("font-style:italic"), math);
    }

    @Test
    void preservesBoldItalicAndExplicitPlainOverride() throws Exception {
        String bold = convert("<m:r><m:rPr><m:sty m:val='bi'/></m:rPr><m:t>x</m:t></m:r>");
        assertTrue(bold.contains("font-weight:bold;font-style:italic"), bold);
        String plain = convert("<m:r><m:rPr><m:sty m:val='p'/></m:rPr>"
                + "<w:rPr><w:i/></w:rPr><m:t>x</m:t></m:r>");
        assertFalse(plain.contains("font-style:italic"), plain);
    }

    @Test
    void explicitRunValueOverridesContainerItalic() throws Exception {
        String mathml = convert("<m:e><m:ctrlPr><w:rPr><w:i/></w:rPr></m:ctrlPr>"
                + "<m:r><w:rPr><w:i w:val='0'/></w:rPr><m:t>x</m:t></m:r></m:e>");
        assertFalse(mathml.contains("font-style:italic"), mathml);
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
