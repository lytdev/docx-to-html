package cn.p4u.smart.parser;

import cn.p4u.smart.model.StyleDef;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * OOXML styles.xml 解析器，负责从 .docx 包中提取所有样式定义及文档默认属性。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>读取并解析 styles.xml 文件，将每个 w:style 元素转换为 {@link StyleDef} 对象</li>
 *   <li>提取 w:docDefaults 中的默认字符和段落属性，作为样式继承链的终极后备</li>
 *   <li>提取样式的标识信息（styleId、name、basedOn、outlineLvl）用于后续样式继承关系解析</li>
 *   <li>提取字符属性（w:rPr）和段落属性（w:pPr）的两种形态：
 *       简写属性 map（便于快速查找）和原始属性嵌套 map（保留完整属性上下文，供主题颜色替换等场景使用）</li>
 * </ul>
 * <p>
 * 主要使用场景：由 {@link DocumentParser} 在解析文档前调用，产出样式定义集合；
 * 后续 DocumentParser 中基于 basedOn 链合并继承样式，再将主题引用替换为具体颜色值。
 * 当样式继承链无法补全字体槽时，使用 docDefaults 作为最后回落。
 */
public final class StylesParser {

    private static final Logger LOG = Logger.getLogger(StylesParser.class.getName());

    /** OOXML wordprocessingml 命名空间 URI */
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private StylesParser() {}

    /**
     * styles.xml 解析结果，包含样式定义映射及文档默认字符属性。
     * <p>
     * docsDefaults 在 OOXML 中定义于 w:docDefaults/w:rPrDefault/w:rPr，
     * 是所有样式继承链的终极后备：当 basedOn 链遍历完毕仍有字体槽为空时，
     * 应从此处取默认值（典型的如 ascii="Times New Roman"、eastAsia="宋体"）。
     */
    public static final class StylesResult {
        /** 样式 ID → 样式定义的不可变映射 */
        private final Map<String, StyleDef> styles;
        /** 文档默认字符属性的原始嵌套 map，结构与 StyleDef.rawRunAttrs 一致；可为 null */
        private final Map<String, Map<String, String>> docDefaultRunAttrs;

        public StylesResult(Map<String, StyleDef> styles,
                            Map<String, Map<String, String>> docDefaultRunAttrs) {
            this.styles = styles;
            this.docDefaultRunAttrs = docDefaultRunAttrs;
        }

        /** @return 样式映射，不可变 */
        public Map<String, StyleDef> styles() { return styles; }

        /** @return 文档默认字符属性嵌套 map，可能为 null */
        public Map<String, Map<String, String>> docDefaultRunAttrs() { return docDefaultRunAttrs; }
    }

    /**
     * 解析 styles.xml 文件，返回样式 ID 到样式定义的映射及文档默认属性。
     * <p>
     * 如果文件不存在或解析失败，返回空结果而非抛出异常——表示文档未定义样式信息。
     * 解析过程中启用了 XXE 防护（禁止 DOCTYPE 声明、设置空 EntityResolver）。
     *
     * @param stylesFile styles.xml 文件的路径，通常位于 .docx 解压目录下的 word/styles.xml
     * @return 解析结果，包含样式映射和文档默认字符属性；文件不存在或解析失败时返回空结果
     */
    public static StylesResult parse(Path stylesFile) {
        if (!java.nio.file.Files.exists(stylesFile)) {
            LOG.warning("Styles file not found: " + stylesFile);
            return new StylesResult(Collections.<String, StyleDef>emptyMap(), null);
        }
        try {
            // 构建 DOM 解析器，启用命名空间支持以便用 getElementsByTagNameNS 精确查找元素
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 安全防护：禁止 DOCTYPE 声明，防止 XXE 攻击
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            // 安全防护：设置空 EntityResolver，阻止外部实体解析
            builder.setEntityResolver(new org.xml.sax.EntityResolver() {
                @Override
                public org.xml.sax.InputSource resolveEntity(String publicId, String systemId) {
                    return new org.xml.sax.InputSource(new StringReader(""));
                }
            });
            Document doc = builder.parse(stylesFile.toFile());

            HashMap<String, StyleDef> styles = new HashMap<String, StyleDef>();

            // 解析 w:docDefaults 中的默认字符属性
            Map<String, Map<String, String>> docDefaultRunAttrs = parseDocDefaults(doc);

            // 遍历所有 w:style 元素，逐个提取样式属性
            NodeList styleNodes = doc.getElementsByTagNameNS(W, "style");
            for (int i = 0; i < styleNodes.getLength(); i++) {
                Element el = (Element) styleNodes.item(i);

                // 提取样式标识信息
                String styleId = el.getAttributeNS(W, "styleId");
                String name = getText(el, "name", "val");
                String basedOn = getText(el, "basedOn", "val");
                Integer outlineLvl = getOutlineLvl(el);

                // 同时提取简写属性和原始属性两种形态，供不同解析阶段使用
                Map<String, String> runProps = parseProps(el, "rPr");
                Map<String, String> paraProps = parseProps(el, "pPr");
                Map<String, Map<String, String>> rawRunAttrs = parseRawAttrs(el, "rPr");
                Map<String, Map<String, String>> rawParaAttrs = parseRawAttrs(el, "pPr");
                // table style 需要 tblPr 中的 tblBorders 等属性
                Map<String, Map<String, String>> rawTblAttrs = parseRawAttrs(el, "tblPr");

                styles.put(styleId, new StyleDef(styleId, name, basedOn, outlineLvl,
                        runProps, paraProps, rawRunAttrs, rawParaAttrs, rawTblAttrs));
            }
            return new StylesResult(Collections.unmodifiableMap(styles), docDefaultRunAttrs);
        } catch (Exception e) {
            LOG.warning("Failed to parse styles: " + e.getMessage());
            return new StylesResult(Collections.<String, StyleDef>emptyMap(), null);
        }
    }

