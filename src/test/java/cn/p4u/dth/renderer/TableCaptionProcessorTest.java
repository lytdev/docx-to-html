package cn.p4u.dth.renderer;

import static org.junit.jupiter.api.Assertions.*;

import cn.p4u.dth.converter.DocxConverter;
import cn.p4u.dth.util.TestDocxBuilder;
import java.nio.file.Files;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

class TableCaptionProcessorTest {
    @Test
    void movesDirectPrecedingCaptionIntoTableAndPreservesInlineMarkup() {
        Document doc = process("<p class='table-title' style='text-align:center'>"
                + "<span>表4.3</span> 游客月收入分组数据</p>"
                + "<table><tr><td>数据</td></tr></table>");

        Element table = doc.selectFirst("table");
        Element caption = table.children().first();
        assertNotNull(caption);
        assertSame(caption, table.children().first());
        assertEquals("表4.3 游客月收入分组数据", caption.text());
        assertEquals("table-title", caption.className());
        assertEquals("text-align:center", caption.attr("style"));
        assertNotNull(caption.selectFirst("span"));
        assertTrue(doc.select("body > p").isEmpty());
    }

    @Test
    void findsCaptionBeforeTableParentAndSupportsFullWidthNumber() {
        Document doc = process("<p>　表３．２　游客年龄数据</p>"
                + "<div class='table-wrapper'><table><tr><td>数据</td></tr></table></div>");

        assertEquals("　表３．２　游客年龄数据", doc.selectFirst("table > caption").text());
        assertTrue(doc.select("body > p").isEmpty());
    }

    @Test
    void doesNotSkipInterveningElementOrConsumeOrdinaryText() {
        Document doc = process("<p>表4.3 标题</p><p>中间正文</p>"
                + "<table><tr><td>数据</td></tr></table>"
                + "<p>表格说明</p><table><tr><td>更多数据</td></tr></table>");

        assertTrue(doc.select("caption").isEmpty());
        assertEquals(3, doc.select("body > p").size());
    }

    @Test
    void doesNotCrossTableCellBoundaryAndIsIdempotent() {
        String input = "<table><tr><td><p>表1 单元格正文</p></td>"
                + "<td><div><table><tr><td>嵌套数据</td></tr></table></div></td></tr></table>";
        String processed = TableCaptionProcessor.process(input);
        assertTrue(Jsoup.parse(processed).select("caption").isEmpty());

        String withCaption = "<p>表1 不应覆盖</p><table><caption>已有标题</caption>"
                + "<tr><td>数据</td></tr></table>";
        assertEquals(withCaption, TableCaptionProcessor.process(withCaption));
    }

    @Test
    void conversionPipelineProducesCaptionAsFirstTableChild() throws Exception {
        try (TestDocxBuilder builder = new TestDocxBuilder()) {
            builder.addContentTypes().addRels().addDocument(
                    "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>"
                            + "<w:r><w:t>表4.3 游客月收入分组数据</w:t></w:r></w:p>"
                            + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>收入</w:t></w:r>"
                            + "</w:p></w:tc></w:tr></w:tbl>");
            try (var input = Files.newInputStream(builder.build())) {
                Document doc = Jsoup.parse(DocxConverter.convert(input));
                Element table = doc.selectFirst("table");
                assertNotNull(table);
                assertEquals("caption", table.children().first().normalName());
                assertEquals("表4.3 游客月收入分组数据", table.selectFirst("caption").text());
                assertTrue(table.selectFirst("caption").attr("style").contains("text-align: center"));
                assertFalse(doc.body().children().stream()
                        .anyMatch(element -> element.normalName().equals("p")
                                && element.text().startsWith("表4.3")));
            }
        }
    }

    private Document process(String body) {
        return Jsoup.parse(TableCaptionProcessor.process(body));
    }
}
