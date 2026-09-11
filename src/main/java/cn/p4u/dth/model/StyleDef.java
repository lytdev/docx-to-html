package cn.p4u.dth.model;

import java.util.Map;
import java.util.Objects;

/**
 * 样式定义不可变值类，对应 OOXML 中 w:style 元素的解析结果。
 * <p>
 * 核心职责：封装样式的标识信息（styleId、name、basedOn、outlineLvl）
 * 以及字符属性（rPr）和段落属性（pPr）的两种形态：
 * <ul>
 *   <li>解析后的简写属性 map（runProps/paragraphProps）：便于快速查找常用属性</li>
 *   <li>原始属性嵌套 map（rawRunAttrs/rawParaAttrs）：保留每个子元素的完整属性，
 *       用于字体颜色主题引用等需要完整上下文的场景</li>
 * </ul>
 * <p>
 * 主要使用场景：StylesParser 解析 styles.xml 后产出原始 StyleDef；
 * DocumentParser 中通过 resolveStyleInheritance 合并 basedOn 链得到完整样式；
 * 再通过 resolveThemeInStyles 将主题引用替换为具体值。
 * HtmlRenderer 和 StyleMapper 不直接使用 StyleDef，
 * 而是通过 DocumentParser.resolveInheritedFontSpec 等方法间接获取样式信息。
 */
public final class StyleDef {

    /** 样式唯一标识（对应 w:styleId 属性），不可为 null */
    private final String styleId;
    /** 样式名称（对应 w:name val 属性），可为 null */
    private final String name;
    /** 父样式 ID（对应 w:basedOn val 属性），可为 null 表示无继承 */
    private final String basedOn;
    /** 大纲级别（对应 w:outlineLvl val 属性），0-5 对应标题 1-6，null 表示非标题 */
    private final Integer outlineLvl;
    /** 解析后的字符属性简写 map，key 为元素名（如 "b"、"sz"），value 为 val 属性值 */
    private final Map<String, String> runProps;
    /** 解析后的段落属性简写 map */
    private final Map<String, String> paragraphProps;
    /** 原始字符属性嵌套 map，key 为元素名，value 为该元素的全部属性 map */
    private final Map<String, Map<String, String>> rawRunAttrs;
    /** 原始段落属性嵌套 map */
    private final Map<String, Map<String, String>> rawParaAttrs;
    /** 原始表格属性嵌套 map（仅 table style 使用），来自 w:tblPr 子元素 */
    private final Map<String, Map<String, String>> rawTblAttrs;

    /**
     * 构造样式定义。
     *
     * @param styleId      样式 ID，String 类型
     * @param name         样式名称，String 类型，可为 null
     * @param basedOn      父样式 ID，String 类型，可为 null
     * @param outlineLvl   大纲级别，Integer 类型，0-5 或 null
     * @param runProps     字符属性简写 map，Map&lt;String, String&gt; 类型
     * @param paragraphProps 段落属性简写 map，Map&lt;String, String&gt; 类型
     * @param rawRunAttrs  原始字符属性嵌套 map，Map&lt;String, Map&lt;String, String&gt;&gt; 类型
     * @param rawParaAttrs 原始段落属性嵌套 map，Map&lt;String, Map&lt;String, String&gt;&gt; 类型
     * @param rawTblAttrs  原始表格属性嵌套 map，Map&lt;String, Map&lt;String, String&gt;&gt; 类型，可为 null
     */
    public StyleDef(String styleId, String name, String basedOn,
                    Integer outlineLvl,
                    Map<String, String> runProps,
                    Map<String, String> paragraphProps,
                    Map<String, Map<String, String>> rawRunAttrs,
                    Map<String, Map<String, String>> rawParaAttrs,
                    Map<String, Map<String, String>> rawTblAttrs) {
        this.styleId = styleId;
        this.name = name;
        this.basedOn = basedOn;
        this.outlineLvl = outlineLvl;
        this.runProps = runProps;
        this.paragraphProps = paragraphProps;
        this.rawRunAttrs = rawRunAttrs;
        this.rawParaAttrs = rawParaAttrs;
        this.rawTblAttrs = rawTblAttrs;
    }

    /** {@return 样式 ID} */
    public String styleId() { return styleId; }
    /** {@return 样式名称，可为 {@code null}} */
    public String name() { return name; }
    /** {@return 父样式 ID，可为 {@code null}} */
    public String basedOn() { return basedOn; }
    /** {@return 大纲级别，可为 {@code null}} */
    public Integer outlineLvl() { return outlineLvl; }
    /** {@return 字符属性简写映射} */
    public Map<String, String> runProps() { return runProps; }
    /** {@return 段落属性简写映射} */
    public Map<String, String> paragraphProps() { return paragraphProps; }
    /** {@return 原始字符属性嵌套映射} */
    public Map<String, Map<String, String>> rawRunAttrs() { return rawRunAttrs; }
    /** {@return 原始段落属性嵌套映射} */
    public Map<String, Map<String, String>> rawParaAttrs() { return rawParaAttrs; }
    /** {@return 原始表格属性嵌套映射，可为 {@code null}} */
    public Map<String, Map<String, String>> rawTblAttrs() { return rawTblAttrs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StyleDef)) return false;
        StyleDef that = (StyleDef) o;
        return Objects.equals(styleId, that.styleId)
                && Objects.equals(name, that.name)
                && Objects.equals(basedOn, that.basedOn)
                && Objects.equals(outlineLvl, that.outlineLvl)
                && Objects.equals(runProps, that.runProps)
                && Objects.equals(paragraphProps, that.paragraphProps)
                && Objects.equals(rawRunAttrs, that.rawRunAttrs)
                && Objects.equals(rawParaAttrs, that.rawParaAttrs)
                && Objects.equals(rawTblAttrs, that.rawTblAttrs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(styleId, name, basedOn, outlineLvl,
                runProps, paragraphProps, rawRunAttrs, rawParaAttrs, rawTblAttrs);
    }

    @Override
    public String toString() {
        return "StyleDef[styleId=" + styleId
                + ", name=" + name
                + ", basedOn=" + basedOn
                + ", outlineLvl=" + outlineLvl
                + ", runProps=" + runProps
                + ", paragraphProps=" + paragraphProps
                + ", rawRunAttrs=" + rawRunAttrs
                + ", rawParaAttrs=" + rawParaAttrs
                + ", rawTblAttrs=" + rawTblAttrs + "]";
    }
}
