package cn.p4u.smart.model;

import java.util.List;
import java.util.Objects;

/**
 * 表格行不可变值类，表示表格中的一行。
 * <p>
 * 核心职责：封装行内的单元格列表和行高信息。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:tr 时构建 TableRow；
 * HtmlRenderer 将其渲染为 &lt;tr&gt; 元素。
 */
public final class TableRow {

    /** 行内的单元格列表，List&lt;TableCell&gt; 类型，不可为 null */
    private final List<TableCell> cells;
    /** 行高，原始 twips 值（如 "720" 表示 36pt），可为 null 表示未指定 */
    private final String height;

    /**
     * 构造表格行。
     *
     * @param cells  单元格列表，List&lt;TableCell&gt; 类型
     * @param height 行高（twips），String 类型，可为 null
     */
    public TableRow(List<TableCell> cells, String height) {
        this.cells = cells;
        this.height = height;
    }

    public List<TableCell> cells() { return cells; }
    public String height() { return height; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableRow)) return false;
        TableRow that = (TableRow) o;
        return Objects.equals(cells, that.cells)
                && Objects.equals(height, that.height);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cells, height);
    }

    @Override
    public String toString() {
        return "TableRow[cells=" + cells + ", height=" + height + "]";
    }
}
