package cn.p4u.smart.renderer;

import cn.p4u.smart.converter.ConversionConfig;
import cn.p4u.smart.model.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * HTML 渲染器 —— 将 {@link DocumentModel} 树转换为完整的 HTML 字符串。
 *
 * <p>核心职责：
 * <ul>
 *   <li>遍历 {@link DocumentModel} 中的 {@link ContentBlock}，将其逐一渲染为 HTML 标签</li>
 *   <li>段落（{@link ParagraphBlock}）自动映射为 h1-h6 或 p 标签，列表段落分组渲染为 ul/ol</li>
 *   <li>段落内元素（文本、超链接、图片、公式、形状）分发到对应的渲染方法</li>
 *   <li>形状（{@link ShapeElement}）转换为内联 SVG，支持组坐标变换和预设几何路径</li>
 *   <li>所有 CSS 均内联为 style 属性，不生成外部样式表</li>
 * </ul>
 *
 * <p>主要使用场景：由 {@link cn.p4u.smart.converter.DocxConverter#convert} 在三阶段管线末尾调用，
 * 将解析器输出的中间模型渲染为可直接嵌入网页的 HTML。
 *
 * <p>本类为无状态工具类，所有方法均为 static，不可实例化。
 */
public final class HtmlRenderer {

    private static final Logger LOG = Logger.getLogger(HtmlRenderer.class.getName());

    /** 私有构造函数，防止实例化 */
    private HtmlRenderer() {}

    /**
     * 将 {@link DocumentModel} 渲染为完整的 HTML 文档字符串。
     *
     * <p>遍历模型中的所有内容块，对列表段落进行分组渲染（连续相同 numId 的段落合并为一个 ul/ol），
     * 其余块交由 {@link #renderBlock} 处理。最终输出包含 DOCTYPE、html/head/body 的完整文档。
     *
     * @param model  已解析的文档模型，包含内容块、编号格式和样式定义
     * @param config 转换配置，控制图片模式（BASE64/LINK）和输出目录等
     * @return 完整的 HTML 文档字符串
     */
    public static String render(DocumentModel model, ConversionConfig config) {
        return render(model, config, null);
    }

    /**
     * 使用指定资源根目录渲染文档模型。资源根目录由高层转换管线管理。
     */
    public static String render(DocumentModel model, ConversionConfig config, Path resourceRoot) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head><meta charset=\"UTF-8\"></head>\n<body>\n");
        List<ContentBlock> blocks = model.content();
        Map<String, String> numFmts = model.numberingFormats();
        for (int i = 0; i < blocks.size(); i++) {
            ContentBlock block = blocks.get(i);
            if (block instanceof ParagraphBlock) {
                ParagraphBlock para = (ParagraphBlock) block;
                // 当前段落属于列表（有 numId 且编号格式存在）时，进入列表分组渲染
                if (para.numId() != null && numFmts.containsKey(para.numId())) {
                    i = renderListGroup(sb, blocks, i, para.numId(), numFmts,
                            config, model.styles(), resourceRoot);
                    continue;
                }
            }
            renderBlock(sb, block, config, model.styles(), resourceRoot);
        }
        sb.append("</body>\n</html>");
        return AdjacentSpanProcessor.process(FigureCaptionProcessor.process(sb.toString()));
    }

    /**
     * 渲染一组连续的列表段落为单个 &lt;ul&gt; 或 &lt;ol&gt; 元素。
     *
     * <p>从 startIdx 开始，将所有具有相同 numId 的连续 ParagraphBlock 渲染为同一个列表。
     * 根据 numId 对应的编号格式决定使用有序列表（ol）还是无序列表（ul）。
     *
     * @param sb        用于拼接 HTML 的 StringBuilder
     * @param blocks    全部内容块列表
     * @param startIdx  列表起始索引
     * @param numId     当前列表的编号 ID
     * @param numFmts   numId → 编号格式映射（如 "bullet"、"decimal" 等）
     * @param config    转换配置
     * @param styles    样式定义映射
     * @return 渲染完成后最后一个列表项的索引（调用者的 for 循环会自增）
     */
    private static int renderListGroup(StringBuilder sb, List<ContentBlock> blocks, int startIdx,
                                        String numId, Map<String, String> numFmts,
                                        ConversionConfig config, Map<String, StyleDef> styles,
                                        Path resourceRoot) {
        String fmt = numFmts.get(numId);
        // "bullet" 格式使用 ul，其余（decimal、lowerLetter 等）使用 ol
        boolean ordered = !"bullet".equals(fmt);
        String listTag = ordered ? "ol" : "ul";

        sb.append("<").append(listTag).append(">\n");
        int i = startIdx;
        while (i < blocks.size()) {
            ContentBlock block = blocks.get(i);
            // 遇到非段落块或 numId 不同的段落，列表结束
            if (!(block instanceof ParagraphBlock)) break;
            ParagraphBlock para = (ParagraphBlock) block;
            if (!numId.equals(para.numId())) break;
            // 将当前段落渲染为列表项
            renderListItem(sb, para, config, styles, resourceRoot);
            i++;
        }
        sb.append("</").append(listTag).append(">\n");
        // 返回最后一个列表项索引减一，因为调用者的 for 循环会自增 i
        return i - 1;
    }

    /**
     * 渲染单个列表项（&lt;li&gt;）。
     *
     * <p>与 {@link #renderParagraph} 类似，但始终使用 li 标签而非 p/h 标签。
     *
     * @param sb     用于拼接 HTML 的 StringBuilder
     * @param para   要渲染的段落块
     * @param config 转换配置
     * @param styles 样式定义映射
     */
    private static void renderListItem(StringBuilder sb, ParagraphBlock para,
                                        ConversionConfig config, Map<String, StyleDef> styles,
                                        Path resourceRoot) {
        String styleAttr = StyleMapper.paragraphStyle(para);
        String paraFontSize = extractParagraphFontSize(para);
        sb.append("<li");
        boolean hasStyle = !styleAttr.isEmpty() || paraFontSize != null;
        if (hasStyle) {
            sb.append(" style=\"");
            if (!styleAttr.isEmpty()) sb.append(styleAttr);
            if (paraFontSize != null) {
                if (!styleAttr.isEmpty()) sb.append("; ");
                sb.append("font-size: ").append(paraFontSize);
            }
            sb.append("\"");
        }
        sb.append(">");
        for (ParagraphElement el : para.elements()) {
            renderParagraphElement(sb, el, config, resourceRoot);
        }
        sb.append("</li>\n");
    }

    /**
     * 根据内容块类型分发渲染逻辑。
     *
     * @param sb     用于拼接 HTML 的 StringBuilder
     * @param block  内容块（段落或表格）
     * @param config 转换配置
     * @param styles 样式定义映射
     */
    private static void renderBlock(StringBuilder sb, ContentBlock block, ConversionConfig config,
                                    Map<String, StyleDef> styles, Path resourceRoot) {
        if (block instanceof ParagraphBlock) {
            renderParagraph(sb, (ParagraphBlock) block, config, styles, resourceRoot);
        } else if (block instanceof TableBlock) {
            renderTable(sb, (TableBlock) block, config, styles, resourceRoot);
        }
    }

    /**
     * 渲染段落为 HTML 标签。
     *
     * <p>通过 {@link #resolveHeadingTag} 判断段落应渲染为标题标签（h1-h6）还是普通段落标签（p），
     * 然后依次渲染段落内所有元素。
     *
     * @param sb     用于拼接 HTML 的 StringBuilder
     * @param para   要渲染的段落块
     * @param config 转换配置
     * @param styles 样式定义映射
     */
    private static void renderParagraph(StringBuilder sb, ParagraphBlock para, ConversionConfig config,
                                        Map<String, StyleDef> styles, Path resourceRoot) {
        // 解析标题级别：如果段落属于标题样式则返回 h1-h6，否则返回 p
        String tag = resolveHeadingTag(para, styles);
        String styleAttr = StyleMapper.paragraphStyle(para);
        // 提取段落中第一个 TextRun 的字号作为段落级 font-size 基线，
        // 确保无显式字号的 MathML 公式等内联元素能够继承正确的字号
        String paraFontSize = extractParagraphFontSize(para);
        sb.append("<").append(tag);
        boolean hasStyle = !styleAttr.isEmpty() || paraFontSize != null;
        if (hasStyle) {
            sb.append(" style=\"");
            if (!styleAttr.isEmpty()) sb.append(styleAttr);
            if (paraFontSize != null) {
                if (!styleAttr.isEmpty()) sb.append("; ");
                sb.append("font-size: ").append(paraFontSize);
            }
            sb.append("\"");
        }
        sb.append(">");
        for (ParagraphElement el : para.elements()) {
            renderParagraphElement(sb, el, config, resourceRoot);
        }
        sb.append("</").append(tag).append(">\n");
    }

    /**
     * 解析段落应使用的 HTML 标签名（h1-h6 或 p）。
     *
     * <p>判定优先级：
     * <ol>
     *   <li>段落自身直接设置了 outlineLvl（最高优先级）</li>
     *   <li>沿样式继承链（basedOn）查找 outlineLvl</li>
     *   <li>根据样式 ID 或名称匹配标题模式（如 "Heading1"、"标题1"、WPS 数字捷径）</li>
     * </ol>
     * 均不匹配时返回 "p"。
     *
     * @param para   段落块
     * @param styles 样式定义映射
     * @return 标签名，如 "h1"、"h3" 或 "p"
     */
    private static String resolveHeadingTag(ParagraphBlock para, Map<String, StyleDef> styles) {
        // 优先级1：段落直接标注了 outlineLvl（0-5 对应 h1-h6）
        if (para.outlineLvl() != null && para.outlineLvl() >= 0 && para.outlineLvl() <= 5) {
            return "h" + (para.outlineLvl() + 1);
        }

        // 优先级2：沿样式继承链查找
        if (para.styleId() != null) {
            HashSet<String> visited = new HashSet<String>();
            String current = para.styleId();
            while (current != null && styles.containsKey(current) && !visited.contains(current)) {
                visited.add(current);
                StyleDef def = styles.get(current);

                // 样式上设置了 outlineLvl
                if (def.outlineLvl() != null && def.outlineLvl() >= 0 && def.outlineLvl() <= 5) {
                    return "h" + (def.outlineLvl() + 1);
                }

                // 回退策略：通过样式 ID 的命名模式推断标题级别
                int level = headingLevelFromId(current);
                if (level > 0) return "h" + level;
                if (def.name() != null) {
                    level = headingLevelFromName(def.name());
                    if (level > 0) return "h" + level;
                }

                // 沿 basedOn 链继续向上查找
                current = def.basedOn();
            }
        }
        return "p";
    }

    /**
     * 根据样式 ID 推断标题级别。
     *
     * <p>支持的模式：
     * <ul>
     *   <li>"Heading1" ～ "Heading6"（英文 Word）</li>
     *   <li>"标题1" ～ "标题6"（中文 WPS）</li>
     *   <li>单字符数字 "2"～"7"（WPS 数字捷径，2=Heading1, 3=Heading2, ...）</li>
     * </ul>
     *
     * @param id 样式 ID
     * @return 标题级别 1-6，无法识别时返回 0
     */
    private static int headingLevelFromId(String id) {
        String lower = id.toLowerCase(Locale.ROOT).trim();
        // 匹配 "heading" 前缀，如 "Heading1"、"heading 2"
        if (lower.startsWith("heading")) {
            String rest = lower.substring(7).trim();
            return parseLevel(rest);
        }
        // 匹配中文 "标题" 前缀，如 "标题1"
        if (lower.startsWith("标题")) {
            String rest = lower.substring(2).trim();
            return parseLevel(rest);
        }
        // WPS 数字捷径：单字符 "2"～"7" 分别映射为 Heading1～Heading6
        // 仅当 ID 为单个数字字符时才启用，避免误匹配普通数字 ID
        if (id.length() == 1) {
            try {
                int n = Integer.parseInt(id);
                if (n >= 2 && n <= 7) return n - 1;
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /**
     * 根据样式名称推断标题级别。
     *
     * <p>与 {@link #headingLevelFromId} 类似，但作用于样式的 name 属性，
     * 支持 "Heading1" 和 "标题1" 两种命名模式。
     *
     * @param name 样式名称
     * @return 标题级别 1-6，无法识别时返回 0
     */
    private static int headingLevelFromName(String name) {
        String lower = name.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("heading")) {
            String rest = lower.substring(7).trim();
            return parseLevel(rest);
        }
        if (lower.startsWith("标题")) {
            String rest = lower.substring(2).trim();
            return parseLevel(rest);
        }
        return 0;
    }

    /**
     * 将字符串解析为整数标题级别。
     *
     * @param s 待解析的数字字符串
     * @return 解析成功的整数值，解析失败返回 0
     */
    private static int parseLevel(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * 从段落元素中提取第一个 {@link TextRun} 的字号（pt 值），用作段落级 font-size 基线。
     *
     * <p>遍历段落的所有元素，找到第一个 TextRun 并提取其 {@link FontSpec} 中已解析的字号。
     * 字号已由 DocumentParser 从半磅值转换为 pt 字符串。找不到 TextRun 时返回 null。
     *
     * @param para 段落块
     * @return 字号字符串（如 "10.5pt"），无 TextRun 时返回 null
     */
    private static String extractParagraphFontSize(ParagraphBlock para) {
        for (ParagraphElement el : para.elements()) {
            if (el instanceof TextRun) {
                FontSpec font = ((TextRun) el).font();
                if (font != null && font.size() != null && !font.size().isEmpty()) {
                    // font.size() 是 OOXML 半磅值字符串（如 "21" = 10.5pt），
                    // 需除以 2 转为 pt
                    try {
                        int hp = Integer.parseInt(font.size());
                        return (hp / 2) + "pt";
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 根据段落元素的实际类型分发到对应的渲染方法。
     *
     * @param sb     用于拼接 HTML 的 StringBuilder
     * @param el     段落元素（文本、超链接、图片、公式或形状）
     * @param config 转换配置
     */
    private static void renderParagraphElement(StringBuilder sb, ParagraphElement el,
                                               ConversionConfig config, Path resourceRoot) {
        if (el instanceof TextRun) {
            renderTextRun(sb, (TextRun) el);
        } else if (el instanceof HyperlinkElement) {
            renderHyperlink(sb, (HyperlinkElement) el);
        } else if (el instanceof ImageElement) {
            renderImage(sb, (ImageElement) el, config, resourceRoot);
        } else if (el instanceof MathElement) {
            renderMath(sb, (MathElement) el, config, resourceRoot);
        } else if (el instanceof ShapeElement) {
            renderShape(sb, (ShapeElement) el);
        }
    }

    /**
     * 渲染文本运行（TextRun）为带有内联样式的 &lt;span&gt; 元素。
     *
     * <p>当拉丁字体与东亚字体不同时，按字符类型分拆为多个 span：
     * <ul>
     *   <li>CJK 字符（汉字、中文标点）→ 东亚字体优先</li>
     *   <li>非 CJK 字符（英文、数字、ASCII 标点）→ 拉丁字体优先</li>
     * </ul>
     * 这与 Word 的按 Unicode 范围选字行为一致。
     *
     * @param sb  用于拼接 HTML 的 StringBuilder
     * @param run 文本运行，包含文本内容和字体/颜色/粗斜等样式
     */
    private static void renderTextRun(StringBuilder sb, TextRun run) {
        FontSpec font = run.font();
        // 仅当拉丁字体与东亚字体均存在且不同时才需要分拆
        boolean needSplit = font != null
                && font.name() != null && !font.name().isEmpty()
                && font.eastAsia() != null && !font.eastAsia().isEmpty()
                && !font.eastAsia().equals(font.name());

        if (!needSplit) {
            // 无需分拆：单字体或相同字体，直接渲染
            String css = StyleMapper.runStyle(run);
            sb.append("<span");
            if (!css.isEmpty()) sb.append(" style=\"").append(css).append("\"");
            sb.append(">").append(escapeHtml(run.text())).append("</span>");
            return;
        }

        // 需要分拆：按字符类型切分为连续的同类型片段
        String text = run.text();
        int len = text.length();
        int start = 0;
        while (start < len) {
            int cp = text.codePointAt(start);
            int charLen = Character.charCount(cp);
            boolean isCjk = isCjkChar(cp);

            // 扫描连续的同类字符
            int end = start + charLen;
            while (end < len) {
                int nextCp = text.codePointAt(end);
                if (isCjkChar(nextCp) != isCjk) break;
                end += Character.charCount(nextCp);
            }

            // 为当前片段生成 CSS：CJK 片段东亚字体优先，非 CJK 片段拉丁字体优先
            String css = StyleMapper.runStyle(run, isCjk);
            sb.append("<span");
            if (!css.isEmpty()) sb.append(" style=\"").append(css).append("\"");
            sb.append(">").append(escapeHtml(text.substring(start, end))).append("</span>");

            start = end;
        }
    }

    /**
     * 判断 Unicode 码点是否属于 CJK 字符范围（汉字、中文标点、全角符号等）。
     *
     * <p>覆盖的 Unicode 区块：
     * <ul>
     *   <li>U+4E00-9FFF  CJK 统一表意文字（常用汉字）</li>
     *   <li>U+3400-4DBF  CJK 统一表意文字扩展 A</li>
     *   <li>U+20000-2EBEF  CJK 统一表意文字扩展 B-F（需代理对）</li>
     *   <li>U+3000-303F  CJK 符号和标点（、。〃々「」等）</li>
     *   <li>U+FF00-FFEF  半角全角形式（，．：；？！等全角标点）</li>
     *   <li>U+FE30-FE4F  CJK 兼容形式</li>
     *   <li>U+2E80-2EFF  CJK 部首补充</li>
     *   <li>U+2F00-2FDF  康熙部首</li>
     *   <li>U+3200-33FF  带圈/括弧 CJK 字母和月份、CJK 兼容</li>
     *   <li>U+F900-FAFF  CJK 兼容表意文字</li>
     * </ul>
     *
     * @param codePoint Unicode 码点
     * @return true 表示属于 CJK 字符范围
     */
    private static boolean isCjkChar(int codePoint) {
        // PRIME / DOUBLE PRIME 等符号（U+2032-U+2037）在 Word 中使用西文字体槽。
        // 它们虽然位于 General Punctuation 区块，却不是中文标点；例如工程制图中的
        // a′、a″ 应跟随拉丁字母使用 w:ascii/w:hAnsi，而不是 w:eastAsia。
        if (codePoint >= 0x2032 && codePoint <= 0x2037) return false;

        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)     // CJK Unified Ideographs (常用汉字)
            || (codePoint >= 0x3400 && codePoint <= 0x4DBF)     // CJK Unified Ideographs Extension A
            || (codePoint >= 0x20000 && codePoint <= 0x2EBEF)   // CJK Ext B-F (生僻字)
            || (codePoint >= 0x3000 && codePoint <= 0x303F)     // CJK Symbols and Punctuation (、。〃「」)
            || (codePoint >= 0xFF00 && codePoint <= 0xFFEF)     // Halfwidth/Fullwidth Forms (，．：；？！)
            || (codePoint >= 0xFE30 && codePoint <= 0xFE4F)     // CJK Compatibility Forms (vertical variants)
            || (codePoint >= 0x2E80 && codePoint <= 0x2EFF)     // CJK Radicals Supplement
            || (codePoint >= 0x2F00 && codePoint <= 0x2FDF)     // Kangxi Radicals
            || (codePoint >= 0x3200 && codePoint <= 0x33FF)     // Enclosed CJK / CJK Compatibility
            || (codePoint >= 0xF900 && codePoint <= 0xFAFF)     // CJK Compatibility Ideographs
            || (codePoint >= 0x2000 && codePoint <= 0x206F)     // General Punctuation (" " — … 等中文常用标点)
            || (codePoint == 0x00B7);                           // MIDDLE DOT (·) 中文间隔号
    }

    /**
     * 渲染超链接为 &lt;a&gt; 元素。
     *
     * <p>当 URL 为空时退化为纯文本渲染（不生成 a 标签），避免生成无效链接。
     * 超链接默认使用 Word 标准蓝色（#0563C1）和下划线样式。
     *
     * @param sb   用于拼接 HTML 的 StringBuilder
     * @param link 超链接元素，包含 URL 和内部文本运行列表
     */
    private static void renderHyperlink(StringBuilder sb, HyperlinkElement link) {
        String url = link.url();
        // URL 为空时退化为纯文本，避免生成无效链接
        if (url == null || url.isEmpty()) {
            for (TextRun run : link.runs()) renderTextRun(sb, run);
            return;
        }
        sb.append("<a href=\"").append(escapeAttr(url)).append("\"")
          .append(" style=\"color: #0563C1; text-decoration: underline;\"");
        sb.append(">");
        for (TextRun run : link.runs()) {
            renderTextRun(sb, run);
        }
        sb.append("</a>");
    }

    /**
     * 渲染图片为 &lt;img&gt; 元素。
     *
     * <p>通过 {@link ImageUriResolver} 统一解析图片 URI，不再根据 ImageMode 分支处理。
     * WMF/EMF 格式在解析前先转换为 PNG，然后以临时文件形式交给 resolver 处理。
     * 图片尺寸由 EMU 转换为像素，文字环绕模式映射为 CSS float/display 样式。
     *
     * @param sb     用于拼接 HTML 的 StringBuilder
     * @param img    图片元素，包含路径、尺寸、MIME 类型和环绕模式
     * @param config 转换配置
     */
    private static void renderImage(StringBuilder sb, ImageElement img,
                                    ConversionConfig config, Path resourceRoot) {
        // 所有图片先按普通图片输出。嵌入型图片使用临时属性传递原始定位信息，
        // HTML 后处理只有在确认它与同级文字混排时才会改为行内图片。
        sb.append("<img class=\"image-block image-item\" data-type=\"image\"");
        if (img.wrapMode() == WrapMode.INLINE) {
            sb.append(" data-docx-embedded=\"true\"");
        }
        if (img.altText() != null && !img.altText().isBlank()) {
            sb.append(" alt=\"").append(escapeAttr(img.altText())).append("\"");
        }
        Path mediaPath = resolveMediaPath(img.mediaPath(), resourceRoot);
        if (mediaPath == null || !Files.exists(mediaPath)) {
            sb.append("><span style=\"color: #999; font-style: italic;\">[image not found]</span>");
            return;
        }

        boolean isWmf = WmfConverter.isWmfOrEmf(img.mimeType());
        int pxW = img.width() > 0 ? emusToPx(img.width()) : 0;
        int pxH = img.height() > 0 ? emusToPx(img.height()) : 0;

        ImageUriResolver resolver = config.imageUriResolver();
        if (isWmf) {
            // Shared pre-step: convert WMF/EMF to PNG before resolving
            try {
                byte[] raw = Files.readAllBytes(mediaPath);
                byte[] png = WmfConverter.convertToPng(raw, pxW, pxH, config);
                if (png != null) {
                    Path tmpFile = Files.createTempFile("wmf2png", ".png");
                    Files.write(tmpFile, png);
                    try {
                        ImageUriResolver.ResolveResult result = resolver.resolve(tmpFile, "image/png");
                        sb.append(" src=\"").append(escapeAttr(result.uri())).append("\"");
                    } finally {
                        Files.deleteIfExists(tmpFile);
                    }
                } else {
                    // WMF conversion failed — try resolving raw file as fallback
                    ImageUriResolver.ResolveResult result = resolver.resolve(mediaPath, img.mimeType());
                    sb.append(" src=\"").append(escapeAttr(result.uri())).append("\"");
                }
            } catch (IOException e) {
                LOG.warning("WMF pre-processing failed for " + mediaPath + ": " + e.getMessage());
                sb.append("><span style=\"color: #999; font-style: italic;\">[image conversion failed]</span>");
                return;
            }
        } else {
            // Non-WMF: resolve directly
            try {
                ImageUriResolver.ResolveResult result = resolver.resolve(mediaPath, img.mimeType());
                sb.append(" src=\"").append(escapeAttr(result.uri())).append("\"");
            } catch (IOException e) {
                LOG.warning("Image resolve failed for " + mediaPath + ": " + e.getMessage());
                sb.append("><span style=\"color: #999; font-style: italic;\">[image resolve failed]</span>");
                return;
            }
        }

        if (img.width() > 0) sb.append(" width=\"").append(pxW).append("\"");
        if (img.height() > 0) sb.append(" height=\"").append(pxH).append("\"");
        // 普通图片不再输出 float，避免 Word 环绕方式破坏转换后的 HTML 布局。
        if (img.wrapMode() == WrapMode.TOP_AND_BOTTOM) {
            sb.append(" style=\"display: block; margin: auto;\"");
        }
        sb.append(">");
    }

    /**
     * 渲染数学公式为 &lt;img&gt; 元素。
     *
     * <p>公式渲染为带有 vertical-align: middle 样式的 img 标签，
     * 图片数据来自 OMML→LaTeX 转换后的备用图片路径。
     * LaTeX 源码保留在 data-latex 属性中，供前端 MathJax/KaTeX 渲染使用。
     *
     * @param sb     用于拼接 HTML 的 StringBuilder
     * @param math   数学公式元素
     * @param config 转换配置
     */
    private static void renderMath(StringBuilder sb, MathElement math,
                                   ConversionConfig config, Path resourceRoot) {
        MathHtmlRenderer.render(sb, math, config, resourceRoot);
    }

    /**
     * 渲染表格为 &lt;table&gt; 元素。
     *
     * <p>逐行逐单元格渲染，支持 colspan/rowspan 合并。
     * 表格和单元格的样式（边框、背景、宽高等）由 {@link StyleMapper} 生成内联 CSS。
     *
     * @param sb     用于拼接 HTML 的 StringBuilder
     * @param table  表格块
     * @param config 转换配置
     * @param styles 样式定义映射
     */
    private static void renderTable(StringBuilder sb, TableBlock table, ConversionConfig config,
                                    Map<String, StyleDef> styles, Path resourceRoot) {
        String tableStyle = StyleMapper.tableStyle(table);
        sb.append("<table style=\"").append(tableStyle).append("\">\n");
        for (TableRow row : table.rows()) {
            String rowStyle = "";
            if (row.height() != null) {
                // atLeast → min-height（最小高度，内容可撑高），exact → height（精确高度）
                if ("atLeast".equals(row.hRule())) {
                    rowStyle = " style=\"min-height: " + row.height() + "pt\"";
                } else {
                    rowStyle = " style=\"height: " + row.height() + "pt\"";
                }
            }
            sb.append("<tr").append(rowStyle).append(">\n");
            for (TableCell cell : row.cells()) {
                String cellStyle = StyleMapper.cellStyle(cell);
                sb.append("<td");
                // 合并列：colspan > 1 时输出 colspan 属性
                if (cell.colspan() > 1) sb.append(" colspan=\"").append(cell.colspan()).append("\"");
                // 合并行：rowspan > 1 时输出 rowspan 属性
                if (cell.rowspan() > 1) sb.append(" rowspan=\"").append(cell.rowspan()).append("\"");
                sb.append(" style=\"").append(cellStyle).append("\">");
                // 单元格内容由一个或多个段落组成，递归渲染
                for (ParagraphBlock para : cell.paragraphs()) {
                    renderParagraph(sb, para, config, styles, resourceRoot);
                }
                sb.append("</td>\n");
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
    }

    /**
     * 将相对媒体路径解析为文件系统上的绝对路径。
     *
     * <p>媒体路径形如 "media/image1.png"，需要拼接解压目录的 word/ 子路径。
     *
     * @param mediaPath 相对媒体路径（如 "media/image1.png"）
     * @param resourceRoot 解压后的资源根目录
     * @return 解析后的绝对路径，若 mediaPath 或 resourceRoot 为 null 则返回 null
     */
    private static Path resolveMediaPath(String mediaPath, Path resourceRoot) {
        if (mediaPath == null || resourceRoot == null) return null;
        return resourceRoot.resolve("word").resolve(mediaPath);
    }

    /**
     * 将 EMU（English Metric Units）转换为像素。
     *
     * <p>1 像素 ≈ 9525 EMU。结果最小为 1，避免生成 0 宽高。
     *
     * @param emus EMU 值
     * @return 像素值，最小为 1
     */
    private static int emusToPx(int emus) {
        return Math.max(1, emus / 9525);
    }

    // ---- 形状 / SVG 渲染 ----

    /**
     * 渲染形状为内联 &lt;svg&gt; 元素。
     *
     * <p>形状尺寸由 EMU 转换为像素，viewBox 与宽高保持一致。
     * 如果形状是组（preset 为 "group"），则递归渲染子形状并应用组坐标变换；
     * 否则渲染为单个 SVG 图形元素（path 或 rect）。
     * 文字环绕模式映射为 SVG 的浮动/居中样式。
     *
     * @param sb    用于拼接 HTML 的 StringBuilder
     * @param shape 形状元素
     */
    private static void renderShape(StringBuilder sb, ShapeElement shape) {
        int pxW = shape.width() > 0 ? emusToPx(shape.width()) : 100;
        int pxH = shape.height() > 0 ? emusToPx(shape.height()) : 100;

        // 构建 SVG 根元素，viewBox 与像素尺寸相同以保证 1:1 映射
        sb.append("<svg width=\"").append(pxW).append("\" height=\"").append(pxH).append("\"")
          .append(" viewBox=\"0 0 ").append(pxW).append(" ").append(pxH).append("\"")
          .append(" xmlns=\"http://www.w3.org/2000/svg\"");

        // 文字环绕模式映射为 CSS 样式
        String floatStyle = wrapModeStyle(shape.wrapMode());
        if (floatStyle != null) sb.append(" style=\"").append(floatStyle).append("\"");

        sb.append(">");

        if ("group".equals(shape.preset()) && !shape.children().isEmpty()) {
            // 组形状：需要将子坐标空间映射到组的像素空间
            renderGroupChildren(sb, shape, pxW, pxH);
        } else {
            // 单个形状：直接渲染 SVG 图形元素
            renderSingleShape(sb, shape, 0, 0, pxW, pxH);
        }

        sb.append("</svg>");
    }

    /**
     * 渲染组形状的子元素。
     *
     * <p>子元素的位置和尺寸定义在组的 child coordinate system（chOff/chExt）中，
     * 这些坐标不是 EMU，而是组变换定义的任意单位空间。
     * 需要通过线性映射将子坐标转换到组的像素空间：
     * <ul>
     *   <li>缩放因子：groupPixelSize / chExt</li>
     *   <li>偏移校正：先减去 chOff 再乘以缩放因子</li>
     * </ul>
     *
     * @param sb      用于拼接 HTML 的 StringBuilder
     * @param group   组形状元素
     * @param groupW  组的像素宽度
     * @param groupH  组的像素高度
     */
    private static void renderGroupChildren(StringBuilder sb, ShapeElement group, int groupW, int groupH) {
        // 提取子坐标系的偏移和范围
        int chOffX = group.chOffX();
        int chOffY = group.chOffY();
        int chExtW = group.chExtW();
        int chExtH = group.chExtH();
        // 计算 chExt → 像素空间的缩放因子
        double scaleX = chExtW > 0 ? (double) groupW / chExtW : 1.0;
        double scaleY = chExtH > 0 ? (double) groupH / chExtH : 1.0;

        for (ShapeElement child : group.children()) {
            // 子元素的宽高和偏移均在 chExt 坐标系中，需要转换为像素空间
            int childPxW = child.width() > 0 ? (int) Math.round(child.width() * scaleX) : 0;
            int childPxH = child.height() > 0 ? (int) Math.round(child.height() * scaleY) : 0;
            // 子元素偏移减去 chOff 基准后再缩放，得到在组像素空间中的位置
            int childPxX = (int) Math.round((child.offX() - chOffX) * scaleX);
            int childPxY = (int) Math.round((child.offY() - chOffY) * scaleY);
            renderSingleShape(sb, child, childPxX, childPxY, childPxW, childPxH);
        }
    }

    /**
     * 渲染单个形状为 SVG 图形元素（&lt;path&gt; 或 &lt;rect&gt;）。
     *
     * <p>根据预设几何类型生成 SVG path 数据；无法识别的预设回退为 rect。
     * 如果形状有位置偏移（x 或 y 不为 0），使用 SVG translate 变换定位。
     *
     * @param sb    用于拼接 HTML 的 StringBuilder
     * @param shape 形状元素
     * @param x     在父 SVG 中的 X 坐标（像素）
     * @param y     在父 SVG 中的 Y 坐标（像素）
     * @param w     形状宽度（像素）
     * @param h     形状高度（像素）
     */
    private static void renderSingleShape(StringBuilder sb, ShapeElement shape,
                                           int x, int y, int w, int h) {
        // 尝试将预设几何名映射为 SVG path 数据
        String pathD = presetToSvgPath(shape.preset(), w, h);
        String fill = shape.fillColor() != null ? shape.fillColor() : "none";
        String stroke = shape.strokeColor() != null ? shape.strokeColor() : "#000";
        float sw = shape.strokeWidth() > 0 ? shape.strokeWidth() : 1;

        boolean noFill = "none".equals(fill);

        if (pathD != null) {
            // 已知预设：使用 path 元素渲染精确几何形状
            sb.append("<path d=\"").append(escapeAttr(pathD)).append("\"");
        } else {
            // 未知预设回退：使用 rect 元素渲染矩形占位
            sb.append("<rect x=\"").append(x).append("\" y=\"").append(y)
              .append("\" width=\"").append(w).append("\" height=\"").append(h).append("\"");
        }

        if (!noFill) sb.append(" fill=\"").append(fill).append("\"");
        else sb.append(" fill=\"none\"");

        sb.append(" stroke=\"").append(stroke).append("\"")
          .append(" stroke-width=\"").append(sw).append("\"");

        // 形状有偏移时使用 translate 变换定位（path 从 0,0 开始绘制）
        if (x != 0 || y != 0) {
            sb.append(" transform=\"translate(").append(x).append(",").append(y).append(")\"");
        }

        sb.append("/>");
    }

    /**
     * 将 DrawingML 预设几何名称映射为 SVG path 数据。
     *
     * <p>路径坐标使用绝对值，基于传入的宽高 w、h 按比例计算，
     * 确保不同尺寸下形状比例一致。无法识别的预设返回 null，
     * 由调用方回退为 rect 元素。
     *
     * @param preset 预设几何名称（如 "rect"、"ellipse"、"rightArrow" 等）
     * @param w      形状宽度（像素）
     * @param h      形状高度（像素）
     * @return SVG path 数据字符串，未知预设返回 null
     */
    private static String presetToSvgPath(String preset, int w, int h) {
        if (preset == null) return null;
        // 路径坐标相对于 w、h 按比例生成，保证缩放时形状不变形
        if ("rect".equals(preset)) return rectPath(w, h);
        if ("parallelogram".equals(preset)) return parallelogramPath(w, h);
        if ("rightArrowCallout".equals(preset)) return rightArrowCalloutPath(w, h);
        if ("quadArrowCallout".equals(preset)) return quadArrowCalloutPath(w, h);
        if ("roundRect".equals(preset)) return roundRectPath(w, h);
        if ("ellipse".equals(preset)) return ellipsePath(w, h);
        if ("triangle".equals(preset)) return trianglePath(w, h);
        if ("rightArrow".equals(preset)) return rightArrowPath(w, h);
        if ("leftArrow".equals(preset)) return leftArrowPath(w, h);
        if ("diamond".equals(preset)) return diamondPath(w, h);
        if ("pentagon".equals(preset)) return pentagonPath(w, h);
        if ("hexagon".equals(preset)) return hexagonPath(w, h);
        if ("star5".equals(preset)) return star5Path(w, h);
        if ("chevron".equals(preset)) return chevronPath(w, h);
        if ("trapezoid".equals(preset)) return trapezoidPath(w, h);
        return null; // 未知预设 → 回退为 rect
    }

    /**
     * 将文字环绕模式映射为 CSS 样式字符串。
     *
     * @param wm 环绕模式
     * @return CSS 样式字符串，INLINE 模式返回 null（无需额外样式）
     */
    private static String wrapModeStyle(WrapMode wm) {
        if (wm == WrapMode.LEFT) return "float: left;";
        if (wm == WrapMode.RIGHT) return "float: right;";
        if (wm == WrapMode.TOP_AND_BOTTOM) return "display: block; margin: auto;";
        return null; // INLINE → 无额外样式
    }

    // ---- 预设几何 SVG 路径（按 w,h 比例生成） ----

    /**
     * 矩形路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据
     */
    private static String rectPath(int w, int h) {
        return "M0,0 L" + w + ",0 L" + w + "," + h + " L0," + h + " Z";
    }

    /**
     * 圆角矩形路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，圆角半径为短边的 1/5
     */
    private static String roundRectPath(int w, int h) {
        int r = Math.min(w, h) / 5;
        return "M" + r + ",0 L" + (w - r) + ",0 Q" + w + ",0 " + w + "," + r
             + " L" + w + "," + (h - r) + " Q" + w + "," + h + " " + (w - r) + "," + h
             + " L" + r + "," + h + " Q0," + h + " 0," + (h - r)
             + " L0," + r + " Q0,0 " + r + ",0 Z";
    }

    /**
     * 平行四边形路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，倾斜量为宽度的 1/5
     */
    private static String parallelogramPath(int w, int h) {
        int skew = w / 5;
        return "M" + skew + ",0 L" + w + ",0 L" + (w - skew) + "," + h + " L0," + h + " Z";
    }

    /**
     * 梯形路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，顶部缩进为宽度的 1/5
     */
    private static String trapezoidPath(int w, int h) {
        int indent = w / 5;
        return "M" + indent + ",0 L" + (w - indent) + ",0 L" + w + "," + h + " L0," + h + " Z";
    }

    /**
     * 椭圆路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，使用两段弧线拼合
     */
    private static String ellipsePath(int w, int h) {
        int rx = w / 2, ry = h / 2;
        return "M" + rx + ",0 A" + rx + "," + ry + " 0 1,1 " + rx + "," + h
             + " A" + rx + "," + ry + " 0 1,1 " + rx + ",0 Z";
    }

    /**
     * 三角形路径（顶点在顶部中间）
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据
     */
    private static String trianglePath(int w, int h) {
        return "M" + (w / 2) + ",0 L" + w + "," + h + " L0," + h + " Z";
    }

    /**
     * 菱形路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据
     */
    private static String diamondPath(int w, int h) {
        int hw = w / 2, hh = h / 2;
        return "M" + hw + ",0 L" + w + "," + hh + " L" + hw + "," + h + " L0," + hh + " Z";
    }

    /**
     * 右箭头路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，箭头头部占宽度的 1/3
     */
    private static String rightArrowPath(int w, int h) {
        int stem = h / 3;
        int headX = w * 2 / 3;
        return "M0," + stem + " L" + headX + "," + stem + " L" + headX + ",0"
             + " L" + w + "," + (h / 2) + " L" + headX + "," + h + " L" + headX + "," + (h - stem)
             + " L0," + (h - stem) + " Z";
    }

    /**
     * 左箭头路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，箭头头部占宽度的 1/3
     */
    private static String leftArrowPath(int w, int h) {
        int stem = h / 3;
        int headX = w / 3;
        return "M" + headX + "," + stem + " L" + w + "," + stem + " L" + w + "," + (h - stem)
             + " L" + headX + "," + (h - stem) + " L" + headX + "," + h
             + " L0," + (h / 2) + " L" + headX + ",0 Z";
    }

    /**
     * V 形箭头路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，缩进为宽度的 1/3
     */
    private static String chevronPath(int w, int h) {
        int indent = w / 3;
        return "M0,0 L" + (w - indent) + ",0 L" + w + "," + (h / 2)
             + " L" + (w - indent) + "," + h + " L0," + h
             + " L" + indent + "," + (h / 2) + " Z";
    }

    /**
     * 正五边形路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，使用三角函数计算顶点坐标
     */
    private static String pentagonPath(int w, int h) {
        double cx = w / 2.0, cy = h / 2.0;
        double r = Math.min(w, h) / 2.0;
        StringBuilder sb = new StringBuilder("M");
        // 从顶部顶点开始，每隔 72 度生成一个顶点
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(-90 + i * 72);
            double px = cx + r * Math.cos(angle);
            double py = cy + r * Math.sin(angle);
            if (i > 0) sb.append(" L");
            sb.append(fmt(px)).append(",").append(fmt(py));
        }
        sb.append(" Z");
        return sb.toString();
    }

    /**
     * 正六边形路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，使用三角函数计算顶点坐标
     */
    private static String hexagonPath(int w, int h) {
        double cx = w / 2.0, cy = h / 2.0;
        double rx = w / 2.0, ry = h / 2.0;
        StringBuilder sb = new StringBuilder("M");
        // 从顶部顶点开始，每隔 60 度生成一个顶点
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(-90 + i * 60);
            double px = cx + rx * Math.cos(angle);
            double py = cy + ry * Math.sin(angle);
            if (i > 0) sb.append(" L");
            sb.append(fmt(px)).append(",").append(fmt(py));
        }
        sb.append(" Z");
        return sb.toString();
    }

    /**
     * 五角星路径
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据，外圆半径取短边一半，内圆半径为外圆的 0.382 倍
     */
    private static String star5Path(int w, int h) {
        double cx = w / 2.0, cy = h / 2.0;
        double outer = Math.min(w, h) / 2.0, inner = outer * 0.382;
        StringBuilder sb = new StringBuilder("M");
        // 交替使用外圆和内圆半径生成 10 个顶点
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            double r = (i % 2 == 0) ? outer : inner;
            double px = cx + r * Math.cos(angle);
            double py = cy + r * Math.sin(angle);
            if (i > 0) sb.append(" L");
            sb.append(fmt(px)).append(",").append(fmt(py));
        }
        sb.append(" Z");
        return sb.toString();
    }

    /**
     * 右箭头标注路径（主体矩形 + 右侧箭头）
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据
     */
    private static String rightArrowCalloutPath(int w, int h) {
        int margin = (int) (w * 0.05);
        int bodyRight = (int) (w * 0.65);
        int arrowTip = w;
        int arrowTop = (int) (h * 0.3);
        int arrowBot = (int) (h * 0.7);
        return "M" + margin + "," + margin
             + " L" + bodyRight + "," + margin
             + " L" + bodyRight + "," + arrowTop
             + " L" + arrowTip + "," + (h / 2)
             + " L" + bodyRight + "," + arrowBot
             + " L" + bodyRight + "," + (h - margin)
             + " L" + margin + "," + (h - margin)
             + " Z";
    }

    /**
     * 四向箭头标注路径（上/下/左/右四个方向的箭头 + 中心矩形）
     * @param w 宽度
     * @param h 高度
     * @return SVG path 数据
     */
    private static String quadArrowCalloutPath(int w, int h) {
        int cx = w / 2, cy = h / 2;
        int bodyL = (int) (w * 0.25), bodyR = (int) (w * 0.75);
        int bodyT = (int) (h * 0.25), bodyB = (int) (h * 0.75);
        int neck = (int) (w * 0.12);

        return "M" + cx + ",0"                               // 上箭头尖
             + " L" + (cx + neck) + "," + bodyT               // 上箭头右翼
             + " L" + bodyR + "," + bodyT                     // 主体右上角
             + " L" + bodyR + "," + (cy - neck)               // 右箭头上翼
             + " L" + w + "," + cy                            // 右箭头尖
             + " L" + bodyR + "," + (cy + neck)               // 右箭头下翼
             + " L" + bodyR + "," + bodyB                     // 主体右下角
             + " L" + (cx + neck) + "," + bodyB               // 下箭头右翼
             + " L" + cx + "," + h                            // 下箭头尖
             + " L" + (cx - neck) + "," + bodyB               // 下箭头左翼
             + " L" + bodyL + "," + bodyB                     // 主体左下角
             + " L" + bodyL + "," + (cy + neck)               // 左箭头下翼
             + " L" + 0 + "," + cy                            // 左箭头尖
             + " L" + bodyL + "," + (cy - neck)               // 左箭头上翼
             + " L" + bodyL + "," + bodyT                     // 主体左上角
             + " L" + (cx - neck) + "," + bodyT               // 上箭头左翼
             + " Z";
    }

    /**
     * 格式化浮点数为保留一位小数的字符串，使用 ROOT locale 避免地区化小数点问题。
     *
     * @param v 浮点数值
     * @return 格式化后的字符串
     */
    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /**
     * 转义 HTML 文本中的特殊字符，防止 XSS 和排版错误。
     *
     * <p>转义规则：&amp; → &amp;amp;，&lt; → &amp;lt;，&gt; → &amp;gt;，" → &amp;quot;
     *
     * @param text 原始文本
     * @return 转义后的安全文本
     */
    private static String escapeHtml(String text) {
        return HtmlEscaper.text(text);
    }

    /**
     * 转义 HTML 属性值中的特殊字符，防止属性注入。
     *
     * <p>仅转义 &amp; 和双引号，因为在属性值（由双引号包裹）中 &lt; &gt; 不需要额外转义。
     *
     * @param value 原始属性值
     * @return 转义后的安全属性值
     */
    private static String escapeAttr(String value) {
        return HtmlEscaper.attribute(value);
    }
}
