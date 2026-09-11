package cn.p4u.dth.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 矢量形状元素不可变值类，表示 DrawingML 预设几何形状或形状组。
 * <p>
 * 核心职责：封装形状的几何类型、尺寸/偏移（EMU 单位）、填充/描边样式、
 * 环绕模式，以及形状组的子坐标系统（chOff/chExt）和子形状列表。
 * 宽/高/偏移单位均为 EMU，与 ImageElement 保持一致。
 * 对于形状组，children 包含在组的子坐标系内定位的子形状。
 * <p>
 * 主要使用场景：DocumentParser 解析 wps:wsp（单个形状）和 wpg:wgp（形状组）时构建；
 * HtmlRenderer 将其渲染为内联 SVG，预设几何体通过 presetToSvgPath 映射为 SVG path。
 */
public final class ShapeElement implements ParagraphElement {

    /** 预设几何体名称（如 "rect"、"ellipse"、"group"），对应 DrawingML 的 prst 属性 */
    private final String preset;
    /** 形状宽度（EMU） */
    private final int width;
    /** 形状高度（EMU） */
    private final int height;
    /** 形状 X 偏移（EMU），组内子形状使用 */
    private final int offX;
    /** 形状 Y 偏移（EMU），组内子形状使用 */
    private final int offY;
    /** 填充颜色值（如 "#FF0000"），"none" 表示无填充，null 表示未指定 */
    private final String fillColor;
    /** 描边颜色值（如 "#000000"），null 表示未指定 */
    private final String strokeColor;
    /** 描边宽度（pt） */
    private final float strokeWidth;
    /** 文字环绕模式 */
    private final WrapMode wrapMode;
    /** 组坐标系原点 X（EMU），仅形状组有意义 */
    private final int chOffX;
    /** 组坐标系原点 Y（EMU），仅形状组有意义 */
    private final int chOffY;
    /** 组坐标系宽度（EMU），仅形状组有意义 */
    private final int chExtW;
    /** 组坐标系高度（EMU），仅形状组有意义 */
    private final int chExtH;
    /** 子形状列表，单个形状为空列表，形状组包含子项 */

    private final List<ShapeElement> children;

    /**
     * 完整构造方法，适用于形状组（包含子形状和坐标系统信息）。
     *
     * @param preset     预设几何体名称，String 类型，"group" 表示形状组
     * @param width      宽度（EMU），int 类型
     * @param height     高度（EMU），int 类型
     * @param offX       X 偏移（EMU），int 类型，组内子形状使用
     * @param offY       Y 偏移（EMU），int 类型，组内子形状使用
     * @param fillColor  填充颜色，String 类型，"none" 表示无填充，null 表示未指定
     * @param strokeColor 描边颜色，String 类型，可为 null
     * @param strokeWidth 描边宽度（pt），float 类型
     * @param wrapMode   环绕模式，WrapMode 枚举
     * @param chOffX     组坐标原点 X（EMU），int 类型
     * @param chOffY     组坐标原点 Y（EMU），int 类型
     * @param chExtW     组坐标宽度（EMU），int 类型
     * @param chExtH     组坐标高度（EMU），int 类型
     * @param children   子形状列表，List&lt;ShapeElement&gt; 类型
     */
    public ShapeElement(String preset, int width, int height,
                        int offX, int offY,
                        String fillColor, String strokeColor, float strokeWidth,
                        WrapMode wrapMode,
                        int chOffX, int chOffY, int chExtW, int chExtH,
                        List<ShapeElement> children) {
        this.preset = preset;
        this.width = width;
        this.height = height;
        this.offX = offX;
        this.offY = offY;
        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.strokeWidth = strokeWidth;
        this.wrapMode = wrapMode;
        this.chOffX = chOffX;
        this.chOffY = chOffY;
        this.chExtW = chExtW;
        this.chExtH = chExtH;
        this.children = children;
    }

    /**
     * 便捷构造方法，适用于单个预设几何形状（无子形状、无偏移）。
     * 内部调用完整构造方法，offX/offY 设为 0，chOff 设为 0，chExt 设为 width/height。
     *
     * @param preset     预设几何体名称
     * @param width      宽度（EMU）
     * @param height     高度（EMU）
     * @param fillColor  填充颜色
     * @param strokeColor 描边颜色
     * @param strokeWidth 描边宽度（pt）
     * @param wrapMode   环绕模式
     */
    public ShapeElement(String preset, int width, int height,
                        String fillColor, String strokeColor, float strokeWidth,
                        WrapMode wrapMode) {
        this(preset, width, height, 0, 0, fillColor, strokeColor, strokeWidth,
                wrapMode, 0, 0, width, height, Collections.<ShapeElement>emptyList());
    }

    /** {@return 预设几何体名称} */
    public String preset() { return preset; }
    /** {@return 形状宽度（EMU）} */
    public int width() { return width; }
    /** {@return 形状高度（EMU）} */
    public int height() { return height; }
    /** {@return 形状 X 偏移（EMU）} */
    public int offX() { return offX; }
    /** {@return 形状 Y 偏移（EMU）} */
    public int offY() { return offY; }
    /** {@return 填充颜色，可为 {@code null}} */
    public String fillColor() { return fillColor; }
    /** {@return 描边颜色，可为 {@code null}} */
    public String strokeColor() { return strokeColor; }
    /** {@return 描边宽度（pt）} */
    public float strokeWidth() { return strokeWidth; }
    /** {@return 文字环绕模式} */
    public WrapMode wrapMode() { return wrapMode; }
    /** {@return 组坐标系原点 X} */
    public int chOffX() { return chOffX; }
    /** {@return 组坐标系原点 Y} */
    public int chOffY() { return chOffY; }
    /** {@return 组坐标系宽度} */
    public int chExtW() { return chExtW; }
    /** {@return 组坐标系高度} */
    public int chExtH() { return chExtH; }
    /** {@return 子形状列表} */
    public List<ShapeElement> children() { return children; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShapeElement)) return false;
        ShapeElement that = (ShapeElement) o;
        return width == that.width
                && height == that.height
                && offX == that.offX
                && offY == that.offY
                && Float.compare(that.strokeWidth, strokeWidth) == 0
                && chOffX == that.chOffX
                && chOffY == that.chOffY
                && chExtW == that.chExtW
                && chExtH == that.chExtH
                && Objects.equals(preset, that.preset)
                && Objects.equals(fillColor, that.fillColor)
                && Objects.equals(strokeColor, that.strokeColor)
                && wrapMode == that.wrapMode
                && Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preset, width, height, offX, offY,
                fillColor, strokeColor, strokeWidth, wrapMode,
                chOffX, chOffY, chExtW, chExtH, children);
    }

    @Override
    public String toString() {
        return "ShapeElement[preset=" + preset
                + ", width=" + width
                + ", height=" + height
                + ", offX=" + offX
                + ", offY=" + offY
                + ", fillColor=" + fillColor
                + ", strokeColor=" + strokeColor
                + ", strokeWidth=" + strokeWidth
                + ", wrapMode=" + wrapMode
                + ", chOffX=" + chOffX
                + ", chOffY=" + chOffY
                + ", chExtW=" + chExtW
                + ", chExtH=" + chExtH
                + ", children=" + children + "]";
    }
}
