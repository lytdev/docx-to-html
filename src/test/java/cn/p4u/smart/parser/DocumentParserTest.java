package cn.p4u.smart.parser;

import cn.p4u.smart.model.*;
import cn.p4u.smart.util.TestDocxBuilder;
import cn.p4u.smart.extractor.DocxExtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserTest {

    private DocumentModel parseBody(String bodyXml) throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
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

    private DocumentModel parseBodyWithRels(String bodyXml, String relsXml) throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocumentRels(relsXml).addDocument(bodyXml);
            Path docxPath = builder.build();
            Path extracted = DocxExtractor.extract(docxPath);
            try {
                return DocumentParser.parse(extracted);
            } finally {
                DocxExtractor.cleanup(extracted);
            }
        }
    }

    private DocumentModel parseWithStyles(String bodyXml, String stylesXml) throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocument(bodyXml).addStyles(stylesXml);
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
        DocumentModel model = parseBody(
            "<w:p>\n" +
            "  <w:pPr><w:pStyle w:val=\"Normal\"/></w:pPr>\n" +
            "  <w:r><w:rPr><w:rFonts w:ascii=\"SimSun\"/><w:sz w:val=\"24\"/></w:rPr><w:t>Hello World</w:t></w:r>\n" +
            "</w:p>");

        assertEquals(1, model.content().size());
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        assertEquals("Normal", para.styleId());
        TextRun run = (TextRun) para.elements().get(0);
        assertEquals("Hello World", run.text());
        assertEquals("SimSun", run.font().name());
    }

    @Test
    void parsesMultipleParagraphs() throws Exception {
        DocumentModel model = parseBody(
            "<w:p><w:r><w:t>First</w:t></w:r></w:p>\n" +
            "<w:p><w:r><w:t>Second</w:t></w:r></w:p>");
        assertEquals(2, model.content().size());
    }

    @Test
    void parsesRunWithAllProperties() throws Exception {
        DocumentModel model = parseBody(
            "<w:p><w:r><w:rPr>\n" +
            "  <w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\"/>\n" +
            "  <w:sz w:val=\"28\"/>\n" +
            "  <w:color w:val=\"FF0000\"/>\n" +
            "  <w:b/>\n" +
            "  <w:i/>\n" +
            "  <w:u w:val=\"single\"/>\n" +
            "  <w:strike/>\n" +
            "  <w:highlight w:val=\"yellow\"/>\n" +
            "  <w:shd w:fill=\"CCCCCC\"/>\n" +
            "  <w:vertAlign w:val=\"superscript\"/>\n" +
            "</w:rPr><w:t>Bold red</w:t></w:r></w:p>");

        TextRun run = (TextRun) ((ParagraphBlock) model.content().get(0)).elements().get(0);
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
        DocumentModel model = parseBody(
            "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>\n" +
            "  <w:r><w:t>Centered</w:t></w:r></w:p>");
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        assertEquals("center", para.alignment());
    }

    @Test
    void parsesParagraphIndentation() throws Exception {
        DocumentModel model = parseBody(
            "<w:p><w:pPr>\n" +
            "  <w:ind w:left=\"720\" w:right=\"360\" w:firstLine=\"480\"/>\n" +
            "</w:pPr>\n" +
            "  <w:r><w:t>Indented</w:t></w:r></w:p>");
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        assertNotNull(para.indentation());
        assertEquals("720", para.indentation().left());
        assertEquals("360", para.indentation().right());
        assertEquals("480", para.indentation().firstLine());
    }

    @Test
    void parsesTable() throws Exception {
        DocumentModel model = parseBody(
            "<w:tbl>\n" +
            "  <w:tr>\n" +
            "    <w:tc>\n" +
            "      <w:p><w:r><w:t>Cell</w:t></w:r></w:p>\n" +
            "    </w:tc>\n" +
            "  </w:tr>\n" +
            "</w:tbl>");
        TableBlock table = (TableBlock) model.content().get(0);
        assertEquals(1, table.rows().size());
        assertEquals(1, table.rows().get(0).cells().size());
    }

    @Test
    void parsesMathElement() throws Exception {
        DocumentModel model = parseBody(
            "<w:p>\n" +
            "  <m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">\n" +
            "    <m:sSup>\n" +
            "      <m:e><m:r><m:t>x</m:t></m:r></m:e>\n" +
            "      <m:sup><m:r><m:t>2</m:t></m:r></m:sup>\n" +
            "    </m:sSup>\n" +
            "  </m:oMath>\n" +
            "</w:p>");
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        MathElement math = (MathElement) para.elements().get(0);
        assertEquals("x^{2}", math.latex());
    }

    @Test
    void parsesDrawingInsideRun() throws Exception {
        DocumentModel model = parseBodyWithRels(
            "<w:p><w:r><w:drawing>\n" +
            "  <wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"\n" +
            "      xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\">\n" +
            "    <wp:extent cx=\"1609725\" cy=\"1259840\"/>\n" +
            "    <a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">\n" +
            "      <a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n" +
            "        <pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n" +
            "          <pic:nvPicPr>\n" +
            "            <pic:cNvPr id=\"1\" name=\"test.png\"/>\n" +
            "            <pic:cNvPicPr/>\n" +
            "          </pic:nvPicPr>\n" +
            "          <pic:blipFill>\n" +
            "            <a:blip r:embed=\"rId1\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>\n" +
            "          </pic:blipFill>\n" +
            "          <pic:spPr>\n" +
            "            <a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1609725\" cy=\"1259840\"/></a:xfrm>\n" +
            "            <a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>\n" +
            "          </pic:spPr>\n" +
            "        </pic:pic>\n" +
            "      </a:graphicData>\n" +
            "    </a:graphic>\n" +
            "  </wp:inline>\n" +
            "</w:drawing></w:r></w:p>",
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image1.png\"/>");

        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        assertFalse(para.elements().isEmpty(), "Should have found an image element");
        assertTrue(para.elements().get(0) instanceof ImageElement,
                "First element should be ImageElement, got: " + para.elements().get(0).getClass().getSimpleName());
        ImageElement img = (ImageElement) para.elements().get(0);
        assertEquals("media/image1.png", img.mediaPath());
        assertTrue(img.width() > 0, "Width should be set");
        assertTrue(img.height() > 0, "Height should be set");
    }

    @Test
    void parsesOutlineLvlFromParagraph() throws Exception {
        DocumentModel model = parseBody(
            "<w:p><w:pPr><w:outlineLvl w:val=\"1\"/></w:pPr>\n" +
            "  <w:r><w:t>Heading 2</w:t></w:r>\n" +
            "</w:p>");
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        assertEquals(1, para.outlineLvl());
    }

    @Test
    void parsesMathInsideRun() throws Exception {
        // 公式位于 w:r 内部（内联公式），应被正确提取为 MathElement
        DocumentModel model = parseBody(
            "<w:p>\n" +
            "  <w:r><w:rPr><w:rFonts w:ascii=\"宋体\" w:hAnsi=\"宋体\" w:eastAsia=\"宋体\"/></w:rPr>\n" +
            "    <w:t>直线方程：</w:t>\n" +
            "    <m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">\n" +
            "      <m:sSup>\n" +
            "        <m:e><m:r><m:t>x</m:t></m:r></m:e>\n" +
            "        <m:sup><m:r><m:t>2</m:t></m:r></m:sup>\n" +
            "      </m:sSup>\n" +
            "    </m:oMath>\n" +
            "  </w:r>\n" +
            "</w:p>");
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        assertEquals(2, para.elements().size(),
                "Should have text + math elements, got: " + para.elements().size());
        // 第一个元素是 TextRun
        assertTrue(para.elements().get(0) instanceof TextRun,
                "First element should be TextRun");
        // 第二个元素是 MathElement
        assertTrue(para.elements().get(1) instanceof MathElement,
                "Second element should be MathElement, got: " + para.elements().get(1).getClass().getSimpleName());
        MathElement math = (MathElement) para.elements().get(1);
        assertEquals("x^{2}", math.latex());
    }

    @Test
    void parsesMathParagraphWithMultipleFormulas() throws Exception {
        // m:oMathPara 包含多个 m:oMath 子公式，应分别解析为独立的 MathElement
        DocumentModel model = parseBody(
            "<w:p>\n" +
            "  <m:oMathPara xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">\n" +
            "    <m:oMath>\n" +
            "      <m:sSup><m:e><m:r><m:t>x</m:t></m:r></m:e>\n" +
            "        <m:sup><m:r><m:t>2</m:t></m:r></m:sup>\n" +
            "      </m:sSup>\n" +
            "    </m:oMath>\n" +
            "    <m:oMath>\n" +
            "      <m:f><m:num><m:r><m:t>1</m:t></m:r></m:num>\n" +
            "        <m:den><m:r><m:t>2</m:t></m:r></m:den>\n" +
            "      </m:f>\n" +
            "    </m:oMath>\n" +
            "  </m:oMathPara>\n" +
            "</w:p>");
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        assertEquals(2, para.elements().size(),
                "oMathPara with 2 oMath children should produce 2 MathElements, got: " + para.elements().size());
        MathElement first = (MathElement) para.elements().get(0);
        MathElement second = (MathElement) para.elements().get(1);
        assertEquals("x^{2}", first.latex());
        assertEquals("\\frac{1}{2}", second.latex());
    }

    private DocumentModel parseBodyWithStyles(String bodyXml, String stylesXml) throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addStyles(stylesXml).addDocument(bodyXml);
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
    void fallsBackToDocDefaultsForMissingAsciiFont() throws Exception {
        String stylesXml =
            "<w:docDefaults>" +
            "  <w:rPrDefault><w:rPr>" +
            "    <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" w:eastAsia=\"宋体\" w:cs=\"Times New Roman\"/>" +
            "  </w:rPr></w:rPrDefault>" +
            "  <w:pPrDefault/>" +
            "</w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Normal\">" +
            "  <w:name w:val=\"Normal\"/>" +
            "</w:style>";

        // Run with hint=eastAsia but no explicit ascii — should fallback to docDefaults
        // CSS output: font-family: 'Times New Roman', '宋体' is correct (Latin→TNR, CJK→宋体)
        String bodyXml =
            "<w:p><w:pPr><w:pStyle w:val=\"Normal\"/></w:pPr>" +
            "  <w:r><w:rPr><w:rFonts w:hint=\"eastAsia\"/></w:rPr><w:t>测试</w:t></w:r>" +
            "</w:p>";

        DocumentModel model = parseBodyWithStyles(bodyXml, stylesXml);
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        TextRun run = (TextRun) para.elements().get(0);
        assertNotNull(run.font(), "Font should not be null when docDefaults is present");
        assertEquals("Times New Roman", run.font().name(), "Should fallback to docDefaults ascii font");
        assertEquals("宋体", run.font().eastAsia(), "Should fallback to docDefaults eastAsia font");
    }

    @Test
    void fallsBackToDocDefaultsForAsciiWhenNoHint() throws Exception {
        String stylesXml =
            "<w:docDefaults>" +
            "  <w:rPrDefault><w:rPr>" +
            "    <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" w:eastAsia=\"宋体\" w:cs=\"Times New Roman\"/>" +
            "  </w:rPr></w:rPrDefault>" +
            "  <w:pPrDefault/>" +
            "</w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Normal\">" +
            "  <w:name w:val=\"Normal\"/>" +
            "</w:style>";

        // Run with no rFonts at all — should fallback to docDefaults entirely
        String bodyXml =
            "<w:p><w:pPr><w:pStyle w:val=\"Normal\"/></w:pPr>" +
            "  <w:r><w:t>Hello</w:t></w:r>" +
            "</w:p>";

        DocumentModel model = parseBodyWithStyles(bodyXml, stylesXml);
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        TextRun run = (TextRun) para.elements().get(0);
        assertNotNull(run.font(), "Font should not be null when docDefaults is present");
        assertEquals("Times New Roman", run.font().name(), "Should fallback to docDefaults ascii font");
        assertEquals("宋体", run.font().eastAsia(), "Should fallback to docDefaults eastAsia font");
    }

    @Test
    void hintEastAsiaPromotesEastAsiaFontWhenNoAsciiAvailable() throws Exception {
        // No docDefaults — only an eastAsia font specified inline
        String stylesXml =
            "<w:style w:type=\"paragraph\" w:styleId=\"Normal\">" +
            "  <w:name w:val=\"Normal\"/>" +
            "</w:style>";

        String bodyXml =
            "<w:p><w:pPr><w:pStyle w:val=\"Normal\"/></w:pPr>" +
            "  <w:r><w:rPr><w:rFonts w:hint=\"eastAsia\" w:eastAsia=\"宋体\"/></w:rPr><w:t>测试</w:t></w:r>" +
            "</w:p>";

        DocumentModel model = parseBodyWithStyles(bodyXml, stylesXml);
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        TextRun run = (TextRun) para.elements().get(0);
        assertNotNull(run.font(), "Font should not be null");
        assertEquals("宋体", run.font().name(), "hint=eastAsia should promote eastAsia font to primary when no ascii");
        assertNull(run.font().eastAsia(), "eastAsia slot should be null after promotion");
    }

    @Test
    void parsesDrawingShapeFromAlternateContent() throws Exception {
        DocumentModel model = parseBody(
            "<w:p><w:r><mc:AlternateContent>\n" +
            "  <mc:Choice Requires=\"wps\">\n" +
            "    <w:drawing><wp:inline><wp:extent cx=\"200000\" cy=\"100000\"/>\n" +
            "      <a:graphic>\n" +
            "        <a:graphicData uri=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\">\n" +
            "          <wps:wsp>\n" +
            "            <wps:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"200000\" cy=\"100000\"/></a:xfrm>\n" +
            "              <a:prstGeom prst=\"parallelogram\"><a:avLst/></a:prstGeom>\n" +
            "              <a:solidFill><a:srgbClr val=\"5B9BD5\"/></a:solidFill>\n" +
            "              <a:ln w=\"12700\"><a:solidFill><a:srgbClr val=\"2E75B6\"/></a:solidFill></a:ln>\n" +
            "            </wps:spPr>\n" +
            "          </wps:wsp>\n" +
            "        </a:graphicData>\n" +
            "      </a:graphic>\n" +
            "    </wp:inline></w:drawing>\n" +
            "  </mc:Choice>\n" +
            "  <mc:Fallback><w:r><w:t>fallback</w:t></w:r></mc:Fallback>\n" +
            "</mc:AlternateContent></w:r></w:p>");
        ParagraphBlock para = (ParagraphBlock) model.content().get(0);
        assertFalse(para.elements().isEmpty(), "Should have found a shape element");
        assertTrue(para.elements().get(0) instanceof ShapeElement,
                "First element should be ShapeElement, got: " + para.elements().get(0).getClass().getSimpleName());
        ShapeElement shape = (ShapeElement) para.elements().get(0);
        assertEquals("parallelogram", shape.preset());
        assertEquals(200000, shape.width());
        assertEquals(100000, shape.height());
        assertEquals("#5B9BD5", shape.fillColor());
        assertEquals("#2E75B6", shape.strokeColor());
    }

    @Test
    void tableCellsWithInsideNoneHaveNoBorder() throws Exception {
        // Table with outer borders but insideH/insideV val="none" — cells should have no border
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblBorders>" +
            "      <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:insideH w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>" +
            "      <w:insideV w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>" +
            "    </w:tblBorders>" +
            "  </w:tblPr>" +
            "  <w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>" +
            "            <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseBody(bodyXml);
        TableBlock table = (TableBlock) model.content().get(0);
        // Table outer border should be preserved
        assertTrue(table.topBorder().hasBorder(), "Table should have top border");
        assertEquals("4", table.topBorder().width());
        assertTrue(table.leftBorder().hasBorder(), "Table should have left border");
        assertEquals("4", table.leftBorder().width());
        assertTrue(table.bottomBorder().hasBorder(), "Table should have bottom border");
        assertEquals("4", table.bottomBorder().width());
        assertTrue(table.rightBorder().hasBorder(), "Table should have right border");
        assertEquals("4", table.rightBorder().width());
        // insideH/insideV are explicitly val="none", so they are BorderSpec.NONE (no fallback)
        assertFalse(table.insideHBorder().hasBorder(), "insideH should have no border when val=none");
        assertFalse(table.insideVBorder().hasBorder(), "insideV should have no border when val=none");
        // Cells should have no border (insideH/insideV = none, cell is first row first col so uses inside borders)
        TableCell cell = table.rows().get(0).cells().get(0);
        // First row cell top uses table.topBorder, but insideH/insideV=none means inner sides have no border
        // For a 2-column single-row table: cell is both first row AND last row
        // top=table.top(sz=4), bottom=table.bottom(sz=4) [last row], left=table.left(sz=4), right=insideV(none)
        assertTrue(cell.topBorder().hasBorder(), "First row cell top should use table top border");
        assertEquals("4", cell.topBorder().width());
        assertTrue(cell.bottomBorder().hasBorder(), "Last row cell bottom should use table bottom border");
        assertEquals("4", cell.bottomBorder().width());
        assertTrue(cell.leftBorder().hasBorder(), "First col cell left should use table left border");
        assertEquals("4", cell.leftBorder().width());
        assertFalse(cell.rightBorder().hasBorder(), "First col cell right should use insideV (none)");
        // Second cell: first-row, last-row, last-col
        TableCell cell2 = table.rows().get(0).cells().get(1);
        assertTrue(cell2.topBorder().hasBorder(), "Second cell top should use table top border");
        assertEquals("4", cell2.topBorder().width());
        assertTrue(cell2.bottomBorder().hasBorder(), "Second cell bottom should use table bottom border");
        assertEquals("4", cell2.bottomBorder().width());
        assertFalse(cell2.leftBorder().hasBorder(), "Last col cell left should use insideV (none)");
        assertTrue(cell2.rightBorder().hasBorder(), "Last col cell right should use table right border");
        assertEquals("4", cell2.rightBorder().width());
    }

    @Test
    void tableCellsWithInsideBorderInheritIt() throws Exception {
        // Table with insideH val="single" sz="4" — cells should inherit inside border
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblBorders>" +
            "      <w:top w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:left w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:right w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"FF0000\"/>" +
            "      <w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"FF0000\"/>" +
            "    </w:tblBorders>" +
            "  </w:tblPr>" +
            "  <w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseBody(bodyXml);
        TableBlock table = (TableBlock) model.content().get(0);
        // Table outer border is sz=8
        assertTrue(table.topBorder().hasBorder(), "Table should have top border");
        assertEquals("8", table.topBorder().width(), "Table top border should be sz=8");
        assertTrue(table.leftBorder().hasBorder(), "Table should have left border");
        assertEquals("8", table.leftBorder().width(), "Table left border should be sz=8");
        assertTrue(table.bottomBorder().hasBorder(), "Table should have bottom border");
        assertEquals("8", table.bottomBorder().width(), "Table bottom border should be sz=8");
        assertTrue(table.rightBorder().hasBorder(), "Table should have right border");
        assertEquals("8", table.rightBorder().width(), "Table right border should be sz=8");
        // insideH/insideV borders are sz=4
        assertTrue(table.insideHBorder().hasBorder(), "Table should have insideH border");
        assertEquals("4", table.insideHBorder().width(), "insideH border should be sz=4");
        assertTrue(table.insideVBorder().hasBorder(), "Table should have insideV border");
        assertEquals("4", table.insideVBorder().width(), "insideV border should be sz=4");
        // Single row, single col cell: it IS top row, bottom row, left col, right col all at once
        // So all four sides use outer borders (sz=8), not inside borders
        TableCell cell = table.rows().get(0).cells().get(0);
        assertTrue(cell.topBorder().hasBorder(), "Cell top should have border");
        assertEquals("8", cell.topBorder().width(), "Cell top should use table top border (sz=8)");
        assertTrue(cell.bottomBorder().hasBorder(), "Cell bottom should have border");
        assertEquals("8", cell.bottomBorder().width(), "Cell bottom should use table bottom border (sz=8)");
        assertTrue(cell.leftBorder().hasBorder(), "Cell left should have border");
        assertEquals("8", cell.leftBorder().width(), "Cell left should use table left border (sz=8)");
        assertTrue(cell.rightBorder().hasBorder(), "Cell right should have border");
        assertEquals("8", cell.rightBorder().width(), "Cell right should use table right border (sz=8)");
    }

    @Test
    void tableCellsFallBackToOuterBorderWhenInsideAbsent() throws Exception {
        // Table with outer borders but no insideH/insideV elements at all — cells fallback to outer
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblBorders>" +
            "      <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "    </w:tblBorders>" +
            "  </w:tblPr>" +
            "  <w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseBody(bodyXml);
        TableBlock table = (TableBlock) model.content().get(0);
        // insideH/insideV absent -> fallback to outer borders on each side
        TableCell cell = table.rows().get(0).cells().get(0);
        // Single row, single col cell: all sides use outer borders (sz=4)
        assertTrue(cell.topBorder().hasBorder(), "Cell top should fallback to outer border when insideH/insideV absent");
        assertEquals("4", cell.topBorder().width());
        assertTrue(cell.bottomBorder().hasBorder(), "Cell bottom should fallback to outer border");
        assertEquals("4", cell.bottomBorder().width());
        assertTrue(cell.leftBorder().hasBorder(), "Cell left should fallback to outer border");
        assertEquals("4", cell.leftBorder().width());
        assertTrue(cell.rightBorder().hasBorder(), "Cell right should fallback to outer border");
        assertEquals("4", cell.rightBorder().width());
    }

    @Test
    void tableWithNoBordersHasNoneOnTableAndCells() throws Exception {
        // Table with no tblBorders at all — both table and cells should have no border
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblW w:w=\"5000\" w:type=\"pct\"/>" +
            "  </w:tblPr>" +
            "  <w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>" +
            "            <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseBody(bodyXml);
        TableBlock table = (TableBlock) model.content().get(0);
        assertFalse(table.topBorder().hasBorder(), "Table with no tblBorders should have no top border");
        assertFalse(table.leftBorder().hasBorder(), "Table with no tblBorders should have no left border");
        assertFalse(table.bottomBorder().hasBorder(), "Table with no tblBorders should have no bottom border");
        assertFalse(table.rightBorder().hasBorder(), "Table with no tblBorders should have no right border");
        TableCell cell = table.rows().get(0).cells().get(0);
        assertFalse(cell.topBorder().hasBorder(), "Cell in borderless table should have no top border");
        assertFalse(cell.leftBorder().hasBorder(), "Cell in borderless table should have no left border");
        assertFalse(cell.bottomBorder().hasBorder(), "Cell in borderless table should have no bottom border");
        assertFalse(cell.rightBorder().hasBorder(), "Cell in borderless table should have no right border");
    }

    @Test
    void tableWithAllNoneBordersHasNoneOnTableAndCells() throws Exception {
        // Table with tblBorders where all sides are val="none"
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblBorders>" +
            "      <w:top w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>" +
            "      <w:left w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>" +
            "      <w:bottom w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>" +
            "      <w:right w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>" +
            "      <w:insideH w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>" +
            "      <w:insideV w:val=\"none\" w:sz=\"0\" w:space=\"0\" w:color=\"auto\"/>" +
            "    </w:tblBorders>" +
            "  </w:tblPr>" +
            "  <w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>" +
            "            <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseBody(bodyXml);
        TableBlock table = (TableBlock) model.content().get(0);
        assertFalse(table.topBorder().hasBorder(), "Table with all-none borders should have no top border");
        assertFalse(table.leftBorder().hasBorder(), "Table with all-none borders should have no left border");
        assertFalse(table.bottomBorder().hasBorder(), "Table with all-none borders should have no bottom border");
        assertFalse(table.rightBorder().hasBorder(), "Table with all-none borders should have no right border");
        TableCell cell = table.rows().get(0).cells().get(0);
        assertFalse(cell.topBorder().hasBorder(), "Cell with explicit none borders should have no top border");
        assertFalse(cell.leftBorder().hasBorder(), "Cell with explicit none borders should have no left border");
        assertFalse(cell.bottomBorder().hasBorder(), "Cell with explicit none borders should have no bottom border");
        assertFalse(cell.rightBorder().hasBorder(), "Cell with explicit none borders should have no right border");
    }

    @Test
    void tableInheritsBordersFromTableStyle() throws Exception {
        // Table with no inline tblBorders but referencing a table style that defines borders
        String stylesXml =
            "<w:styles>" +
            "  <w:style w:type=\"table\" w:styleId=\"TableGrid\">" +
            "    <w:name w:val=\"Table Grid\"/>" +
            "    <w:tblPr>" +
            "      <w:tblBorders>" +
            "        <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "        <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "        <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "        <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "        <w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "        <w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      </w:tblBorders>" +
            "    </w:tblPr>" +
            "  </w:style>" +
            "</w:styles>";
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblStyle w:val=\"TableGrid\"/>" +
            "    <w:tblW w:w=\"5000\" w:type=\"pct\"/>" +
            "  </w:tblPr>" +
            "  <w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseWithStyles(bodyXml, stylesXml);
        TableBlock table = (TableBlock) model.content().get(0);
        assertTrue(table.topBorder().hasBorder(), "Table should inherit top border from table style");
        assertEquals("4", table.topBorder().width());
        assertTrue(table.leftBorder().hasBorder(), "Table should inherit left border from table style");
        assertEquals("4", table.leftBorder().width());
        assertTrue(table.bottomBorder().hasBorder(), "Table should inherit bottom border from table style");
        assertEquals("4", table.bottomBorder().width());
        assertTrue(table.rightBorder().hasBorder(), "Table should inherit right border from table style");
        assertEquals("4", table.rightBorder().width());
        TableCell cell = table.rows().get(0).cells().get(0);
        // Single row, single col cell: all sides use outer borders
        assertTrue(cell.topBorder().hasBorder(), "Cell should have border from table style");
        assertEquals("4", cell.topBorder().width(), "Cell top should use table top border from style");
        assertTrue(cell.bottomBorder().hasBorder(), "Cell bottom should have border");
        assertEquals("4", cell.bottomBorder().width(), "Cell bottom should use table bottom border from style");
        assertTrue(cell.leftBorder().hasBorder(), "Cell left should have border");
        assertEquals("4", cell.leftBorder().width(), "Cell left should use table left border from style");
        assertTrue(cell.rightBorder().hasBorder(), "Cell right should have border");
        assertEquals("4", cell.rightBorder().width(), "Cell right should use table right border from style");
    }

    @Test
    void tableStyleWithNoBordersMakesBorderlessTable() throws Exception {
        // Table referencing a style with no tblBorders — table and cells should be borderless
        String stylesXml =
            "<w:styles>" +
            "  <w:style w:type=\"table\" w:styleId=\"PlainTable\">" +
            "    <w:name w:val=\"Plain Table\"/>" +
            "    <w:tblPr>" +
            "      <w:tblW w:w=\"0\" w:type=\"auto\"/>" +
            "    </w:tblPr>" +
            "  </w:style>" +
            "</w:styles>";
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblStyle w:val=\"PlainTable\"/>" +
            "  </w:tblPr>" +
            "  <w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseWithStyles(bodyXml, stylesXml);
        TableBlock table = (TableBlock) model.content().get(0);
        assertFalse(table.topBorder().hasBorder(), "Table with style having no borders should have no top border");
        assertFalse(table.leftBorder().hasBorder(), "Table with style having no borders should have no left border");
        assertFalse(table.bottomBorder().hasBorder(), "Table with style having no borders should have no bottom border");
        assertFalse(table.rightBorder().hasBorder(), "Table with style having no borders should have no right border");
        TableCell cell = table.rows().get(0).cells().get(0);
        assertFalse(cell.topBorder().hasBorder(), "Cell in table with style having no borders should have no top border");
        assertFalse(cell.leftBorder().hasBorder(), "Cell in table with style having no borders should have no left border");
        assertFalse(cell.bottomBorder().hasBorder(), "Cell in table with style having no borders should have no bottom border");
        assertFalse(cell.rightBorder().hasBorder(), "Cell in table with style having no borders should have no right border");
    }

    @Test
    void tableCellTcBordersNilSuppressesBorder() throws Exception {
        // Table with outer borders + insideV, but a specific cell uses
        // tcBorders val="nil" to suppress the left border.
        // This is the pattern used in demo.docx where SR2,NR3 has no left border.
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblBorders>" +
            "      <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "    </w:tblBorders>" +
            "  </w:tblPr>" +
            "  <w:tr>" +
            "    <w:tc><w:tcPr><w:tcBorders><w:left w:val=\"nil\"/></w:tcBorders></w:tcPr>" +
            "        <w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>" +
            "    <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>" +
            "  </w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseBody(bodyXml);
        TableBlock table = (TableBlock) model.content().get(0);

        // Cell A is in the left column, so its default left would be tblLeft,
        // but tcBorders left=nil should suppress it to NONE.
        TableCell cellA = table.rows().get(0).cells().get(0);
        assertFalse(cellA.leftBorder().hasBorder(),
                "Cell A tcBorders left=nil should suppress left border");
        assertTrue(cellA.topBorder().hasBorder(),
                "Cell A should still have top border from tblTop");
        assertTrue(cellA.bottomBorder().hasBorder(),
                "Cell A should still have bottom border from insideH");
        assertTrue(cellA.rightBorder().hasBorder(),
                "Cell A should still have right border from insideV");

        // Cell B is unaffected — still has its default borders.
        TableCell cellB = table.rows().get(0).cells().get(1);
        assertTrue(cellB.leftBorder().hasBorder(),
                "Cell B should have left border from insideV");
        assertTrue(cellB.rightBorder().hasBorder(),
                "Cell B right column should have right border from tblRight");
    }

    @Test
    void tableCellTcBordersNilSuppressesInteriorBorder() throws Exception {
        // Interior cell uses tcBorders val="nil" to suppress right border,
        // and adjacent cell also suppresses left border — simulating SR1,NR4 pattern.
        String bodyXml =
            "<w:tbl>" +
            "  <w:tblPr>" +
            "    <w:tblBorders>" +
            "      <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "      <w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>" +
            "    </w:tblBorders>" +
            "  </w:tblPr>" +
            "  <w:tr>" +
            "    <w:tc><w:tcPr><w:tcBorders><w:right w:val=\"nil\"/></w:tcBorders></w:tcPr>" +
            "        <w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>" +
            "    <w:tc><w:tcPr><w:tcBorders><w:left w:val=\"nil\"/></w:tcBorders></w:tcPr>" +
            "        <w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>" +
            "    <w:tc><w:p><w:r><w:t>C</w:t></w:r></w:p></w:tc>" +
            "  </w:tr>" +
            "</w:tbl>";
        DocumentModel model = parseBody(bodyXml);
        TableBlock table = (TableBlock) model.content().get(0);

        // Cell A: right=nil should suppress the insideV border on its right
        TableCell cellA = table.rows().get(0).cells().get(0);
        assertFalse(cellA.rightBorder().hasBorder(),
                "Cell A tcBorders right=nil should suppress right border");
        assertTrue(cellA.leftBorder().hasBorder(),
                "Cell A should still have left border from tblLeft");

        // Cell B: left=nil should suppress the insideV border on its left
        TableCell cellB = table.rows().get(0).cells().get(1);
        assertFalse(cellB.leftBorder().hasBorder(),
                "Cell B tcBorders left=nil should suppress left border");
        assertTrue(cellB.rightBorder().hasBorder(),
                "Cell B should have right border from insideV");

        // Cell C: unaffected
        TableCell cellC = table.rows().get(0).cells().get(2);
        assertTrue(cellC.leftBorder().hasBorder(),
                "Cell C should have left border from insideV");
        assertTrue(cellC.rightBorder().hasBorder(),
                "Cell C right col should have right border from tblRight");
    }
}
