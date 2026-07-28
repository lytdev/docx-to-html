package cn.p4u.smart.renderer;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.model.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HtmlRendererTest {

    @Test
    void rendersParagraphWithStyledRun() {
        TextRun run = new TextRun("Hello",
                new FontSpec("SimSun", "24", "#000000", null, null),
                true, false, false, false, null, null, false, false, "");
        ParagraphBlock para = new ParagraphBlock("", null, "left", null, Arrays.asList(run));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("<p"));
        assertTrue(html.contains("font-family: 'SimSun'"));
        assertTrue(html.contains("font-weight: bold"));
        assertTrue(html.contains("font-size: 12pt"));
        assertTrue(html.contains("Hello</span>"));
    }

    @Test
    void rendersHyperlink() {
        TextRun run = new TextRun("click",
                new FontSpec(null, null, null, null, null),
                false, false, false, false, null, null, false, false, "");
        HyperlinkElement link = new HyperlinkElement("https://example.com", Arrays.asList(run));
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(link));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("href=\"https://example.com\""));
        assertTrue(html.contains("click</span></a>"));
        assertTrue(html.contains("color: #0563C1"));
    }

    @Test
    void rendersTable() {
        TextRun run = new TextRun("cell",
                new FontSpec(null, null, null, null, null),
                false, false, false, false, null, null, false, false, "");
        ParagraphBlock cellPara = new ParagraphBlock("", null, null, null, Arrays.asList(run));
        TableCell cell = new TableCell(Arrays.asList(cellPara), 1, 1, null,
                BorderSpec.NONE, BorderSpec.NONE, BorderSpec.NONE, BorderSpec.NONE, null, true);
        TableRow row = new TableRow(Arrays.asList(cell), null, null);
        TableBlock table = new TableBlock(Arrays.asList(row), null,
                BorderSpec.NONE, BorderSpec.NONE, BorderSpec.NONE, BorderSpec.NONE,
                BorderSpec.NONE, BorderSpec.NONE, true);
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(table), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("<table"));
        // 无边框表格应输出 border: none 而非 border-collapse
        assertTrue(html.contains("border: none"));
        assertTrue(html.contains("<td"));
        assertTrue(html.contains("cell</span>"));
    }

    @Test
    void rendersMathWithDataLatex() {
        MathElement math = new MathElement("E=mc^2", null, null);
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(math));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("data-latex=\"E=mc^2\""));
    }

    @Test
    void rendersHiddenTable() {
        ParagraphBlock cellPara = new ParagraphBlock("", null, null, null, Collections.<ParagraphElement>emptyList());
        TableCell cell = new TableCell(Arrays.asList(cellPara), 1, 1, null,
                BorderSpec.NONE, BorderSpec.NONE, BorderSpec.NONE, BorderSpec.NONE, null, false);
        TableRow row = new TableRow(Arrays.asList(cell), null, null);
        TableBlock table = new TableBlock(Arrays.asList(row), null,
                BorderSpec.NONE, BorderSpec.NONE, BorderSpec.NONE, BorderSpec.NONE,
                BorderSpec.NONE, BorderSpec.NONE, false);
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(table), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("visibility: hidden"));
    }

    @Test
    void outputIsCompleteHtmlDocument() {
        TextRun run = new TextRun("Test",
                new FontSpec(null, null, null, null, null),
                false, false, false, false, null, null, false, false, "");
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(run));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<html>"));
        assertTrue(html.contains("</html>"));
        assertTrue(html.contains("<meta charset=\"UTF-8\">"));
    }

    @Test
    void rendersHeadingViaOutlineLvl() {
        TextRun run = new TextRun("Title",
                new FontSpec(null, null, null, null, null),
                true, false, false, false, null, null, false, false, "");
        ParagraphBlock para = new ParagraphBlock("", 0, null, null, Arrays.asList(run));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("<h1>"));
    }

    @Test
    void rendersHeadingViaStyleBasedOnChain() {
        StyleDef headingStyle = new StyleDef("Heading1", "heading 1", null, 0,
                Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(),
                Collections.<String, Map<String, String>>emptyMap(), Collections.<String, Map<String, String>>emptyMap(),
                Collections.<String, Map<String, String>>emptyMap());
        StyleDef customStyle = new StyleDef("MyTitle", "My Title", "Heading1", null,
                Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(),
                Collections.<String, Map<String, String>>emptyMap(), Collections.<String, Map<String, String>>emptyMap(),
                Collections.<String, Map<String, String>>emptyMap());
        TextRun run = new TextRun("Custom Heading",
                new FontSpec(null, null, null, null, null),
                true, false, false, false, null, null, false, false, "");
        ParagraphBlock para = new ParagraphBlock("MyTitle", null, null, null, Arrays.asList(run));
        Map<String, StyleDef> stylesMap = new HashMap<>();
        stylesMap.put("Heading1", headingStyle);
        stylesMap.put("MyTitle", customStyle);
        DocumentModel model = new DocumentModel(stylesMap, Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("<h1>"), "Expected <h1> via basedOn chain, got: " + html);
    }

    @Test
    void rendersEastAsianFontFallback() {
        TextRun run = new TextRun("你好",
                new FontSpec("Calibri", "24", "#000000", "宋体", null),
                false, false, false, false, null, null, false, false, "");
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(run));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        // CJK text "你好" → East-Asian font primary
        assertTrue(html.contains("font-family: '宋体', 'Calibri'"),
                "Expected East-Asian font primary for CJK text, got: " + html);
    }

    @Test
    void rendersSingleSvgShape() {
        ShapeElement shape = new ShapeElement("parallelogram", 300000, 100000,
                "#5B9BD5", "#2E75B6", 1.0f, WrapMode.INLINE);
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(shape));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("<svg"), "Expected <svg> element, got: " + html);
        assertTrue(html.contains("width=\"31\""), "Expected width from EMU->px, got: " + html);
        assertTrue(html.contains("fill=\"#5B9BD5\""), "Expected fill color, got: " + html);
        assertTrue(html.contains("stroke=\"#2E75B6\""), "Expected stroke color, got: " + html);
    }

    @Test
    void rendersShapeGroupWithChildren() {
        // Child width/height and chExt are in the group's child coordinate system,
        // NOT in EMU. Group width/height are in EMU.
        // Child occupies half the chExt -> half the group pixel width
        ShapeElement child1 = new ShapeElement("rect", 200000, 100000,
                "#FF0000", "#000", 1.0f, WrapMode.INLINE);
        ShapeElement group = new ShapeElement("group", 400000, 200000,
                0, 0, null, null, 0, WrapMode.INLINE,
                0, 0, 400000, 200000, Arrays.asList(child1));
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(group));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("<svg"), "Expected <svg> element, got: " + html);
        assertTrue(html.contains("<path"), "Expected <path> for child shape, got: " + html);
        // Verify child is rendered (group width = 400000 EMU -> 42px)
        int groupW = 400000 / 9525;
        assertTrue(html.contains("width=\"" + groupW + "\""), "Group SVG width, got: " + html);
    }

    @Test
    void rendersShapeWithFloatLeft() {
        ShapeElement shape = new ShapeElement("ellipse", 200000, 200000,
                "#00FF00", "#000", 1.0f, WrapMode.LEFT);
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(shape));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("float: left"), "Expected float:left for LEFT wrap, got: " + html);
    }

    @Test
    void ellipseShapeRendersWithArcPath() {
        ShapeElement shape = new ShapeElement("ellipse", 200000, 100000,
                "#FF0000", "#000", 1.0f, WrapMode.INLINE);
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(shape));
        DocumentModel model = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        String html = HtmlRenderer.render(model, ConversionConfig.defaults());
        assertTrue(html.contains("A"), "Ellipse should use arc (A) command, got: " + html);
    }
}
