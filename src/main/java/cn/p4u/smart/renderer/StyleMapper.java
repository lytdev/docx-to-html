package cn.p4u.smart.renderer;

import cn.p4u.smart.model.*;
import java.util.*;

/**
 * 样式映射器：将文档模型中的样式属性转换为 CSS 内联样式字符串。
 *
 * <p>核心职责：
 * <ul>
 *   <li>将 TextRun 的字体、颜色、加粗、斜体、下划线、删除线、高亮、上下标等属性映射为 CSS</li>
 *   <li>将 ParagraphBlock 的对齐方式、缩进映射为 CSS</li>
 *   <li>将 TableBlock / TableCell 的宽度、边框、背景色映射为 CSS</li>
 *   <li>处理 OOXML 特有的单位转换（半磅→pt、八分之一磅→pt、twips→pt）</li>
 *   <li>过滤无效颜色值（auto / none），避免输出无意义的 CSS</li>
 * </ul>
 *
 * <p>主要使用场景：{@link HtmlRenderer} 在渲染各类型内容块时调用本类的静态方法，
 * 获取可直接写入 HTML style 属性的 CSS 字符串。
 */
public final class StyleMapper {

    /** OOXML 高亮色关键字到 CSS 十六进制颜色的映射表 */
    private static final Map<String, String> HIGHLIGHT_COLORS;
    static {
        HashMap<String, String> m = new HashMap<String, String>();
        m.put("yellow", "#FFFF00");
        m.put("green", "#00FF00");
        m.put("cyan", "#00FFFF");
        m.put("magenta", "#FF00FF");
        m.put("blue", "#0000FF");
        m.put("red", "#FF0000");
        m.put("darkBlue", "#00008B");
        m.put("darkCyan", "#008B8B");
        m.put("darkGreen", "#006400");
        m.put("darkMagenta", "#8B008B");
        m.put("darkRed", "#8B0000");
        m.put("darkYellow", "#808000");
        m.put("darkGray", "#808080");
        m.put("lightGray", "#C0C0C0");
        m.put("black", "#000000");
        HIGHLIGHT_COLORS = Collections.unmodifiableMap(m);
    }

    /** 工具类禁止实例化 */
    private StyleMapper() {}

    /**
     * 将文本运行（TextRun）的样式属性映射为 CSS 内联样式字符串。
     *
     * <p>处理的样式包括：字体族、字号、颜色、加粗、斜体、文本装饰（下划线/删除线）、
     * 高亮/底纹背景色、上下标。
     *
     * @param run 文本运行对象，包含字体、颜色、装饰等样式信息
     * @return 拼接好的 CSS 样式字符串，各属性以 "; " 分隔；若无任何样式则返回空字符串
     */
    public static String runStyle(TextRun run) {
        ArrayList<String> parts = new ArrayList<String>();
        FontSpec font = run.font();

        if (font != null) {
            // 构建字体族列表：优先使用拉丁字体，附加东亚字体作为后备
            // 注意：字体名使用单引号包裹，避免与 HTML style="..." 的双引号冲突
            if (font.name() != null && !font.name().isEmpty()) {
                StringBuilder family = new StringBuilder("'" + font.name() + "'");
                if (font.eastAsia() != null && !font.eastAsia().isEmpty()
                        && !font.eastAsia().equals(font.name())) {
                    // 东亚字体与拉丁字体不同时，追加为 CSS font-family 后备值
                    family.append(", '").append(font.eastAsia()).append("'");
                }
                parts.add("font-family: " + family);
            } else if (font.eastAsia() != null && !font.eastAsia().isEmpty()) {
                // 仅有东亚字体时直接使用
                parts.add("font-family: '" + font.eastAsia() + "'");
            }
            if (font.size() != null && !font.size().isEmpty()) {
                // 字号单位转换：OOXML 中 w:sz 以半磅为单位，需除以 2 得到 pt
                String pt = halfPointsToPt(font.size());
                if (pt != null) parts.add("font-size: " + pt);
            }
            if (font.color() != null && !font.color().isEmpty()
                    && !"auto".equalsIgnoreCase(font.color()) && !"none".equalsIgnoreCase(font.color())) {
                // 过滤 "auto" 和 "none" —— 它们在 CSS 中无意义，auto 通常表示默认黑色
                parts.add("color: " + (font.color().startsWith("#") ? "" : "#") + font.color());
            }
        }
        if (run.bold()) parts.add("font-weight: bold");
        if (run.italic()) parts.add("font-style: italic");

        // 合并文本装饰属性：下划线和删除线可同时存在，统一写入 text-decoration
        ArrayList<String> decorations = new ArrayList<String>();
        if (run.underline()) decorations.add("underline");
        if (run.strike()) decorations.add("line-through");
        if (!decorations.isEmpty()) parts.add("text-decoration: " + String.join(" ", decorations));

        // 高亮色处理：优先使用 w:highlight（OOXML 关键字），否则回退到 w:shadingFill（十六进制色值）
        if (run.highlight() != null && !run.highlight().isEmpty()
                && !"auto".equalsIgnoreCase(run.highlight()) && !"none".equalsIgnoreCase(run.highlight())) {
            // 先查高亮色关键字映射表（如 "yellow"→"#FFFF00"），若未匹配则视为原始十六进制值
            String color = HIGHLIGHT_COLORS.containsKey(run.highlight()) ? HIGHLIGHT_COLORS.get(run.highlight()) : "#" + run.highlight();
            parts.add("background-color: " + color);
        } else if (run.shading() != null && !run.shading().isEmpty()
                && !"auto".equalsIgnoreCase(run.shading()) && !"none".equalsIgnoreCase(run.shading())) {
            // 底纹色：过滤 auto/none 后直接作为十六进制颜色输出
            parts.add("background-color: " + (run.shading().startsWith("#") ? "" : "#") + run.shading());
        }

        // 上下标：使用 CSS vertical-align 和 font-size 模拟
        if (run.superscript()) {
            parts.add("vertical-align: super");
            parts.add("font-size: smaller");
        } else if (run.subscript()) {
            parts.add("vertical-align: sub");
            parts.add("font-size: smaller");
        }

        return String.join("; ", parts);
    }

