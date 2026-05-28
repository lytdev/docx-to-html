package cn.p4u.smart.renderer;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HtmlRendererTest {

    @Test
    void rendersParagraphWithStyledRun() {
        var run = new TextRun("Hello",
                new FontSpec("SimSun", "24", "#000000"),
                true, false, false, false, null, null, false, false, "");
        var para = new ParagraphBlock("", "left", null, List.of(run));
        var model = new DocumentModel(Map.of(), List.of(para));

        String html = HtmlRenderer.render(model, ConversionConfig.base64Defaults());
        assertTrue(html.contains("<p"));
        assertTrue(html.contains("font-family: SimSun"));
        assertTrue(html.contains("font-weight: bold"));
        assertTrue(html.contains("font-size: 12pt"));
        assertTrue(html.contains("Hello</span>"));
    }

    @Test
    void rendersHyperlink() {
        var run = new TextRun("click",
                new FontSpec(null, null, null),
                false, false, false, false, null, null, false, false, "");
        var link = new HyperlinkElement("https://example.com", List.of(run));
        var para = new ParagraphBlock("", null, null, List.of(link));
        var model = new DocumentModel(Map.of(), List.of(para));

        String html = HtmlRenderer.render(model, ConversionConfig.base64Defaults());
        assertTrue(html.contains("href=\"https://example.com\""));
        assertTrue(html.contains("click</span></a>"));
        assertTrue(html.contains("color: #0563C1"));
    }

    @Test
    void rendersTable() {
        var run = new TextRun("cell",
                new FontSpec(null, null, null),
                false, false, false, false, null, null, false, false, "");
        var cellPara = new ParagraphBlock("", null, null, List.of(run));
        var cell = new TableCell(List.of(cellPara), 1, 1, null, null, null, null, true);
        var row = new TableRow(List.of(cell), null);
        var table = new TableBlock(List.of(row), null, null, null, true);
        var model = new DocumentModel(Map.of(), List.of(table));

        String html = HtmlRenderer.render(model, ConversionConfig.base64Defaults());
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("border-collapse: collapse"));
        assertTrue(html.contains("<td"));
        assertTrue(html.contains("cell</span>"));
    }

    @Test
    void rendersMathWithDataLatex() {
        var math = new MathElement("E=mc^2", null, null);
        var para = new ParagraphBlock("", null, null, List.of(math));
        var model = new DocumentModel(Map.of(), List.of(para));

        String html = HtmlRenderer.render(model, ConversionConfig.base64Defaults());
        assertTrue(html.contains("data-latex=\"E=mc^2\""));
    }

    @Test
    void rendersHiddenTable() {
        var cellPara = new ParagraphBlock("", null, null, List.of());
        var cell = new TableCell(List.of(cellPara), 1, 1, null, null, null, null, false);
        var row = new TableRow(List.of(cell), null);
        var table = new TableBlock(List.of(row), null, null, null, false);
        var model = new DocumentModel(Map.of(), List.of(table));

        String html = HtmlRenderer.render(model, ConversionConfig.base64Defaults());
        assertTrue(html.contains("visibility: hidden"));
    }

    @Test
    void outputIsCompleteHtmlDocument() {
        var run = new TextRun("Test",
                new FontSpec(null, null, null),
                false, false, false, false, null, null, false, false, "");
        var para = new ParagraphBlock("", null, null, List.of(run));
        var model = new DocumentModel(Map.of(), List.of(para));

        String html = HtmlRenderer.render(model, ConversionConfig.base64Defaults());
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<html>"));
        assertTrue(html.contains("</html>"));
        assertTrue(html.contains("<meta charset=\"UTF-8\">"));
    }
}
