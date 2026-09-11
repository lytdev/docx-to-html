package cn.p4u.dth.model;

import java.util.Objects;

/**
 * 边框规格不可变值类，描述单个边框侧的宽度与颜色。
 * <p>
 * 核心职责：封装表格/单元格某一侧边框的宽度（八分之一磅原始值）及颜色（十六进制），
 * 用于 TableBlock 和 TableCell 的逐侧边框表示。
 * <p>
 * 主要使用场景：DocumentParser 解析 tblBorders/tcBorders 时构建 BorderSpec；
 * StyleMapper 将其转换为 CSS border-top/border-left 等逐侧样式。
 * width 和 color 均为 null 时表示该侧无边框。
 */
public final class BorderSpec {

    /** 无边框的常量实例，width 和 color 均为 null */
    public static final BorderSpec NONE = new BorderSpec(null, null);

    /** 边框宽度，原始八分之一磅值（如 "4" 表示 0.5pt），null 表示无边框 */
    private final String width;
    /** 边框颜色，十六进制值（如 "000000"），null 表示默认/未指定 */
    private final String color;

    /**
     * 构造边框规格。
     *
     * @param width 边框宽度，String 类型，原始八分之一磅值，可为 null
     * @param color 边框颜色，String 类型，十六进制值，可为 null
     */
    public BorderSpec(String width, String color) {
        this.width = width;
        this.color = color;
    }

    public String width() { return width; }
    public String color() { return color; }

    /**
     * 判断该侧是否有有效边框（width 不为 null）。
     *
     * @return 有边框时返回 true
     */
    public boolean hasBorder() { return width != null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BorderSpec)) return false;
        BorderSpec that = (BorderSpec) o;
        return Objects.equals(width, that.width) && Objects.equals(color, that.color);
    }

    @Override
    public int hashCode() { return Objects.hash(width, color); }

    @Override
    public String toString() {
        return "BorderSpec[width=" + width + ", color=" + color + "]";
    }
}
