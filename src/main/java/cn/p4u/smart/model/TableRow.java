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
    /** 行高（已转换为 CSS pt 值，如 "101.5"），可为 null 表示未指定 */
    private final String height;
    /** 行高规则：null / "auto" 自动；"atLeast" 最小高度；"exact" 精确高度 */
    private final String hRule;

    /**
     * 构造表格行。
     *
     * @param cells  单元格列表，List&lt;TableCell&gt; 类型
     * @param height 行高（CSS pt 值），String 类型，可为 null
     * @param hRule  行高规则，可为 null（同 auto）
     */
    public TableRow(List<TableCell> cells, String height, String hRule) {
        this.cells = cells;
        this.height = height;
        this.hRule = hRule;
    }

    public List<TableCell> cells() { return cells; }
    public String height() { return height; }
    public String hRule() { return hRule; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableRow)) return false;
        TableRow that = (TableRow) o;
        return Objects.equals(cells, that.cells)
                && Objects.equals(height, that.height)
                && Objects.equals(hRule, that.hRule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cells, height, hRule);
    }

    @Override
    public String toString() {
        return "TableRow[cells=" + cells + ", height=" + height + ", hRule=" + hRule + "]";
    }
}
