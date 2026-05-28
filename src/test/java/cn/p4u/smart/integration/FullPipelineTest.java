package cn.p4u.smart.integration;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.converter.ConversionResult;
import cn.p4u.smart.converter.DocxConverter;
import cn.p4u.smart.util.TestDocxBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FullPipelineTest {

    private String convertBody(String bodyXml) throws Exception {
        try (var builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocument(bodyXml);
            Path docxPath = builder.build();
            ConversionResult result = DocxConverter.convert(docxPath, ConversionConfig.base64Defaults());
            return result.html();
        }
    }

    private String convertBodyWithRels(String bodyXml, String relsXml) throws Exception {
        try (var builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocumentRels(relsXml).addDocument(bodyXml);
            Path docxPath = builder.build();
            ConversionResult result = DocxConverter.convert(docxPath, ConversionConfig.base64Defaults());
            return result.html();
        }
    }

    @Test
    void convertsStyledParagraphFullPipeline() throws Exception {
        String html = convertBody("""
            <w:p>
              <w:pPr><w:jc w:val="center"/></w:pPr>
              <w:r><w:rPr>
                <w:rFonts w:ascii="Arial"/>
                <w:sz w:val="28"/>
                <w:color w:val="FF0000"/>
                <w:b/>
                <w:i/>
                <w:u w:val="single"/>
              </w:rPr><w:t>Bold Red</w:t></w:r>
            </w:p>""");

        assertTrue(html.contains("text-align: center"), "Expected text-align: center, got: " + html);
        assertTrue(html.contains("font-family: Arial"), "Expected font-family: Arial, got: " + html);
        assertTrue(html.contains("font-size: 14pt"), "Expected font-size: 14pt, got: " + html);
        assertTrue(html.contains("color: #FF0000"), "Expected color: #FF0000, got: " + html);
        assertTrue(html.contains("font-weight: bold"), "Expected font-weight: bold, got: " + html);
        assertTrue(html.contains("font-style: italic"), "Expected font-style: italic, got: " + html);
        assertTrue(html.contains("text-decoration: underline"), "Expected text-decoration: underline, got: " + html);
        assertTrue(html.contains("Bold Red"), "Expected text 'Bold Red', got: " + html);
    }

    @Test
    void convertsTableFullPipeline() throws Exception {
        String html = convertBody("""
            <w:tbl>
              <w:tblPr>
                <w:tblW w:w="5000" w:type="pct"/>
                <w:tblBorders>
                  <w:top w:val="single" w:sz="4" w:color="000000"/>
                </w:tblBorders>
              </w:tblPr>
              <w:tr>
                <w:tc>
                  <w:tcPr><w:shd w:fill="EEEEEE"/></w:tcPr>
                  <w:p><w:r><w:t>Cell A</w:t></w:r></w:p>
                </w:tc>
                <w:tc>
                  <w:p><w:r><w:t>Cell B</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
            </w:tbl>""");

        assertTrue(html.contains("<table"), "Expected <table element, got: " + html);
        assertTrue(html.contains("Cell A"), "Expected 'Cell A', got: " + html);
        assertTrue(html.contains("Cell B"), "Expected 'Cell B', got: " + html);
        assertTrue(html.contains("border-collapse: collapse"), "Expected border-collapse: collapse, got: " + html);
    }

    @Test
    void convertsHyperlinkFullPipeline() throws Exception {
        String html = convertBodyWithRels("""
            <w:p>
              <w:hyperlink r:id="rId5" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <w:r><w:t>Click here</w:t></w:r>
              </w:hyperlink>
            </w:p>""",
            """
            <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com"/>""");

        assertTrue(html.contains("Click here"), "Expected 'Click here', got: " + html);
        assertTrue(html.contains("href=\"https://example.com\""), "Expected href to example.com, got: " + html);
    }

    @Test
    void convertsMathFullPipeline() throws Exception {
        String html = convertBody("""
            <w:p>
              <m:oMath xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">
                <m:sSup>
                  <m:e><m:r><m:t>x</m:t></m:r></m:e>
                  <m:sup><m:r><m:t>2</m:t></m:r></m:sup>
                </m:sSup>
              </m:oMath>
            </w:p>""");

        assertTrue(html.contains("data-latex=\"x^{2}\""), "Expected data-latex=\"x^{2}\", got: " + html);
    }

    @Test
    void outputIsCompleteHtmlDocument() throws Exception {
        String html = convertBody("<w:p><w:r><w:t>Test</w:t></w:r></w:p>");
        assertTrue(html.startsWith("<!DOCTYPE html>"), "Expected DOCTYPE html, got: " + html);
        assertTrue(html.contains("<html>"), "Expected <html>, got: " + html);
        assertTrue(html.contains("</html>"), "Expected </html>, got: " + html);
        assertTrue(html.contains("<meta charset=\"UTF-8\">"), "Expected meta charset, got: " + html);
    }
}
