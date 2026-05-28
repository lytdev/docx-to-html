package cn.p4u.smart.renderer;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.model.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;

public final class HtmlRenderer {

    private HtmlRenderer() {}

    public static String render(DocumentModel model, ConversionConfig config) {
        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head><meta charset=\"UTF-8\"></head>\n<body>\n");
        for (var block : model.content()) {
            renderBlock(sb, block, config);
        }
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private static void renderBlock(StringBuilder sb, ContentBlock block, ConversionConfig config) {
        if (block instanceof ParagraphBlock para) {
            renderParagraph(sb, para, config);
        } else if (block instanceof TableBlock table) {
            renderTable(sb, table, config);
        }
    }

    private static void renderParagraph(StringBuilder sb, ParagraphBlock para, ConversionConfig config) {
        var styleAttr = StyleMapper.paragraphStyle(para);
        sb.append("<p");
        if (!styleAttr.isEmpty()) sb.append(" style=\"").append(styleAttr).append("\"");
        sb.append(">");
        for (var el : para.elements()) {
            renderParagraphElement(sb, el, config);
        }
        sb.append("</p>\n");
    }

    private static void renderParagraphElement(StringBuilder sb, ParagraphElement el, ConversionConfig config) {
        switch (el) {
            case TextRun run -> renderTextRun(sb, run);
            case HyperlinkElement link -> renderHyperlink(sb, link);
            case ImageElement img -> renderImage(sb, img, config);
            case MathElement math -> renderMath(sb, math, config);
        }
    }

    private static void renderTextRun(StringBuilder sb, TextRun run) {
        var css = StyleMapper.runStyle(run);
        sb.append("<span");
        if (!css.isEmpty()) sb.append(" style=\"").append(css).append("\"");
        sb.append(">").append(escapeHtml(run.text())).append("</span>");
    }

    private static void renderHyperlink(StringBuilder sb, HyperlinkElement link) {
        sb.append("<a href=\"").append(escapeAttr(link.url())).append("\"")
          .append(" style=\"color: #0563C1; text-decoration: underline;\"");
        sb.append(">");
        for (var run : link.runs()) {
            renderTextRun(sb, run);
        }
        sb.append("</a>");
    }

    private static void renderImage(StringBuilder sb, ImageElement img, ConversionConfig config) {
        sb.append("<img");
        Path mediaPath = resolveMediaPath(img.mediaPath(), config);
        if (config.imageMode() == ConversionConfig.ImageMode.BASE64) {
            String dataUri = ImageHandler.toBase64DataUri(mediaPath, img.mimeType());
            sb.append(" src=\"").append(dataUri).append("\"");
        } else {
            String relative = img.mediaPath() != null ? img.mediaPath().replace("media/", "") : "";
            sb.append(" src=\"").append(escapeAttr(config.imageOutputDir().getFileName() + "/" + relative)).append("\"");
            if (mediaPath != null && Files.exists(mediaPath)) {
                ImageHandler.copyToDir(mediaPath, config.imageOutputDir(), relative);
            }
        }
        if (img.width() > 0) sb.append(" width=\"").append(emusToPx(img.width())).append("\"");
        if (img.height() > 0) sb.append(" height=\"").append(emusToPx(img.height())).append("\"");
        if (img.wrapMode() == WrapMode.LEFT) sb.append(" style=\"float: left;\"");
        else if (img.wrapMode() == WrapMode.RIGHT) sb.append(" style=\"float: right;\"");
        sb.append(">");
    }

    private static void renderMath(StringBuilder sb, MathElement math, ConversionConfig config) {
        if (math.imagePath() != null) {
            Path imgPath = resolveMediaPath(math.imagePath(), config);
            String src = ImageHandler.toBase64DataUri(imgPath, math.mimeType());
            sb.append("<img src=\"").append(src).append("\" style=\"vertical-align: middle;\"");
        } else {
            sb.append("<img src=\"\" style=\"vertical-align: middle;\"");
        }
        if (math.latex() != null && !math.latex().isEmpty()) {
            sb.append(" data-latex=\"").append(escapeAttr(math.latex())).append("\"");
        }
        sb.append(">");
    }

    private static void renderTable(StringBuilder sb, TableBlock table, ConversionConfig config) {
        var tableStyle = StyleMapper.tableStyle(table);
        sb.append("<table style=\"").append(tableStyle).append("\">\n");
        for (var row : table.rows()) {
            String rowStyle = row.height() != null ? " style=\"height: " + row.height() + "\"" : "";
            sb.append("<tr").append(rowStyle).append(">\n");
            for (var cell : row.cells()) {
                var cellStyle = StyleMapper.cellStyle(cell);
                sb.append("<td");
                if (cell.colspan() > 1) sb.append(" colspan=\"").append(cell.colspan()).append("\"");
                if (cell.rowspan() > 1) sb.append(" rowspan=\"").append(cell.rowspan()).append("\"");
                sb.append(" style=\"").append(cellStyle).append("\">");
                for (var para : cell.paragraphs()) {
                    renderParagraph(sb, para, config);
                }
                sb.append("</td>\n");
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
    }

    private static Path resolveMediaPath(String mediaPath, ConversionConfig config) {
        if (mediaPath == null || config.extractedDir() == null) return null;
        return config.extractedDir().resolve("word").resolve(mediaPath);
    }

    private static int emusToPx(int emus) {
        return Math.max(1, emus / 9525);
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeAttr(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;");
    }
}
