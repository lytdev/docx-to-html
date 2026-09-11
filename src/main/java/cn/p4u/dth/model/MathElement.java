package cn.p4u.dth.model;

import java.util.Objects;

/**
 * 公式元素不可变值类，表示段落中的一个数学公式。
 * <p>
 * 核心职责：封装公式的 LaTeX 表达式和可选的公式图片回退路径，
 * 支持 MathML（OMML）→ LaTeX 转换与图片渲染两种呈现方式。
 * <p>
 * 主要使用场景：DocumentParser 解析 m:oMath / m:oMathPara 时，
 * 先调用 OmmlToLatexConverter 将 OMML 转为 LaTeX 字符串，
 * 再查找 r:id 引用的公式图片作为回退，两者一同存入 MathElement；
 * HtmlRenderer 优先渲染为带 data-latex 属性的 &lt;img&gt;，
 * 配合前端 MathJax/KaTeX 渲染，也可直接显示公式图片。
 */
public final class MathElement implements ParagraphElement {

    /** 公式的 LaTeX 表达式，String 类型，转换失败时可能为空字符串但不为 null */
    private final String latex;
    /** 公式的 MathML XML 表示，String 类型，可为 null（未转换或无 OMML 原数据时） */
    private final String mathml;
    /** 公式图片的介质相对路径（如 "media/image1.emf"），可为 null 表示无图片回退 */
    private final String imagePath;
    /** 公式图片的 MIME 类型，可为 null */
    private final String mimeType;
    /** 公式图片在 Word 中的显示宽度，单位为 EMU；0 表示未知 */
    private final int width;
    /** 公式图片在 Word 中的显示高度，单位为 EMU；0 表示未知 */
    private final int height;

    /**
     * 构造公式元素。
     *
     * @param latex     LaTeX 表达式，String 类型，可为空字符串
     * @param imagePath 图片回退路径，String 类型，可为 null
     * @param mimeType  图片 MIME 类型，String 类型，可为 null
     */
    public MathElement(String latex, String imagePath, String mimeType) {
        this(latex, null, imagePath, mimeType);
    }

    /**
     * 构造公式元素（含 MathML）。
     *
     * @param latex     LaTeX 表达式，String 类型，可为空字符串
     * @param mathml    MathML XML，String 类型，可为 null
     * @param imagePath 图片回退路径，String 类型，可为 null
     * @param mimeType  图片 MIME 类型，String 类型，可为 null
     */
    public MathElement(String latex, String mathml, String imagePath, String mimeType) {
        this(latex, mathml, imagePath, mimeType, 0, 0);
    }

    /**
     * 构造公式元素（含 MathML）。
     *
     * @param latex     LaTeX 表达式，String 类型，可为空字符串
     * @param mathml    MathML XML，String 类型，可为 null
     * @param imagePath 图片回退路径，String 类型，可为 null
     * @param mimeType  图片 MIME 类型，String 类型，可为 null
     * @param width  图片的宽
     * @param height  图片的高
     */
    public MathElement(String latex, String mathml, String imagePath, String mimeType,
                       int width, int height) {
        this.latex = latex;
        this.mathml = mathml;
        this.imagePath = imagePath;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
    }

    /**
     * 返回 LaTeX。
     * @return 公式的 LaTeX 表达式
     */
    public String latex() { return latex; }

    /**
     * 返回 MathML。
     * @return 公式的 MathML XML；不存在时为 {@code null}
     */
    public String mathml() { return mathml; }

    /**
     * 返回公式图片路径。
     * @return 相对路径；不存在时为 {@code null}
     */
    public String imagePath() { return imagePath; }

    /**
     * 返回公式图片类型。
     * @return MIME 类型；不存在时为 {@code null}
     */
    public String mimeType() { return mimeType; }

    /**
     * 返回公式图片宽度。
     * @return 宽度（EMU），0 表示未知
     */
    public int width() { return width; }

    /**
     * 返回公式图片高度。
     * @return 高度（EMU），0 表示未知
     */
    public int height() { return height; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MathElement)) return false;
        MathElement that = (MathElement) o;
        return width == that.width
                && height == that.height
                && Objects.equals(latex, that.latex)
                && Objects.equals(mathml, that.mathml)
                && Objects.equals(imagePath, that.imagePath)
                && Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latex, mathml, imagePath, mimeType, width, height);
    }

    @Override
    public String toString() {
        return "MathElement[latex=" + latex
                + ", mathml=" + mathml
                + ", imagePath=" + imagePath
                + ", mimeType=" + mimeType
                + ", width=" + width
                + ", height=" + height + "]";
    }
}
