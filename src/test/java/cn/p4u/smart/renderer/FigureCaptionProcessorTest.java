package cn.p4u.smart.renderer;

import static org.junit.jupiter.api.Assertions.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class FigureCaptionProcessorTest {
    @Test
    void distinguishesFormulaAndOrdinaryImageClasses() {
        Document doc = process("<p><img src='formula' data-type='formula' "
                + "class='custom image-block image-item'></p><p><img src='photo'></p>");
        assertEquals("custom formula-item formula-image", doc.selectFirst("img[data-type=formula]").className());
        assertEquals("image-block image-item", doc.selectFirst("img[src=photo]").className());
        assertEquals("image", doc.selectFirst("img[src=photo]").attr("data-type"));
        assertEquals(doc.outerHtml(), FigureCaptionProcessor.process(doc.outerHtml()));
    }

    @Test
    void conversionPipelineProducesFigureForDocxCaption() throws Exception {
        try (cn.p4u.smart.util.TestDocxBuilder builder = new cn.p4u.smart.util.TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocumentRels("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/a.png\"/>")
                    .addMedia("a.png", new byte[]{1, 2, 3})
                    .addDocument("<w:p><w:r><w:t>图片前的行内文字</w:t></w:r>"
                            + "<w:r><w:drawing><wp:inline><wp:extent cx=\"9525\" cy=\"9525\"/>"
                            + "<wp:docPr id=\"1\" name=\"图片名称\" descr=\"图片别名 &amp; &quot;测试&quot;\"/>"
                            + "<a:graphic><a:graphicData><a:blip r:embed=\"rId1\"/></a:graphicData></a:graphic>"
                            + "</wp:inline></w:drawing></w:r></w:p>"
                            + "<w:p><w:r><w:t>图3-9　直接接触防护</w:t></w:r></w:p>");
            try (var input = java.nio.file.Files.newInputStream(builder.build())) {
                Document result = Jsoup.parse(cn.p4u.smart.converter.DocxConverter.convert(input));
                assertEquals(1, result.select("figure img").size());
                assertEquals("image-inline image-item", result.selectFirst("figure img").className());
                assertEquals("图3-9　直接接触防护", result.selectFirst("figure img").attr("alt"));
                assertTrue(result.body().text().contains("图片前的行内文字"));
                assertTrue(result.selectFirst("figcaption").text().contains("直接接触防护"));
            }
        }
    }

    @Test
    void allStandaloneImagesAreBlockAndImageFloatIsRemoved() throws Exception {
        try (cn.p4u.smart.util.TestDocxBuilder builder = new cn.p4u.smart.util.TestDocxBuilder()) {
            builder.addContentTypes().addRels()
                    .addDocumentRels("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/a.png\"/>"
                            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/b.png\"/>"
                            + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/c.png\"/>")
                    .addMedia("a.png", new byte[]{1}).addMedia("b.png", new byte[]{2})
                    .addMedia("c.png", new byte[]{3})
                    .addDocument(imageParagraph("rId1", "<wp:wrapSquare wrapText=\"bothSides\"/>")
                            + imageParagraph("rId2", "<wp:wrapTopAndBottom/>")
                            + embeddedImageParagraph("rId3"));
            try (var input = java.nio.file.Files.newInputStream(builder.build())) {
                Document result = Jsoup.parse(cn.p4u.smart.converter.DocxConverter.convert(input));
                assertEquals("image-block image-item", result.select("img").get(0).className());
                assertEquals("image-block image-item", result.select("img").get(1).className());
                assertEquals("image-block image-item", result.select("img").get(2).className());
                assertEquals("嵌入图片名称", result.select("img").get(2).attr("alt"));
                assertEquals(3, result.select("img[data-type=image]").size());
                assertTrue(result.select("img[style*=float]").isEmpty());
                assertTrue(result.select("img[data-docx-embedded]").isEmpty());
            }
        }
    }

    private String imageParagraph(String relationshipId, String wrapXml) {
        return "<w:p><w:r><w:drawing><wp:anchor><wp:extent cx=\"9525\" cy=\"9525\"/>"
                + wrapXml + "<a:graphic><a:graphicData><a:blip r:embed=\"" + relationshipId
                + "\"/></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r></w:p>";
    }

    private String embeddedImageParagraph(String relationshipId) {
        return "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"9525\" cy=\"9525\"/>"
                + "<wp:docPr id=\"3\" name=\"嵌入图片名称\"/>"
                + "<a:graphic><a:graphicData><a:blip r:embed=\"" + relationshipId
                + "\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>";
    }

    private Document process(String body) {
        return Jsoup.parse(FigureCaptionProcessor.process(body));
    }

    @Test
    void groupsDirectSiblingAndPreservesAttributes() {
        Document doc = process("<img src='a.png' width='100'><p style='text-align:center'>图3-9　<b>直接接触防护</b></p>");
        assertEquals(1, doc.select("figure").size());
        assertEquals("100", doc.selectFirst("figure img").attr("width"));
        assertEquals("图3-9　直接接触防护", doc.selectFirst("figcaption").wholeText());
        assertTrue(doc.selectFirst("figcaption").children().isEmpty());
        assertEquals("image-block image-item", doc.selectFirst("figure > img").className());
    }

    @Test
    void climbsNestedWrappersAndIgnoresWhitespace() {
        Document doc = process("<div><p><span><img src='a'></span></p></div><p>　&nbsp; </p><p>　图 3-9 防护</p>");
        assertEquals(1, doc.select("figure > img").size());
        assertEquals(2, doc.selectFirst("figure").childrenSize());
        assertEquals("图 3-9 防护", doc.selectFirst("figcaption").text().strip());
    }

    @Test
    void combinesCaptionSplitAcrossRunsInSameParagraph() {
        Document doc = process("<p><img src='a'>　<span>图</span><b>3-9</b><span>　防护</span></p>");
        assertEquals("图3-9　防护", doc.selectFirst("figcaption").wholeText());
        assertTrue(doc.selectFirst("figcaption").children().isEmpty());
        assertTrue(doc.select("p figure").isEmpty());
        assertEquals(1, doc.select("img").size());
    }

    @Test
    void supportsBareTextAndFullWidthNumbers() {
        assertEquals("图３－９ 防护", process("<div><img src='a'> 图３－９ 防护</div>")
                .selectFirst("figcaption").text());
    }

    @Test
    void doesNotSkipBodyTextOrAnotherImage() {
        String html = "<img src='a'><p>正文</p><p>图3-9 防护</p>";
        assertTrue(process(html).select("figure").isEmpty());
        Document doc = process("<img src='a'><img src='b'><p>图3-9 防护</p>");
        assertEquals("b", doc.selectFirst("figure img").attr("src"));
        assertEquals(1, doc.select("figure img").size());
    }

    @Test
    void doesNotCrossTableCellsAndIsIdempotent() {
        assertTrue(process("<table><tr><td><img src='a'></td><td>图3-9 防护</td></tr></table>")
                .select("figure").isEmpty());
        String html = FigureCaptionProcessor.process("<p><img src='a'></p><p>图3-9 防护</p>");
        assertEquals(html, FigureCaptionProcessor.process(html));
    }

    @Test
    void groupsWithinTableCellsAndListItems() {
        Document doc = process("<table><tr><td><p><img src='a'></p><p>图1 测试</p></td></tr></table>"
                + "<ul><li><img src='b'><span>图2 测试</span></li></ul>");
        assertEquals(2, doc.select("figure").size());
        assertNotNull(doc.selectFirst("td figure"));
        assertNotNull(doc.selectFirst("li figure"));
    }

    @Test
    void preservesExistingImageClassesWithoutCaption() {
        Document doc = process("<img src='a' class='existing'>");
        assertTrue(doc.selectFirst("img").hasClass("existing"));
        assertTrue(doc.selectFirst("img").hasClass("image-block"));
        assertTrue(doc.selectFirst("img").hasClass("image-item"));
    }

    @Test
    void preservesInlineImageClassAndRemovesConflictingBlockClass() {
        Document doc = process("<p><span>前文</span><img src='a' data-docx-embedded='true' "
                + "class='image-inline image-block'><span>后文</span></p>");
        assertEquals("image-inline image-item", doc.selectFirst("img").className());
        assertEquals("image", doc.selectFirst("img").attr("data-type"));
        assertFalse(doc.selectFirst("img").hasClass("image-block"));
    }

    @Test
    void textElementSiblingMakesBlockImageInline() {
        Document doc = process("<p><span>前文</span><img src='a' data-docx-embedded='true' "
                + "class='image-block image-item'></p>"
                + "<p><img src='b' data-docx-embedded='true' class='image-block'><span>　 </span></p>"
                + "<p><span>正文</span><img src='c' class='image-inline' style='float:right; width:10px'></p>");
        assertEquals("image-inline image-item", doc.selectFirst("img[src=a]").className());
        assertEquals("image-block image-item", doc.selectFirst("img[src=b]").className());
        assertEquals("image-block image-item", doc.selectFirst("img[src=c]").className());
        assertEquals("width:10px;", doc.selectFirst("img[src=c]").attr("style"));
        assertEquals(3, doc.select("img[data-type=image]").size());
        assertTrue(doc.select("img[data-docx-embedded]").isEmpty());
    }

    @Test
    void preservesOtherContentAndEscapesPlainCaption() {
        Document doc = process("<div><p>正文<img src='a'></p></div><p>图1 <b>&lt;防护&gt;&amp;</b></p>");
        assertTrue(doc.body().text().contains("正文"));
        assertEquals("图1 <防护>&", doc.selectFirst("figcaption").wholeText());
        assertEquals("图1 <防护>&", doc.selectFirst("figure > img").attr("alt"));
        assertTrue(doc.selectFirst("figcaption").children().isEmpty());
        assertEquals(2, doc.selectFirst("figure").childrenSize());
        assertNotNull(doc.selectFirst("figure > img"));
    }
}
