package cn.p4u.dth.renderer;

import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/** 将表格前紧邻的“表 + 数字”标题移动为 table 的 caption。 */
final class TableCaptionProcessor {
    private static final Pattern CAPTION = Pattern.compile("^表[\\s\\p{Z}]*[0-9０-９]",
            Pattern.UNICODE_CHARACTER_CLASS);
    // 可以穿过普通布局容器，但不能越过会改变标题归属关系的语义边界。
    private static final Set<String> BOUNDARIES = Set.of(
            "body", "html", "td", "th", "li", "table", "caption", "figure", "figcaption");

    private TableCaptionProcessor() {}

    static String process(String html) {
        if (!html.contains("<table")) return html;
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        boolean changed = false;
        for (Element table : new ArrayList<>(document.select("table"))) {
            if (hasDirectCaption(table)) continue;
            Element candidate = precedingElement(table);
            if (candidate == null || !isCaption(candidate)) continue;

            Element caption = new Element("caption");
            // 段落的对齐、类名等属性属于标题展示信息，一并转移到 caption。
            caption.attributes().addAll(candidate.attributes());
            for (Node child : new ArrayList<>(candidate.childNodes())) caption.appendChild(child);
            table.prependChild(caption);
            candidate.remove();
            changed = true;
        }
        return changed ? document.outerHtml() : html;
    }

    private static boolean hasDirectCaption(Element table) {
        return table.children().stream().anyMatch(child -> child.normalName().equals("caption"));
    }

    /**
     * 先检查 table 自身的前一个元素；table 是容器中的首个元素时，再逐层检查父容器的前一个元素。
     * 一旦遇到一个非标题元素就停止，确保只识别真正紧邻的标题。
     */
    private static Element precedingElement(Element table) {
        Element anchor = table;
        while (true) {
            Element previous = anchor.previousElementSibling();
            if (previous != null) return previous;
            Element parent = anchor.parent();
            if (parent == null || BOUNDARIES.contains(parent.normalName())) return null;
            anchor = parent;
        }
    }

    private static boolean isCaption(Element candidate) {
        if (candidate.normalName().equals("table")
                || !candidate.select("table,figure,img,svg,math,video,audio,iframe").isEmpty()) {
            return false;
        }
        return CAPTION.matcher(stripLeadingWhitespace(candidate.wholeText())).find();
    }

    private static String stripLeadingWhitespace(String text) {
        return text.replaceFirst("^[\\s\\p{Z}]+", "");
    }
}
