package cn.p4u.dth.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.p4u.dth.converter.ConversionConfig;
import cn.p4u.dth.model.MathElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MathHtmlRendererTest {
  @TempDir Path resourceRoot;

  @Test
  void imageHasHigherPriorityThanMathMlAndLatex() throws Exception {
    Path image = resourceRoot.resolve("word/media/formula.png");
    Files.createDirectories(image.getParent());
    Files.write(image, new byte[] {1, 2, 3});
    AtomicBoolean resolved = new AtomicBoolean();
    ImageUriResolver resolver = (path, mimeType) -> {
      resolved.set(true);
      assertEquals(image, path);
      return new ImageUriResolver.ResolveResult("memory:formula", mimeType);
    };
    ConversionConfig config = ConversionConfig.builder()
        .imageUriResolver(resolver)
        .build();
    MathElement math = new MathElement("x", "<math>ignored</math>",
        "media/formula.png", "image/png", 190500, 95250);

    StringBuilder html = new StringBuilder();
    MathHtmlRenderer.render(html, math, config, resourceRoot);

    assertTrue(resolved.get());
    assertTrue(html.toString().startsWith("<img src=\"memory:formula\""));
    assertTrue(html.toString().contains("data-latex=\"x\""));
    assertTrue(html.toString().contains("width=\"20\""));
    assertTrue(html.toString().contains("height=\"10\""));
    assertFormulaImage(html.toString());
  }

  @Test
  void onlineFormulaKeepsDedicatedAttributesAfterHtmlPostProcessing() {
    var math = new MathElement("x+1", null, null);
    var paragraph = new cn.p4u.dth.model.ParagraphBlock(
        "", null, null, null, java.util.List.of(math));
    var model = new cn.p4u.dth.model.DocumentModel(
        java.util.Map.of(), java.util.List.of(paragraph), null);
    String html = HtmlRenderer.render(model, ConversionConfig.defaults());
    assertFormulaImage(html);
    assertEquals("x+1", org.jsoup.Jsoup.parse(html).selectFirst("img").attr("data-latex"));
  }

  @Test
  void imageWithoutLatexStillHasFormulaAttributes() {
    var config = ConversionConfig.builder().imageUriResolver((path, mime) ->
        new ImageUriResolver.ResolveResult("memory:formula", mime)).build();
    var html = new StringBuilder();
    MathHtmlRenderer.render(html, new MathElement("", "media/formula.png", "image/png"),
        config, resourceRoot);
    assertFormulaImage(html.toString());
  }

  @Test
  void failedImageStillHasFormulaAttributes() {
    var config = ConversionConfig.builder().imageUriResolver((path, mime) -> {
      throw new java.io.IOException("test failure");
    }).build();
    var html = new StringBuilder();
    MathHtmlRenderer.render(html, new MathElement("x", "media/formula.png", "image/png"),
        config, resourceRoot);
    assertFormulaImage(html.toString());
    assertTrue(html.toString().contains("[math image resolve failed]"));
  }

  /** 同时检查渲染结果及后处理结果，防止普通图片类名被重新加回。 */
  private void assertFormulaImage(String html) {
    for (String result : java.util.List.of(html, FigureCaptionProcessor.process(html))) {
      var image = org.jsoup.Jsoup.parse(result).selectFirst("img");
      assertEquals("formula-item formula-image", image.className());
      assertEquals("formula", image.attr("data-type"));
    }
  }

  @Test
  void mathMlHasHigherPriorityThanOnlineLatex() {
    ConversionConfig config = ConversionConfig.builder()
        .latexRenderUrl("https://example.test/{latex}")
        .build();
    MathElement math = new MathElement("x", "<math><mi>x</mi></math>", null, null);

    StringBuilder html = new StringBuilder();
    MathHtmlRenderer.render(html, math, config, resourceRoot);

    assertEquals("<math><mi>x</mi></math>", html.toString());
  }

  @Test
  void latexTextFallbackEscapesHtml() {
    ConversionConfig config = ConversionConfig.builder().latexRenderUrl(null).build();
    MathElement math = new MathElement("a<b&c", null, null);

    StringBuilder html = new StringBuilder();
    MathHtmlRenderer.render(html, math, config, resourceRoot);

    assertEquals(
        "<span data-latex=\"a<b&amp;c\" class=\"latex-formula\">a&lt;b&amp;c</span>",
        html.toString());
  }
}
