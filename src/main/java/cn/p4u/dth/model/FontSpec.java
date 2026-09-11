package cn.p4u.dth.model;

import java.util.Objects;

/**
 * 字体规格不可变值类，描述一段文本的完整字体信息。
 * <p>
 * 核心职责：封装字体的五个维度信息（西文字体名、字号、颜色、东亚字体、复杂脚本字体），
 * 用于 TextRun 的排版渲染。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:rPr/w:rFonts 等元素时构建 FontSpec，
 * HtmlRenderer 通过 StyleMapper 将其转换为 CSS font-family/font-size/color 样式。
 */
public final class FontSpec {

    /** 西文字体名（对应 w:rFonts 的 ascii 属性） */
    private final String name;
    /** 字号，单位为半磅（对应 w:sz 的 val 属性），可能为 null */
    private final String size;
    /** 字体颜色，十六进制值如 "FF0000"，可能为 null */
    private final String color;
    /** 东亚字体名（对应 w:rFonts 的 eastAsia 属性），用于中日韩文字 */
    private final String eastAsia;
    /** 复杂脚本字体名（对应 w:rFonts 的 cs 属性），用于阿拉伯/希伯来文 */

    private final String cs;

    /**
     * 构造完整的字体规格。
     *
     * @param name      西文字体名，String 类型，可为 null 表示未指定
     * @param size      字号（半磅值），String 类型，可为 null 表示未指定
     * @param color     字体颜色（十六进制），String 类型，可为 null 表示未指定
     * @param eastAsia  东亚字体名，String 类型，可为 null 表示未指定
     * @param cs        复杂脚本字体名，String 类型，可为 null 表示未指定
     */
    public FontSpec(String name, String size, String color,
                    String eastAsia, String cs) {
        this.name = name;
        this.size = size;
        this.color = color;
        this.eastAsia = eastAsia;
        this.cs = cs;
    }

    public String name() { return name; }
    public String size() { return size; }
    public String color() { return color; }
    public String eastAsia() { return eastAsia; }
    public String cs() { return cs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FontSpec)) return false;
        FontSpec that = (FontSpec) o;
        return Objects.equals(name, that.name)
                && Objects.equals(size, that.size)
                && Objects.equals(color, that.color)
                && Objects.equals(eastAsia, that.eastAsia)
                && Objects.equals(cs, that.cs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, size, color, eastAsia, cs);
    }

    @Override
    public String toString() {
        return "FontSpec[name=" + name
                + ", size=" + size
                + ", color=" + color
                + ", eastAsia=" + eastAsia
                + ", cs=" + cs + "]";
    }
}
