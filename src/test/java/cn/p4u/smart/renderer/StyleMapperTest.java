package cn.p4u.smart.renderer;

import cn.p4u.smart.model.*;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class StyleMapperTest {

    @Test
    void mapsRunStyleToInlineCss() {
        TextRun run = new TextRun("Hello",
                new FontSpec("SimSun", "24", "#FF0000", null, null),
                true, true, true, true,
                "yellow", "CCCCCC",
                true, false, "");
        String css = StyleMapper.runStyle(run);
        assertTrue(css.contains("font-family: 'SimSun'"));
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
        ParagraphBlock para = new ParagraphBlock("", null, "center",
                new Indentation("720", "360", "480"), Collections.<ParagraphElement>emptyList());
        String css = StyleMapper.paragraphStyle(para);
        assertTrue(css.contains("text-align: center"));
        assertTrue(css.contains("margin-left: 36.0pt"));
        assertTrue(css.contains("margin-right: 18.0pt"));
        assertTrue(css.contains("text-indent: 24.0pt"));
    }

    @Test
    void omitsNullProperties() {
        TextRun run = new TextRun("Hello", new FontSpec(null, null, null, null, null),
                false, false, false, false, null, null, false, false, "");
        String css = StyleMapper.runStyle(run);
        assertFalse(css.contains("font-family"));
        assertFalse(css.contains("font-size"));
        assertFalse(css.contains("color"));
    }

    @Test
    void mapsHighlightColor() {
        TextRun run = new TextRun("Hi", new FontSpec(null, null, null, null, null),
                false, false, false, false, "green", null, false, false, "");
        String css = StyleMapper.runStyle(run);
        assertTrue(css.contains("background-color: #00FF00"));
    }

    @Test
    void mapsTableVisibility() {
        TableBlock table = new TableBlock(Collections.<TableRow>emptyList(), "200px",
                new BorderSpec("8", "000000"), new BorderSpec("8", "000000"),
                new BorderSpec("8", "000000"), new BorderSpec("8", "000000"),
                BorderSpec.NONE, BorderSpec.NONE, false);
        String css = StyleMapper.tableStyle(table);
        assertTrue(css.contains("visibility: hidden"));
    }

    @Test
    void mapsEastAsianFontFallback() {
        TextRun run = new TextRun("Hello",
                new FontSpec("Calibri", null, null, "宋体", null),
                false, false, false, false, null, null, false, false, "");
        String css = StyleMapper.runStyle(run);
        assertTrue(css.contains("font-family: 'Calibri', '宋体'"),
                "Expected East-Asian font fallback, got: " + css);
    }

    @Test
    void mapsOnlyEastAsianFontWhenLatinMissing() {
        TextRun run = new TextRun("Hello",
                new FontSpec(null, null, null, "宋体", null),
                false, false, false, false, null, null, false, false, "");
        String css = StyleMapper.runStyle(run);
        assertTrue(css.contains("font-family: '宋体'"),
                "Expected only East-Asian font, got: " + css);
    }
}
