package cn.p4u.smart.parser;

import cn.p4u.smart.model.*;
import cn.p4u.smart.util.TestDocxBuilder;
import cn.p4u.smart.extractor.DocxExtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserTest {

    private DocumentModel parseBody(String bodyXml) throws Exception {
        try (var builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocument(bodyXml);
            Path docxPath = builder.build();
            Path extracted = DocxExtractor.extract(docxPath);
            try {
                return DocumentParser.parse(extracted);
            } finally {
                DocxExtractor.cleanup(extracted);
            }
        }
    }

    @Test
    void parsesPlainParagraph() throws Exception {
        var model = parseBody("""
            <w:p>
              <w:pPr><w:pStyle w:val="Normal"/></w:pPr>
              <w:r><w:rPr><w:rFonts w:ascii="SimSun"/><w:sz w:val="24"/></w:rPr><w:t>Hello World</w:t></w:r>
            </w:p>""");

        assertEquals(1, model.content().size());
        var para = (ParagraphBlock) model.content().getFirst();
        assertEquals("Normal", para.styleId());
        var run = (TextRun) para.elements().getFirst();
        assertEquals("Hello World", run.text());
        assertEquals("SimSun", run.font().name());
    }

    @Test
    void parsesMultipleParagraphs() throws Exception {
        var model = parseBody("""
            <w:p><w:r><w:t>First</w:t></w:r></w:p>
            <w:p><w:r><w:t>Second</w:t></w:r></w:p>""");
        assertEquals(2, model.content().size());
    }

    @Test
    void parsesRunWithAllProperties() throws Exception {
        var model = parseBody("""
            <w:p><w:r><w:rPr>
              <w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>
              <w:sz w:val="28"/>
              <w:color w:val="FF0000"/>
              <w:b/>
              <w:i/>
              <w:u w:val="single"/>
              <w:strike/>
              <w:highlight w:val="yellow"/>
              <w:shd w:fill="CCCCCC"/>
              <w:vertAlign w:val="superscript"/>
            </w:rPr><w:t>Bold red</w:t></w:r></w:p>""");

        var run = (TextRun) ((ParagraphBlock) model.content().getFirst()).elements().getFirst();
        assertEquals("Arial", run.font().name());
        assertEquals("FF0000", run.font().color());
        assertTrue(run.bold());
        assertTrue(run.italic());
        assertTrue(run.underline());
        assertTrue(run.strike());
        assertEquals("yellow", run.highlight());
        assertEquals("CCCCCC", run.shading());
        assertTrue(run.superscript());
    }

    @Test
    void parsesParagraphAlignment() throws Exception {
        var model = parseBody("""
            <w:p><w:pPr><w:jc w:val="center"/></w:pPr>
              <w:r><w:t>Centered</w:t></w:r></w:p>""");
        var para = (ParagraphBlock) model.content().getFirst();
        assertEquals("center", para.alignment());
    }

    @Test
    void parsesParagraphIndentation() throws Exception {
        var model = parseBody("""
            <w:p><w:pPr>
              <w:ind w:left="720" w:right="360" w:firstLine="480"/>
            </w:pPr>
              <w:r><w:t>Indented</w:t></w:r></w:p>""");
        var para = (ParagraphBlock) model.content().getFirst();
        assertNotNull(para.indentation());
        assertEquals("720", para.indentation().left());
        assertEquals("360", para.indentation().right());
        assertEquals("480", para.indentation().firstLine());
    }

    @Test
    void parsesTable() throws Exception {
        var model = parseBody("""
            <w:tbl>
              <w:tr>
                <w:tc>
                  <w:p><w:r><w:t>Cell</w:t></w:r></w:p>
                </w:tc>
              </w:tr>
            </w:tbl>""");
        var table = (TableBlock) model.content().getFirst();
        assertEquals(1, table.rows().size());
        assertEquals(1, table.rows().getFirst().cells().size());
    }

    @Test
    void parsesMathElement() throws Exception {
        var model = parseBody("""
            <w:p>
              <m:oMath xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">
                <m:sSup>
                  <m:e><m:r><m:t>x</m:t></m:r></m:e>
                  <m:sup><m:r><m:t>2</m:t></m:r></m:sup>
                </m:sSup>
              </m:oMath>
            </w:p>""");
        var para = (ParagraphBlock) model.content().getFirst();
        var math = (MathElement) para.elements().getFirst();
        assertEquals("x^{2}", math.latex());
    }
}
