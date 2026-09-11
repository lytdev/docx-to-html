package cn.p4u.dth.model;

import java.util.Map;
import java.util.Objects;

/**
 * 主题定义不可变值类，对应 OOXML 主题文件（theme1.xml）的解析结果。
 * <p>
 * 核心职责：封装文档主题的颜色方案和字体方案，供 DocumentParser 在样式解析阶段
 * 将主题引用（如 themeColor="accent1"、asciiTheme="minorAscii"）替换为具体值。
 * <p>
 * 主要使用场景：ThemeParser 解析主题文件后构建 ThemeDef 存入 DocumentModel；
 * DocumentParser 中 resolveThemeFont/resolveThemeColor 方法通过 ThemeDef
 * 完成主题引用的实时解析。
 */
public final class ThemeDef {

    /** 颜色方案，包含 12 个标准主题色，可为 null 表示无颜色方案 */
    private final ColorScheme colors;
    /** 字体方案，包含主/辅字体的西文/东亚/复杂脚本字体，可为 null 表示无字体方案 */
    private final FontScheme fonts;

    /**
     * 构造主题定义。
     *
     * @param colors 颜色方案，ColorScheme 类型，可为 null
     * @param fonts  字体方案，FontScheme 类型，可为 null
     */
    public ThemeDef(ColorScheme colors, FontScheme fonts) {
        this.colors = colors;
        this.fonts = fonts;
    }

