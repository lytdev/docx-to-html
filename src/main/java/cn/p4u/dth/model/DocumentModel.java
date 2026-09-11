package cn.p4u.dth.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/**
 * 文档模型根节点不可变值类，表示整个 .docx 文档的完整结构化表示。
 * <p>
 * 核心职责：作为三阶段管线的中间产物，承载解析阶段的全部输出数据的根容器，
 * 包含样式定义、主题信息、编号格式和顶层内容块列表。
 * <p>
 * 主要使用场景：DocumentParser.parse() 的返回值；
 * HtmlRenderer.render() 的输入参数。
 * DocumentModel 是不可变的，所有子列表和子 map 均为不可修改视图。
 */
public final class DocumentModel {

    /** 样式定义 map，key 为 styleId，value 为 StyleDef 实例 */
    private final Map<String, StyleDef> styles;
    /** 顶层内容块列表（段落和表格），按文档顺序排列 */
    private final List<ContentBlock> content;
    /** 主题定义，包含颜色方案和字体方案，可为 null 表示无主题文件 */
    private final ThemeDef theme;
    /** 编号格式 map，key 为 numId，value 为格式名（如 "decimal"、"bullet"） */

    private final Map<String, String> numberingFormats;

    /**
     * 简化构造方法，不含编号格式（默认为空 map）。
     *
     * @param styles  样式定义 map，Map&lt;String, StyleDef&gt; 类型
     * @param content 内容块列表，List&lt;ContentBlock&gt; 类型
     * @param theme   主题定义，ThemeDef 类型，可为 null
     */
    public DocumentModel(Map<String, StyleDef> styles, List<ContentBlock> content,
                         ThemeDef theme) {
        this(styles, content, theme, Collections.<String, String>emptyMap());
    }

    /**
     * 完整构造方法。
     *
     * @param styles           样式定义 map，Map&lt;String, StyleDef&gt; 类型
     * @param content          内容块列表，List&lt;ContentBlock&gt; 类型
     * @param theme            主题定义，ThemeDef 类型，可为 null
     * @param numberingFormats 编号格式 map，Map&lt;String, String&gt; 类型，null 时视为空 map
     */
    public DocumentModel(Map<String, StyleDef> styles, List<ContentBlock> content,
                         ThemeDef theme, Map<String, String> numberingFormats) {
        this.styles = styles;
        this.content = content;
        this.theme = theme;
        // 防御性空值处理：null 视为空 map
        this.numberingFormats = numberingFormats != null ? numberingFormats : Collections.<String, String>emptyMap();
    }

    /**
     * 返回样式定义。
     * @return 以样式 ID 为键的样式定义映射
     */
    public Map<String, StyleDef> styles() { return styles; }

    /**
     * 返回文档内容。
     * @return 按文档顺序排列的顶层内容块
     */
    public List<ContentBlock> content() { return content; }

    /**
     * 返回文档主题。
     * @return 文档没有主题文件时为 {@code null}
     */
    public ThemeDef theme() { return theme; }

    /**
     * 返回编号格式。
     * @return 以编号 ID 为键的编号格式映射
     */
    public Map<String, String> numberingFormats() { return numberingFormats; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentModel)) return false;
        DocumentModel that = (DocumentModel) o;
        return Objects.equals(styles, that.styles)
                && Objects.equals(content, that.content)
                && Objects.equals(theme, that.theme)
                && Objects.equals(numberingFormats, that.numberingFormats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(styles, content, theme, numberingFormats);
    }

    @Override
    public String toString() {
        return "DocumentModel[styles=" + styles
                + ", content=" + content
                + ", theme=" + theme
                + ", numberingFormats=" + numberingFormats + "]";
    }
}
