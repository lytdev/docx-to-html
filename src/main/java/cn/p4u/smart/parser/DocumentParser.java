package cn.p4u.smart.parser;

import cn.p4u.smart.DocxConversionException;
import cn.p4u.smart.model.*;
import org.w3c.dom.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * OOXML 文档解析器，负责将解压后的 .docx XML 文件解析为结构化的 {@link DocumentModel} 树。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>解析 word/document.xml，将 w:body 下的段落、表格等节点映射为 ContentBlock 模型</li>
 *   <li>解析样式继承链（w:basedOn），将父样式的属性合并到子样式，形成完整样式定义</li>
 *   <li>解析主题引用，将 themeFont/themeColor 替换为主题文件中定义的具体字体名和颜色值</li>
 *   <li>解析 VML/DrawingML 图形，提取图片、形状和形状组的属性</li>
 *   <li>解析 OMML 数学公式，委托 {@link OmmlToLatexConverter} 转换为 LaTeX 表达式</li>
 *   <li>解析编号格式，提取 numId 到格式名的映射供渲染器使用</li>
 * </ul>
 * <p>
 * 主要使用场景：作为三阶段管线的第二阶段，由 {@link cn.p4u.smart.converter.DocxConverter}
 * 在 DocxExtractor 解压完成后调用 {@link #parse(Path)} 方法，产出不可变的 DocumentModel，
 * 随后传递给 HtmlRenderer 进行 HTML 渲染。
 */
public final class DocumentParser {

    // ---- OOXML 命名空间常量 ----

    /** w: 命名空间 — WordprocessingML，用于段落、表格、Run 等文档核心元素 */
    private static final String W  = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    /** r: 命名空间 — Relationships，用于超链接和图片引用（r:id / r:embed） */
    private static final String R  = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    /** m: 命名空间 — Office Math，用于 OMML 数学公式元素（m:oMath / m:oMathPara） */
    private static final String M  = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    /** wp: 命名空间 — WordprocessingML Drawing，用于图片/形状的定位信息（wp:extent / wp:anchor） */
    private static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    /** a: 命名空间 — DrawingML Main，用于图形通用属性（a:blip / a:solidFill / a:prstGeom） */
    private static final String A  = "http://schemas.openxmlformats.org/drawingml/2006/main";
    /** v: 命名空间 — VML（Vector Markup Language），用于旧版矢量图形和图片（v:shape / v:imagedata） */
    private static final String V  = "urn:schemas-microsoft-com:vml";
    /** mc: 命名空间 — Markup Compatibility，用于 AlternateContent 兼容性降级（mc:Choice / mc:Fallback） */
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    /** wps: 命名空间 — Word 2010 DrawingML 形状（wps:wsp） */
    private static final String WPS = "http://schemas.microsoft.com/office/word/2010/wordprocessingShape";
    /** wpg: 命名空间 — Word 2010 DrawingML 形状组（wpg:wgp） */
    private static final String WPG = "http://schemas.microsoft.com/office/word/2010/wordprocessingGroup";

    // ---- 解析辅助数据 ----

    /** 关系文件解析结果，key 为 rId，value 为关系目标路径和类型 */
    private final Map<String, RelsParser.Rel> rels;
    /** 样式定义 map，key 为 styleId，已完成继承合并和主题解析 */
    private final Map<String, StyleDef> styles;
    /** 文档默认字符属性的嵌套 map，来自 w:docDefaults/w:rPrDefault/w:rFonts；主题引用已解析为具体值；可为 null */
    private final Map<String, Map<String, String>> docDefaultRunAttrs;
    /** 默认段落样式的 styleId（标记 w:default="1" 且 w:type="paragraph"）；可为 null */
    private final String defaultParaStyleId;
    /** 默认字符样式的 styleId（标记 w:default="1" 且 w:type="character"）；可为 null */
    private final String defaultCharStyleId;
    /** 主题定义，包含颜色方案和字体方案 */
    private final ThemeDef theme;
    /** 编号格式 map，key 为 numId，value 为格式名（如 "decimal"、"bullet"） */
    private final Map<String, String> numberingFormats;

    /**
     * 私有构造方法，在创建解析器实例时完成所有辅助文件的解析和样式预处理。
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>解析关系文件（_rels/document.xml.rels）</li>
     *   <li>解析样式文件（styles.xml）得到原始样式</li>
     *   <li>解析主题文件（theme1.xml）</li>
     *   <li>解析编号文件（numbering.xml）</li>
     *   <li>对原始样式执行继承合并（resolveStyleInheritance）</li>
     *   <li>对合并后的样式执行主题引用解析（resolveThemeInStyles）</li>
     * </ol>
     *
     * @param extractedDir 解压后的 .docx 临时目录路径
     */
    private DocumentParser(Path extractedDir) {
        this.rels = RelsParser.parse(extractedDir.resolve("word/_rels/document.xml.rels"));
        StylesParser.StylesResult stylesResult = StylesParser.parse(extractedDir.resolve("word/styles.xml"));
        Map<String, StyleDef> rawStyles = stylesResult.styles();
        Map<String, Map<String, String>> rawDocDefaults = stylesResult.docDefaultRunAttrs();
        this.defaultParaStyleId = stylesResult.defaultParaStyleId();
        this.defaultCharStyleId = stylesResult.defaultCharStyleId();
        this.theme = ThemeParser.parse(extractedDir.resolve("word/theme/theme1.xml"));
        this.numberingFormats = NumberingParser.parse(extractedDir.resolve("word/numbering.xml"));
        // 先合并样式继承链，使每个样式包含其祖先的属性；
        // docDefaults 作为终极后备注入到继承链底部
        Map<String, StyleDef> resolved = resolveStyleInheritance(rawStyles, rawDocDefaults);
        // 再将主题引用替换为具体值，确保 renderer 拿到的是完全解析的样式
        this.styles = resolveThemeInStyles(resolved);
        // docDefaultRunAttrs 可能包含主题引用（如 asciiTheme="minorHAnsi"），
        // 需要在主题解析完成后同样替换为具体值，否则 resolveInheritedFontSpec
        // 的 docDefaults 后备阶段无法找到实际字体名
        this.docDefaultRunAttrs = resolveThemeInDocDefaults(rawDocDefaults);
    }

    /**
     * 解析 .docx 文档的主入口方法。
     * <p>
     * 读取解压目录下的 word/document.xml，构建 DOM 树后遍历 w:body 的子节点，
     * 将 w:p（段落）和 w:tbl（表格）分别解析为 ParagraphBlock 和 TableBlock，
     * 最终组装为不可变的 DocumentModel 返回。
     *
     * @param extractedDir .docx 解压后的临时目录路径
     * @return 解析完成的文档模型，包含样式、主题、编号格式和内容块列表
     * @throws DocxConversionException 如果 document.xml 不存在或解析失败
     */
    public static DocumentModel parse(Path extractedDir) {
        DocumentParser parser = new DocumentParser(extractedDir);
        Path docFile = extractedDir.resolve("word/document.xml");
        if (!Files.exists(docFile)) {
            throw new DocxConversionException("document.xml not found in extracted directory");
        }
        try {
            // 所有 OOXML 入口统一使用安全工厂，避免某个解析器漏配 XXE 防护。
            org.w3c.dom.Document doc = SecureXmlDocuments.parse(docFile);

            // 查找 w:body 元素，不存在则返回空文档模型
            NodeList bodyNodes = doc.getElementsByTagNameNS(W, "body");
            if (bodyNodes.getLength() == 0) {
                return new DocumentModel(parser.styles, Collections.<ContentBlock>emptyList(), parser.theme, parser.numberingFormats);
            }
            Element body = (Element) bodyNodes.item(0);
            ArrayList<ContentBlock> contentBlocks = new ArrayList<ContentBlock>();

            // 遍历 body 的直接子元素，按类型分派解析
            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (!(child instanceof Element)) continue;
                Element el = (Element) child;
                String localName = el.getLocalName();
                if (W.equals(el.getNamespaceURI())) {
                    if ("p".equals(localName)) {
                        contentBlocks.add(parser.parseParagraph(el));
                    } else if ("tbl".equals(localName)) {
                        contentBlocks.add(parser.parseTable(el));
                    }
                }
            }
            return new DocumentModel(parser.styles, Collections.unmodifiableList(contentBlocks), parser.theme, parser.numberingFormats);
        } catch (DocxConversionException e) {
            throw e;
        } catch (Exception e) {
            throw new DocxConversionException("Failed to parse document.xml", e);
        }
    }

    // ---- 样式继承合并 ----

    /**
     * 解析样式继承关系，对每个样式沿 w:basedOn 链向上合并父样式的属性。
     * <p>
     * 在 OOXML 中，样式可以通过 w:basedOn 引用父样式，子样式只需定义与父样式不同的属性。
     * 此方法确保每个样式最终包含继承链上所有祖先的属性，子样式的同名属性覆盖父样式。
     * <p>
     * 当 docDefaultRunAttrs 不为空时，会创建一个合成的 docDefaults 样式并注入到继承链底部，
     * 使所有根样式（无 basedOn 或 basedOn 指向不存在的样式）都能默认继承文档默认属性。
     *
     * @param raw               解析 styles.xml 得到的原始样式 map
     * @param docDefaultRunAttrs 文档默认字符属性的原始嵌套 map，可为 null
     * @return 合并后的不可变样式 map，每个样式已包含完整的继承属性（含 docDefaults 后备）
     */
    private Map<String, StyleDef> resolveStyleInheritance(Map<String, StyleDef> raw,
                                                          Map<String, Map<String, String>> docDefaultRunAttrs) {
        // 如果存在 docDefaults，构建合成样式并注入到 raw map 的底部
        Map<String, StyleDef> effectiveRaw = raw;
        String docDefaultId = null;
        if (docDefaultRunAttrs != null && !docDefaultRunAttrs.isEmpty()) {
            docDefaultId = "";
            StyleDef docDefaultStyle = new StyleDef(
                    docDefaultId, "docDefaults", null, null,
                    Collections.<String, String>emptyMap(),
                    Collections.<String, String>emptyMap(),
                    docDefaultRunAttrs,
                    Collections.<String, Map<String, String>>emptyMap(),
                    Collections.<String, Map<String, String>>emptyMap());
            // 复制 raw map 并添加合成样式；同时将根样式（无 basedOn 或 basedOn 缺失）指向 docDefaults
            HashMap<String, StyleDef> augmented = new HashMap<String, StyleDef>(raw);
            augmented.put(docDefaultId, docDefaultStyle);
            for (Map.Entry<String, StyleDef> entry : raw.entrySet()) {
                StyleDef def = entry.getValue();
                if (def.basedOn() == null || !raw.containsKey(def.basedOn())) {
                    // 将根样式链接到 docDefaults 合成样式
                    augmented.put(def.styleId(), new StyleDef(
                            def.styleId(), def.name(), docDefaultId, def.outlineLvl(),
                            def.runProps(), def.paragraphProps(),
                            def.rawRunAttrs(), def.rawParaAttrs(), def.rawTblAttrs()));
                }
            }
            effectiveRaw = Collections.unmodifiableMap(augmented);
        }

        HashMap<String, StyleDef> resolved = new HashMap<String, StyleDef>();
        for (Map.Entry<String, StyleDef> entry : effectiveRaw.entrySet()) {
            if (docDefaultId != null && docDefaultId.equals(entry.getKey())) {
                continue; // 不需要将合成 docDefaults 样式放入最终结果
            }
            resolved.put(entry.getKey(), mergeWithParents(entry.getKey(), effectiveRaw, new HashSet<String>()));
        }
        return Collections.unmodifiableMap(resolved);
    }

    /**
     * 递归合并指定样式与其所有祖先样式的属性。
     * <p>
     * 合并策略：先递归获取父样式的完整属性，再将当前样式的属性覆盖上去。
     * 对于字符属性（runProps）和段落属性（paragraphProps），子样式覆盖父样式的同名属性；
     * 对于原始属性嵌套 map（rawRunAttrs/rawParaAttrs），同样采用子覆盖父的策略；
     * 对于大纲级别（outlineLvl），优先使用子样式定义，缺失时继承父样式。
     * 使用 visited 集合防止循环引用导致无限递归。
     *
     * @param styleId 当前待合并的样式 ID
     * @param raw     原始样式 map
     * @param visited 已访问的样式 ID 集合，用于检测循环引用
     * @return 合并后的完整样式定义，如果样式不存在则返回 null
     */
    private StyleDef mergeWithParents(String styleId, Map<String, StyleDef> raw, Set<String> visited) {
        // 样式不存在则返回 null
        if (!raw.containsKey(styleId)) return null;
        // 检测到循环引用，直接返回当前样式避免无限递归
        if (visited.contains(styleId)) return raw.get(styleId);
        visited.add(styleId);

        StyleDef current = raw.get(styleId);
        // 无父样式或父样式不存在，直接返回当前样式
        if (current.basedOn() == null || !raw.containsKey(current.basedOn())) {
            return current;
        }
        // 递归获取已合并的父样式
        StyleDef parent = mergeWithParents(current.basedOn(), raw, visited);
        if (parent == null) return current;

        // 合并字符属性：先复制父样式属性，再用子样式覆盖
        HashMap<String, String> mergedRun = new HashMap<String, String>(parent.runProps());
        mergedRun.putAll(current.runProps());
        // 合并段落属性：同上
        HashMap<String, String> mergedPara = new HashMap<String, String>(parent.paragraphProps());
        mergedPara.putAll(current.paragraphProps());

        // 大纲级别：子样式优先，缺失时继承父样式
        Integer outlineLvl = current.outlineLvl() != null ? current.outlineLvl() : parent.outlineLvl();

        // 合并原始字符属性嵌套 map
        Map<String, Map<String, String>> mergedRawRun = new HashMap<String, Map<String, String>>(parent.rawRunAttrs());
        mergedRawRun.putAll(current.rawRunAttrs());
        // 合并原始段落属性嵌套 map
        Map<String, Map<String, String>> mergedRawPara = new HashMap<String, Map<String, String>>(parent.rawParaAttrs());
        mergedRawPara.putAll(current.rawParaAttrs());
        // 合并原始表格属性嵌套 map（table style 继承）。
        // 需要深度合并：tblBorders 内层 map 使用 "侧名.属性名" 格式，
        // 父样式和子样式可能定义不同侧边的边框，不应相互覆盖。
        Map<String, Map<String, String>> parentTbl = parent.rawTblAttrs() != null
                ? deepCopyNestedMap(parent.rawTblAttrs())
                : new HashMap<String, Map<String, String>>();
        if (current.rawTblAttrs() != null) {
            mergeNestedMap(parentTbl, current.rawTblAttrs());
        }

        return new StyleDef(current.styleId(), current.name(), current.basedOn(),
                outlineLvl, Collections.unmodifiableMap(mergedRun), Collections.unmodifiableMap(mergedPara),
                Collections.unmodifiableMap(mergedRawRun), Collections.unmodifiableMap(mergedRawPara),
                Collections.unmodifiableMap(parentTbl));
    }

    // ---- 主题解析（在样式中替换主题引用为具体值） ----

    /**
     * 对已合并的样式执行主题引用解析，将 themeFont/themeColor 引用替换为主题文件中的具体值。
     * <p>
     * 需要处理三种主题引用：
     * <ul>
     *   <li>w:rFonts 中的 asciiTheme/hAnsiTheme/eastAsiaTheme/csTheme → 解析为具体字体名</li>
     *   <li>w:color 中的 themeColor → 解析为具体颜色值（可能含 tint/shade 调整）</li>
     *   <li>w:shd 中的 themeFill → 解析为具体填充颜色值（可能含 tint/shade 调整）</li>
     * </ul>
     * 解析后的样式属性完全具体化，渲染器无需再处理主题引用。
     *
     * @param styleMap 已完成继承合并的样式 map
     * @return 主题引用已替换为具体值的不可变样式 map
     */
    private Map<String, StyleDef> resolveThemeInStyles(Map<String, StyleDef> styleMap) {
        HashMap<String, StyleDef> result = new HashMap<String, StyleDef>();
        for (Map.Entry<String, StyleDef> entry : styleMap.entrySet()) {
            StyleDef def = entry.getValue();
            // 复制原始字符属性 map，以便修改
            HashMap<String, Map<String, String>> newRawRun = new HashMap<String, Map<String, String>>(def.rawRunAttrs());

            // 解析 w:rFonts 中的主题字体引用
            Map<String, String> rFontsAttrs = newRawRun.get("rFonts");
            if (rFontsAttrs != null) {
                HashMap<String, String> updated = new HashMap<String, String>(rFontsAttrs);
                // 逐一解析四种字体槽的主题引用
                resolveFontAttr(updated, "asciiTheme", "ascii");
                resolveFontAttr(updated, "hAnsiTheme", "hAnsi");
                resolveFontAttr(updated, "eastAsiaTheme", "eastAsia");
                resolveFontAttr(updated, "csTheme", "cs");
                newRawRun.put("rFonts", Collections.unmodifiableMap(updated));
            }

            // 解析 w:color 中的主题颜色引用
            // 当 w:color 存在 themeColor 属性但缺少 val 属性时，需从主题中查找具体颜色
            Map<String, String> colorAttrs = newRawRun.get("color");
            if (colorAttrs != null) {
                HashMap<String, String> updated = new HashMap<String, String>(colorAttrs);
                String themeColor = updated.get("themeColor");
                if (themeColor != null && !updated.containsKey("val")) {
                    String resolved = resolveThemeColor(themeColor,
                            updated.get("themeTint"), updated.get("themeShade"));
                    if (resolved != null) updated.put("val", resolved);
                }
                newRawRun.put("color", Collections.unmodifiableMap(updated));
            }

            // 解析 w:shd 中的主题填充引用
            Map<String, String> shdAttrs = newRawRun.get("shd");
            if (shdAttrs != null) {
                HashMap<String, String> updated = new HashMap<String, String>(shdAttrs);
                String themeFill = updated.get("themeFill");
                if (themeFill != null) {
                    String resolved = resolveThemeColor(themeFill,
                            updated.get("themeFillTint"), updated.get("themeFillShade"));
                    // 将解析后的颜色值写入 fill 属性
                    if (resolved != null) updated.put("fill", resolved);
                }
                newRawRun.put("shd", Collections.unmodifiableMap(updated));
            }

            // 解析 rawTblAttrs 中的 tblBorders 主题引用
            Map<String, Map<String, String>> newRawTbl = def.rawTblAttrs() != null
                    ? deepCopyNestedMap(def.rawTblAttrs())
                    : null;
            if (newRawTbl != null) {
                Map<String, String> tblBorders = newRawTbl.get("tblBorders");
                if (tblBorders != null) {
                    // 逐侧边解析边框颜色中的主题引用
                    String[] borderSides = {"top", "left", "bottom", "right", "insideH", "insideV"};
                    for (String side : borderSides) {
                        String colorKey = side + ".color";
                        String themeColorKey = side + ".themeColor";
                        String themeColor = tblBorders.get(themeColorKey);
                        if (themeColor != null) {
                            String resolved = resolveThemeColor(themeColor,
                                    tblBorders.get(side + ".themeTint"),
                                    tblBorders.get(side + ".themeShade"));
                            if (resolved != null) tblBorders.put(colorKey, resolved);
                        }
                    }
                }
            }

            result.put(entry.getKey(), new StyleDef(def.styleId(), def.name(), def.basedOn(),
                    def.outlineLvl(), def.runProps(),
                    def.paragraphProps(), Collections.unmodifiableMap(newRawRun), def.rawParaAttrs(),
                    newRawTbl != null ? Collections.unmodifiableMap(newRawTbl) : null));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 对文档默认属性（docDefaultRunAttrs）执行主题引用解析。
     * <p>
     * 与 {@link #resolveThemeInStyles(Map)} 逻辑一致，但针对 docDefaults 的原始属性 map。
     * Word 的默认 docDefaults 通常使用主题引用（如 w:asciiTheme="minorHAnsi"），
     * 这些引用必须在字体继承链的终极后备阶段被解析为具体字体名，
     * 否则 {@link #resolveInheritedFontSpec} 的 docDefaults 后备将无法找到实际字体值。
     *
     * @param rawAttrs 解析自 w:docDefaults 的原始字符属性嵌套 map，可为 null
     * @return 主题引用已替换为具体值的不可变 map，若输入为 null 则返回 null
     */
    private Map<String, Map<String, String>> resolveThemeInDocDefaults(Map<String, Map<String, String>> rawAttrs) {
        if (rawAttrs == null) return null;
        HashMap<String, Map<String, String>> result = new HashMap<String, Map<String, String>>(rawAttrs);

        // 解析 w:rFonts 中的主题字体引用
        Map<String, String> rFontsAttrs = result.get("rFonts");
        if (rFontsAttrs != null) {
            HashMap<String, String> updated = new HashMap<String, String>(rFontsAttrs);
            resolveFontAttr(updated, "asciiTheme", "ascii");
            resolveFontAttr(updated, "hAnsiTheme", "hAnsi");
            resolveFontAttr(updated, "eastAsiaTheme", "eastAsia");
            resolveFontAttr(updated, "csTheme", "cs");
            result.put("rFonts", Collections.unmodifiableMap(updated));
        }

        // 解析 w:color 中的主题颜色引用
        Map<String, String> colorAttrs = result.get("color");
        if (colorAttrs != null) {
            HashMap<String, String> updated = new HashMap<String, String>(colorAttrs);
            String themeColor = updated.get("themeColor");
            if (themeColor != null && !updated.containsKey("val")) {
                String resolved = resolveThemeColor(themeColor,
                        updated.get("themeTint"), updated.get("themeShade"));
                if (resolved != null) updated.put("val", resolved);
            }
            result.put("color", Collections.unmodifiableMap(updated));
        }

        // 解析 w:shd 中的主题填充引用
        Map<String, String> shdAttrs = result.get("shd");
        if (shdAttrs != null) {
            HashMap<String, String> updated = new HashMap<String, String>(shdAttrs);
            String themeFill = updated.get("themeFill");
            if (themeFill != null && !updated.containsKey("fill")) {
                String resolved = resolveThemeColor(themeFill,
                        updated.get("themeFillTint"), updated.get("themeFillShade"));
                if (resolved != null) updated.put("fill", resolved);
            }
            result.put("shd", Collections.unmodifiableMap(updated));
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 解析单个字体属性的主题引用。
     * <p>
     * 如果属性 map 中存在 themeAttr（如 "asciiTheme"）但没有对应的 targetAttr（如 "ascii"），
     * 则从主题字体方案中查找具体字体名并填入 targetAttr。
     *
     * @param attrs      属性 map，会被直接修改
     * @param themeAttr  主题引用属性名（如 "asciiTheme"）
     * @param targetAttr 目标属性名（如 "ascii"）
     */
    private void resolveFontAttr(Map<String, String> attrs, String themeAttr, String targetAttr) {
        String themeRef = attrs.get(themeAttr);
        if (themeRef != null && !attrs.containsKey(targetAttr)) {
            String resolved = resolveThemeFont(themeRef);
            if (resolved != null) attrs.put(targetAttr, resolved);
        }
    }

    // ---- 主题解析辅助方法 ----

    /**
     * 根据主题引用名解析主题字体。
     * <p>
     * 主题字体方案分为 major（标题字体）和 minor（正文字体），每类包含西文、东亚、
     * 复杂脚本三个字体槽。某些引用名（如 majorHAnsi/majorAscii）映射到同一西文字体。
     * 东亚字体优先使用脚本覆盖（script override）中"简体中文"的字体设置。
     *
     * @param themeRef 主题字体引用名（如 "minorAscii"、"majorEastAsia"）
     * @return 解析后的字体名，若主题不存在或引用名无法识别则返回 null
     */
    String resolveThemeFont(String themeRef) {
        if (theme == null || theme.fonts() == null) return null;
        ThemeDef.FontScheme fonts = theme.fonts();
        // major 系列 — 标题字体
        if ("majorHAnsi".equals(themeRef) || "majorAscii".equals(themeRef)) return fonts.majorLatin();
        if ("majorEastAsia".equals(themeRef)) return lookupScriptOverride(fonts.majorScriptOverrides(), themeRef, fonts.majorEastAsia());
        if ("majorCs".equals(themeRef)) return fonts.majorCs();
        // minor 系列 — 正文字体
        if ("minorHAnsi".equals(themeRef) || "minorAscii".equals(themeRef)) return fonts.minorLatin();
        if ("minorEastAsia".equals(themeRef)) return lookupScriptOverride(fonts.minorScriptOverrides(), themeRef, fonts.minorEastAsia());
        if ("minorCs".equals(themeRef)) return fonts.minorCs();
        return null;
    }

    /**
     * 在字体方案的脚本覆盖（script override）中查找指定脚本的字体名。
     * <p>
     * 主题文件可以为特定文字系统（如"Hans"=简体中文）定义独立的字体，
     * 如果覆盖中存在则返回覆盖值，否则使用 fallback 默认值。
     *
     * @param overrides 脚本覆盖 map，key 为脚本文本（如 "Hans"），value 为字体名
     * @param themeRef  原始主题引用名（目前仅用于日志，查找逻辑固定使用"Hans"）
     * @param fallback  当覆盖中不存在时的默认字体名
     * @return 最终使用的字体名
     */
    private String lookupScriptOverride(Map<String, String> overrides, String themeRef, String fallback) {
        // 对于简体中文脚本（Hans），优先使用覆盖中定义的字体
        String resolved = overrides.get("Hans");
        return resolved != null ? resolved : fallback;
    }

    /**
     * 根据主题颜色引用名解析主题颜色，支持 tint（变亮）和 shade（变暗）调整。
     * <p>
     * OOXML 定义了 12 个标准颜色槽位（dk1/lt1/dk2/lt2/accent1-6/hlink/folHlink），
     * 每个槽位对应主题文件中的一个具体颜色值。tint 和 shade 参数用于在基础颜色上
     * 进行亮度调整，tint 向白色混合（变亮），shade 向黑色混合（变暗）。
     *
     * @param themeColor 主题颜色引用名（如 "accent1"、"dk1"）
     * @param tint       变亮参数（十六进制字符串），255 为原色，0 为纯白，null 表示不变亮
     * @param shade      变暗参数（十六进制字符串），255 为原色，0 为纯黑，null 表示不变暗
     * @return 解析后的十六进制颜色值（如 "FF0000"），若主题不存在或引用名无法识别则返回 null
     */
    String resolveThemeColor(String themeColor, String tint, String shade) {
        if (theme == null || theme.colors() == null) return null;
        ThemeDef.ColorScheme colors = theme.colors();
        // 根据主题颜色引用名匹配颜色槽位
        String base;
        if ("dk1".equals(themeColor)) base = colors.dk1();
        else if ("lt1".equals(themeColor)) base = colors.lt1();
        else if ("dk2".equals(themeColor)) base = colors.dk2();
        else if ("lt2".equals(themeColor)) base = colors.lt2();
        else if ("accent1".equals(themeColor)) base = colors.accent1();
        else if ("accent2".equals(themeColor)) base = colors.accent2();
        else if ("accent3".equals(themeColor)) base = colors.accent3();
        else if ("accent4".equals(themeColor)) base = colors.accent4();
        else if ("accent5".equals(themeColor)) base = colors.accent5();
        else if ("accent6".equals(themeColor)) base = colors.accent6();
        else if ("hlink".equals(themeColor)) base = colors.hlink();
        else if ("folHlink".equals(themeColor)) base = colors.folHlink();
        else base = null;

        if (base == null) return null;
        // 优先应用 tint（变亮），再应用 shade（变暗），两者互斥
        if (tint != null && !tint.isEmpty()) return applyTint(base, tint);
        if (shade != null && !shade.isEmpty()) return applyShade(base, shade);
        return base;
    }

    /**
     * 对颜色应用 tint 变亮效果。
     * <p>
     * 公式：新分量 = round(原分量 * tint/255 + 255 * (1 - tint/255))
     * 当 tint=255 时保持原色，tint=0 时变为纯白。
     *
     * @param baseHex  基础颜色（6 位十六进制，如 "FF0000"）
     * @param tintHex  tint 参数（十六进制字符串，如 "CC"）
     * @return 变亮后的颜色（6 位十六进制），解析失败时返回原色
     */
    static String applyTint(String baseHex, String tintHex) {
        try {
            int base = Integer.parseInt(baseHex, 16);
            int tint = Integer.parseInt(tintHex, 16);
            int r = (base >> 16) & 0xFF;
            int g = (base >> 8) & 0xFF;
            int b = base & 0xFF;
            // 向白色混合：分量 = 原值 * (tint/255) + 255 * (1 - tint/255)
            r = (int) Math.round(r * (tint / 255.0) + 255 * (1 - tint / 255.0));
            g = (int) Math.round(g * (tint / 255.0) + 255 * (1 - tint / 255.0));
            b = (int) Math.round(b * (tint / 255.0) + 255 * (1 - tint / 255.0));
            // 钳位到 [0, 255] 防止溢出
            r = Math.min(255, Math.max(0, r));
            g = Math.min(255, Math.max(0, g));
            b = Math.min(255, Math.max(0, b));
            return String.format("%02X%02X%02X", r, g, b);
        } catch (NumberFormatException e) {
            return baseHex;
        }
    }

    /**
     * 对颜色应用 shade 变暗效果。
     * <p>
     * 公式：新分量 = round(原分量 * shade/255)
     * 当 shade=255 时保持原色，shade=0 时变为纯黑。
     *
     * @param baseHex   基础颜色（6 位十六进制，如 "FF0000"）
     * @param shadeHex  shade 参数（十六进制字符串，如 "99"）
     * @return 变暗后的颜色（6 位十六进制），解析失败时返回原色
     */
    static String applyShade(String baseHex, String shadeHex) {
        try {
            int base = Integer.parseInt(baseHex, 16);
            int shade = Integer.parseInt(shadeHex, 16);
            int r = (base >> 16) & 0xFF;
            int g = (base >> 8) & 0xFF;
            int b = base & 0xFF;
            // 向黑色混合：分量 = 原值 * (shade/255)
            r = (int) Math.round(r * shade / 255.0);
            g = (int) Math.round(g * shade / 255.0);
            b = (int) Math.round(b * shade / 255.0);
            // 钳位到 [0, 255] 防止溢出
            r = Math.min(255, Math.max(0, r));
            g = Math.min(255, Math.max(0, g));
            b = Math.min(255, Math.max(0, b));
            return String.format("%02X%02X%02X", r, g, b);
        } catch (NumberFormatException e) {
            return baseHex;
        }
    }

    // ---- 沿样式继承链解析 FontSpec ----

    /**
     * 沿样式的 basedOn 继承链解析完整的字体规格（FontSpec）。
     * <p>
     * 当 inline 的 w:rFonts 属性不完整时（如缺少 eastAsia 字体槽），
     * 需要沿样式链向上查找，用祖先样式中对应字体槽的值补全缺失字段。
     * 这确保了即使在样式定义中跨层级声明字体，最终也能得到完整的字体信息。
     * <p>
     * 按 OOXML 规范的字体系属性继承优先级：
     * <ol>
     *   <li>inline 的 w:rFonts 属性（最高优先级）</li>
     *   <li>Run 样式（w:rStyle）的 basedOn 继承链</li>
     *   <li>段落样式（w:pStyle）的 w:rPr basedOn 继承链</li>
     *   <li>docDefaults 文档默认值（终极后备）</li>
     * </ol>
     *
     * @param runStyleId    当前 Run 引用的字符样式 ID，可为 null
     * @param paraStyleId   当前段落引用的段落样式 ID，可为 null
     * @param inlineRFonts  inline 的 w:rFonts 属性 map，可为 null
     * @return 包含所有可用字体槽的 FontSpec，若所有槽均为空则返回 null
     */
    private FontSpec resolveInheritedFontSpec(String runStyleId, String paraStyleId,
                                              Map<String, String> inlineRFonts) {
        // 优先使用 inline 属性
        String ascii = inlineRFonts != null ? inlineRFonts.get("ascii") : null;
        String eastAsia = inlineRFonts != null ? inlineRFonts.get("eastAsia") : null;
        String cs = inlineRFonts != null ? inlineRFonts.get("cs") : null;

        // Phase 1: 若 inline 属性存在缺失字段，沿 Run 样式(w:rStyle)的 basedOn 链向上补全
        if (runStyleId != null && styles.containsKey(runStyleId)) {
            HashSet<String> visited = new HashSet<String>();
            String current = runStyleId;
            while (current != null && styles.containsKey(current) && !visited.contains(current)) {
                visited.add(current);
                StyleDef def = styles.get(current);
                Map<String, Map<String, String>> rawRun = def.rawRunAttrs();
                Map<String, String> rFontsAttrs = rawRun.get("rFonts");
                if (rFontsAttrs != null) {
                    if (isBlank(ascii)) {
                        String v = rFontsAttrs.get("ascii");
                        if (v == null || v.isEmpty()) v = rFontsAttrs.get("hAnsi");
                        if (v != null && !v.isEmpty()) ascii = v;
                    }
                    if (isBlank(eastAsia)) { String v = rFontsAttrs.get("eastAsia"); if (v != null && !v.isEmpty()) eastAsia = v; }
                    if (isBlank(cs)) { String v = rFontsAttrs.get("cs"); if (v != null && !v.isEmpty()) cs = v; }
                }
                current = def.basedOn();
            }
        }

        // Phase 2: 若 Run 样式链仍未补全，沿段落样式(w:pStyle)的 w:rPr basedOn 链向上补全
        // OOXML 规范：段落样式的 Run 属性部分优先于 docDefaults
        if (paraStyleId != null && styles.containsKey(paraStyleId)) {
            HashSet<String> visited = new HashSet<String>();
            String current = paraStyleId;
            while (current != null && styles.containsKey(current) && !visited.contains(current)) {
                visited.add(current);
                StyleDef def = styles.get(current);
                Map<String, Map<String, String>> rawRun = def.rawRunAttrs();
                Map<String, String> rFontsAttrs = rawRun.get("rFonts");
                if (rFontsAttrs != null) {
                    if (isBlank(ascii)) {
                        String v = rFontsAttrs.get("ascii");
                        if (v == null || v.isEmpty()) v = rFontsAttrs.get("hAnsi");
                        if (v != null && !v.isEmpty()) ascii = v;
                    }
                    if (isBlank(eastAsia)) { String v = rFontsAttrs.get("eastAsia"); if (v != null && !v.isEmpty()) eastAsia = v; }
                    if (isBlank(cs)) { String v = rFontsAttrs.get("cs"); if (v != null && !v.isEmpty()) cs = v; }
                }
                current = def.basedOn();
            }
        }

        // Phase 3: docDefaults 作为终极后备（主题引用已在构造时解析为具体值）
        if (docDefaultRunAttrs != null) {
            Map<String, String> ddFonts = docDefaultRunAttrs.get("rFonts");
            if (ddFonts != null) {
                if (isBlank(ascii)) {
                    String v = ddFonts.get("ascii");
                    if (v == null || v.isEmpty()) v = ddFonts.get("hAnsi");
                    if (v != null && !v.isEmpty()) ascii = v;
                }
                if (isBlank(eastAsia)) { String v = ddFonts.get("eastAsia"); if (v != null && !v.isEmpty()) eastAsia = v; }
                if (isBlank(cs)) { String v = ddFonts.get("cs"); if (v != null && !v.isEmpty()) cs = v; }
            }
        }
        // 所有槽位均为空则无需创建 FontSpec
        if (isBlank(ascii) && isBlank(eastAsia) && isBlank(cs)) return null;
        return new FontSpec(isBlank(ascii) ? null : ascii, null, null, isBlank(eastAsia) ? null : eastAsia, isBlank(cs) ? null : cs);
    }

    /**
     * 解析布尔型 Run 属性（w:b、w:i、w:u、w:strike），沿样式继承链回退。
     * <p>
     * 当 inline w:rPr 未设置某属性时，按 OOXML 优先级回退：
     * <ol>
     *   <li>Run 样式（w:rStyle）的 basedOn 继承链（runProps 已合并）</li>
     *   <li>段落样式（w:pStyle）的 w:rPr basedOn 继承链（runProps 已合并）</li>
     * </ol>
     * 属性的值为 "0"/"false"/"off"/"none"/"nil" 时表示关闭，其余值表示启用。
     *
     * @param runStyleId  当前 Run 引用的字符样式 ID，可为 null
     * @param paraStyleId 当前段落引用的段落样式 ID，可为 null
     * @param propName    属性名称（如 "b"、"i"、"u"、"strike"）
     * @return 该属性是否启用
     */
    private boolean resolveBoolPropFromStyles(String runStyleId, String paraStyleId, String propName) {
        // Phase 1: Run 样式（w:rStyle）的 runProps（已在 mergeWithParents 中沿 basedOn 合并）
        if (runStyleId != null && styles.containsKey(runStyleId)) {
            String val = styles.get(runStyleId).runProps().get(propName);
            if (val != null) {
                return !"0".equals(val) && !"false".equals(val)
                        && !"off".equals(val) && !"none".equals(val) && !"nil".equals(val);
            }
        }
        // Phase 2: 段落样式（w:pStyle）的 runProps（已在 mergeWithParents 中沿 basedOn 合并）
        if (paraStyleId != null && styles.containsKey(paraStyleId)) {
            String val = styles.get(paraStyleId).runProps().get(propName);
            if (val != null) {
                return !"0".equals(val) && !"false".equals(val)
                        && !"off".equals(val) && !"none".equals(val) && !"nil".equals(val);
            }
        }
        return false;
    }

    /**
     * 沿样式继承链解析 w:sz 字号属性（半磅值字符串）。
     * <p>
     * 当 inline w:rPr 未设置 w:sz 时，按 OOXML 优先级回退：
     * <ol>
     *   <li>Run 样式（w:rStyle）的 runProps（已在 mergeWithParents 中沿 basedOn 合并，含 docDefaults）</li>
     *   <li>段落样式（w:pStyle）的 runProps（已在 mergeWithParents 中沿 basedOn 合并，含 docDefaults）</li>
     * </ol>
     *
     * @param runStyleId  当前 Run 引用的字符样式 ID，可为 null
     * @param paraStyleId 当前段落引用的段落样式 ID，可为 null
     * @return 半磅值字符串（如 "21"），未找到时返回 null
     */
    private String resolveSzFromStyles(String runStyleId, String paraStyleId) {
        // Phase 1: Run 样式（w:rStyle）的 runProps（已沿 basedOn 合并，含 docDefaults）
        if (runStyleId != null && styles.containsKey(runStyleId)) {
            String val = styles.get(runStyleId).runProps().get("sz");
            if (val != null && !val.isEmpty()) return val;
        }
        // Phase 2: 段落样式（w:pStyle）的 runProps（已沿 basedOn 合并，含 docDefaults）
        if (paraStyleId != null && styles.containsKey(paraStyleId)) {
            String val = styles.get(paraStyleId).runProps().get("sz");
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    // ---- 段落解析 ----

    /**
     * 解析 w:p 元素，提取段落属性和内联元素。
     * <p>
     * 段落属性从 w:pPr 子元素中提取，包括样式引用、对齐方式、大纲级别、
     * 缩进信息和编号属性。内联元素按顺序收集，包括文本 Run、超链接、
     * 数学公式、绘图图形、AlternateContent 降级内容和嵌入对象。
     *
     * @param pEl w:p 元素
     * @return 解析后的段落块对象
     */
    private ParagraphBlock parseParagraph(Element pEl) {
        String styleId = null;
        Integer outlineLvl = null;
        String alignment = null;
        Indentation indentation = null;
        String numId = null;
        Integer ilvl = null;
        ArrayList<ParagraphElement> elements = new ArrayList<ParagraphElement>();

        // 第一遍：提取段落属性（需要先知道 paraStyleId 才能向下传递字体继承）
        NodeList children = pEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            Element el = (Element) child;
            if (W.equals(el.getNamespaceURI()) && "pPr".equals(el.getLocalName())) {
                styleId = getAttrVal(el, W, "pStyle", "val");
                alignment = getAttrVal(el, W, "jc", "val");
                outlineLvl = getOutlineLvl(el);
                // 提取编号属性（numId 和 ilvl）
                NodeList numPrNodes = el.getElementsByTagNameNS(W, "numPr");
                if (numPrNodes.getLength() > 0) {
                    Element numPr = (Element) numPrNodes.item(0);
                    numId = getAttrVal(numPr, W, "numId", "val");
                    String ilvlStr = getAttrVal(numPr, W, "ilvl", "val");
                    if (ilvlStr != null && !ilvlStr.isEmpty()) {
                        try { ilvl = Integer.parseInt(ilvlStr); } catch (NumberFormatException ignored) {}
                    }
                }
                // 提取缩进属性
                NodeList indNodes = el.getElementsByTagNameNS(W, "ind");
                if (indNodes.getLength() > 0) {
                    indentation = parseIndentation((Element) indNodes.item(0));
                }
                break; // pPr 最多一个，找到即可退出
            }
        }

        // OOXML 规范：段落未指定 w:pStyle 时默认使用标记 w:default="1" 的段落样式
        if (styleId == null) {
            styleId = defaultParaStyleId;
        }

        // 从样式继承链补全段落属性：inline w:pPr 缺失时回退到 style 的 paragraphProps
        if (styleId != null && styles.containsKey(styleId)) {
            StyleDef styleDef = styles.get(styleId);
            Map<String, String> pProps = styleDef.paragraphProps();
            if (alignment == null) {
                String jc = pProps.get("jc");
                if (jc != null && !jc.isEmpty()) alignment = jc;
            }
            if (outlineLvl == null) {
                outlineLvl = styleDef.outlineLvl();
            }
            if (indentation == null) {
                Map<String, String> indAttrs = styleDef.rawParaAttrs().get("ind");
                if (indAttrs != null) {
                    indentation = parseIndentationFromAttrs(indAttrs);
                }
            }
        }

        // 第二遍：解析内联元素，传递 paraStyleId 用于字体继承
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            Element el = (Element) child;
            String ns = el.getNamespaceURI();
            String localName = el.getLocalName();

            if (W.equals(ns) && "pPr".equals(localName)) {
                // 已在第一遍处理
            } else if (W.equals(ns) && "r".equals(localName)) {
                // 文本 Run，可能包含内嵌图形需拆分处理
                extractRunContent(el, elements, styleId);
            } else if (W.equals(ns) && "hyperlink".equals(localName)) {
                // 超链接
                elements.add(parseHyperlink(el, styleId));
            } else if (M.equals(ns) && ("oMath".equals(localName) || "oMathPara".equals(localName))) {
                // OMML 数学公式（段落级直接子元素）
                // oMathPara 可能包含多个 oMath 子公式，需分别解析
                parseMathElements(el, elements);
            } else if (W.equals(ns) && "drawing".equals(localName)) {
                // DrawingML 绘图（图片、形状或形状组）
                if (!tryAddDrawingOrShape(el, elements)) { /* ignored */ }
            } else if (MC.equals(ns) && "AlternateContent".equals(localName)) {
                // 兼容性降级内容
                extractAlternateContent(el, elements);
            } else if (W.equals(ns) && "object".equals(localName)) {
                // 嵌入对象（旧版 OLE 对象内的图片）
                ImageElement img = parseObject(el);
                if (img != null) elements.add(img);
            }
        }
        return new ParagraphBlock(styleId, outlineLvl, alignment, indentation,
                Collections.unmodifiableList(elements), numId, ilvl);
    }

    /**
     * 提取 w:r 元素中的内容，处理可能混合了文本和图形的复杂 Run。
     * <p>
     * 普通 Run 只包含 w:t 文本节点，直接解析为 TextRun。
     * 但 Run 也可能包含 w:drawing、w:pict、w:object 或 mc:AlternateContent，
     * 此时需要拆分处理：图形元素单独提取，文本部分合并为独立的 TextRun。
     * 这样确保图形与文本在段落中交替出现，顺序与原文档一致。
     *
     * @param rEl      w:r 元素
     * @param elements 用于收集解析结果的列表
     */
    private void extractRunContent(Element rEl, List<ParagraphElement> elements, String paraStyleId) {
        // 检测 Run 中是否包含图形或数学公式相关子元素
        boolean hasDrawing = hasChildLocalName(rEl, W, "drawing");
        boolean hasPict = hasChildLocalName(rEl, W, "pict");
        boolean hasObject = hasChildLocalName(rEl, W, "object");
        boolean hasAC = hasChildLocalName(rEl, MC, "AlternateContent");
        boolean hasMath = hasChildLocalName(rEl, M, "oMath") || hasChildLocalName(rEl, M, "oMathPara");
        if (!hasDrawing && !hasPict && !hasObject && !hasAC && !hasMath) {
            // 纯文本 Run，直接解析
            elements.add(parseRun(rEl, paraStyleId));
            return;
        }
        // 混合内容 Run：逐个子元素分派处理，保持元素在段落中的原始顺序
        NodeList runChildren = rEl.getChildNodes();
        StringBuilder textParts = new StringBuilder();
        for (int i = 0; i < runChildren.getLength(); i++) {
            Node rc = runChildren.item(i);
            if (!(rc instanceof Element)) continue;
            Element rce = (Element) rc;
            String rns = rce.getNamespaceURI();
            String rln = rce.getLocalName();
            if (W.equals(rns) && "drawing".equals(rln)) {
                // 遇到图形子元素前，先将累积的文本输出为 TextRun（保持顺序）
                flushTextParts(textParts, rEl, elements, paraStyleId);
                if (!tryAddDrawingOrShape(rce, elements)) { /* ignored */ }
            } else if (W.equals(rns) && "pict".equals(rln)) {
                flushTextParts(textParts, rEl, elements, paraStyleId);
                extractPict(rce, elements);
            } else if (W.equals(rns) && "object".equals(rln)) {
                flushTextParts(textParts, rEl, elements, paraStyleId);
                ImageElement img = parseObject(rce);
                if (img != null) elements.add(img);
            } else if (MC.equals(rns) && "AlternateContent".equals(rln)) {
                flushTextParts(textParts, rEl, elements, paraStyleId);
                extractAlternateContent(rce, elements);
            } else if (M.equals(rns) && ("oMath".equals(rln) || "oMathPara".equals(rln))) {
                // 遇到内联公式前，先将累积的文本输出，确保文本在公式前面
                flushTextParts(textParts, rEl, elements, paraStyleId);
                parseMathElements(rce, elements);
            } else if (W.equals(rns) && "t".equals(rln)) {
                // 收集文本内容（延迟输出，直到遇到非文本元素或循环结束）
                textParts.append(rce.getTextContent());
            }
        }
        // 循环结束后，如果文本为空且未通过 flushTextParts 输出过文本，
        // 尝试从更深层的 w:t 节点获取（这种情况可能出现在 rPr 先于 w:t 的场景）
        // 已通过 flushTextParts 处理过的 Run 不需要此降级逻辑
        if (textParts.length() == 0 && elements.isEmpty()) {
            NodeList tNodes = rEl.getElementsByTagNameNS(W, "t");
            if (tNodes.getLength() > 0) {
                textParts.append(tNodes.item(0).getTextContent());
            }
        }
        flushTextParts(textParts, rEl, elements, paraStyleId);
    }

    /**
     * 将累积的文本片段输出为 TextRun 并添加到元素列表。
     * <p>
     * 用于 {@link #extractRunContent} 中的顺序保持逻辑：当 Run 中混合了文本和
     * 图形/公式元素时，需要在遇到非文本元素时将之前的文本先输出，确保
     * 段落元素的顺序与原文档一致。
     * 如果文本为空则不做任何操作。
     *
     * @param textParts    累积的文本片段
     * @param rEl          w:r 元素（用于提取格式属性）
     * @param elements     目标元素列表
     * @param paraStyleId  段落样式 ID，用于字体继承，可为 null
     */
    private void flushTextParts(StringBuilder textParts, Element rEl,
                                 List<ParagraphElement> elements, String paraStyleId) {
        if (textParts.length() == 0) return;
        elements.add(parseRunWithText(rEl, textParts.toString(), paraStyleId));
        textParts.setLength(0); // 清空累积的文本缓冲区
    }

    /**
     * 尝试从 w:drawing 元素中提取图片、单个形状或形状组。
     * <p>
     * 按优先级依次尝试：光栅图片（a:blip引用）> 形状组（wpg:wgp）> 单个形状（wps:wsp）。
     * 成功提取任一类型即返回 true，否则返回 false。
     *
     * @param drawingEl w:drawing 元素
     * @param elements  用于收集解析结果的列表
     * @return 是否成功提取了内容
     */
    private boolean tryAddDrawingOrShape(Element drawingEl, List<ParagraphElement> elements) {
        // 优先尝试光栅图片
        ImageElement img = parseDrawing(drawingEl);
        if (img != null) { elements.add(img); return true; }
        // 其次尝试形状组
        ShapeElement group = parseDrawingGroup(drawingEl);
        if (group != null) { elements.add(group); return true; }
        // 最后尝试单个形状
        ShapeElement shape = parseDrawingShape(drawingEl);
        if (shape != null) { elements.add(shape); return true; }
        return false;
    }

    /**
     * 使用指定的文本内容解析 Run，生成 TextRun 对象。
     * <p>
     * 与 {@link #parseRun(Element)} 类似，但文本内容由调用方提供而非从 w:t 元素提取。
     * 用于混合内容 Run 中文本与图形拆分后的场景。
     *
     * @param rEl  w:r 元素
     * @param text         由调用方提供的文本内容
     * @param paraStyleId  段落样式 ID（w:pStyle），用于段落级字体继承，可为 null
     * @return 解析后的 TextRun 对象
     */
    private TextRun parseRunWithText(Element rEl, String text, String paraStyleId) {
        FontSpec font = null;
        boolean bold = false, italic = false, underline = false, strike = false;
        String highlight = null, shading = null;
        boolean superscript = false, subscript = false;
        String runStyleId = null;

        NodeList rPrNodes = rEl.getElementsByTagNameNS(W, "rPr");
        if (rPrNodes.getLength() > 0) {
            Element rPr = (Element) rPrNodes.item(0);
            runStyleId = getAttrVal(rPr, W, "rStyle", "val");

            String fontName = null, fontSize = null, fontColor = null;
            String eastAsia = null, cs = null;

            // 解析 w:rFonts 各字体槽，优先使用显式值，缺失时从主题引用解析
            NodeList fontsNodes = rPr.getElementsByTagNameNS(W, "rFonts");
            String hint = null; // w:hint 指示主字体方向："eastAsia" → 用东亚字体做主字体
            if (fontsNodes.getLength() > 0) {
                Element fontsEl = (Element) fontsNodes.item(0);
                hint = getNonEmptyAttr(fontsEl, W, "hint");
                fontName = getNonEmptyAttr(fontsEl, W, "ascii");
                if (fontName == null) fontName = getNonEmptyAttr(fontsEl, W, "hAnsi");
                if (fontName == null) {
                    // ascii/hAnsi 字体缺失，尝试从 asciiTheme 主题引用解析
                    String themeRef = getNonEmptyAttr(fontsEl, W, "asciiTheme");
                    if (themeRef != null) fontName = resolveThemeFont(themeRef);
                }
                eastAsia = getNonEmptyAttr(fontsEl, W, "eastAsia");
                if (eastAsia == null) {
                    // 东亚字体缺失，尝试从 eastAsiaTheme 主题引用解析
                    String themeRef = getNonEmptyAttr(fontsEl, W, "eastAsiaTheme");
                    if (themeRef != null) eastAsia = resolveThemeFont(themeRef);
                }
                cs = getNonEmptyAttr(fontsEl, W, "cs");
                if (cs == null) {
                    // 复杂脚本字体缺失，尝试从 csTheme 主题引用解析
                    String themeRef = getNonEmptyAttr(fontsEl, W, "csTheme");
                    if (themeRef != null) cs = resolveThemeFont(themeRef);
                }
            }
            // 解析字号（半磅值，需除以 2 得到 pt）；inline 缺失时沿样式继承链回退
            NodeList szNodes = rPr.getElementsByTagNameNS(W, "sz");
            if (szNodes.getLength() > 0) {
                fontSize = ((Element) szNodes.item(0)).getAttributeNS(W, "val");
                if (fontSize.isEmpty()) fontSize = null;
            }
            if (fontSize == null) {
                fontSize = resolveSzFromStyles(runStyleId, paraStyleId);
            }
            // 解析字体颜色，优先显式值，缺失时从主题颜色引用解析
            NodeList colorNodes = rPr.getElementsByTagNameNS(W, "color");
            if (colorNodes.getLength() > 0) {
                Element colorEl = (Element) colorNodes.item(0);
                fontColor = getNonEmptyAttr(colorEl, W, "val");
                if (fontColor == null) {
                    // 颜色值缺失，尝试从 themeColor 主题引用解析
                    String themeColor = getNonEmptyAttr(colorEl, W, "themeColor");
                    if (themeColor != null) {
                        fontColor = resolveThemeColor(themeColor,
                                getNonEmptyAttr(colorEl, W, "themeTint"),
                                getNonEmptyAttr(colorEl, W, "themeShade"));
                    }
                }
            }

            // 若部分字体槽仍为空，沿样式继承链补全
            if (fontName == null || eastAsia == null || cs == null) {
                FontSpec styleFont = resolveInheritedFontSpec(runStyleId, paraStyleId, null);
                if (styleFont != null) {
                    if (fontName == null) fontName = styleFont.name();
                    if (eastAsia == null) eastAsia = styleFont.eastAsia();
                    if (cs == null) cs = styleFont.cs();
                }
            }

            // 根据 w:hint 属性调整主字体：当 hint="eastAsia" 且拉丁字体为空时，
            // 将东亚字体提升为 CSS font-family 主值
            if (fontName == null && "eastAsia".equals(hint) && eastAsia != null) {
                fontName = eastAsia;
                eastAsia = null;
            }

            font = new FontSpec(fontName, fontSize, fontColor, eastAsia, cs);
            // 布尔格式属性：inline rPr 有该元素时用 inline 值，否则沿样式链回退
            bold = hasElement(rPr, W, "b") ? isBoolPropEnabled(rPr, W, "b") : resolveBoolPropFromStyles(runStyleId, paraStyleId, "b");
            italic = hasElement(rPr, W, "i") ? isBoolPropEnabled(rPr, W, "i") : resolveBoolPropFromStyles(runStyleId, paraStyleId, "i");
            underline = hasElement(rPr, W, "u") ? isBoolPropEnabled(rPr, W, "u") : resolveBoolPropFromStyles(runStyleId, paraStyleId, "u");
            strike = hasElement(rPr, W, "strike") ? isBoolPropEnabled(rPr, W, "strike") : resolveBoolPropFromStyles(runStyleId, paraStyleId, "strike");
            // 文本高亮颜色
            highlight = getAttrVal(rPr, W, "highlight", "val");
            // 字符底纹/背景颜色
            NodeList shdNodes = rPr.getElementsByTagNameNS(W, "shd");
            if (shdNodes.getLength() > 0) {
                Element shdEl = (Element) shdNodes.item(0);
                shading = getNonEmptyAttr(shdEl, W, "fill");
                if (shading == null) {
                    // 填充值缺失，尝试从 themeFill 主题引用解析
                    String themeFill = getNonEmptyAttr(shdEl, W, "themeFill");
                    if (themeFill != null) {
                        shading = resolveThemeColor(themeFill,
                                getNonEmptyAttr(shdEl, W, "themeFillTint"),
                                getNonEmptyAttr(shdEl, W, "themeFillShade"));
                    }
                }
            }
            // 上标/下标对齐
            NodeList vertNodes = rPr.getElementsByTagNameNS(W, "vertAlign");
            if (vertNodes.getLength() > 0) {
                String vertVal = ((Element) vertNodes.item(0)).getAttributeNS(W, "val");
                superscript = "superscript".equals(vertVal);
                subscript = "subscript".equals(vertVal);
            }
        } else {
            // 无 rPr 元素，仍尝试从样式继承链获取字体默认值
            FontSpec styleFont = resolveInheritedFontSpec(runStyleId, paraStyleId, null);
            String styleSz = resolveSzFromStyles(runStyleId, paraStyleId);
            if (styleFont != null || styleSz != null) {
                font = new FontSpec(
                    styleFont != null ? styleFont.name() : null,
                    styleSz,
                    styleFont != null ? styleFont.color() : null,
                    styleFont != null ? styleFont.eastAsia() : null,
                    styleFont != null ? styleFont.cs() : null);
            }
            // 布尔格式属性也沿样式链回退
            bold = resolveBoolPropFromStyles(runStyleId, paraStyleId, "b");
            italic = resolveBoolPropFromStyles(runStyleId, paraStyleId, "i");
            underline = resolveBoolPropFromStyles(runStyleId, paraStyleId, "u");
            strike = resolveBoolPropFromStyles(runStyleId, paraStyleId, "strike");
        }

        return new TextRun(text, font, bold, italic, underline, strike,
                highlight, shading, superscript, subscript, runStyleId);
    }

    /**
     * 解析 mc:AlternateContent 元素，优先使用 mc:Choice 中的内容，降级到 mc:Fallback。
     * <p>
     * OOXML 使用 AlternateContent 表示平台特定内容的选择与降级。
     * 例如新版 Word 使用 DrawingML（mc:Choice），旧版使用 VML（mc:Fallback）。
     * 此方法按官方规范优先尝试 Choice，失败后降级到 Fallback。
     *
     * @param acEl     mc:AlternateContent 元素
     * @param elements 用于收集解析结果的列表
     */
    private void extractAlternateContent(Element acEl, List<ParagraphElement> elements) {
        // 优先尝试 mc:Choice（通常包含新版 DrawingML 内容）
        NodeList choiceNodes = acEl.getElementsByTagNameNS(MC, "Choice");
        for (int i = 0; i < choiceNodes.getLength(); i++) {
            Element choice = (Element) choiceNodes.item(i);
            if (extractContentFromContainer(choice, elements)) return;
        }
        // Choice 全部失败，降级到 mc:Fallback（通常包含旧版 VML 内容）
        NodeList fallbackNodes = acEl.getElementsByTagNameNS(MC, "Fallback");
        for (int i = 0; i < fallbackNodes.getLength(); i++) {
            Element fallback = (Element) fallbackNodes.item(i);
            if (extractContentFromContainer(fallback, elements)) return;
        }
    }

    /**
     * 从 mc:Choice 或 mc:Fallback 容器中提取绘图和嵌入对象内容。
     * <p>
     * 按优先级处理：w:drawing（光栅图片 > 形状组 > 单个形状）> w:object。
     *
     * @param container mc:Choice 或 mc:Fallback 元素
     * @param elements  用于收集解析结果的列表
     * @return 是否成功提取了内容
     */
    private boolean extractContentFromContainer(Element container, List<ParagraphElement> elements) {
        boolean found = false;
        // 处理 w:drawing 元素 — 可能包含光栅图片或 DrawingML 形状
        NodeList drawingNodes = container.getElementsByTagNameNS(W, "drawing");
        for (int i = 0; i < drawingNodes.getLength(); i++) {
            Element drawingEl = (Element) drawingNodes.item(i);
            // 优先尝试光栅图片（a:blip 引用）
            ImageElement img = parseDrawing(drawingEl);
            if (img != null) {
                elements.add(img);
                found = true;
                continue;
            }
            // 其次尝试 DrawingML 形状组（wpg:wgp）— 组内包含 wps:wsp 子形状
            ShapeElement group = parseDrawingGroup(drawingEl);
            if (group != null) {
                elements.add(group);
                found = true;
                continue;
            }
            // 最后尝试顶层单个 DrawingML 形状（wps:wsp）
            ShapeElement shape = parseDrawingShape(drawingEl);
            if (shape != null) {
                elements.add(shape);
                found = true;
            }
        }
        // 检查容器内的 w:object 元素
        NodeList objectNodes = container.getElementsByTagNameNS(W, "object");
        for (int i = 0; i < objectNodes.getLength(); i++) {
            Element objEl = (Element) objectNodes.item(i);
            ImageElement img = parseObject(objEl);
            if (img != null) {
                elements.add(img);
                found = true;
            }
        }
        // 检查容器内的 m:oMath 数学公式元素（可能在 mc:Choice/mc:Fallback 中）
        NodeList mathNodes = container.getElementsByTagNameNS(M, "oMath");
        for (int i = 0; i < mathNodes.getLength(); i++) {
            Element mathEl = (Element) mathNodes.item(i);
            parseMathElements(mathEl, elements);
            found = true;
        }
        NodeList mathParaNodes = container.getElementsByTagNameNS(M, "oMathPara");
        for (int i = 0; i < mathParaNodes.getLength(); i++) {
            Element mathEl = (Element) mathParaNodes.item(i);
            parseMathElements(mathEl, elements);
            found = true;
        }
        return found;
    }

    /**
     * 解析 w:pict 元素，提取 VML 图形中的图片。
     * <p>
     * w:pict 是旧版图形容器，通常包含 v:shape > v:imagedata 结构。
     *
     * @param pictEl   w:pict 元素
     * @param elements 用于收集解析结果的列表
     */
    private void extractPict(Element pictEl, List<ParagraphElement> elements) {
        // w:pict 可以包含 v:shape > v:imagedata
        NodeList shapeNodes = pictEl.getElementsByTagNameNS(V, "shape");
        for (int i = 0; i < shapeNodes.getLength(); i++) {
            Element shapeEl = (Element) shapeNodes.item(i);
            ImageElement img = parseVmlShape(shapeEl);
            if (img != null) elements.add(img);
        }
    }

    /**
     * 解析 w:object 元素，提取旧版 OLE 嵌入对象中的图片。
     * <p>
     * w:object 内部通常包含 v:shape > v:imagedata 结构来显示对象的预览图。
     *
     * @param objectEl w:object 元素
     * @return 解析后的图片元素，若无可用图片则返回 null
     */
    private ImageElement parseObject(Element objectEl) {
        // w:object 包含 v:shape > v:imagedata r:id="rId18"
        NodeList shapeNodes = objectEl.getElementsByTagNameNS(V, "shape");
        if (shapeNodes.getLength() > 0) {
            return parseVmlShape((Element) shapeNodes.item(0));
        }
        return null;
    }

    /**
     * 解析 VML v:shape 元素，提取图片引用和尺寸信息。
     * <p>
     * VML 是旧版矢量标记语言，Word 使用 v:shape + v:imagedata 表示嵌入式图片。
     * 尺寸信息从 v:shape 的 style 属性中以 CSS 样式格式提取，
     * 然后统一转换为 EMU 单位，与 DrawingML 的 cx/cy 保持一致，
     * 以便渲染器统一处理。
     *
     * @param shapeEl v:shape 元素
     * @return 解析后的图片元素，若无有效图片引用则返回 null
     */
    private ImageElement parseVmlShape(Element shapeEl) {
        NodeList imagedataNodes = shapeEl.getElementsByTagNameNS(V, "imagedata");
        if (imagedataNodes.getLength() == 0) return null;

        Element imagedataEl = (Element) imagedataNodes.item(0);
        // 从 r:id 属性获取图片的关系 ID
        String rId = imagedataEl.getAttributeNS(R, "id");
        if (rId.isEmpty()) return null;
        if (!rels.containsKey(rId)) return null;

        String mediaPath = rels.get(rId).target();
        String mimeType = guessMimeType(mediaPath);

        // 从 VML style 属性解析尺寸并转换为 EMU
        int width = 0, height = 0;
        String style = shapeEl.getAttribute("style");
        if (style != null && !style.isEmpty()) {
            width = parseVmlSize(style, "width");
            height = parseVmlSize(style, "height");
        }

        return new ImageElement(mediaPath, mimeType, width, height, WrapMode.INLINE);
    }

    /**
     * 从 VML style 属性字符串中解析指定尺寸属性的值，并转换为 EMU 单位。
     * <p>
     * VML style 使用 CSS 语法，如 "width:100pt;height:50pt"。
     * 支持的单位：pt（磅）、in（英寸）、cm（厘米）、mm（毫米）、px（像素）。
     * 转换为 EMU 是为了与 DrawingML 的 cx/cy 统一处理。
     * EMU 转换系数：1pt = 12700 EMU，1in = 914400 EMU，
     * 1cm = 360000 EMU，1mm = 36000 EMU，1px = 9525 EMU。
     * 若单位无法识别，默认按 pt 处理。
     *
     * @param style VML style 属性字符串
     * @param prop  要解析的属性名（"width" 或 "height"）
     * @return 转换为 EMU 后的整数值，解析失败返回 0
     */
    private int parseVmlSize(String style, String prop) {
        int idx = style.indexOf(prop + ":");
        if (idx < 0) return 0;
        // 定位数值部分的起始和结束位置
        int start = idx + prop.length() + 1;
        int end = start;
        while (end < style.length() && (Character.isDigit(style.charAt(end)) || style.charAt(end) == '.')) {
            end++;
        }
        if (end == start) return 0;
        try {
            double val = Double.parseDouble(style.substring(start, end));
            // 提取单位字符串
            int unitStart = end;
            while (unitStart < style.length() && !Character.isLetter(style.charAt(unitStart))) unitStart++;
            int unitEnd = unitStart;
            while (unitEnd < style.length() && Character.isLetter(style.charAt(unitEnd))) unitEnd++;
            String unit = style.substring(unitStart, unitEnd);
            // 按 OOXML 标准将各种单位统一转换为 EMU
            if ("pt".equals(unit)) return (int) Math.round(val * 12700);
            if ("in".equals(unit)) return (int) Math.round(val * 914400);
            if ("cm".equals(unit)) return (int) Math.round(val * 360000);
            if ("mm".equals(unit)) return (int) Math.round(val * 36000);
            if ("px".equals(unit)) return (int) Math.round(val * 9525);
            // 无法识别单位时默认按 pt 处理
            return (int) Math.round(val * 12700);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 从 w:pPr 元素中提取大纲级别（w:outlineLvl 的 val 属性）。
     * <p>
     * 大纲级别 0-5 对应标题 1-6，用于后续将段落映射为 h1-h6 标签。
     *
     * @param pPrEl w:pPr 元素
     * @return 大纲级别（0-5），若不存在或解析失败则返回 null
     */
    private Integer getOutlineLvl(Element pPrEl) {
        NodeList nodes = pPrEl.getElementsByTagNameNS(W, "outlineLvl");
        if (nodes.getLength() > 0) {
            String val = ((Element) nodes.item(0)).getAttributeNS(W, "val");
            if (!val.isEmpty()) {
                try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    /**
     * 解析 w:ind 元素，提取缩进属性值。
     * <p>
     * 属性值单位为 twips（1/20 磅），由渲染器负责转换为 CSS 单位。
     *
     * @param indEl w:ind 元素
     * @return 缩进对象，包含左缩进、右缩进和首行缩进
     */
    private Indentation parseIndentation(Element indEl) {
        String left = indEl.getAttributeNS(W, "left");
        String right = indEl.getAttributeNS(W, "right");
        String firstLine = indEl.getAttributeNS(W, "firstLine");
        // 空字符串转为 null，便于后续判断属性是否缺失
        if (left.isEmpty()) left = null;
        if (right.isEmpty()) right = null;
        if (firstLine.isEmpty()) firstLine = null;
        return new Indentation(left, right, firstLine);
    }

    /**
     * 从属性 map 解析缩进信息（用于从样式的 rawParaAttrs 回退）。
     *
     * @param attrs w:ind 元素的属性 map（key: left/right/firstLine）
     * @return 缩进对象，若所有属性均为空则返回 null
     */
    private Indentation parseIndentationFromAttrs(Map<String, String> attrs) {
        String left = attrs.get("left");
        if (left != null && left.isEmpty()) left = null;
        String right = attrs.get("right");
        if (right != null && right.isEmpty()) right = null;
        String firstLine = attrs.get("firstLine");
        if (firstLine != null && firstLine.isEmpty()) firstLine = null;
        if (left == null && right == null && firstLine == null) return null;
        return new Indentation(left, right, firstLine);
    }

    // ---- 文本 Run 解析 ----

    /**
     * 解析单纯的 w:r 元素（不含内嵌图形），提取文本和格式属性。
     * <p>
     * 从 w:rPr 子元素中提取字符格式，包括字体、字号、颜色、加粗、斜体、
     * 下划线、删除线、高亮、底纹和上下标等。字体属性缺失时沿样式继承链补全。
     *
     * @param rEl          w:r 元素
     * @param paraStyleId  段落样式 ID（w:pStyle），用于段落级字体继承，可为 null
     * @return 解析后的 TextRun 对象
     */
    private TextRun parseRun(Element rEl, String paraStyleId) {
        String text = "";
        FontSpec font = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        String highlight = null;
        String shading = null;
        boolean superscript = false;
        boolean subscript = false;
        String runStyleId = null;

        NodeList rPrNodes = rEl.getElementsByTagNameNS(W, "rPr");
        if (rPrNodes.getLength() > 0) {
            Element rPr = (Element) rPrNodes.item(0);
            runStyleId = getAttrVal(rPr, W, "rStyle", "val");

            String fontName = null;
            String fontSize = null;
            String fontColor = null;
            String eastAsia = null;
            String cs = null;

            // 解析 w:rFonts 各字体槽
            NodeList fontsNodes = rPr.getElementsByTagNameNS(W, "rFonts");
            String hint = null; // w:hint 指示主字体方向："eastAsia" → 用东亚字体做主字体
            if (fontsNodes.getLength() > 0) {
                Element fontsEl = (Element) fontsNodes.item(0);
                hint = getNonEmptyAttr(fontsEl, W, "hint");
                // 优先使用显式字体名，缺失时从主题引用解析
                fontName = getNonEmptyAttr(fontsEl, W, "ascii");
                if (fontName == null) fontName = getNonEmptyAttr(fontsEl, W, "hAnsi");
                if (fontName == null) {
                    String themeRef = getNonEmptyAttr(fontsEl, W, "asciiTheme");
                    if (themeRef != null) fontName = resolveThemeFont(themeRef);
                }
                eastAsia = getNonEmptyAttr(fontsEl, W, "eastAsia");
                if (eastAsia == null) {
                    String themeRef = getNonEmptyAttr(fontsEl, W, "eastAsiaTheme");
                    if (themeRef != null) eastAsia = resolveThemeFont(themeRef);
                }
                cs = getNonEmptyAttr(fontsEl, W, "cs");
                if (cs == null) {
                    String themeRef = getNonEmptyAttr(fontsEl, W, "csTheme");
                    if (themeRef != null) cs = resolveThemeFont(themeRef);
                }
            }
            // 解析字号（val 为半磅值）；inline 缺失时沿样式继承链回退
            NodeList szNodes = rPr.getElementsByTagNameNS(W, "sz");
            if (szNodes.getLength() > 0) {
                fontSize = ((Element) szNodes.item(0)).getAttributeNS(W, "val");
                if (fontSize.isEmpty()) fontSize = null;
            }
            if (fontSize == null) {
                fontSize = resolveSzFromStyles(runStyleId, paraStyleId);
            }
            // 解析字符颜色，优先显式值，缺失时从主题引用解析
            NodeList colorNodes = rPr.getElementsByTagNameNS(W, "color");
            if (colorNodes.getLength() > 0) {
                Element colorEl = (Element) colorNodes.item(0);
                fontColor = getNonEmptyAttr(colorEl, W, "val");
                if (fontColor == null) {
                    String themeColor = getNonEmptyAttr(colorEl, W, "themeColor");
                    if (themeColor != null) {
                        fontColor = resolveThemeColor(themeColor,
                                getNonEmptyAttr(colorEl, W, "themeTint"),
                                getNonEmptyAttr(colorEl, W, "themeShade"));
                    }
                }
            }

            // 部分字体槽仍为空时，沿样式继承链补全默认值
            if (fontName == null || eastAsia == null || cs == null) {
                FontSpec styleFont = resolveInheritedFontSpec(runStyleId, paraStyleId, null);
                if (styleFont != null) {
                    if (fontName == null) fontName = styleFont.name();
                    if (eastAsia == null) eastAsia = styleFont.eastAsia();
                    if (cs == null) cs = styleFont.cs();
                }
            }

            // 根据 w:hint 属性调整主字体：当 hint="eastAsia" 且拉丁字体为空时，
            // 将东亚字体提升为 CSS font-family 主值
            if (fontName == null && "eastAsia".equals(hint) && eastAsia != null) {
                fontName = eastAsia;
                eastAsia = null;
            }

            font = new FontSpec(fontName, fontSize, fontColor, eastAsia, cs);

            // 布尔格式属性：inline rPr 有该元素时用 inline 值，否则沿样式链回退
            bold = hasElement(rPr, W, "b") ? isBoolPropEnabled(rPr, W, "b") : resolveBoolPropFromStyles(runStyleId, paraStyleId, "b");
            italic = hasElement(rPr, W, "i") ? isBoolPropEnabled(rPr, W, "i") : resolveBoolPropFromStyles(runStyleId, paraStyleId, "i");
            underline = hasElement(rPr, W, "u") ? isBoolPropEnabled(rPr, W, "u") : resolveBoolPropFromStyles(runStyleId, paraStyleId, "u");
            strike = hasElement(rPr, W, "strike") ? isBoolPropEnabled(rPr, W, "strike") : resolveBoolPropFromStyles(runStyleId, paraStyleId, "strike");

            // 文本高亮颜色
            highlight = getAttrVal(rPr, W, "highlight", "val");
            // 字符底纹/背景填充颜色
            NodeList shdNodes = rPr.getElementsByTagNameNS(W, "shd");
            if (shdNodes.getLength() > 0) {
                Element shdEl = (Element) shdNodes.item(0);
                shading = getNonEmptyAttr(shdEl, W, "fill");
                if (shading == null) {
                    // 填充值缺失，尝试从 themeFill 主题引用解析
                    String themeFill = getNonEmptyAttr(shdEl, W, "themeFill");
                    if (themeFill != null) {
                        shading = resolveThemeColor(themeFill,
                                getNonEmptyAttr(shdEl, W, "themeFillTint"),
                                getNonEmptyAttr(shdEl, W, "themeFillShade"));
                    }
                }
            }
            // 解析上标/下标对齐方式
            NodeList vertNodes = rPr.getElementsByTagNameNS(W, "vertAlign");
            if (vertNodes.getLength() > 0) {
                String vertVal = ((Element) vertNodes.item(0)).getAttributeNS(W, "val");
                superscript = "superscript".equals(vertVal);
                subscript = "subscript".equals(vertVal);
            }
        } else {
            // 仅当无 rPr 时，仍尝试从样式继承链获取字体默认值
            FontSpec styleFont = resolveInheritedFontSpec(runStyleId, paraStyleId, null);
            String styleSz = resolveSzFromStyles(runStyleId, paraStyleId);
            if (styleFont != null || styleSz != null) {
                font = new FontSpec(
                    styleFont != null ? styleFont.name() : null,
                    styleSz,
                    styleFont != null ? styleFont.color() : null,
                    styleFont != null ? styleFont.eastAsia() : null,
                    styleFont != null ? styleFont.cs() : null);
            }
            // 布尔格式属性也沿样式链回退
            bold = resolveBoolPropFromStyles(runStyleId, paraStyleId, "b");
            italic = resolveBoolPropFromStyles(runStyleId, paraStyleId, "i");
            underline = resolveBoolPropFromStyles(runStyleId, paraStyleId, "u");
            strike = resolveBoolPropFromStyles(runStyleId, paraStyleId, "strike");
        }

        // 提取文本内容
        NodeList tNodes = rEl.getElementsByTagNameNS(W, "t");
        if (tNodes.getLength() > 0) {
            text = tNodes.item(0).getTextContent();
        }

        return new TextRun(text, font, bold, italic, underline, strike,
                highlight, shading, superscript, subscript, runStyleId);
    }

    // ---- 超链接解析 ----

    /**
     * 解析 w:hyperlink 元素，提取目标 URL 和内部文本 Run。
     * <p>
     * 超链接的目标地址通过 r:id 属性引用关系文件中的 URL。
     * 超链接文本由内部的 w:r 元素组成，逐个解析为 TextRun。
     *
     * @param hlEl        w:hyperlink 元素
     * @param paraStyleId 段落样式 ID，用于字体继承，可为 null
     * @return 解析后的超链接元素
     */
    private HyperlinkElement parseHyperlink(Element hlEl, String paraStyleId) {
        // 通过 r:id 关系 ID 查找目标 URL
        String rId = hlEl.getAttributeNS(R, "id");
        String url = null;
        if (!rId.isEmpty() && rels.containsKey(rId)) {
            url = rels.get(rId).target();
        }
        // 解析超链接内的文本 Run
        ArrayList<TextRun> runs = new ArrayList<TextRun>();
        NodeList children = hlEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (W.equals(el.getNamespaceURI()) && "r".equals(el.getLocalName())) {
                    runs.add(parseRun(el, paraStyleId));
                }
            }
        }
        return new HyperlinkElement(url, Collections.unmodifiableList(runs));
    }

    // ---- 数学公式解析 ----

    /**
     * 解析 OMML 数学公式元素（m:oMath 或 m:oMathPara），将结果添加到 elements 列表。
     * <p>
     * 对于单个 m:oMath，直接解析为一个 MathElement。
     * 对于 m:oMathPara（公式段落容器，可包含多个 oMath），将其中的每个 m:oMath
     * 子公式分别解析为独立的 MathElement，保持公式在多行环境中的顺序。
     *
     * @param mathEl   m:oMath 或 m:oMathPara 元素
     * @param elements 用于收集解析结果的列表
     */
    private void parseMathElements(Element mathEl, List<ParagraphElement> elements) {
        String localName = mathEl.getLocalName();
        if ("oMath".equals(localName)) {
            // 单个公式，直接解析
            MathElement math = parseSingleMath(mathEl);
            if (math != null) elements.add(math);
        } else if ("oMathPara".equals(localName)) {
            // 公式段落容器，可能包含多个 oMath 子公式
            // 各子公式分别解析为独立的 MathElement
            NodeList children = mathEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    Element childEl = (Element) child;
                    if (M.equals(childEl.getNamespaceURI()) && "oMath".equals(childEl.getLocalName())) {
                        MathElement math = parseSingleMath(childEl);
                        if (math != null) elements.add(math);
                    } else if (M.equals(childEl.getNamespaceURI())) {
                        // 其他 math 元素（如 oMath 内的容器），尝试递归提取其中的 oMath
                        parseMathElements(childEl, elements);
                    }
                }
            }
        }
    }

    /**
     * 解析单个 m:oMath 元素为 MathElement 模型对象。
     * <p>
     * 委托 {@link OmmlToLatexConverter} 将 OMML XML 转换为 LaTeX 数学表达式。
     * 同时检查公式中是否包含备用图片引用（r:id），用于不支持 LaTeX 渲染时的降级显示。
     *
     * @param mathEl m:oMath 元素
     * @return 解析后的数学元素，包含 LaTeX 表达式和可选的降级图片路径；若转换失败返回 null
     */
    private MathElement parseSingleMath(Element mathEl) {
        // 将 OMML XML 转换为 LaTeX 字符串
        String latex = OmmlToLatexConverter.convert(mathEl);
        // 同时转换为 MathML（浏览器原生支持，无需外部依赖）
        String mathml = OmmlToMathmlConverter.convert(mathEl);
        if (latex == null || latex.isEmpty()) return null;
        String imagePath = null;
        String mimeType = null;

        // 查找公式内的备用图片引用（r:id 元素）
        // 部分公式在 OMML 中包含 VML 降级图片，用于不支持 OMML 渲染的旧版 Word
        NodeList altNodes = mathEl.getElementsByTagNameNS(R, "id");
        for (int i = 0; i < altNodes.getLength(); i++) {
            String altRId = ((Attr) altNodes.item(i)).getValue();
            if (rels.containsKey(altRId)) {
                RelsParser.Rel rel = rels.get(altRId);
                imagePath = rel.target();
                mimeType = guessMimeType(imagePath);
                break;
            }
        }
        return new MathElement(latex, mathml, imagePath, mimeType);
    }

    // ---- 绘图/图片解析 ----

    /**
     * 解析 w:drawing 元素中的光栅图片（a:blip 引用）。
     * <p>
     * 从 a:blip 元素的 r:embed 属性获取关系 ID，查找图片文件路径。
     * 尺寸从 wp:extent 的 cx/cy 属性获取（EMU 单位）。
     * 环绕模式根据 wp:inline / wp:anchor 和其子元素确定。
     * 若 w:drawing 不包含 a:blip 引用，说明是纯形状而非图片，返回 null。
     *
     * @param drawingEl w:drawing 元素
     * @return 解析后的图片元素，若是纯形状则返回 null
     */
    private ImageElement parseDrawing(Element drawingEl) {
        // 搜索 a:blip 元素获取图片的关系 ID
        NodeList blipNodes = drawingEl.getElementsByTagNameNS(A, "blip");
        String rEmbed = null;
        if (blipNodes.getLength() > 0) {
            rEmbed = ((Element) blipNodes.item(0)).getAttributeNS(R, "embed");
        }
        // 无嵌入引用说明不是光栅图片
        if (rEmbed == null || rEmbed.isEmpty()) return null;
        if (!rels.containsKey(rEmbed)) return null;

        String mediaPath = rels.get(rEmbed).target();
        String mimeType = guessMimeType(mediaPath);

        // 从 wp:extent 获取图片原始尺寸（EMU）
        int width = 0;
        int height = 0;
        WrapMode wrapMode = WrapMode.INLINE;

        NodeList extentNodes = drawingEl.getElementsByTagNameNS(WP, "extent");
        if (extentNodes.getLength() > 0) {
            Element extentEl = (Element) extentNodes.item(0);
            String cx = extentEl.getAttribute("cx");
            String cy = extentEl.getAttribute("cy");
            if (!cx.isEmpty()) width = (int) Long.parseLong(cx);
            if (!cy.isEmpty()) height = (int) Long.parseLong(cy);
        }

        // 根据父元素判断初始环绕模式
        Node parent = drawingEl.getParentNode();
        if (parent instanceof Element) {
            Element parentEl = (Element) parent;
            if (W.equals(parentEl.getNamespaceURI())) {
                String parentLocal = parentEl.getLocalName();
                if ("wrapNone".equals(parentLocal)) {
                    wrapMode = WrapMode.INLINE;
                } else if ("wrapSquare".equals(parentLocal)) {
                    // 四周环绕：根据 wrapText 属性确定左右
                    NodeList sideNodes = parentEl.getElementsByTagNameNS(WP, "wrapSquare");
                    if (sideNodes.getLength() > 0) {
                        String side = ((Element) sideNodes.item(0)).getAttribute("wrapText");
                        wrapMode = "left".equals(side) ? WrapMode.LEFT : WrapMode.RIGHT;
                    } else {
                        wrapMode = WrapMode.LEFT;
                    }
                } else if ("wrapTight".equals(parentLocal)) {
                    wrapMode = WrapMode.LEFT;
                } else if ("wrapTopAndBottom".equals(parentLocal)) {
                    wrapMode = WrapMode.TOP_AND_BOTTOM;
                } else {
                    wrapMode = WrapMode.INLINE;
                }
            }
        }

        // 根据 wp:inline / wp:anchor 覆盖环绕模式
        NodeList inlineNodes = drawingEl.getElementsByTagNameNS(WP, "inline");
        NodeList anchorNodes = drawingEl.getElementsByTagNameNS(WP, "anchor");
        if (inlineNodes.getLength() > 0) {
            // 行内图片无文字环绕
            wrapMode = WrapMode.INLINE;
        } else if (anchorNodes.getLength() > 0) {
            // 锚定图片默认改为左环绕（因为 inline 默认值不适用锚定图）
            if (wrapMode == WrapMode.INLINE) {
                wrapMode = WrapMode.LEFT;
            }
            // 根据锚定元素内的环绕子元素精确判断
            Element anchor = (Element) anchorNodes.item(0);
            if (hasChildLocalName(anchor, WP, "wrapSquare")) wrapMode = WrapMode.LEFT;
            else if (hasChildLocalName(anchor, WP, "wrapTopAndBottom")) wrapMode = WrapMode.TOP_AND_BOTTOM;
            else if (hasChildLocalName(anchor, WP, "wrapTight")) wrapMode = WrapMode.LEFT;
        }

        return new ImageElement(mediaPath, mimeType, width, height, wrapMode);
    }

    // ---- DrawingML 形状解析 ----

    /**
     * 解析 w:drawing 中包含的单个 DrawingML 形状（wps:wsp）。
     * <p>
     * 提取形状的预设几何体类型、变换信息（偏移和尺寸）、填充颜色、
     * 描边颜色和宽度，以及样式引用中的颜色覆盖。
     *
     * @param drawingEl w:drawing 元素
     * @return 解析后的形状元素，若无可用的 wps:wsp 则返回 null
     */
    private ShapeElement parseDrawingShape(Element drawingEl) {
        NodeList wspNodes = drawingEl.getElementsByTagNameNS(WPS, "wsp");
        if (wspNodes.getLength() == 0) return null;
        return parseWsp((Element) wspNodes.item(0), drawingEl);
    }

    /**
     * 解析 w:drawing 中包含的 DrawingML 形状组（wpg:wgp）。
     * <p>
     * 形状组拥有独立的子坐标系（由 wpg:grpSpPr/a:xfrm 的 chOff 和 chExt 定义），
     * 子形状在组内的定位通过 offset 和组坐标系计算得出。
     * 此方法解析组的尺寸、子坐标系参数以及所有子形状。
     *
     * @param drawingEl w:drawing 元素
     * @return 解析后的形状组元素，若无可用的 wpg:wgp 则返回 null
     */
    private ShapeElement parseDrawingGroup(Element drawingEl) {
        NodeList wgpNodes = drawingEl.getElementsByTagNameNS(WPG, "wgp");
        if (wgpNodes.getLength() == 0) return null;
        Element wgpEl = (Element) wgpNodes.item(0);

        // 从 wp:extent 获取组的整体尺寸（EMU）
        int gWidth = 0, gHeight = 0;
        NodeList extentNodes = drawingEl.getElementsByTagNameNS(WP, "extent");
        if (extentNodes.getLength() > 0) {
            Element extEl = (Element) extentNodes.item(0);
            String cx = extEl.getAttribute("cx");
            String cy = extEl.getAttribute("cy");
            if (!cx.isEmpty()) gWidth = (int) Long.parseLong(cx);
            if (!cy.isEmpty()) gHeight = (int) Long.parseLong(cy);
        }
        WrapMode wrapMode = parseWrapMode(drawingEl);

        // 解析组的子坐标系：chOff 定义子坐标原点，chExt 定义子坐标范围
        // 子形状的坐标需根据 chOff/chExt 与组实际尺寸的比例进行缩放
        int chOffX = 0, chOffY = 0, chExtW = gWidth, chExtH = gHeight;
        NodeList grpSpPrNodes = wgpEl.getElementsByTagNameNS(WPG, "grpSpPr");
        if (grpSpPrNodes.getLength() > 0) {
            Element xfrm = firstChild((Element) grpSpPrNodes.item(0), A, "xfrm");
            if (xfrm != null) {
                // 子坐标原点偏移
                Element chOff = firstChild(xfrm, A, "chOff");
                if (chOff != null) {
                    chOffX = attrInt(chOff, "x");
                    chOffY = attrInt(chOff, "y");
                }
                // 子坐标范围
                Element chExt = firstChild(xfrm, A, "chExt");
                if (chExt != null) {
                    chExtW = attrInt(chExt, "cx");
                    chExtH = attrInt(chExt, "cy");
                }
            }
        }

        // 递归解析组内的所有子形状
        ArrayList<ShapeElement> children = new ArrayList<ShapeElement>();
        NodeList childWsp = wgpEl.getElementsByTagNameNS(WPS, "wsp");
        for (int i = 0; i < childWsp.getLength(); i++) {
            ShapeElement shape = parseWsp((Element) childWsp.item(i), null);
            if (shape != null) children.add(shape);
        }

        return new ShapeElement("group", gWidth, gHeight, 0, 0, null, null, 0,
                wrapMode, chOffX, chOffY, chExtW, chExtH, Collections.unmodifiableList(children));
    }

    /**
     * 解析单个 wps:wsp 形状元素，提取几何类型、变换、填充和描边样式。
     * <p>
     * 使用 firstChild 方法获取直属子元素，避免误取嵌套在子形状中的同名元素。
     * 若形状自身尺寸为零，尝试从外层 w:drawing 的 wp:extent 获取默认尺寸。
     * wps:style 中的 lnRef（线型引用）和 fillRef（填充引用）可作为颜色回退值。
     *
     * @param wspEl     wps:wsp 元素
     * @param drawingEl 外层 w:drawing 元素，可为 null（组内子形状时为 null）
     * @return 解析后的形状元素
     */
    private ShapeElement parseWsp(Element wspEl, Element drawingEl) {
        // 默认几何形状为矩形
        String preset = "rect";
        int width = 0, height = 0, offX = 0, offY = 0;
        String fillColor = null, strokeColor = null;
        float strokeWidth = 0;

        // 使用 firstChild 获取直属的 spPr，避免拾取嵌套组子形状的 spPr
        Element spPr = firstChild(wspEl, WPS, "spPr");
        if (spPr == null) {
            // 某些形状使用 a:spPr 作为形状属性容器
            spPr = firstChild(wspEl, A, "spPr");
        }
        if (spPr != null) {

            // 预设几何体（prstGeom 的 prst 属性，如 "rect"、"ellipse"）
            NodeList prstGeomNodes = spPr.getElementsByTagNameNS(A, "prstGeom");
            if (prstGeomNodes.getLength() > 0) {
                String prst = ((Element) prstGeomNodes.item(0)).getAttribute("prst");
                if (!prst.isEmpty()) preset = prst;
            }

            // 变换信息 — 提取形状偏移和尺寸
            NodeList xfrmNodes = spPr.getElementsByTagNameNS(A, "xfrm");
            if (xfrmNodes.getLength() > 0) {
                Element xfrm = (Element) xfrmNodes.item(0);
                Element off = firstChild(xfrm, A, "off");
                if (off != null) {
                    offX = attrInt(off, "x");
                    offY = attrInt(off, "y");
                }
                Element ext = firstChild(xfrm, A, "ext");
                if (ext != null) {
                    width = attrInt(ext, "cx");
                    height = attrInt(ext, "cy");
                }
            }

            // 填充颜色
            fillColor = parseFill(spPr);

            // 描边/线条样式
            NodeList lnNodes = spPr.getElementsByTagNameNS(A, "ln");
            if (lnNodes.getLength() > 0) {
                Element lnEl = (Element) lnNodes.item(0);
                // 线宽属性 w 单位为 EMU，除以 12700 转换为 pt
                String w = lnEl.getAttribute("w");
                if (!w.isEmpty()) {
                    try { strokeWidth = Long.parseLong(w) / 12700f; } catch (NumberFormatException ignored) {}
                }
                Element solidFill = firstChild(lnEl, A, "solidFill");
                if (solidFill != null) {
                    strokeColor = parseColorElement(solidFill);
                }
            }
        }

        // 若 spPr 中无尺寸信息，则从外层 w:drawing 的 wp:extent 获取
        if ((width == 0 || height == 0) && drawingEl != null) {
            NodeList extentNodes = drawingEl.getElementsByTagNameNS(WP, "extent");
            if (extentNodes.getLength() > 0) {
                Element extEl = (Element) extentNodes.item(0);
                if (width == 0) width = attrInt(extEl, "cx");
                if (height == 0) height = attrInt(extEl, "cy");
            }
        }

        // 从 wps:style 中提取样式引用作为颜色回退值
        Element style = firstChild(wspEl, WPS, "style");
        if (style != null) {
            // 线型引用（lnRef）— 提取描边颜色
            Element lnRef = firstChild(style, A, "lnRef");
            if (lnRef != null && strokeColor == null) strokeColor = parseColorElement(lnRef);
            // 填充引用（fillRef）— 提取填充颜色
            Element fillRef = firstChild(style, A, "fillRef");
            if (fillRef != null && fillColor == null) fillColor = parseColorElement(fillRef);
        }

        WrapMode wrapMode = (drawingEl != null) ? parseWrapMode(drawingEl) : WrapMode.INLINE;

        return new ShapeElement(preset, width, height, offX, offY, fillColor, strokeColor, strokeWidth,
                wrapMode, 0, 0, width, height, Collections.<ShapeElement>emptyList());
    }

    /**
     * 从形状属性元素（a:spPr）中解析填充颜色。
     * <p>
     * 按 OOXML 规范优先级处理三种填充类型：
     * <ol>
     *   <li>a:solidFill — 纯色填充，直接提取颜色值</li>
     *   <li>a:noFill — 无填充，返回 "none"</li>
     *   <li>a:gradFill — 渐变填充，取第一个渐变停止点的颜色作为近似值</li>
     * </ol>
     *
     * @param spPr 形状属性元素
     * @return 填充颜色字符串（如 "#FF0000"、"none"），无填充定义时返回 null
     */
    private String parseFill(Element spPr) {
        // 纯色填充
        Element solidFill = firstChild(spPr, A, "solidFill");
        if (solidFill != null) return parseColorElement(solidFill);
        // 无填充
        if (hasChildLocalName(spPr, A, "noFill")) return "none";
        // 渐变填充 — 仅取第一个停止点颜色作为简化近似
        Element gradFill = firstChild(spPr, A, "gradFill");
        if (gradFill != null) {
            NodeList gsNodes = gradFill.getElementsByTagNameNS(A, "gs");
            if (gsNodes.getLength() > 0) return parseColorElement((Element) gsNodes.item(0));
        }
        return null;
    }

    /**
     * 从包含颜色定义的元素中解析颜色值。
     * <p>
     * 支持两种颜色定义方式：
     * <ul>
     *   <li>a:srgbClr — 直接十六进制颜色值（如 val="FF0000"），返回 "#FF0000"</li>
     *   <li>a:schemeClr — 主题颜色引用（如 val="accent1"），从主题中查找具体颜色值</li>
     * </ul>
     *
     * @param el 包含颜色子元素的父元素
     * @return 解析后的颜色字符串（如 "#FF0000"），无法解析时返回 null
     */
    private String parseColorElement(Element el) {
        // 优先尝试 srgbClr（直接十六进制颜色）
        NodeList srgbNodes = el.getElementsByTagNameNS(A, "srgbClr");
        if (srgbNodes.getLength() > 0) {
            String val = ((Element) srgbNodes.item(0)).getAttribute("val");
            if (!val.isEmpty()) return "#" + val;
        }
        // 其次尝试 schemeClr（主题颜色引用）
        NodeList schemeNodes = el.getElementsByTagNameNS(A, "schemeClr");
        if (schemeNodes.getLength() > 0) {
            String val = ((Element) schemeNodes.item(0)).getAttribute("val");
            if (!val.isEmpty() && theme != null) {
                String resolved = resolveThemeColor(val, null, null);
                // 确保返回值带有 # 前缀
                if (resolved != null && !resolved.startsWith("#")) return "#" + resolved;
                return resolved;
            }
        }
        return null;
    }

    /**
     * 从 w:drawing 元素中解析图片/形状的文字环绕模式。
     * <p>
     * 行内图（wp:inline）为 INLINE，锚定图（wp:anchor）根据其子元素判断：
     * wrapSquare → LEFT，wrapTopAndBottom → TOP_AND_BOTTOM，wrapTight → LEFT，
     * 其他默认为 LEFT。
     *
     * @param drawingEl w:drawing 元素
     * @return 解析后的环绕模式
     */
    private WrapMode parseWrapMode(Element drawingEl) {
        NodeList inlineNodes = drawingEl.getElementsByTagNameNS(WP, "inline");
        if (inlineNodes.getLength() > 0) return WrapMode.INLINE;

        NodeList anchorNodes = drawingEl.getElementsByTagNameNS(WP, "anchor");
        if (anchorNodes.getLength() > 0) {
            Element anchor = (Element) anchorNodes.item(0);
            if (hasChildLocalName(anchor, WP, "wrapSquare")) return WrapMode.LEFT;
            if (hasChildLocalName(anchor, WP, "wrapTopAndBottom")) return WrapMode.TOP_AND_BOTTOM;
            if (hasChildLocalName(anchor, WP, "wrapTight")) return WrapMode.LEFT;
            // 锚定图默认使用左环绕
            return WrapMode.LEFT;
        }
        return WrapMode.INLINE;
    }

    /**
     * 获取父元素的第一个匹配指定命名空间和本地名称的直接子元素。
     * <p>
     * 与 getElementsByTagNameNS 不同，此方法只查找直属子节点，
     * 不会递归查找更深层的后代元素。这在处理嵌套形状组时尤为重要，
     * 避免误取子形状中的同名元素。
     *
     * @param parent    父元素
     * @param ns        目标命名空间 URI
     * @param localName 目标元素的本地名称
     * @return 匹配的直接子元素，未找到返回 null
     */
    private static Element firstChild(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c instanceof Element) {
                Element e = (Element) c;
                if (ns.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName())) {
                    return e;
                }
            }
        }
        return null;
    }

    /**
     * 解析元素的整型属性值，解析失败时返回 0。
     * <p>
     * 使用 Long.parseLong 解析后强转 int，以正确处理 OOXML 中常见的
     * 大数值属性（如 EMU 值可能超过 int 范围但实际上低 32 位有效）。
     *
     * @param el   目标元素
     * @param attr 属性名
     * @return 属性的整型值，属性不存在或格式错误时返回 0
     */
    private static int attrInt(Element el, String attr) {
        String v = el.getAttribute(attr);
        if (v == null || v.isEmpty()) return 0;
        try { return (int) Long.parseLong(v); } catch (NumberFormatException e) { return 0; }
    }

    // ---- 表格解析 ----

    /**
     * 解析 w:tbl 元素，提取表格属性和行数据。
     * <p>
     * 表格属性包括：宽度（百分比或磅值）、逐侧外边框与内边框样式、可见性。
     * 宽度转换规则：type="pct" 时 w/50 取百分比，type="dxa" 时 w/20 取磅值。
     * 边框采用逐侧（top/left/bottom/right）独立解析，支持三线表等部分边框可见的样式。
     * 隐藏表格通过 w:tblPr/w:hidden 的 val 属性判断。
     *
     * @param tblEl w:tbl 元素
     * @return 解析后的表格块对象
     */
    private TableBlock parseTable(Element tblEl) {
        ArrayList<TableRow> rows = new ArrayList<TableRow>();

        // 解析表格宽度
        String tblWidth = null;
        NodeList tblWNodes = tblEl.getElementsByTagNameNS(W, "tblW");
        if (tblWNodes.getLength() > 0) {
            Element tblWEl = (Element) tblWNodes.item(0);
            String wVal = tblWEl.getAttributeNS(W, "w");
            String wType = tblWEl.getAttributeNS(W, "type");
            if ("pct".equals(wType) && !wVal.isEmpty()) {
                tblWidth = (Integer.parseInt(wVal) / 50) + "%";
            } else if ("dxa".equals(wType) && !wVal.isEmpty()) {
                tblWidth = (Integer.parseInt(wVal) / 20.0) + "pt";
            } else if (!"auto".equals(wType) && !wVal.isEmpty()) {
                tblWidth = wVal;
            }
            // type="auto" 表示宽度由内容自动决定，不设置 CSS width（等同于 width: auto）
        }

        // 逐侧解析外边框与内边框
        BorderSpec topBorder = BorderSpec.NONE;
        BorderSpec leftBorder = BorderSpec.NONE;
        BorderSpec bottomBorder = BorderSpec.NONE;
        BorderSpec rightBorder = BorderSpec.NONE;
        BorderSpec insideHBorder = BorderSpec.NONE;
        BorderSpec insideVBorder = BorderSpec.NONE;

        // 先提取表格样式 ID
        String tblStyleId = null;
        NodeList tblPrForStyleNodes = tblEl.getElementsByTagNameNS(W, "tblPr");
        if (tblPrForStyleNodes.getLength() > 0) {
            Element tblPrForStyle = (Element) tblPrForStyleNodes.item(0);
            NodeList tblStyleNodes = tblPrForStyle.getElementsByTagNameNS(W, "tblStyle");
            if (tblStyleNodes.getLength() > 0) {
                String val = ((Element) tblStyleNodes.item(0)).getAttributeNS(W, "val");
                if (val != null && !val.isEmpty()) tblStyleId = val;
            }
        }

        NodeList tblBordersNodes = tblEl.getElementsByTagNameNS(W, "tblBorders");
        if (tblBordersNodes.getLength() > 0) {
            Element bordersEl = (Element) tblBordersNodes.item(0);
            topBorder = parseOuterBorderSpec(bordersEl, "top");
            leftBorder = parseOuterBorderSpec(bordersEl, "left");
            bottomBorder = parseOuterBorderSpec(bordersEl, "bottom");
            rightBorder = parseOuterBorderSpec(bordersEl, "right");
            insideHBorder = parseInnerBorderSpec(bordersEl, "insideH");
            insideVBorder = parseInnerBorderSpec(bordersEl, "insideV");
        } else if (tblStyleId != null) {
            // 内联 tblBorders 缺失时，从 table style 继承
            Map<String, Map<String, String>> styleTblBorders = resolveInheritedTblBorders(tblStyleId);
            if (styleTblBorders != null && styleTblBorders.containsKey("tblBorders")) {
                Map<String, String> styleBordersMap = styleTblBorders.get("tblBorders");
                topBorder = parseOuterBorderSpecFromMap(styleBordersMap, "top");
                leftBorder = parseOuterBorderSpecFromMap(styleBordersMap, "left");
                bottomBorder = parseOuterBorderSpecFromMap(styleBordersMap, "bottom");
                rightBorder = parseOuterBorderSpecFromMap(styleBordersMap, "right");
                insideHBorder = parseInnerBorderSpecFromMap(styleBordersMap, "insideH");
                insideVBorder = parseInnerBorderSpecFromMap(styleBordersMap, "insideV");
            }
        }

        // 判断表格可见性
        boolean visibility = true;
        NodeList tblPrNodes = tblEl.getElementsByTagNameNS(W, "tblPr");
        if (tblPrNodes.getLength() > 0) {
            Element tblPr = (Element) tblPrNodes.item(0);
            NodeList hiddenNodes = tblPr.getElementsByTagNameNS(W, "hidden");
            if (hiddenNodes.getLength() > 0) {
                String val = ((Element) hiddenNodes.item(0)).getAttributeNS(W, "val");
                if ("1".equals(val) || "true".equals(val)) visibility = false;
            }
        }

        // 解析表格结构（含合并信息），然后逐行解析单元格边框
        // 先收集所有 w:tr 元素
        ArrayList<Element> trElements = new ArrayList<Element>();
        NodeList children = tblEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (W.equals(el.getNamespaceURI()) && "tr".equals(el.getLocalName())) {
                    trElements.add(el);
                }
            }
        }

        int totalRows = trElements.size();

        // 第一遍：建立网格坐标，收集每个 tc 的 (row, colStart, colSpan, vMerge 状态)
        // grid[row][colSlot] = { tcEl, vMerge(restart/continue/none), gridSpan }
        // 对于 vMerge continue 的单元格，它们占据列位置但不产生独立 <td>
        int gridCols = 0;
        ArrayList<int[]> cellSlots = new ArrayList<int[]>(); // [rowIdx, colStart, gridSpan, vMerge: 0=none,1=restart,2=continue]
        ArrayList<Element> cellElements = new ArrayList<Element>(); // parallel to cellSlots

        for (int ri = 0; ri < totalRows; ri++) {
            int colCursor = 0;
            NodeList tcNodes = trElements.get(ri).getElementsByTagNameNS(W, "tc");
            for (int ti = 0; ti < tcNodes.getLength(); ti++) {
                Element tcEl = (Element) tcNodes.item(ti);
                int gs = 1;
                int vm = 0; // 0=none,1=restart,2=continue
                Element tcPr = firstChildNS(tcEl, W, "tcPr");
                if (tcPr != null) {
                    Element gsEl = firstChildNS(tcPr, W, "gridSpan");
                    if (gsEl != null) {
                        String val = gsEl.getAttributeNS(W, "val");
                        if (!val.isEmpty()) gs = Integer.parseInt(val);
                    }
                    Element vmEl = firstChildNS(tcPr, W, "vMerge");
                    if (vmEl != null) {
                        String val = vmEl.getAttributeNS(W, "val");
                        if ("restart".equals(val)) vm = 1;
                        else vm = 2;
                    }
                }
                cellSlots.add(new int[]{ri, colCursor, gs, vm});
                cellElements.add(tcEl);
                // continue 单元格不计入列跨度（它们的列位置已被 restart 占据）
                colCursor += gs;
            }
            if (colCursor > gridCols) gridCols = colCursor;
        }

        // 第二遍：计算每个 vMerge restart 单元格的实际 rowspan
        // 遍历网格，对于每个 restart 单元格 (ri, colStart)，向下查找同一列位置的 continue 单元格
        HashMap<String, Integer> rowspanMap = new HashMap<String, Integer>();
        for (int idx = 0; idx < cellSlots.size(); idx++) {
            int[] slot = cellSlots.get(idx);
            if (slot[3] != 1) continue; // only restart cells
            int ri = slot[0];
            int colStart = slot[1];
            int span = 1;
            // 向下扫描后续行
            for (int nextRi = ri + 1; nextRi < totalRows; nextRi++) {
                boolean found = false;
                for (int j = 0; j < cellSlots.size(); j++) {
                    int[] s2 = cellSlots.get(j);
                    if (s2[0] == nextRi && s2[1] == colStart && s2[3] == 2) {
                        found = true;
                        break;
                    }
                }
                if (found) span++;
                else break;
            }
            rowspanMap.put(ri + "," + colStart, Integer.valueOf(span));
        }

        // 第三遍：逐行构建 TableCell，使用网格坐标判断位置和 rowspan
        for (int rowIdx = 0; rowIdx < totalRows; rowIdx++) {
            // 解析行高（w:trHeight：val 为 twips，需转换为 pt；hRule 控制高度行为）
            String height = null;
            String hRule = null;
            NodeList trPrNodes = trElements.get(rowIdx).getElementsByTagNameNS(W, "trPr");
            if (trPrNodes.getLength() > 0) {
                Element trPr = (Element) trPrNodes.item(0);
                NodeList trHeightNodes = trPr.getElementsByTagNameNS(W, "trHeight");
                if (trHeightNodes.getLength() > 0) {
                    Element trHeightEl = (Element) trHeightNodes.item(0);
                    String val = trHeightEl.getAttributeNS(W, "val");
                    if (!val.isEmpty()) {
                        try {
                            // twips → pt：除以 20
                            double pt = Long.parseLong(val) / 20.0;
                            // 去除末尾无意义的 .0
                            if (pt == Math.floor(pt) && !Double.isInfinite(pt)) {
                                height = String.valueOf((long) pt);
                            } else {
                                height = String.valueOf(pt);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    hRule = trHeightEl.getAttributeNS(W, "hRule");
                    if (hRule.isEmpty()) hRule = null;
                }
            }

            ArrayList<TableCell> cells = new ArrayList<TableCell>();
            // 查找该行中所有非 continue 的单元格
            for (int idx = 0; idx < cellSlots.size(); idx++) {
                int[] slot = cellSlots.get(idx);
                if (slot[0] != rowIdx) continue;       // not this row
                if (slot[3] == 2) continue;            // skip continue cells
                Element tcEl = cellElements.get(idx);
                int colStart = slot[1];
                int colspan = slot[2];
                int actualRowspan = 1;
                if (slot[3] == 1) { // restart — lookup computed rowspan
                    Integer span = rowspanMap.get(rowIdx + "," + colStart);
                    if (span != null) actualRowspan = span.intValue();
                }
                boolean isTopRow = (rowIdx == 0);
                boolean isBottomRow = (rowIdx + actualRowspan >= totalRows);
                boolean isLeftCol = (colStart == 0);
                boolean isRightCol = (colStart + colspan >= gridCols);

                // 收集 vMerge continue 单元格的 tcBorders 覆盖
                // 在 OOXML 中，continue 单元格可以声明边框来修饰合并区域对应行的边框
                // 对于 rowspan > 1 的单元格，需要将 continue 行的边框覆盖合并进来
                ArrayList<Element> continueTcBorders = null;
                if (actualRowspan > 1) {
                    for (int j = 0; j < cellSlots.size(); j++) {
                        int[] s2 = cellSlots.get(j);
                        if (s2[3] != 2) continue;  // only continue cells
                        if (s2[1] != colStart) continue;  // same column position
                        if (s2[0] <= rowIdx || s2[0] >= rowIdx + actualRowspan) continue; // within span
                        Element continueTcPr = firstChildNS(cellElements.get(j), W, "tcPr");
                        if (continueTcPr != null) {
                            Element borders = firstChildNS(continueTcPr, W, "tcBorders");
                            if (borders != null) {
                                if (continueTcBorders == null) {
                                    continueTcBorders = new ArrayList<Element>();
                                }
                                continueTcBorders.add(borders);
                            }
                        }
                    }
                }

                cells.add(parseTableCell(tcEl, actualRowspan, continueTcBorders,
                        isTopRow, isBottomRow, isLeftCol, isRightCol,
                        topBorder, leftBorder, bottomBorder, rightBorder,
                        insideHBorder, insideVBorder));
            }
            rows.add(new TableRow(Collections.unmodifiableList(cells), height, hRule));
        }

        // 表格外边框不再需要根据单元格进行修正：外边框已下放到各边缘单元格，
        // 由 parseTableCell 中的位置逻辑自动分配（isTopRow→tblTop 等），
        // 因此每个单元格可以独立控制各侧边框，无需整体调整表格级边框。

        return new TableBlock(Collections.unmodifiableList(rows),
                tblWidth,
                topBorder, leftBorder, bottomBorder, rightBorder,
                insideHBorder, insideVBorder,
                visibility);
    }

    /**
     * 获取指定命名空间子元素中最先出现的一个。
     * <p>
     * 与 getElementsByTagNameNS 不同，此方法只查找直接子节点，
     * 避免深层嵌套元素被误匹配。
     */
    private static Element firstChildNS(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (ns.equals(el.getNamespaceURI()) && localName.equals(el.getLocalName())) {
                    return el;
                }
            }
        }
        return null;
    }

    /**
     * 解析 w:tr 元素，提取行高和单元格数据。
     * <p>
     * 行高从 w:trPr/w:trHeight 的 val 属性获取（twips 单位），
     * 在构造 TableRow 前转换为 pt（除以 20）。
     * w:hRule 属性决定 CSS 呈现方式：atLeast → min-height；exact → height。
     *
    /**
     * 解析 w:tc 元素，提取单元格属性和段落内容。
     * <p>
     * 单元格边框根据其在表格中的位置从表格级 tblBorders 解析，
     * 再由本单元格的 tcBorders 和 vMerge continue 单元格的 tcBorders 逐侧覆盖。
     *
     * @param tcEl              w:tc 元素
     * @param rowspan           实际垂直跨度（含 continue 行）
     * @param continueTcBorders vMerge continue 单元格的 tcBorders 列表，可为 null
     * @param isTopRow          是否首行
     * @param isBottomRow       是否末行
     * @param isLeftCol         是否最左列
     * @param isRightCol        是否最右列
     * @param tblTop            表格顶部外边框
     * @param tblLeft           表格左侧外边框
     * @param tblBottom         表格底部外边框
     * @param tblRight          表格右侧外边框
     * @param insideH           行间水平内边框
     * @param insideV           列间垂直内边框
     * @return 解析后的表格单元格对象
     */
    private TableCell parseTableCell(Element tcEl, int rowspan,
                                     ArrayList<Element> continueTcBorders,
                                     boolean isTopRow, boolean isBottomRow,
                                     boolean isLeftCol, boolean isRightCol,
                                     BorderSpec tblTop, BorderSpec tblLeft,
                                     BorderSpec tblBottom, BorderSpec tblRight,
                                     BorderSpec insideH, BorderSpec insideV) {
        int colspan = 1;
        String width = null;
        String bgColor = null;
        boolean visibility = true;

        // 根据单元格位置确定各侧的默认边框
        // 首行顶部 → 表格顶部外边框，否则 → insideH
        // 末行底部 → 表格底部外边框，否则 → insideH
        // 首列左侧 → 表格左侧外边框，否则 → insideV
        // 末列右侧 → 表格右侧外边框，否则 → insideV
        // 若 insideH/insideV 无边框（width==null），且对应的内侧存在显式声明，
        // 则该侧无边框（不回退到外边框）；若内侧未显式声明，则回退到外边框（兼容旧文档）
        BorderSpec cellTop = isTopRow ? tblTop : insideH;
        BorderSpec cellBottom = isBottomRow ? tblBottom : insideH;
        BorderSpec cellLeft = isLeftCol ? tblLeft : insideV;
        BorderSpec cellRight = isRightCol ? tblRight : insideV;

        NodeList tcPrNodes = tcEl.getElementsByTagNameNS(W, "tcPr");
        if (tcPrNodes.getLength() > 0) {
            Element tcPr = (Element) tcPrNodes.item(0);

            // 水平合并列数
            NodeList gridSpanNodes = tcPr.getElementsByTagNameNS(W, "gridSpan");
            if (gridSpanNodes.getLength() > 0) {
                String val = ((Element) gridSpanNodes.item(0)).getAttributeNS(W, "val");
                if (!val.isEmpty()) colspan = Integer.parseInt(val);
            }

            // 单元格宽度
            NodeList tcWNodes = tcPr.getElementsByTagNameNS(W, "tcW");
            if (tcWNodes.getLength() > 0) {
                Element tcWEl = (Element) tcWNodes.item(0);
                String wVal = tcWEl.getAttributeNS(W, "w");
                String wType = tcWEl.getAttributeNS(W, "type");
                if ("pct".equals(wType) && !wVal.isEmpty()) {
                    width = (Integer.parseInt(wVal) / 50) + "%";
                } else if ("dxa".equals(wType) && !wVal.isEmpty()) {
                    width = (Integer.parseInt(wVal) / 20.0) + "pt";
                } else if (!"auto".equals(wType) && !wVal.isEmpty()) {
                    width = wVal;
                }
                // type="auto" 表示宽度由内容自动决定，不设置 CSS width
            }

            // 单元格逐侧边框覆盖 — tcBorders 可对任意侧进行覆盖
            NodeList tcBordersNodes = tcPr.getElementsByTagNameNS(W, "tcBorders");
            if (tcBordersNodes.getLength() > 0) {
                Element tcBordersEl = (Element) tcBordersNodes.item(0);
                cellTop = applyCellBorderOverride(tcBordersEl, "top", cellTop);
                // 对于 rowspan > 1 的合并单元格，bottom 边框由 continue 行决定，
                // 所以 restart 行中的 bottom=nil 不应覆盖位置计算的默认值。
                // 如果 restart 行有 bottom=有效值（非 nil/none），则需要保留。
                if (rowspan <= 1) {
                    cellBottom = applyCellBorderOverride(tcBordersEl, "bottom", cellBottom);
                } else {
                    BorderSpec restartBw = applyCellBorderOverride(tcBordersEl, "bottom", cellBottom);
                    if (restartBw.hasBorder()) {
                        cellBottom = restartBw;
                    }
                    // else: bottom=nil 在 rowspan>1 时保留位置默认值，交给 continue 合并
                }
                cellLeft = applyCellBorderOverride(tcBordersEl, "left", cellLeft);
                cellRight = applyCellBorderOverride(tcBordersEl, "right", cellRight);
            }

            // 合并 vMerge continue 单元格的 tcBorders 覆盖
            // 在 OOXML 中，continue 单元格可以声明边框来修饰合并单元格跨行时的渲染
            // 对于 bottom 侧：最后一行 continue 的 bottom 最优先（倒序遍历先遇到）
            // 对于 top 侧：使用 restart 单元格自身的 top（continue 的 top 通常不影响渲染）
            // 对于 left/right 侧：最后一行 continue 的覆盖优先
            if (continueTcBorders != null) {
                for (int bi = continueTcBorders.size() - 1; bi >= 0; bi--) {
                    Element cbEl = continueTcBorders.get(bi);
                    cellBottom = applyCellBorderOverride(cbEl, "bottom", cellBottom);
                    cellLeft = applyCellBorderOverride(cbEl, "left", cellLeft);
                    cellRight = applyCellBorderOverride(cbEl, "right", cellRight);
                }
            }

            // 单元格背景填充颜色
            NodeList shdNodes = tcPr.getElementsByTagNameNS(W, "shd");
            if (shdNodes.getLength() > 0) {
                Element shdEl = (Element) shdNodes.item(0);
                bgColor = getNonEmptyAttr(shdEl, W, "fill");
                if (bgColor == null) {
                    String themeFill = getNonEmptyAttr(shdEl, W, "themeFill");
                    if (themeFill != null) {
                        bgColor = resolveThemeColor(themeFill,
                                getNonEmptyAttr(shdEl, W, "themeFillTint"),
                                getNonEmptyAttr(shdEl, W, "themeFillShade"));
                    }
                }
            }

            // 判断单元格可见性
            NodeList hiddenNodes = tcPr.getElementsByTagNameNS(W, "hidden");
            if (hiddenNodes.getLength() > 0) {
                String val = ((Element) hiddenNodes.item(0)).getAttributeNS(W, "val");
                if ("1".equals(val) || "true".equals(val)) visibility = false;
            }
        }

        // 解析单元格内的段落
        ArrayList<ParagraphBlock> paragraphs = new ArrayList<ParagraphBlock>();
        NodeList children = tcEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (W.equals(el.getNamespaceURI()) && "p".equals(el.getLocalName())) {
                    paragraphs.add(parseParagraph(el));
                }
            }
        }

        return new TableCell(Collections.unmodifiableList(paragraphs),
                colspan, rowspan, width,
                cellTop, cellLeft, cellBottom, cellRight,
                bgColor, visibility);
    }

    // ---- 边框解析辅助方法 ----

    /**
     * 解析 tblBorders 中外侧边的 BorderSpec。
     * <p>
     * val="none"/"nil" 或 sz="0"/空 → BorderSpec.NONE。
     */
    private BorderSpec parseOuterBorderSpec(Element bordersEl, String side) {
        NodeList nodes = bordersEl.getElementsByTagNameNS(W, side);
        if (nodes.getLength() == 0) return BorderSpec.NONE;
        Element borderEl = (Element) nodes.item(0);
        String val = borderEl.getAttributeNS(W, "val");
        if ("none".equals(val) || "nil".equals(val)) return BorderSpec.NONE;
        String sz = borderEl.getAttributeNS(W, "sz");
        if (sz.isEmpty() || "0".equals(sz)) return BorderSpec.NONE;
        String color = borderEl.getAttributeNS(W, "color");
        return new BorderSpec(sz, color.isEmpty() ? null : color);
    }

    /**
     * 解析 tblBorders 中内侧边（insideH/insideV）的 BorderSpec。
     * <p>
     * 若侧边不存在返回 BorderSpec.NONE。
     */
    private BorderSpec parseInnerBorderSpec(Element bordersEl, String side) {
        String bw = parseBorderWidth(bordersEl, side);
        if (bw == null) return BorderSpec.NONE;
        String bc = parseBorderColor(bordersEl, side);
        return new BorderSpec(bw, bc);
    }

    /**
     * 从样式 map 中解析外侧边的 BorderSpec。
     */
    private BorderSpec parseOuterBorderSpecFromMap(Map<String, String> map, String side) {
        String val = map.get(side + ".val");
        if (val == null || "none".equals(val) || "nil".equals(val)) return BorderSpec.NONE;
        String sz = map.get(side + ".sz");
        if (sz == null || sz.isEmpty() || "0".equals(sz)) return BorderSpec.NONE;
        String color = map.get(side + ".color");
        return new BorderSpec(sz, (color == null || color.isEmpty()) ? null : color);
    }

    /**
     * 从样式 map 中解析内侧边的 BorderSpec。
     */
    private BorderSpec parseInnerBorderSpecFromMap(Map<String, String> map, String side) {
        if (!map.containsKey(side + ".val")) return BorderSpec.NONE;
        String val = map.get(side + ".val");
        if ("none".equals(val) || "nil".equals(val)) return BorderSpec.NONE;
        String sz = map.get(side + ".sz");
        if (sz == null || sz.isEmpty() || "0".equals(sz)) return BorderSpec.NONE;
        String color = map.get(side + ".color");
        return new BorderSpec(sz, (color == null || color.isEmpty()) ? null : color);
    }

    /**
     * 对单元格的某一侧边框应用 tcBorders 覆盖。
     * <p>
     * 若 tcBorders 中该侧有显式设置（val="none" 或有效边框），则替换；
     * 若 val="nil" 或该侧不存在，则保持表格级计算的边框。
     */
    private BorderSpec applyCellBorderOverride(Element tcBordersEl, String side, BorderSpec inherited) {
        NodeList nodes = tcBordersEl.getElementsByTagNameNS(W, side);
        if (nodes.getLength() == 0) return inherited;
        Element borderEl = (Element) nodes.item(0);
        String val = borderEl.getAttributeNS(W, "val");
        // nil = 显式抑制该侧边框（OOXML 规范：单元格级 nil 移除该侧边框）
        if ("nil".equals(val)) return BorderSpec.NONE;
        // none 或 sz=0 → 显式无边框
        if ("none".equals(val)) return BorderSpec.NONE;
        String sz = borderEl.getAttributeNS(W, "sz");
        if (sz.isEmpty() || "0".equals(sz)) return BorderSpec.NONE;
        String color = borderEl.getAttributeNS(W, "color");
        return new BorderSpec(sz, color.isEmpty() ? null : color);
    }

    /**
     * 解析指定侧边的边框宽度（用于内侧边和通用解析）。
     * val="none"/"nil" 或 sz="0"/空 → null。
     */
    private String parseBorderWidth(Element bordersEl, String side) {
        NodeList nodes = bordersEl.getElementsByTagNameNS(W, side);
        if (nodes.getLength() == 0) return null;
        Element borderEl = (Element) nodes.item(0);
        String val = borderEl.getAttributeNS(W, "val");
        if ("none".equals(val) || "nil".equals(val)) return null;
        String sz = borderEl.getAttributeNS(W, "sz");
        if (sz.isEmpty() || "0".equals(sz)) return null;
        return sz;
    }

    /**
     * 解析指定侧边的边框颜色。
     */
    private String parseBorderColor(Element bordersEl, String side) {
        NodeList nodes = bordersEl.getElementsByTagNameNS(W, side);
        if (nodes.getLength() == 0) return null;
        String color = ((Element) nodes.item(0)).getAttributeNS(W, "color");
        return color.isEmpty() ? null : color;
    }

    /**
     * 从 table style 的继承链中解析 tblBorders 信息。
     * <p>
     * 沿 basedOn 链从根样式到叶样式逐层合并 rawTblAttrs，其中 tblBorders 内层 map
     * 使用 "侧名.属性名" 格式存储（由 StylesParser.parseRawAttrs 展平）。
     */
    private Map<String, Map<String, String>> resolveInheritedTblBorders(String tblStyleId) {
        if (styles == null || tblStyleId == null) return null;
        StyleDef def = styles.get(tblStyleId);
        if (def == null) return null;

        ArrayList<StyleDef> chain = new ArrayList<StyleDef>();
        HashSet<String> visited = new HashSet<String>();
        StyleDef cur = def;
        while (cur != null && visited.add(cur.styleId())) {
            chain.add(cur);
            cur = cur.basedOn() != null ? styles.get(cur.basedOn()) : null;
        }
        Collections.reverse(chain);

        Map<String, Map<String, String>> merged = new HashMap<String, Map<String, String>>();
        for (StyleDef s : chain) {
            if (s.rawTblAttrs() != null && !s.rawTblAttrs().isEmpty()) {
                mergeNestedMap(merged, s.rawTblAttrs());
            }
        }
        return merged.isEmpty() ? null : merged;
    }

    /**
     * 深度合并两层嵌套 map（child 的内层 map 会合并到 parent 的对应内层 map 中，
     * 而非整体替换）。用于 rawTblAttrs 的继承合并。
     */
    private static void mergeNestedMap(
            Map<String, Map<String, String>> target,
            Map<String, Map<String, String>> source) {
        for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {
            Map<String, String> existing = target.get(entry.getKey());
            if (existing != null) {
                HashMap<String, String> merged = new HashMap<String, String>(existing);
                merged.putAll(entry.getValue());
                target.put(entry.getKey(), merged);
            } else {
                target.put(entry.getKey(), new HashMap<String, String>(entry.getValue()));
            }
        }
    }

    /**
     * 深拷贝两层嵌套 map。
     */
    private static Map<String, Map<String, String>> deepCopyNestedMap(
            Map<String, Map<String, String>> source) {
        HashMap<String, Map<String, String>> copy = new HashMap<String, Map<String, String>>();
        for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashMap<String, String>(entry.getValue()));
        }
        return copy;
    }

    // ---- 工具方法 ----

    /**
     * 判断字符串是否为 null 或空。
     *
     * @param s 待判断的字符串
     * @return 如果为 null 或空则返回 true
     */
    private static boolean isBlank(String s) { return s == null || s.isEmpty(); }

    /**
     * 在父元素中查找指定子元素，并返回其指定属性的值。
     * <p>
     * 如果找到子元素但属性值为空字符串，返回 null（而非空串），
     * 以便调用方统一使用 null 表示"属性缺失"。
     *
     * @param parent        父元素
     * @param ns            子元素的命名空间 URI
     * @param childLocalName 子元素的本地名称
     * @param attrName       目标属性名（带命名空间）
     * @return 属性值，子元素不存在或属性为空时返回 null
     */
    private static String getAttrVal(Element parent, String ns, String childLocalName, String attrName) {
        NodeList nodes = parent.getElementsByTagNameNS(ns, childLocalName);
        if (nodes.getLength() > 0) {
            String val = ((Element) nodes.item(0)).getAttributeNS(ns, attrName);
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    /**
     * 获取元素指定命名空间属性的值，忽略空字符串。
     * <p>
     * 与 {@link Element#getAttributeNS} 不同，此方法将空字符串转换为 null，
     * 便于调用方区分"属性不存在"和"属性值为空"。
     *
     * @param el       目标元素
     * @param ns       属性的命名空间 URI
     * @param attrName 属性名
     * @return 属性值（非空字符串），属性不存在或为空时返回 null
     */
    private static String getNonEmptyAttr(Element el, String ns, String attrName) {
        String val = el.getAttributeNS(ns, attrName);
        return val.isEmpty() ? null : val;
    }

    /**
     * 检查父元素是否包含指定命名空间和本地名称的后代元素。
     *
     * @param parent         父元素
     * @param ns             目标元素的命名空间 URI
     * @param childLocalName 目标元素的本地名称
     * @return 是否包含至少一个匹配的后代元素
     */
    private static boolean hasElement(Element parent, String ns, String childLocalName) {
        return parent.getElementsByTagNameNS(ns, childLocalName).getLength() > 0;
    }

    /**
     * 检查布尔型 OOXML 属性（如 w:b、w:i、w:strike）是否启用。
     * <p>
     * OOXML 规范：布尔型属性元素存在且无 w:val 属性时表示启用；
     * w:val="0" 或 w:val="false" 表示显式关闭；
     * w:val="1" 或 w:val="true" 表示显式启用。
     *
     * @param parent         父元素
     * @param ns             目标元素的命名空间 URI
     * @param childLocalName 目标元素的本地名称
     * @return 该属性是否启用
     */
    private static boolean isBoolPropEnabled(Element parent, String ns, String childLocalName) {
        NodeList nodes = parent.getElementsByTagNameNS(ns, childLocalName);
        if (nodes.getLength() == 0) return false;
        String val = ((Element) nodes.item(0)).getAttributeNS(ns, "val");
        // 无 val 属性 → 元素存在即启用；val="0"/"false" → 显式关闭
        if (val.isEmpty()) return true;
        String lv = val.toLowerCase();
        return !(lv.equals("0") || lv.equals("false") || lv.equals("off")
                || lv.equals("none") || lv.equals("nil"));
    }

    /**
     * 检查父元素是否包含指定命名空间和本地名称的直接子元素。
     * <p>
     * 仅检查直属子节点，不递归查找后代元素。
     *
     * @param parent    父元素
     * @param ns        目标元素的命名空间 URI
     * @param localName 目标元素的本地名称
     * @return 是否包含至少一个匹配的直接子元素
     */
    private static boolean hasChildLocalName(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element el = (Element) child;
                if (ns.equals(el.getNamespaceURI()) && localName.equals(el.getLocalName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 根据文件扩展名猜测 MIME 类型。
     * <p>
     * 支持 PNG、JPEG、GIF、BMP、SVG、TIFF、EMF、WMF 等常见图片格式，
     * 无法识别时默认返回 "application/octet-stream"。
     *
     * @param path 文件路径（相对或绝对路径）
     * @return 对应的 MIME 类型字符串
     */
    private static String guessMimeType(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "image/tiff";
        if (lower.endsWith(".emf")) return "image/x-emf";
        if (lower.endsWith(".wmf")) return "image/x-wmf";
        return "application/octet-stream";
    }
}