    /**
     * 将段落（ParagraphBlock）的样式属性映射为 CSS 内联样式字符串。
     *
     * <p>处理的样式包括：文本对齐方式、左右缩进和首行缩进。
     *
     * @param para 段落对象，包含对齐方式和缩进信息
     * @return 拼接好的 CSS 样式字符串，各属性以 "; " 分隔；若无任何样式则返回空字符串
     */
    public static String paragraphStyle(ParagraphBlock para) {
        ArrayList<String> parts = new ArrayList<String>();
        if (para.alignment() != null && !para.alignment().isEmpty()) {
            parts.add("text-align: " + para.alignment());
        }
        if (para.indentation() != null) {
            Indentation ind = para.indentation();
            // 缩进值以 twips 为单位，需除以 20 转为 pt
            appendTwips(parts, "margin-left", ind.left());
            appendTwips(parts, "margin-right", ind.right());
            appendTwips(parts, "text-indent", ind.firstLine());
        }
        return String.join("; ", parts);
    }

    /**
     * 将表格（TableBlock）的样式属性映射为 CSS 内联样式字符串。
     *
     * <p>处理的样式包括：宽度、边框合并模式、可见性。
     *
     * <p>表格外边框不在此处输出，而是在各边缘单元格的 cellStyle() 中输出。
     * 这是因为 border-collapse:collapse 模式下，表格级边框会在冲突解决时
     * 覆盖单元格的 border:none，导致 tcBorders val=nil 无法正确抑制边框。
     *
     * @param table 表格对象，包含宽度和可见性信息
     * @return 拼接好的 CSS 样式字符串
     */
    public static String tableStyle(TableBlock table) {
        ArrayList<String> parts = new ArrayList<String>();
        if (table.width() != null) parts.add("width: " + table.width());
        // 不在 <table> 上输出外边框：使用 border-collapse:collapse 时，
        // 表格级边框会在冲突解决中覆盖单元格的 border:none，
        // 导致单元格 tcBorders val=nil 无法正确抑制该侧边框。
        // 改为将所有边框（含表格外边框）下放到各边缘单元格上，
        // 这样每个单元格边缘可以独立控制是否有边框。
        parts.add("border: none");
        // 始终输出 border-collapse: collapse，确保相邻单元格边框正确合并
        parts.add("border-collapse: collapse");
        parts.add("visibility: " + (table.visibility() ? "visible" : "hidden"));
        return String.join("; ", parts);
    }

