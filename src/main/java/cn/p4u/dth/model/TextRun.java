package cn.p4u.dth.model;

import java.util.Objects;

/**
 * 文本运行不可变值类，表示段落中一段具有相同样式的连续文本。
 * <p>
 * 核心职责：封装文本内容及其完整的字符级格式信息（字体、加粗、斜体、下划线、
 * 删除线、高亮、底纹、上下标、样式引用），是文档模型中最细粒度的文本单元。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:r 元素时构建 TextRun；
 * HtmlRenderer 将其渲染为 &lt;span&gt; 并通过 StyleMapper 生成内联 CSS 样式。
 */
public final class TextRun implements ParagraphElement {

    /** 文本内容，String 类型，可能为空字符串但不会为 null */
    private final String text;
    /** 字体规格，FontSpec 类型，可为 null 表示使用默认字体 */
    private final FontSpec font;
    /** 是否加粗 */
    private final boolean bold;
    /** 是否斜体 */
    private final boolean italic;
    /** 是否带下划线 */
    private final boolean underline;
    /** 是否带删除线 */
    private final boolean strike;
    /** 文本高亮颜色（如 "yellow"），可为 null */
    private final String highlight;
    /** 文本底纹/背景色（十六进制），可为 null */
    private final String shading;
    /** 是否为上标 */
    private final boolean superscript;
    /** 是否为下标 */
    private final boolean subscript;
    /** 引用的字符样式 ID，可为 null 表示无样式引用 */
    private final String styleId;

    /**
     * 构造完整的文本运行。
     *
     * @param text        文本内容，String 类型
     * @param font        字体规格，FontSpec 类型，可为 null
     * @param bold        是否加粗，boolean 类型
     * @param italic      是否斜体，boolean 类型
     * @param underline   是否带下划线，boolean 类型
     * @param strike      是否带删除线，boolean 类型
     * @param highlight   高亮颜色名，String 类型，可为 null
     * @param shading     底纹颜色值，String 类型，可为 null
     * @param superscript 是否上标，boolean 类型
     * @param subscript   是否下标，boolean 类型
     * @param styleId     字符样式 ID，String 类型，可为 null
     */
    public TextRun(String text, FontSpec font,
                   boolean bold, boolean italic, boolean underline, boolean strike,
                   String highlight, String shading,
                   boolean superscript, boolean subscript,
                   String styleId) {
        this.text = text;
        this.font = font;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strike = strike;
        this.highlight = highlight;
        this.shading = shading;
        this.superscript = superscript;
        this.subscript = subscript;
        this.styleId = styleId;
    }

    public String text() { return text; }
    public FontSpec font() { return font; }
    public boolean bold() { return bold; }
    public boolean italic() { return italic; }
    public boolean underline() { return underline; }
    public boolean strike() { return strike; }
    public String highlight() { return highlight; }
    public String shading() { return shading; }
    public boolean superscript() { return superscript; }
    public boolean subscript() { return subscript; }
    public String styleId() { return styleId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextRun)) return false;
        TextRun that = (TextRun) o;
        return bold == that.bold
                && italic == that.italic
                && underline == that.underline
                && strike == that.strike
                && superscript == that.superscript
                && subscript == that.subscript
                && Objects.equals(text, that.text)
                && Objects.equals(font, that.font)
                && Objects.equals(highlight, that.highlight)
                && Objects.equals(shading, that.shading)
                && Objects.equals(styleId, that.styleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, font, bold, italic, underline, strike,
                highlight, shading, superscript, subscript, styleId);
    }

    @Override
    public String toString() {
        return "TextRun[text=" + text
                + ", font=" + font
                + ", bold=" + bold
                + ", italic=" + italic
                + ", underline=" + underline
                + ", strike=" + strike
                + ", highlight=" + highlight
                + ", shading=" + shading
                + ", superscript=" + superscript
                + ", subscript=" + subscript
                + ", styleId=" + styleId + "]";
    }
}