    /** {@return 主题颜色方案，可为 {@code null}} */
    public ColorScheme colors() { return colors; }
    /** {@return 主题字体方案，可为 {@code null}} */
    public FontScheme fonts() { return fonts; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThemeDef)) return false;
        ThemeDef that = (ThemeDef) o;
        return Objects.equals(colors, that.colors)
                && Objects.equals(fonts, that.fonts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(colors, fonts);
    }

    @Override
    public String toString() {
        return "ThemeDef[colors=" + colors + ", fonts=" + fonts + "]";
    }

    /**
     * 颜色方案不可变值类，包含 OOXML 主题定义的 12 个标准颜色槽位。
     * <p>
     * 对应主题文件中 &lt;a:clrScheme&gt; 下的 dk1/lt1/dk2/lt2/accent1-6/hlink/folHlink 元素。
     * 每个槽位存储解析后的十六进制颜色值（如 "000000"），null 表示未定义。
     */
    public static final class ColorScheme {

        /** 深色 1（通常为黑色） */
        private final String dk1;
        /** 浅色 1（通常为白色） */
        private final String lt1;
        /** 深色 2 */
        private final String dk2;
        /** 浅色 2 */
        private final String lt2;
        /** 强调色 1 */
        private final String accent1;
        /** 强调色 2 */
        private final String accent2;
        /** 强调色 3 */
        private final String accent3;
        /** 强调色 4 */
        private final String accent4;
        /** 强调色 5 */
        private final String accent5;
        /** 强调色 6 */
        private final String accent6;
        /** 超链接颜色 */
        private final String hlink;
        /** 已访问超链接颜色 */
        private final String folHlink;

        /**
         * 构造颜色方案。
         *
         * @param dk1      深色 1，String 类型，可为 null
         * @param lt1      浅色 1，String 类型，可为 null
         * @param dk2      深色 2，String 类型，可为 null
         * @param lt2      浅色 2，String 类型，可为 null
         * @param accent1  强调色 1，String 类型，可为 null
         * @param accent2  强调色 2，String 类型，可为 null
         * @param accent3  强调色 3，String 类型，可为 null
         * @param accent4  强调色 4，String 类型，可为 null
         * @param accent5  强调色 5，String 类型，可为 null
         * @param accent6  强调色 6，String 类型，可为 null
         * @param hlink    超链接颜色，String 类型，可为 null
         * @param folHlink 已访问超链接颜色，String 类型，可为 null
         */
        public ColorScheme(String dk1, String lt1, String dk2, String lt2,
                           String accent1, String accent2, String accent3, String accent4,
                           String accent5, String accent6,
                           String hlink, String folHlink) {
            this.dk1 = dk1;
            this.lt1 = lt1;
            this.dk2 = dk2;
            this.lt2 = lt2;
            this.accent1 = accent1;
            this.accent2 = accent2;
            this.accent3 = accent3;
            this.accent4 = accent4;
            this.accent5 = accent5;
            this.accent6 = accent6;
            this.hlink = hlink;
            this.folHlink = folHlink;
        }

        /** {@return 深色 1，可为 {@code null}} */
        public String dk1() { return dk1; }
        /** {@return 浅色 1，可为 {@code null}} */
        public String lt1() { return lt1; }
        /** {@return 深色 2，可为 {@code null}} */
        public String dk2() { return dk2; }
        /** {@return 浅色 2，可为 {@code null}} */
        public String lt2() { return lt2; }
        /** {@return 强调色 1，可为 {@code null}} */
        public String accent1() { return accent1; }
        /** {@return 强调色 2，可为 {@code null}} */
        public String accent2() { return accent2; }
        /** {@return 强调色 3，可为 {@code null}} */
        public String accent3() { return accent3; }
        /** {@return 强调色 4，可为 {@code null}} */
        public String accent4() { return accent4; }
        /** {@return 强调色 5，可为 {@code null}} */
        public String accent5() { return accent5; }
        /** {@return 强调色 6，可为 {@code null}} */
        public String accent6() { return accent6; }
        /** {@return 超链接颜色，可为 {@code null}} */
        public String hlink() { return hlink; }
        /** {@return 已访问超链接颜色，可为 {@code null}} */
        public String folHlink() { return folHlink; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ColorScheme)) return false;
            ColorScheme that = (ColorScheme) o;
            return Objects.equals(dk1, that.dk1)
                    && Objects.equals(lt1, that.lt1)
                    && Objects.equals(dk2, that.dk2)
                    && Objects.equals(lt2, that.lt2)
                    && Objects.equals(accent1, that.accent1)
                    && Objects.equals(accent2, that.accent2)
                    && Objects.equals(accent3, that.accent3)
                    && Objects.equals(accent4, that.accent4)
                    && Objects.equals(accent5, that.accent5)
                    && Objects.equals(accent6, that.accent6)
                    && Objects.equals(hlink, that.hlink)
                    && Objects.equals(folHlink, that.folHlink);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dk1, lt1, dk2, lt2,
                    accent1, accent2, accent3, accent4, accent5, accent6,
                    hlink, folHlink);
        }

        @Override
        public String toString() {
            return "ColorScheme[dk1=" + dk1
                    + ", lt1=" + lt1
                    + ", dk2=" + dk2
                    + ", lt2=" + lt2
                    + ", accent1=" + accent1
                    + ", accent2=" + accent2
                    + ", accent3=" + accent3
                    + ", accent4=" + accent4
                    + ", accent5=" + accent5
                    + ", accent6=" + accent6
                    + ", hlink=" + hlink
                    + ", folHlink=" + folHlink + "]";
        }
    }

    /**
     * 字体方案不可变值类，包含主字体和辅字体的西文/东亚/复杂脚本字体及脚本覆盖。
     * <p>
     * 对应主题文件中 &lt;a:fontScheme&gt; 下 &lt;a:majorFont&gt; 和 &lt;a:minorFont&gt; 元素。
     * major 代表标题字体方案，minor 代表正文字体方案。
     */
    public static final class FontScheme {

        /** 主字体西文名（如 "Calibri Light"） */
        private final String majorLatin;
        /** 主字体东亚名（如 "微软雅黑"） */
        private final String majorEastAsia;
        /** 主字体复杂脚本名 */
        private final String majorCs;
        /** 辅字体西文名（如 "Calibri"） */
        private final String minorLatin;
        /** 辅字体东亚名 */
        private final String minorEastAsia;
        /** 辅字体复杂脚本名 */
        private final String minorCs;
        /** 主字体脚本覆盖 map，key 为脚本文本（如 "Hans"），value 为字体名 */
        private final Map<String, String> majorScriptOverrides;
        /** 辅字体脚本覆盖 map */

        private final Map<String, String> minorScriptOverrides;

        /**
         * 构造字体方案。
         *
         * @param majorLatin           主字体西文名，String 类型，可为 null
         * @param majorEastAsia        主字体东亚名，String 类型，可为 null
         * @param majorCs              主字体复杂脚本名，String 类型，可为 null
         * @param minorLatin           辅字体西文名，String 类型，可为 null
         * @param minorEastAsia        辅字体东亚名，String 类型，可为 null
         * @param minorCs              辅字体复杂脚本名，String 类型，可为 null
         * @param majorScriptOverrides 主字体脚本覆盖 map，可为 null
         * @param minorScriptOverrides 辅字体脚本覆盖 map，可为 null
         */
        public FontScheme(String majorLatin, String majorEastAsia, String majorCs,
                          String minorLatin, String minorEastAsia, String minorCs,
                          Map<String, String> majorScriptOverrides,
                          Map<String, String> minorScriptOverrides) {
            this.majorLatin = majorLatin;
            this.majorEastAsia = majorEastAsia;
            this.majorCs = majorCs;
            this.minorLatin = minorLatin;
            this.minorEastAsia = minorEastAsia;
            this.minorCs = minorCs;
            this.majorScriptOverrides = majorScriptOverrides;
            this.minorScriptOverrides = minorScriptOverrides;
        }

        /** {@return 主字体西文字体名，可为 {@code null}} */
        public String majorLatin() { return majorLatin; }
        /** {@return 主字体东亚字体名，可为 {@code null}} */
        public String majorEastAsia() { return majorEastAsia; }
        /** {@return 主字体复杂脚本字体名，可为 {@code null}} */
        public String majorCs() { return majorCs; }
        /** {@return 辅字体西文字体名，可为 {@code null}} */
        public String minorLatin() { return minorLatin; }
        /** {@return 辅字体东亚字体名，可为 {@code null}} */
        public String minorEastAsia() { return minorEastAsia; }
        /** {@return 辅字体复杂脚本字体名，可为 {@code null}} */
        public String minorCs() { return minorCs; }
        /** {@return 主字体脚本覆盖映射，可为 {@code null}} */
        public Map<String, String> majorScriptOverrides() { return majorScriptOverrides; }
        /** {@return 辅字体脚本覆盖映射，可为 {@code null}} */
        public Map<String, String> minorScriptOverrides() { return minorScriptOverrides; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FontScheme)) return false;
            FontScheme that = (FontScheme) o;
            return Objects.equals(majorLatin, that.majorLatin)
                    && Objects.equals(majorEastAsia, that.majorEastAsia)
                    && Objects.equals(majorCs, that.majorCs)
                    && Objects.equals(minorLatin, that.minorLatin)
                    && Objects.equals(minorEastAsia, that.minorEastAsia)
                    && Objects.equals(minorCs, that.minorCs)
                    && Objects.equals(majorScriptOverrides, that.majorScriptOverrides)
                    && Objects.equals(minorScriptOverrides, that.minorScriptOverrides);
        }

        @Override
        public int hashCode() {
            return Objects.hash(majorLatin, majorEastAsia, majorCs,
                    minorLatin, minorEastAsia, minorCs,
                    majorScriptOverrides, minorScriptOverrides);
        }

        @Override
        public String toString() {
            return "FontScheme[majorLatin=" + majorLatin
                    + ", majorEastAsia=" + majorEastAsia
                    + ", majorCs=" + majorCs
                    + ", minorLatin=" + minorLatin
                    + ", minorEastAsia=" + minorEastAsia
                    + ", minorCs=" + minorCs
                    + ", majorScriptOverrides=" + majorScriptOverrides
                    + ", minorScriptOverrides=" + minorScriptOverrides + "]";
        }
    }
}
