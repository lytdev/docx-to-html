package cn.p4u.dth.renderer;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/** HTML 输出的最后一步：合并同一父元素下属性相同、直接相邻的 span。 */
final class AdjacentSpanProcessor {
    private static final List<String> MATCH_ATTRIBUTES = List.of("style", "id", "class");

    private AdjacentSpanProcessor() {}

    static String process(String html) {
        if (!html.contains("<span")) return html;
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        return mergeChildren(document) ? document.outerHtml() : html;
    }

    /**
     * 先处理子树，再处理兄弟节点。移动原有子节点，保留文字、转义字符和嵌套格式。
     * 不越过文本（包括空白）、注释、换行标签或其他元素，以免改变内容顺序和样式边界。
     */
    private static boolean mergeChildren(Element parent) {
        boolean changed = false;
        for (Element child : new ArrayList<>(parent.children())) {
            changed |= mergeChildren(child);
        }
        for (Node node = parent.firstChild(); node != null; node = node.nextSibling()) {
            if (!(node instanceof Element span) || !span.normalName().equals("span")) continue;
            boolean merged = false;
            while (span.nextSibling() instanceof Element next
                    && next.normalName().equals("span") && sameAttributes(span, next)) {
                for (Node child : new ArrayList<>(next.childNodes())) span.appendChild(child);
                next.remove();
                merged = true;
            }
            if (merged) {
                changed = true;
                // 两个外层 span 合并后，内部原本分隔的 span 也可能成为相邻节点。
                mergeChildren(span);
            }
        }
        return changed;
    }

    private static boolean sameAttributes(Element first, Element second) {
        for (String name : MATCH_ATTRIBUTES) {
            // 缺失属性与显式空属性分别处理；属性值按原字符串比较，不重排 CSS 或 class。
            if (first.hasAttr(name) != second.hasAttr(name)
                    || !first.attr(name).equals(second.attr(name))) return false;
        }
        return true;
    }
}
