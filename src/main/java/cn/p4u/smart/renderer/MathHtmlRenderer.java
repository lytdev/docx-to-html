package cn.p4u.smart.renderer;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.model.MathElement;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.List;

/**
 * 按优先级渲染数学公式（Chain of Responsibility 模式）。
 *
 * <p>每个步骤只判断并处理一种数据来源。第一个支持当前公式的步骤完成渲染后，
 * 后续步骤不再执行。阅读顺序就是实际降级顺序：图片 → MathML → 在线服务 → LaTeX 文本。</p>
 */
final class MathHtmlRenderer {
  private static final List<RenderStep> STEPS = List.of(
      new ImageStep(), new MathMlStep(), new OnlineLatexStep(), new LatexTextStep());

  private MathHtmlRenderer() {}

  static void render(
      StringBuilder html, MathElement math, ConversionConfig config, Path resourceRoot) {
    RenderContext context = new RenderContext(html, math, config, resourceRoot);
    for (RenderStep step : STEPS) {
      if (step.supports(context)) {
        step.render(context);
        return;
      }
    }
  }

  /** 责任链中每个节点都实现这两个小方法。 */
  private interface RenderStep {
    boolean supports(RenderContext context);

    void render(RenderContext context);
  }

  /** 把渲染需要共同使用的数据收拢起来，避免方法参数不断增长。 */
  private record RenderContext(
      StringBuilder html, MathElement math, ConversionConfig config, Path resourceRoot) {}

  private static final class ImageStep implements RenderStep {
    @Override
    public boolean supports(RenderContext context) {
      return context.math().imagePath() != null;
    }

    @Override
    public void render(RenderContext context) {
      MathElement math = context.math();
      Path imagePath = context.resourceRoot() == null
          ? null
          : context.resourceRoot().resolve("word").resolve(math.imagePath());
      try {
        ImageUriResolver.ResolveResult result =
            context.config().imageUriResolver().resolve(imagePath, math.mimeType());
        context.html().append("<img src=\"")
            .append(HtmlEscaper.attribute(result.uri()))
            .append("\" style=\"vertical-align: middle;\"");
      } catch (IOException e) {
        context.html().append(
            "<img src=\"\" style=\"vertical-align: middle; color: #999; font-style: italic;\" "
                + "alt=\"[math image resolve failed]\"");
      }
      appendFormulaAttributes(context.html());
      appendLatexAttribute(context.html(), math.latex());
      context.html().append(">");
    }
  }

  private static final class MathMlStep implements RenderStep {
    @Override
    public boolean supports(RenderContext context) {
      return hasText(context.math().mathml());
    }

    @Override
    public void render(RenderContext context) {
      context.html().append(context.math().mathml());
    }
  }

  private static final class OnlineLatexStep implements RenderStep {
    @Override
    public boolean supports(RenderContext context) {
      return hasText(context.math().latex()) && context.config().latexRenderUrl() != null;
    }

    @Override
    public void render(RenderContext context) {
      String latex = context.math().latex();
      try {
        String encoded = URLEncoder.encode(latex, "UTF-8");
        String source = context.config().latexRenderUrl().replace("{latex}", encoded);
        context.html().append("<img src=\"")
            .append(HtmlEscaper.attribute(source))
            .append("\" style=\"vertical-align: middle;\" alt=\"")
            .append(HtmlEscaper.attribute(latex))
            .append("\" data-latex=\"")
            .append(HtmlEscaper.attribute(latex))
            .append("\"");
        appendFormulaAttributes(context.html());
        context.html().append(">");
      } catch (java.io.UnsupportedEncodingException e) {
        context.html().append("<span style=\"color: #999;\">[")
            .append(HtmlEscaper.text(latex))
            .append("]</span>");
      }
    }
  }

  private static final class LatexTextStep implements RenderStep {
    @Override
    public boolean supports(RenderContext context) {
      return hasText(context.math().latex());
    }

    @Override
    public void render(RenderContext context) {
      String latex = context.math().latex();
      context.html().append("<span data-latex=\"")
          .append(HtmlEscaper.attribute(latex))
          .append("\" class=\"latex-formula\">")
          .append(HtmlEscaper.text(latex))
          .append("</span>");
    }
  }

  /** 本地图片、在线公式图片和加载失败占位图片使用相同的公式标记。 */
  private static void appendFormulaAttributes(StringBuilder html) {
    html.append(" class=\"formula-item formula-image\" data-type=\"formula\"");
  }

  private static void appendLatexAttribute(StringBuilder html, String latex) {
    if (hasText(latex)) {
      html.append(" data-latex=\"").append(HtmlEscaper.attribute(latex)).append("\"");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