    /**
     * 将表格单元格（TableCell）的样式属性映射为 CSS 内联样式字符串。
     *
     * <p>处理的样式包括：宽度、逐侧边框、背景色、可见性。
     *
     * @param cell 表格单元格对象
     * @return 拼接好的 CSS 样式字符串
     */
    public static String cellStyle(TableCell cell) {
        ArrayList<String> parts = new ArrayList<String>();
        if (cell.width() != null) parts.add("width: " + cell.width());
        appendBorderSide(parts, "border-top", cell.topBorder());
        appendBorderSide(parts, "border-left", cell.leftBorder());
        appendBorderSide(parts, "border-bottom", cell.bottomBorder());
        appendBorderSide(parts, "border-right", cell.rightBorder());
        // 若所有四侧均无边框，输出 border: none
        if (!cell.topBorder().hasBorder() && !cell.leftBorder().hasBorder()
                && !cell.bottomBorder().hasBorder() && !cell.rightBorder().hasBorder()) {
            parts.add("border: none");
        }
        if (cell.bgColor() != null && !"auto".equalsIgnoreCase(cell.bgColor())) {
            parts.add("background-color: " + (cell.bgColor().startsWith("#") ? "" : "#") + cell.bgColor());
        }
        parts.add("visibility: " + (cell.visibility() ? "visible" : "hidden"));
        return String.join("; ", parts);
    }

    /**
     * 将 BorderSpec 映射为 CSS 逐侧边框属性并追加到 parts 列表。
     * <p>
     * 有边框时输出 "border-{side}: {pt} solid #{color}"，
     * 无边框时输出 "border-{side}: none"。
     *
     * @param parts     CSS 属性列表
     * @param cssProp   CSS 属性名（如 "border-top"）
     * @param border    边框规格
     */
    private static void appendBorderSide(List<String> parts, String cssProp, BorderSpec border) {
        if (border.hasBorder()) {
            String bc = border.color();
            if (bc == null || "auto".equalsIgnoreCase(bc)) bc = "000";
            parts.add(cssProp + ": " + eighthsToPt(border.width()) + " solid #" + bc);
        } else {
            parts.add(cssProp + ": none");
        }
    }

    /**
     * 将半磅值转换为磅值（pt）字符串。
     *
     * <p>OOXML 中 w:sz 属性以半磅为单位，例如 val="24" 表示 12pt 字号。
     *
     * @param halfPoints 半磅值的字符串表示
     * @return 转换后的 pt 字符串（如 "12pt"）；若输入无法解析则返回 null
     */
    private static String halfPointsToPt(String halfPoints) {
        try {
            int hp = Integer.parseInt(halfPoints);
            // 半磅除以 2 得到磅值
            return (hp / 2) + "pt";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 将八分之一磅值转换为磅值（pt）字符串。
     *
     * <p>OOXML 中边框的 w:sz 属性以八分之一磅为单位，例如 val="4" 表示 0.5pt 边框。
     *
     * @param eighths 八分之一磅值的字符串表示
     * @return 转换后的 pt 字符串（如 "0.5pt"）；若输入无法解析则返回 "1pt" 作为默认边框宽度
     */
    private static String eighthsToPt(String eighths) {
        try {
            double val = Integer.parseInt(eighths) / 8.0;
            // 八分之一磅除以 8 得到磅值，保留小数
            return val + "pt";
        } catch (NumberFormatException e) {
            // 解析失败时返回 1pt 默认值，确保边框仍有可见宽度
            return "1pt";
        }
    }

    /**
     * 将 twips 值转换为磅值（pt）并追加到 CSS 属性列表中。
     *
     * <p>OOXML 中缩进和间距属性（w:left / w:right / w:firstLine）以 twips 为单位，
     * 1 twip = 1/20 磅，因此需除以 20 转为 pt。
     *
     * @param parts   CSS 属性列表，转换结果会追加到此列表
     * @param prop    CSS 属性名（如 "margin-left"、"text-indent"）
     * @param twips   twips 值的字符串表示；为 null 或空串时不追加
     */
    private static void appendTwips(List<String> parts, String prop, String twips) {
        if (twips != null && !twips.isEmpty()) {
            try {
                int tw = Integer.parseInt(twips);
                // twips 除以 20 得到磅值
                parts.add(prop + ": " + (tw / 20.0) + "pt");
            } catch (NumberFormatException ignored) {}
        }
    }
}
