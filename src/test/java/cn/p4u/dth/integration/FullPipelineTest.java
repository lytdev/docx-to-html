package cn.p4u.dth.integration;

import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.converter.DocxConverter;
import cn.p4u.dth.util.TestDocxBuilder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FullPipelineTest {

    private String convertBody(String bodyXml) throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocument(bodyXml);
            Path docxPath = builder.build();
            try (InputStream docxStream = Files.newInputStream(docxPath)) {
                return DocxConverter.convert(docxStream, ConversionConfig.defaults());
            }
        }
    }

    private String convertBodyWithRels(String bodyXml, String relsXml) throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocumentRels(relsXml).addDocument(bodyXml);
            Path docxPath = builder.build();
            try (InputStream docxStream = Files.newInputStream(docxPath)) {
                return DocxConverter.convert(docxStream, ConversionConfig.defaults());
            }
        }
    }

    @Test
    void convertsStyledParagraphFullPipeline() throws Exception {
        String html = convertBody(
            "<w:p>\n" +
            "  <w:pPr><w:jc w:val=\"center\"/></w:pPr>\n" +
            "  <w:r><w:rPr>\n" +
            "    <w:rFonts w:ascii=\"Arial\"/>\n" +
            "    <w:sz w:val=\"28\"/>\n" +
            "    <w:color w:val=\"FF0000\"/>\n" +
            "    <w:b/>\n" +
            "    <w:i/>\n" +
            "    <w:u w:val=\"single\"/>\n" +
            "  </w:rPr><w:t>Bold Red</w:t></w:r>\n" +
            "</w:p>");

        assertTrue(html.contains("text-align: center"), "Expected text-align: center, got: " + html);
        assertTrue(html.contains("font-family: 'Arial'"), "Expected font-family: 'Arial', got: " + html);
        assertTrue(html.contains("font-size: 14pt"), "Expected font-size: 14pt, got: " + html);
        assertTrue(html.contains("color: #FF0000"), "Expected color: #FF0000, got: " + html);
        assertTrue(html.contains("font-weight: bold"), "Expected font-weight: bold, got: " + html);
        assertTrue(html.contains("font-style: italic"), "Expected font-style: italic, got: " + html);
        assertTrue(html.contains("text-decoration: underline"), "Expected text-decoration: underline, got: " + html);
        assertTrue(html.contains("Bold Red"), "Expected text 'Bold Red', got: " + html);
    }

    @Test
    void convertsTableFullPipeline() throws Exception {
        String html = convertBody(
            "<w:tbl>\n" +
            "  <w:tblPr>\n" +
            "    <w:tblW w:w=\"5000\" w:type=\"pct\"/>\n" +
            "    <w:tblBorders>\n" +
            "      <w:top w:val=\"single\" w:sz=\"4\" w:color=\"000000\"/>\n" +
            "    </w:tblBorders>\n" +
            "  </w:tblPr>\n" +
            "  <w:tr>\n" +
            "    <w:tc>\n" +
            "      <w:tcPr><w:shd w:fill=\"EEEEEE\"/></w:tcPr>\n" +
            "      <w:p><w:r><w:t>Cell A</w:t></w:r></w:p>\n" +
            "    </w:tc>\n" +
            "    <w:tc>\n" +
            "      <w:p><w:r><w:t>Cell B</w:t></w:r></w:p>\n" +
            "    </w:tc>\n" +
            "  </w:tr>\n" +
            "</w:tbl>");

        assertTrue(html.contains("<table"), "Expected <table element, got: " + html);
        assertTrue(html.contains("Cell A"), "Expected 'Cell A', got: " + html);
        assertTrue(html.contains("Cell B"), "Expected 'Cell B', got: " + html);
        assertTrue(html.contains("border-collapse: collapse"), "Expected border-collapse: collapse, got: " + html);
    }

    @Test
    void convertsHyperlinkFullPipeline() throws Exception {
        String html = convertBodyWithRels(
            "<w:p>\n" +
            "  <w:hyperlink r:id=\"rId5\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
            "    <w:r><w:t>Click here</w:t></w:r>\n" +
            "  </w:hyperlink>\n" +
            "</w:p>",
            "<Relationship Id=\"rId5\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"https://example.com\"/>");

        assertTrue(html.contains("Click here"), "Expected 'Click here', got: " + html);
        assertTrue(html.contains("href=\"https://example.com\""), "Expected href to example.com, got: " + html);
    }

    @Test
    void convertsMathFullPipeline() throws Exception {
        String html = convertBody(
            "<w:p>\n" +
            "  <m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">\n" +
            "    <m:sSup>\n" +
            "      <m:e><m:r><m:t>x</m:t></m:r></m:e>\n" +
            "      <m:sup><m:r><m:t>2</m:t></m:r></m:sup>\n" +
            "    </m:sSup>\n" +
            "  </m:oMath>\n" +
            "</w:p>");

        // 无备用图片的公式应输出为浏览器原生渲染的 MathML 元素
        assertTrue(html.contains("<math"), "Expected <math> element, got: " + html);
        assertTrue(html.contains("<msup>"), "Expected MathML msup for superscript, got: " + html);
        assertTrue(html.matches("(?s).*<mi[^>]*mathvariant=\"normal\"[^>]*>x</mi>.*"),
                "Expected unformatted MathML mi with x to stay upright, got: " + html);
    }

    @Test
    void outputIsCompleteHtmlDocument() throws Exception {
        String html = convertBody("<w:p><w:r><w:t>Test</w:t></w:r></w:p>");
        assertTrue(html.startsWith("<!DOCTYPE html>"), "Expected DOCTYPE html, got: " + html);
        assertTrue(html.contains("<html>"), "Expected <html>, got: " + html);
        assertTrue(html.contains("</html>"), "Expected </html>, got: " + html);
        assertTrue(html.contains("<meta charset=\"UTF-8\">"), "Expected meta charset, got: " + html);
    }

    @Test
    void convertsThemeFontsWithHeadingDetection() throws Exception {
        String themeXml =
            "<a:themeElements>\n" +
            "  <a:clrScheme name=\"Office\">\n" +
            "    <a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>\n" +
            "    <a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>\n" +
            "    <a:dk2><a:srgbClr val=\"44546A\"/></a:dk2>\n" +
            "    <a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2>\n" +
            "    <a:accent1><a:srgbClr val=\"5B9BD5\"/></a:accent1>\n" +
            "    <a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2>\n" +
            "    <a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3>\n" +
            "    <a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4>\n" +
            "    <a:accent5><a:srgbClr val=\"4472C4\"/></a:accent5>\n" +
            "    <a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6>\n" +
            "    <a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink>\n" +
            "    <a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink>\n" +
            "  </a:clrScheme>\n" +
            "  <a:fontScheme name=\"Office\">\n" +
            "    <a:majorFont>\n" +
            "      <a:latin typeface=\"Calibri Light\"/>\n" +
            "      <a:ea typeface=\"\"/>\n" +
            "      <a:cs typeface=\"\"/>\n" +
            "      <a:font script=\"Hans\" typeface=\"宋体\"/>\n" +
            "    </a:majorFont>\n" +
            "    <a:minorFont>\n" +
            "      <a:latin typeface=\"Calibri\"/>\n" +
            "      <a:ea typeface=\"\"/>\n" +
            "      <a:cs typeface=\"\"/>\n" +
            "      <a:font script=\"Hans\" typeface=\"宋体\"/>\n" +
            "    </a:minorFont>\n" +
            "  </a:fontScheme>\n" +
            "</a:themeElements>";

        String stylesXml =
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\">\n" +
            "  <w:name w:val=\"heading 1\"/>\n" +
            "  <w:basedOn w:val=\"Normal\"/>\n" +
            "  <w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr>\n" +
            "  <w:rPr><w:sz w:val=\"32\"/></w:rPr>\n" +
            "</w:style>\n" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Normal\">\n" +
            "  <w:name w:val=\"Normal\"/>\n" +
            "  <w:rPr>\n" +
            "    <w:rFonts w:asciiTheme=\"minorHAnsi\" w:eastAsiaTheme=\"minorEastAsia\"/>\n" +
            "    <w:sz w:val=\"21\"/>\n" +
            "  </w:rPr>\n" +
            "</w:style>";

        String bodyXml =
            "<w:p>\n" +
            "  <w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>\n" +
            "  <w:r><w:t>Title</w:t></w:r>\n" +
            "</w:p>\n" +
            "<w:p>\n" +
            "  <w:r><w:rPr>\n" +
            "    <w:rFonts w:asciiTheme=\"minorHAnsi\" w:eastAsiaTheme=\"minorEastAsia\"/>\n" +
            "  </w:rPr><w:t>Body text</w:t></w:r>\n" +
            "</w:p>";

        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addStyles(stylesXml).addTheme(themeXml).addDocument(bodyXml);
            Path docxPath = builder.build();
            String html;
            try (InputStream docxStream = Files.newInputStream(docxPath)) {
                html = DocxConverter.convert(docxStream, ConversionConfig.defaults());
            }

            assertTrue(html.contains("<h1"), "Expected <h1> for heading, got: " + html);
            assertTrue(html.contains("Title"), "Expected 'Title', got: " + html);
            assertTrue(html.contains("Calibri"), "Expected Calibri from theme, got: " + html);
            assertTrue(html.contains("宋体"), "Expected 宋体 from theme, got: " + html);
        }
    }

    @Test
    void convertsBorderlessTableFullPipeline() throws Exception {
        String html = convertBody(
            "<w:tbl>\n" +
            "  <w:tblPr>\n" +
            "    <w:tblW w:w=\"5000\" w:type=\"pct\"/>\n" +
            "  </w:tblPr>\n" +
            "  <w:tr>\n" +
            "    <w:tc>\n" +
            "      <w:p><w:r><w:t>A</w:t></w:r></w:p>\n" +
            "    </w:tc>\n" +
            "    <w:tc>\n" +
            "      <w:p><w:r><w:t>B</w:t></w:r></w:p>\n" +
            "    </w:tc>\n" +
            "  </w:tr>\n" +
            "</w:tbl>");

        assertTrue(html.contains("<table"), "Expected <table element, got: " + html);
        assertTrue(html.contains("border: none"), "Table without borders should output border: none, got: " + html);
        // Cells should also have border: none
        int tdIdx = html.indexOf("<td");
        String tdStyle = html.substring(tdIdx, html.indexOf(">", tdIdx));
        assertTrue(tdStyle.contains("border: none"), "Cell in borderless table should have border: none, got: " + tdStyle);
    }
}
