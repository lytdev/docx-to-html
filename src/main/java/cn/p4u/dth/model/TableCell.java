package cn.p4u.dth.model;

import java.util.List;
import java.util.Objects;

/**
 * 表格单元格不可变值类，表示表格行中的一个单元格。
 * <p>
 * 核心职责：封装单元格内的段落列表、合并跨度信息、宽度、逐侧边框、背景色和可见性。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:tc 时构建 TableCell；
 * HtmlRenderer 将其渲染为 &lt;td&gt; 元素，通过 StyleMapper 生成逐侧边框/宽度/背景色 CSS。
 * <p>
 * 边框采用逐侧（top/left/bottom/right）表示方式，以正确支持三线表等
 * 部分侧有边框、部分侧无边框的表格样式。解析器根据单元格在表格中的位置
 * （首行/末行/首列/末列）从 tblBorders 的内外边框中计算各侧边框，
 * 并允许 tcBorders 对任意侧进行覆盖。
 */
public final class TableCell {

    /** 单元格内的段落列表，List&lt;ParagraphBlock&gt; 类型 */
    private final List<ParagraphBlock> paragraphs;
    /** 水平合并跨度（对应 w:gridSpan），默认 1 */
    private final int colspan;
    /** 垂直合并跨度（对应 w:vMerge val="restart"），当前仅标记 1 或 0 */
    private final int rowspan;
    /** 单元格宽度，CSS 格式字符串（如 "50%" 或 "150pt"），可为 null */
    private final String width;
    /** 单元格顶部边框 */
    private final BorderSpec topBorder;
    /** 单元格左侧边框 */
    private final BorderSpec leftBorder;
    /** 单元格底部边框 */
    private final BorderSpec bottomBorder;
    /** 单元格右侧边框 */
    private final BorderSpec rightBorder;
    /** 单元格背景色，十六进制值（如 "FFFF00"），可为 null */
    private final String bgColor;
    /** 单元格是否可见，false 时 CSS 输出 visibility: hidden */
    private final boolean visibility;

    /**
     * 构造表格单元格。
     *
     * @param paragraphs  段落列表，List&lt;ParagraphBlock&gt; 类型
     * @param colspan     水平跨度，int 类型，最小值为 1
     * @param rowspan     垂直跨度，int 类型，1 表示合并起始，0 表示被合并
     * @param width       宽度，String 类型，CSS 格式，可为 null
     * @param topBorder   顶部边框，BorderSpec 类型
     * @param leftBorder  左侧边框，BorderSpec 类型
     * @param bottomBorder 底部边框，BorderSpec 类型
     * @param rightBorder 右侧边框，BorderSpec 类型
     * @param bgColor     背景色，String 类型，可为 null
     * @param visibility  是否可见，boolean 类型
     */
    public TableCell(List<ParagraphBlock> paragraphs,
                     int colspan, int rowspan,
                     String width,
                     BorderSpec topBorder, BorderSpec leftBorder,
                     BorderSpec bottomBorder, BorderSpec rightBorder,
                     String bgColor, boolean visibility) {
        this.paragraphs = paragraphs;
        this.colspan = colspan;
        this.rowspan = rowspan;
        this.width = width;
        this.topBorder = topBorder;
        this.leftBorder = leftBorder;
        this.bottomBorder = bottomBorder;
        this.rightBorder = rightBorder;
        this.bgColor = bgColor;
        this.visibility = visibility;
    }

    public List<ParagraphBlock> paragraphs() { return paragraphs; }
    public int colspan() { return colspan; }
    public int rowspan() { return rowspan; }
    public String width() { return width; }
    public BorderSpec topBorder() { return topBorder; }
    public BorderSpec leftBorder() { return leftBorder; }
    public BorderSpec bottomBorder() { return bottomBorder; }
    public BorderSpec rightBorder() { return rightBorder; }
    public String bgColor() { return bgColor; }
    public boolean visibility() { return visibility; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableCell)) return false;
        TableCell that = (TableCell) o;
        return colspan == that.colspan
                && rowspan == that.rowspan
                && visibility == that.visibility
                && Objects.equals(paragraphs, that.paragraphs)
                && Objects.equals(width, that.width)
                && Objects.equals(topBorder, that.topBorder)
                && Objects.equals(leftBorder, that.leftBorder)
                && Objects.equals(bottomBorder, that.bottomBorder)
                && Objects.equals(rightBorder, that.rightBorder)
                && Objects.equals(bgColor, that.bgColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paragraphs, colspan, rowspan, width,
                topBorder, leftBorder, bottomBorder, rightBorder,
                bgColor, visibility);
    }

    @Override
    public String toString() {
        return "TableCell[paragraphs=" + paragraphs
                + ", colspan=" + colspan
                + ", rowspan=" + rowspan
                + ", width=" + width
                + ", topBorder=" + topBorder
                + ", leftBorder=" + leftBorder
                + ", bottomBorder=" + bottomBorder
                + ", rightBorder=" + rightBorder
                + ", bgColor=" + bgColor
                + ", visibility=" + visibility + "]";
    }
}
