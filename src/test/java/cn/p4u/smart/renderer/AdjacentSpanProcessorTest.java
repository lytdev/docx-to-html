package cn.p4u.smart.renderer;

import static org.junit.jupiter.api.Assertions.*;
import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.model.*;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class AdjacentSpanProcessorTest {
    @Test
    void mergesWholeSequenceAndRetainsAttributesAndContent() {
        String span = "<span style='color:red' id='caption' class='text'>";
        var doc = Jsoup.parse(AdjacentSpanProcessor.process("<p>" + span + "甲</span>"
                + span + "<b>乙</b>&amp;</span>" + span + "丙</span></p>"));
        assertEquals(1, doc.select("span").size());
        var merged = doc.selectFirst("span");
        assertEquals("color:red", merged.attr("style"));
        assertEquals("caption", merged.id());
        assertEquals("text", merged.className());
        assertEquals("甲乙&丙", merged.text());
        assertEquals("乙", merged.selectFirst("b").text());
    }

    @Test
    void differentAttributesAndInterveningNodesPreventMerge() {
        for (String html : List.of(
                "<span style='a'>甲</span><span style='b'>乙</span>",
                "<span id='a'>甲</span><span id='b'>乙</span>",
                "<span class='a'>甲</span><span class='b'>乙</span>",
                "<span>甲</span><span style=''>乙</span>",
                "<span>甲</span> <span>乙</span>",
                "<span>甲</span>正文<span>乙</span>",
                "<span>甲</span><br><span>乙</span>",
                "<span>甲</span><!--注释--><span>乙</span>",
                "<p><span>甲</span></p><p><span>乙</span></p>")) {
            assertEquals(html, AdjacentSpanProcessor.process(html), html);
        }
    }

    @Test
    void mergesNewlyAdjacentNestedSpansAndIsIdempotent() {
        String html = AdjacentSpanProcessor.process(
                "<p><span><span>甲</span></span><span><span>乙</span></span></p>");
        assertEquals(2, Jsoup.parse(html).select("span").size());
        assertEquals("甲乙", Jsoup.parse(html).selectFirst("p").text());
        assertEquals(html, AdjacentSpanProcessor.process(html));
    }

    @Test
    void rendererMergesConsecutiveTextRuns() {
        var runs = List.<ParagraphElement>of(run("甲"), run("乙"), run("丙"));
        var paragraph = new ParagraphBlock("", null, null, null, runs);
        var model = new DocumentModel(Map.of(), List.of(paragraph), null);
        var doc = Jsoup.parse(HtmlRenderer.render(model, ConversionConfig.defaults()));
        assertEquals(1, doc.select("p > span").size());
        assertEquals("甲乙丙", doc.selectFirst("p > span").text());
    }

    private TextRun run(String text) {
        return new TextRun(text, new FontSpec(null, null, null, null, null),
                false, false, false, false, null, null, false, false, "");
    }
}