    /**
     * 从父元素中查找指定标签名的子元素，并返回其某个命名空间属性的值。
     * <p>
     * 用于提取 w:name val、w:basedOn val 等单值属性。
     *
     * @param parent   父元素，通常为 w:style 元素
     * @param tagName  要查找的子元素本地名称，如 "name"、"basedOn"
     * @param attr     要获取的属性本地名称，通常为 "val"
     * @return 属性值字符串；若子元素不存在则返回 null
     */
    private static String getText(Element parent, String tagName, String attr) {
        NodeList nodes = parent.getElementsByTagNameNS(W, tagName);
        if (nodes.getLength() > 0) {
            return ((Element) nodes.item(0)).getAttributeNS(W, attr);
        }
        return null;
    }

    /**
     * 从样式元素中提取大纲级别（w:outlineLvl）。
     * <p>
     * outlineLvl 嵌套在 w:pPr 元素内，值 0-5 分别对应标题 1-6。
     * 用于后续将段落样式映射为 HTML 的 h1-h6 标签。
     *
     * @param styleEl w:style 元素
     * @return 大纲级别整数（0-5）；若不存在或值无效则返回 null
     */
    private static Integer getOutlineLvl(Element styleEl) {
        // outlineLvl 嵌套在 pPr 内，需先定位 pPr 再查找子元素
        NodeList pPrNodes = styleEl.getElementsByTagNameNS(W, "pPr");
        if (pPrNodes.getLength() > 0) {
            Element pPr = (Element) pPrNodes.item(0);
            NodeList lvlNodes = pPr.getElementsByTagNameNS(W, "outlineLvl");
            if (lvlNodes.getLength() > 0) {
                String val = ((Element) lvlNodes.item(0)).getAttributeNS(W, "val");
                if (!val.isEmpty()) {
                    try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    /**
     * 解析样式元素的属性子元素（w:rPr 或 w:pPr），提取简写属性 map。
     * <p>
     * 对每个子元素，取其 w:val 属性值作为 map 的 value；
     * 若子元素无 w:val 属性（如布尔型属性 w:b、w:i），则 value 设为 "true"。
     * 这种简写形式便于后续代码快速查询常用属性。
     *
     * @param parent   父元素，通常为 w:style 元素
     * @param propName 属性容器元素名，"rPr" 表示字符属性，"pPr" 表示段落属性
     * @return 不可变的属性映射，key 为子元素本地名称（如 "b"、"sz"），value 为属性值或 "true"
     */
    private static Map<String, String> parseProps(Element parent, String propName) {
        NodeList nodes = parent.getElementsByTagNameNS(W, propName);
        if (nodes.getLength() == 0) return Collections.emptyMap();

        HashMap<String, String> props = new HashMap<String, String>();
        NodeList children = ((Element) nodes.item(0)).getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element ce = (Element) child;
                String localName = ce.getLocalName();
                String val = ce.getAttributeNS(W, "val");
                // 无 val 属性的布尔型元素（如 w:b、w:i）记为 "true"，表示该属性已启用
                props.put(localName, val.isEmpty() ? "true" : val);
            }
        }
        return Collections.unmodifiableMap(props);
    }

    /**
     * 解析样式元素的属性子元素（w:rPr 或 w:pPr），提取原始属性嵌套 map。
     * <p>
     * 与 {@link #parseProps} 不同，此方法保留每个子元素的全部属性（包括命名空间属性与非命名空间属性），
     * 结构为：元素名 → (属性名 → 属性值)。
     * 用于需要完整属性上下文的场景，例如 w:color 的 themeColor 属性引用主题配色方案，
     * 后续解析阶段需同时访问 val 和 themeColor 才能完成主题颜色替换。
     *
     * @param parent   父元素，通常为 w:style 元素
     * @param propName 属性容器元素名，"rPr" 表示字符属性，"pPr" 表示段落属性
     * @return 不可变的嵌套属性映射；外层 key 为子元素本地名称，内层 key 为属性本地名称，value 为属性值
     */
    private static Map<String, Map<String, String>> parseRawAttrs(Element parent, String propName) {
        NodeList nodes = parent.getElementsByTagNameNS(W, propName);
        if (nodes.getLength() == 0) return Collections.emptyMap();

        HashMap<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();
        NodeList children = ((Element) nodes.item(0)).getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element ce = (Element) child;
                String localName = ce.getLocalName();

                // tblBorders 包含嵌套的边侧子元素（top/left/bottom/right/insideH/insideV），
                // 需要将各边的属性展平为 "侧名.属性名" 格式存储在内层 map 中，
                // 以便后续 resolveInheritedTblBorders 能够提取边框信息。
                if ("tblBorders".equals(localName)) {
                    HashMap<String, String> borderParts = new HashMap<String, String>();
                    NodeList sideNodes = ce.getChildNodes();
                    for (int k = 0; k < sideNodes.getLength(); k++) {
                        Node sideNode = sideNodes.item(k);
                        if (sideNode instanceof Element) {
                            Element sideEl = (Element) sideNode;
                            String sideName = sideEl.getLocalName();
                            NamedNodeMap sideAttrs = sideEl.getAttributes();
                            for (int m = 0; m < sideAttrs.getLength(); m++) {
                                Attr a = (Attr) sideAttrs.item(m);
                                borderParts.put(sideName + "." + a.getLocalName(), a.getValue());
                            }
                        }
                    }
                    result.put(localName, Collections.unmodifiableMap(borderParts));
                } else {
                    // 收集该子元素的全部属性，包括 w: 命名空间属性和其他命名空间属性
                    HashMap<String, String> attrs = new HashMap<String, String>();
                    NamedNodeMap allAttrs = ce.getAttributes();
                    for (int j = 0; j < allAttrs.getLength(); j++) {
                        Attr attr = (Attr) allAttrs.item(j);
                        attrs.put(attr.getLocalName(), attr.getValue());
                    }
                    result.put(localName, Collections.unmodifiableMap(attrs));
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 解析 w:docDefaults 中的默认字符属性，提取为与 StyleDef.rawRunAttrs 结构一致的嵌套 map。
     * <p>
     * OOXML 规定 w:docDefaults/w:rPrDefault/w:rPr 包含全文档的默认 Run 属性。
     * 这些默认值在样式继承链中处于最底层——当 Run 的 inline 属性和 basedOn 链
     * 均未能提供某个字体槽时，应从此处取值作为终极后备。
     *
     * @param doc styles.xml 的 DOM 文档对象
     * @return 默认字符属性的嵌套 map，若无 docDefaults 或 rPrDefault 则返回 null
     */
    private static Map<String, Map<String, String>> parseDocDefaults(Document doc) {
        NodeList ddNodes = doc.getElementsByTagNameNS(W, "docDefaults");
        if (ddNodes.getLength() == 0) return null;

        Element docDefaults = (Element) ddNodes.item(0);
        NodeList rPrDefaultNodes = docDefaults.getElementsByTagNameNS(W, "rPrDefault");
        if (rPrDefaultNodes.getLength() == 0) return null;

        Element rPrDefault = (Element) rPrDefaultNodes.item(0);
        NodeList rPrNodes = rPrDefault.getElementsByTagNameNS(W, "rPr");
        if (rPrNodes.getLength() == 0) return null;

        Element rPr = (Element) rPrNodes.item(0);
        HashMap<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();
        NodeList children = rPr.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element ce = (Element) child;
                String localName = ce.getLocalName();

                HashMap<String, String> attrs = new HashMap<String, String>();
                NamedNodeMap allAttrs = ce.getAttributes();
                for (int j = 0; j < allAttrs.getLength(); j++) {
                    Attr attr = (Attr) allAttrs.item(j);
                    attrs.put(attr.getLocalName(), attr.getValue());
                }
                result.put(localName, Collections.unmodifiableMap(attrs));
            }
        }
        return result.isEmpty() ? null : Collections.unmodifiableMap(result);
    }
}
