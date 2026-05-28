package cn.p4u.smart.renderer;

import cn.p4u.smart.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StyleMapperTest {

    @Test
    void mapsRunStyleToInlineCss() {
        var run = new TextRun("Hello",
                new FontSpec("SimSun", "24", "#FF0000"),
                true, true, true, true,
                "yellow", "CCCCCC",
                true, false, "");
        String css = StyleMapper.runStyle(run);
        assertTrue(css.contains("font-family: SimSun"));
        assertTrue(css.contains("font-size: 12pt"));
        assertTrue(css.contains("color: #FF0000"));
        assertTrue(css.contains("font-weight: bold"));
        assertTrue(css.contains("font-style: italic"));
        assertTrue(css.contains("text-decoration: underline line-through"));
        assertTrue(css.contains("background-color: #FFFF00"));
        assertTrue(css.contains("vertical-align: super"));
        assertTrue(css.contains("font-size: smaller"));
    }

    @Test
    void mapsParagraphStyleToInlineCss() {
        var para = new ParagraphBlock("", "center",
                new Indentation("720", "360", "480"), List.of());
        String css = StyleMapper.paragraphStyle(para);
        assertTrue(css.contains("text-align: center"));
        assertTrue(css.contains("margin-left: 36.0pt"));
        assertTrue(css.contains("margin-right: 18.0pt"));
        assertTrue(css.contains("text-indent: 24.0pt"));
    }

    @Test
    void omitsNullProperties() {
        var run = new TextRun("Hello", new FontSpec(null, null, null),
                false, false, false, false, null, null, false, false, "");
        String css = StyleMapper.runStyle(run);
        assertFalse(css.contains("font-family"));
        assertFalse(css.contains("font-size"));
        assertFalse(css.contains("color"));
    }

    @Test
    void mapsHighlightColor() {
        var run = new TextRun("Hi", new FontSpec(null, null, null),
                false, false, false, false, "green", null, false, false, "");
        String css = StyleMapper.runStyle(run);
        assertTrue(css.contains("background-color: #00FF00"));
    }

    @Test
    void mapsTableVisibility() {
        var table = new TableBlock(List.of(), "200px", "1px", "#000", false);
        String css = StyleMapper.tableStyle(table);
        assertTrue(css.contains("visibility: hidden"));
    }
}
