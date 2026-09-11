package cn.p4u.dth.model;

import java.util.Objects;

/**
 * 图片元素不可变值类，表示段落中嵌入的一张图片。
 * <p>
 * 核心职责：封装图片的介质路径、MIME 类型、尺寸（EMU 单位）、文字环绕模式和替代文本，
 * 是文档模型中图片的完整描述。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:drawing / v:imagedata 时构建 ImageElement；
 * HtmlRenderer 根据配置将其渲染为 base64 内嵌的 &lt;img&gt; 或外部链接的 &lt;img&gt;；
 * 对于 WMF/EMF 格式，HtmlRenderer 按 ConversionConfig 中的策略通过 WmfConverter 转换为 PNG。
 */
public final class ImageElement implements ParagraphElement {

    /** 图片在 docx 解压目录中的相对路径（如 "media/image1.png"） */
    private final String mediaPath;
    /** 图片的 MIME 类型（如 "image/png"） */
    private final String mimeType;
    /** 图片宽度，单位为 EMU（English Metric Units），0 表示未知 */
    private final int width;
    /** 图片高度，单位为 EMU，0 表示未知 */
    private final int height;
    /** 文字环绕模式 */
    private final WrapMode wrapMode;
    /** 图片名称或别名，用于 HTML img 的 alt 属性；不存在时为 null */
    private final String altText;

    /**
     * 构造图片元素。
     *
     * @param mediaPath 介质相对路径，String 类型，如 "media/image1.png"
     * @param mimeType  MIME 类型，String 类型，如 "image/png"
     * @param width     宽度（EMU），int 类型，0 表示尺寸未知
     * @param height    高度（EMU），int 类型，0 表示尺寸未知
     * @param wrapMode  环绕模式，WrapMode 枚举，不可为 null
     */
    public ImageElement(String mediaPath, String mimeType,
                        int width, int height,
                        WrapMode wrapMode) {
        this(mediaPath, mimeType, width, height, wrapMode, null);
    }

    /**
     * 构造包含名称或别名的图片元素。
     *
     * @param mediaPath 介质相对路径，如 {@code media/image1.png}
     * @param mimeType 图片 MIME 类型，如 {@code image/png}
     * @param width 图片宽度（EMU），0 表示未知
     * @param height 图片高度（EMU），0 表示未知
     * @param wrapMode 文字环绕模式
     * @param altText 图片名称或别名，可为 {@code null}
     */
    public ImageElement(String mediaPath, String mimeType,
                        int width, int height,
                        WrapMode wrapMode, String altText) {
        this.mediaPath = mediaPath;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.wrapMode = wrapMode;
        this.altText = altText;
    }

    /**
     * 返回图片路径。
     * @return DOCX 解压目录中的相对路径
     */
    public String mediaPath() { return mediaPath; }

    /**
     * 返回图片类型。
     * @return 图片 MIME 类型
     */
    public String mimeType() { return mimeType; }

    /**
     * 返回图片宽度。
     * @return 图片宽度（EMU），0 表示未知
     */
    public int width() { return width; }

    /**
     * 返回图片高度。
     * @return 图片高度（EMU），0 表示未知
     */
    public int height() { return height; }

    /**
     * 返回环绕方式。
     * @return 文字环绕模式
     */
    public WrapMode wrapMode() { return wrapMode; }

    /**
     * 返回替代文本。
     * @return 图片名称或别名；不存在时为 {@code null}
     */
    public String altText() { return altText; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImageElement)) return false;
        ImageElement that = (ImageElement) o;
        return width == that.width
                && height == that.height
                && Objects.equals(mediaPath, that.mediaPath)
                && Objects.equals(mimeType, that.mimeType)
                && Objects.equals(altText, that.altText)
                && wrapMode == that.wrapMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mediaPath, mimeType, width, height, wrapMode, altText);
    }

    @Override
    public String toString() {
        return "ImageElement[mediaPath=" + mediaPath
                + ", mimeType=" + mimeType
                + ", width=" + width
                + ", height=" + height
                + ", wrapMode=" + wrapMode
                + ", altText=" + altText + "]";
    }
}
