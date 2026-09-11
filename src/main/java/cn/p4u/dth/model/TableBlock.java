package cn.p4u.dth.model;

import java.util.List;
import java.util.Objects;

/**
 * 表格内容块不可变值类，表示文档中的一个完整表格。
 * <p>
 * 核心职责：封装表格的行列表、整体宽度、逐侧外边框与内边框样式和可见性，
 * 实现 ContentBlock 接口以作为文档顶层内容块。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:tbl 元素时构建 TableBlock；
 * HtmlRenderer 将其渲染为 &lt;table&gt; 元素，通过 StyleMapper 生成边框和宽度 CSS。
 * <p>
 * 边框模型采用逐侧（top/left/bottom/right）表示方式，以正确支持三线表等
 * 部分边框可见的表格样式。insideH 和 insideV 保留用于解析阶段计算单元格边框。
 */
public final class TableBlock implements ContentBlock {

    /** 表格的行列表，List&lt;TableRow&gt; 类型，不可为 null */
    private final List<TableRow> rows;
    /** 表格宽度，CSS 格式字符串（如 "100%" 或 "300pt"），可为 null */
    private final String width;
    /** 表格顶部外边框 */
    private final BorderSpec topBorder;
    /** 表格左侧外边框 */
    private final BorderSpec leftBorder;
    /** 表格底部外边框 */
    private final BorderSpec bottomBorder;
    /** 表格右侧外边框 */
    private final BorderSpec rightBorder;
    /** 表格行间水平内边框（insideH），解析阶段用于计算单元格边框 */
    private final BorderSpec insideHBorder;
    /** 表格列间垂直内边框（insideV），解析阶段用于计算单元格边框 */
    private final BorderSpec insideVBorder;
    /** 表格是否可见，false 时 CSS 输出 visibility: hidden */
    private final boolean visibility;

    /**
     * 构造表格块。
     *
     * @param rows          行列表，List&lt;TableRow&gt; 类型
     * @param width         表格宽度，String 类型，CSS 格式，可为 null
     * @param topBorder     顶部外边框，BorderSpec 类型
     * @param leftBorder    左侧外边框，BorderSpec 类型
     * @param bottomBorder  底部外边框，BorderSpec 类型
     * @param rightBorder   右侧外边框，BorderSpec 类型
     * @param insideHBorder 行间水平内边框，BorderSpec 类型
     * @param insideVBorder 列间垂直内边框，BorderSpec 类型
     * @param visibility    是否可见，boolean 类型
     */
    public TableBlock(List<TableRow> rows,
                      String width,
                      BorderSpec topBorder, BorderSpec leftBorder,
                      BorderSpec bottomBorder, BorderSpec rightBorder,
                      BorderSpec insideHBorder, BorderSpec insideVBorder,
                      boolean visibility) {
        this.rows = rows;
        this.width = width;
        this.topBorder = topBorder;
        this.leftBorder = leftBorder;
        this.bottomBorder = bottomBorder;
        this.rightBorder = rightBorder;
        this.insideHBorder = insideHBorder;
        this.insideVBorder = insideVBorder;
        this.visibility = visibility;
    }

    /** {@return 按原顺序排列的表格行} */
    public List<TableRow> rows() { return rows; }
    /** {@return 表格 CSS 宽度，可为 {@code null}} */
    public String width() { return width; }
    /** {@return 顶部外边框} */
    public BorderSpec topBorder() { return topBorder; }
    /** {@return 左侧外边框} */
    public BorderSpec leftBorder() { return leftBorder; }
    /** {@return 底部外边框} */
    public BorderSpec bottomBorder() { return bottomBorder; }
    /** {@return 右侧外边框} */
    public BorderSpec rightBorder() { return rightBorder; }
    /** {@return 行间水平内边框} */
    public BorderSpec insideHBorder() { return insideHBorder; }
    /** {@return 列间垂直内边框} */
    public BorderSpec insideVBorder() { return insideVBorder; }
    /** {@return 表格可见时为 {@code true}} */
    public boolean visibility() { return visibility; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableBlock)) return false;
        TableBlock that = (TableBlock) o;
        return visibility == that.visibility
                && Objects.equals(rows, that.rows)
                && Objects.equals(width, that.width)
                && Objects.equals(topBorder, that.topBorder)
                && Objects.equals(leftBorder, that.leftBorder)
                && Objects.equals(bottomBorder, that.bottomBorder)
                && Objects.equals(rightBorder, that.rightBorder)
                && Objects.equals(insideHBorder, that.insideHBorder)
                && Objects.equals(insideVBorder, that.insideVBorder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, width, topBorder, leftBorder, bottomBorder,
                rightBorder, insideHBorder, insideVBorder, visibility);
    }

    @Override
    public String toString() {
        return "TableBlock[rows=" + rows
                + ", width=" + width
                + ", topBorder=" + topBorder
                + ", leftBorder=" + leftBorder
                + ", bottomBorder=" + bottomBorder
                + ", rightBorder=" + rightBorder
                + ", insideHBorder=" + insideHBorder
                + ", insideVBorder=" + insideVBorder
                + ", visibility=" + visibility + "]";
    }
}
