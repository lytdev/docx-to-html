package cn.p4u.smart.model;

import java.util.List;
import java.util.Objects;

/**
 * 段落内容块不可变值类，表示文档中的一个段落。
 * <p>
 * 核心职责：封装段落的样式引用、标题级别、对齐方式、缩进信息、
 * 列表编号引用以及段落内的内联元素列表，实现 ContentBlock 接口。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:p 元素时构建 ParagraphBlock；
 * HtmlRenderer 根据大纲级别渲染为 h1-h6 或 p 标签，
 * 并通过 StyleMapper 生成文本对齐、缩进等 CSS。
 */
public final class ParagraphBlock implements ContentBlock {

    /** 引用的段落样式 ID（对应 w:pStyle val 属性），可为 null */
    private final String styleId;
    /** 大纲级别（0-5 对应标题 1-6），可为 null 表示普通段落 */
    private final Integer outlineLvl;
    /** 文本对齐方式（如 "center"、"right"），可为 null 表示默认左对齐 */
    private final String alignment;
    /** 缩进信息，可为 null 表示无缩进 */
    private final Indentation indentation;
    /** 段落内的内联元素列表，List&lt;ParagraphElement&gt; 类型 */
    private final List<ParagraphElement> elements;
    /** 列表编号 ID（对应 w:numId），可为 null 表示非列表段落 */
    private final String numId;
    /** 列表缩进级别（对应 w:ilvl），可为 null 表示默认级别 0 */

    private final Integer ilvl;

    /**
     * 简化构造方法，不包含列表编号信息（numId 和 ilvl 设为 null）。
     *
     * @param styleId     段落样式 ID，String 类型，可为 null
     * @param outlineLvl  大纲级别，Integer 类型，可为 null
     * @param alignment   对齐方式，String 类型，可为 null
     * @param indentation 缩进信息，Indentation 类型，可为 null
     * @param elements    内联元素列表，List&lt;ParagraphElement&gt; 类型
     */
    public ParagraphBlock(String styleId, Integer outlineLvl, String alignment,
                          Indentation indentation,
                          List<ParagraphElement> elements) {
        this(styleId, outlineLvl, alignment, indentation, elements, null, null);
    }

    /**
     * 完整构造方法，包含列表编号信息。
     *
     * @param styleId     段落样式 ID，String 类型，可为 null
     * @param outlineLvl  大纲级别，Integer 类型，可为 null
     * @param alignment   对齐方式，String 类型，可为 null
     * @param indentation 缩进信息，Indentation 类型，可为 null
     * @param elements    内联元素列表，List&lt;ParagraphElement&gt; 类型
     * @param numId       列表编号 ID，String 类型，可为 null
     * @param ilvl        列表缩进级别，Integer 类型，可为 null
     */
    public ParagraphBlock(String styleId, Integer outlineLvl, String alignment,
                          Indentation indentation,
                          List<ParagraphElement> elements,
                          String numId, Integer ilvl) {
        this.styleId = styleId;
        this.outlineLvl = outlineLvl;
        this.alignment = alignment;
        this.indentation = indentation;
        this.elements = elements;
        this.numId = numId;
        this.ilvl = ilvl;
    }

    public String styleId() { return styleId; }
    public Integer outlineLvl() { return outlineLvl; }
    public String alignment() { return alignment; }
    public Indentation indentation() { return indentation; }
    public List<ParagraphElement> elements() { return elements; }
    public String numId() { return numId; }
    public Integer ilvl() { return ilvl; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParagraphBlock)) return false;
        ParagraphBlock that = (ParagraphBlock) o;
        return Objects.equals(styleId, that.styleId)
                && Objects.equals(outlineLvl, that.outlineLvl)
                && Objects.equals(alignment, that.alignment)
                && Objects.equals(indentation, that.indentation)
                && Objects.equals(elements, that.elements)
                && Objects.equals(numId, that.numId)
                && Objects.equals(ilvl, that.ilvl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(styleId, outlineLvl, alignment, indentation, elements, numId, ilvl);
    }

    @Override
    public String toString() {
        return "ParagraphBlock[styleId=" + styleId
                + ", outlineLvl=" + outlineLvl
                + ", alignment=" + alignment
                + ", indentation=" + indentation
                + ", numId=" + numId
                + ", ilvl=" + ilvl
                + ", elements=" + elements + "]";
    }
}
