package cn.p4u.dth.renderer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * 将紧邻的图片和图注组合为 figure。
 *
 * <p>使用 HTML 树识别父子、兄弟关系，避免用正则表达式解析嵌套标签。
 * 正则只判断可见文字是否以“图 + 数字”开头。普通图片添加展示类名，公式保留专用类名。</p>
 */
final class FigureCaptionProcessor {
    private static final Pattern CAPTION = Pattern.compile("^图[\\s\\p{Z}]*[0-9０-９]",
            Pattern.UNICODE_CHARACTER_CLASS);
    // 不跨越单元格、列表项和已有图文容器寻找图注。
    private static final Set<String> BOUNDARIES = Set.of(
            "body", "html", "td", "th", "li", "figure", "figcaption", "table", "tr");
    private static final Set<String> INLINE = Set.of(
            "span", "a", "b", "strong", "i", "em", "u", "s", "sup", "sub", "small", "br");

    private FigureCaptionProcessor() {}

    static String process(String html) {
        if (!html.contains("<img")) return html;
        Document document = Jsoup.parse(html);
        document.outputSettings().prettyPrint(false);
        boolean changed = false;
        List<Element> images = new ArrayList<>(document.select("img"));
        // 先在原始 HTML 结构上完成所有图片分类，避免后续移动节点影响兄弟关系判断。
        for (Element image : images) changed |= normalizeImageType(image);
        for (Element image : images) {
            if (image.closest("figure") != null) continue;
            Node anchor = image;
            Node candidate;
            // 图片后没有内容时向外查找父元素的紧邻兄弟，但不越过语义边界。
            while (true) {
                candidate = nextContent(anchor);
                if (candidate != null) break;
                Element parent = anchor.parent() instanceof Element e ? e : null;
                if (parent == null || BOUNDARIES.contains(parent.normalName())) break;
                anchor = parent;
            }
            if (candidate == null) continue;
            List<Node> caption = captionNodes(candidate);
            StringBuilder text = new StringBuilder();
            for (Node node : caption) text.append(visibleText(node));
            if (!CAPTION.matcher(stripWhitespace(text.toString())).find()) continue;
            // 图注必须是文字；不能把下一张图片或表格一起吞入图注。
            if (caption.stream().anyMatch(FigureCaptionProcessor::containsMedia)) continue;

            Element parent = (Element) anchor.parent();
            // figure 不能嵌套在 p 中。保留原来的属性，用 div 承接混排内容。
            if (parent.normalName().equals("p")) parent.tagName("div");
            Element figure = new Element("figure");
            // 把图片本身移入 figure，原包裹中若还有正文或其他图片则留在原处。
            if (anchor == image) anchor.before(figure);
            else anchor.after(figure);
            Node oldParent = image.parent();
            figure.appendChild(image);
            // 仅清理移出图片后变空的包裹层，不删除其他有内容的节点。
            while (oldParent != parent && oldParent instanceof Element && isWhitespace(oldParent)) {
                Node nextParent = oldParent.parent();
                oldParent.remove();
                oldParent = nextParent;
            }
            String captionText = stripWhitespace(text.toString());
            // 图注比 Word 中常见的“图片 1”等内部名称更能描述图片，优先作为 alt。
            image.attr("alt", captionText);
            Element figcaption = new Element("figcaption");
            figure.appendChild(figcaption);
            // text() 创建文本节点并自动转义特殊字符，图注中不再保留任何子标签。
            figcaption.text(captionText);
            for (Node node : caption) node.remove();
            changed = true;
        }
        return changed ? document.outerHtml() : html;
    }

    /**
     * 统一图片语义属性和展示类名。
     *
     * <p>只有嵌入型图片与同一父元素下带可见文字的兄弟元素混排时，才识别为行内图片。
     * 其他普通图片全部识别为块级图片。公式图片始终使用自己的类型和类名。</p>
     */
    private static boolean normalizeImageType(Element image) {
        String before = image.outerHtml();
        removeFloatStyle(image);
        if ("formula".equals(image.attr("data-type"))) {
            image.removeAttr("data-docx-embedded");
            setImageClasses(image, "formula-item", "formula-image");
        } else {
            boolean inline = "true".equals(image.attr("data-docx-embedded"))
                    && hasTextElementSibling(image);
            image.removeAttr("data-docx-embedded");
            image.attr("data-type", "image");
            setImageClasses(image, inline ? "image-inline" : "image-block", "image-item");
        }
        return !before.equals(image.outerHtml());
    }

    /** 原位设置图片专用类，保留业务自定义类及 class 属性在标签中的位置。 */
    private static void setImageClasses(Element image, String primary, String secondary) {
        LinkedHashSet<String> classes = new LinkedHashSet<>(image.classNames());
        classes.removeAll(Set.of(
                "image-block", "image-inline", "image-item", "formula-item", "formula-image"));
        classes.add(primary);
        classes.add(secondary);
        image.attr("class", String.join(" ", classes));
    }

    /** 删除图片 style 中的 float 声明，同时保留尺寸、对齐等其他内联样式。 */
    private static void removeFloatStyle(Element image) {
        if (!image.hasAttr("style")) return;
        List<String> retained = new ArrayList<>();
        for (String declaration : image.attr("style").split(";")) {
            String trimmed = declaration.trim();
            if (!trimmed.isEmpty() && !trimmed.matches("(?i)^float\\s*:.*$")) {
                retained.add(trimmed);
            }
        }
        if (retained.isEmpty()) image.removeAttr("style");
        else image.attr("style", String.join("; ", retained) + ";");
    }

    /** 只检查直接兄弟元素；空标签、纯空格元素和图片自身都不算文字混排。 */
    private static boolean hasTextElementSibling(Element image) {
        for (Element sibling : image.siblingElements()) {
            // figcaption 是 figure 的结构化图注，不属于与图片混排的正文。
            if (sibling.normalName().equals("figcaption")) continue;
            if (!stripWhitespace(sibling.wholeText()).isEmpty()) return true;
        }
        return false;
    }

    private static Node nextContent(Node node) {
        Node next = node.nextSibling();
        while (next != null && isWhitespace(next)) next = next.nextSibling();
        return next;
    }

    private static boolean isWhitespace(Node node) {
        if (node instanceof TextNode text) return stripWhitespace(text.getWholeText()).isEmpty();
        if (node instanceof Element element) {
            String tag = element.normalName();
            return (INLINE.contains(tag) || tag.equals("p") || tag.equals("div"))
                    && !containsMedia(element) && stripWhitespace(element.wholeText()).isEmpty();
        }
        return false;
    }

    private static boolean containsMedia(Node node) {
        return node instanceof Element element
                && !element.select("img,svg,math,table,figure,video,audio,iframe,hr,input").isEmpty();
    }

    /** 连续行内标签可能分别保存“图”“3-9”和标题，因此合并判断它们的文字。 */
    private static List<Node> captionNodes(Node first) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(first);
        if (isInline(first)) {
            for (Node next = first.nextSibling(); next != null && isInline(next);
                    next = next.nextSibling()) {
                if (next instanceof Element element && element.normalName().equals("br")) break;
                nodes.add(next);
            }
        }
        return nodes;
    }

    private static boolean isInline(Node node) {
        return node instanceof TextNode
                || node instanceof Element element && INLINE.contains(element.normalName());
    }

    private static String visibleText(Node node) {
        if (node instanceof TextNode text) return text.getWholeText();
        return node instanceof Element element ? element.wholeText() : "";
    }

    private static String stripWhitespace(String text) {
        return text.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
    }
}
