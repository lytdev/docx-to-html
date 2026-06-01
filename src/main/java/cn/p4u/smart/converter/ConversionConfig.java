package cn.p4u.smart.converter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * 转换配置不可变值类，控制 DocxConverter 的转换行为。
 * <p>
 * 核心职责：封装图片嵌入模式、图片输出目录、解压目录和临时文件保留策略，
 * 作为 DocxConverter.convert() 的参数传入。
 * <p>
 * 主要使用场景：CliRunner 根据命令行参数构建 ConversionConfig；
 * DocxConverter 用它决定图片处理方式和临时文件清理行为。
 */
public final class ConversionConfig {

    /** 图片嵌入模式 */
    private final ImageMode imageMode;
    /** 图片输出目录（link 模式下图片复制到此目录） */
    private final Path imageOutputDir;
    /** docx 解压目录（由 DocxExtractor 创建，CLI 层通常设为 null） */
    private final Path extractedDir;
    /** 是否保留解压的临时目录（调试用） */
    private final boolean keepTemp;
    /**
     * LaTeX 公式渲染服务 URL 模板，包含 LaTeX 代码的公式将渲染为图片。
     * 模板中用 {latex} 占位符表示 LaTeX 源码，渲染时替换为 URL 编码后的 LaTeX。
     * 默认使用 codecogs.com 的 SVG 渲染服务。设为 null 则不使用在线服务渲染。
     * <p>例如: "https://latex.codecogs.com/svg.image?{latex}"
     */
    private final String latexRenderUrl;

    /**
     * 构造转换配置。
     *
     * @param imageMode     图片嵌入模式，ImageMode 枚举，不可为 null
     * @param imageOutputDir 图片输出目录，Path 类型，link 模式下必须有值
     * @param extractedDir  解压目录，Path 类型，可为 null（由转换器自动创建）
     * @param keepTemp      是否保留临时目录，boolean 类型
     */
    public ConversionConfig(ImageMode imageMode, Path imageOutputDir,
                            Path extractedDir, boolean keepTemp) {
        this(imageMode, imageOutputDir, extractedDir, keepTemp,
                "https://latex.codecogs.com/svg.image?{latex}");
    }

    /**
     * 构造转换配置（含 LaTeX 渲染 URL）。
     *
     * @param imageMode       图片嵌入模式，ImageMode 枚举，不可为 null
     * @param imageOutputDir  图片输出目录，Path 类型，link 模式下必须有值
     * @param extractedDir    解压目录，Path 类型，可为 null
     * @param keepTemp        是否保留临时目录，boolean 类型
     * @param latexRenderUrl  LaTeX 渲染服务 URL 模板，String 类型，可为 null
     */
    public ConversionConfig(ImageMode imageMode, Path imageOutputDir,
                            Path extractedDir, boolean keepTemp,
                            String latexRenderUrl) {
        this.imageMode = imageMode;
        this.imageOutputDir = imageOutputDir;
        this.extractedDir = extractedDir;
        this.keepTemp = keepTemp;
        this.latexRenderUrl = latexRenderUrl;
    }

    public ImageMode imageMode() { return imageMode; }
    public Path imageOutputDir() { return imageOutputDir; }
    public Path extractedDir() { return extractedDir; }
    public boolean keepTemp() { return keepTemp; }
    /**
     * 获取 LaTeX 公式渲染服务 URL 模板。
     * @return URL 模板，可能为 null（表示不使用在线渲染服务）
     */
    public String latexRenderUrl() { return latexRenderUrl; }

    /**
     * 图片嵌入模式枚举。
     * <ul>
     *   <li>BASE64 — 将图片转为 base64 data URI 内嵌到 HTML</li>
     *   <li>LINK  — 将图片复制到外部目录，HTML 中使用相对路径引用</li>
     * </ul>
     */
    public enum ImageMode { BASE64, LINK }

    /**
     * 创建 base64 模式的默认配置。
     * 图片输出目录默认为 "images"，不保留临时文件。
     *
     * @return 默认 base64 配置，ConversionConfig 类型
     */
    public static ConversionConfig base64Defaults() {
        return new ConversionConfig(ImageMode.BASE64, Paths.get("images"), null, false);
    }

    /**
     * 创建 link 模式的默认配置。
     * 不保留临时文件。
     *
     * @param imageOutputDir 图片输出目录，Path 类型
     * @return 默认 link 配置，ConversionConfig 类型
     */
    public static ConversionConfig linkDefaults(Path imageOutputDir) {
        return new ConversionConfig(ImageMode.LINK, imageOutputDir, null, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversionConfig)) return false;
        ConversionConfig that = (ConversionConfig) o;
        return keepTemp == that.keepTemp
                && imageMode == that.imageMode
                && Objects.equals(imageOutputDir, that.imageOutputDir)
                && Objects.equals(extractedDir, that.extractedDir)
                && Objects.equals(latexRenderUrl, that.latexRenderUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageMode, imageOutputDir, extractedDir, keepTemp, latexRenderUrl);
    }

    @Override
    public String toString() {
        return "ConversionConfig[imageMode=" + imageMode
                + ", imageOutputDir=" + imageOutputDir
                + ", extractedDir=" + extractedDir
                + ", keepTemp=" + keepTemp
                + ", latexRenderUrl=" + latexRenderUrl + "]";
    }
}
