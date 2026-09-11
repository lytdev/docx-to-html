package cn.p4u.dth.model;

import java.util.Objects;

/**
 * 段落缩进信息不可变值类。
 * <p>
 * 核心职责：描述段落的左缩进、右缩进和首行缩进，数值单位为 twips（1/20 磅），
 * 与 OOXML 中 w:ind 元素的属性直接对应。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:pPr/w:ind 时构建 Indentation，
 * HtmlRenderer 通过 StyleMapper 将 twips 转换为 pt 并输出 CSS margin/text-indent。
 */
public final class Indentation {

    /** 左缩进值（twips），String 类型，可为 null 表示未设置 */
    private final String left;
    /** 右缩进值（twips），String 类型，可为 null 表示未设置 */
    private final String right;
    /** 首行缩进值（twips），String 类型，可为 null 表示未设置 */
    private final String firstLine;

    /**
     * 构造缩进信息。
     *
     * @param left      左缩进（twips），String 类型，null 表示段落无左缩进
     * @param right     右缩进（twips），String 类型，null 表示段落无右缩进
     * @param firstLine 首行缩进（twips），String 类型，null 表示无首行缩进
     */
    public Indentation(String left, String right, String firstLine) {
        this.left = left;
        this.right = right;
        this.firstLine = firstLine;
    }

    public String left() { return left; }
    public String right() { return right; }
    public String firstLine() { return firstLine; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Indentation)) return false;
        Indentation that = (Indentation) o;
        return Objects.equals(left, that.left)
                && Objects.equals(right, that.right)
                && Objects.equals(firstLine, that.firstLine);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right, firstLine);
    }

    @Override
    public String toString() {
        return "Indentation[left=" + left
                + ", right=" + right
                + ", firstLine=" + firstLine + "]";
    }
}
