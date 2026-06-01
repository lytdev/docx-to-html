package cn.p4u.smart.renderer;

import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 图片处理器，负责将 docx 中提取的图片资源嵌入到 HTML 输出中。
 *
 * <p>核心职责：
 * <ul>
 *   <li>将图片文件转换为 Base64 编码的 Data URI，用于 HTML 内联嵌入</li>
 *   <li>将图片文件复制到输出目录，用于 HTML 外部引用</li>
 *   <li>对 WMF/EMF 格式的图片自动转换为 PNG（浏览器无法直接渲染 WMF/EMF）</li>
 *   <li>防御路径遍历攻击，确保输出路径不会逃逸到目标目录之外</li>
 * </ul>
 *
 * <p>主要使用场景：
 * <ul>
 *   <li>{@link HtmlRenderer} 渲染图片元素时，根据配置选择内联（base64）或外联（文件复制）方式</li>
 *   <li>VML 形状中嵌入的旧格式图片（imagedata）也需要通过本类处理</li>
 * </ul>
 */
public final class ImageHandler {

    private static final Logger LOG = Logger.getLogger(ImageHandler.class.getName());

    private ImageHandler() {}

    /**
     * 将图片文件转为 Base64 Data URI（不指定逻辑尺寸的简化版本）。
     *
     * <p>当图片的显示尺寸未知时使用此方法，WMF/EMF 转换将以原始尺寸渲染。
     *
     * @param imagePath 图片文件的路径
     * @param mimeType  图片的 MIME 类型，如 "image/png"、"image/x-wmf"
     * @return Base64 编码的 Data URI 字符串；文件不存在或读取失败时返回空字符串
     */
    public static String toBase64DataUri(Path imagePath, String mimeType) {
        return toBase64DataUri(imagePath, mimeType, 0, 0);
    }

    /**
     * 将图片文件转为 Base64 Data URI，可指定逻辑显示尺寸。
     *
     * <p>处理流程：
     * <ol>
     *   <li>检查文件是否存在，不存在则记录警告并返回空字符串</li>
     *   <li>读取文件全部字节</li>
     *   <li>若 MIME 类型为 WMF/EMF，调用 {@link WmfConverter} 转为 PNG；
     *       转换失败则保留原始数据（浏览器可能无法显示）</li>
     *   <li>将字节数组进行 Base64 编码，拼接为 Data URI</li>
     * </ol>
     *
     * @param imagePath     图片文件的路径
     * @param mimeType      图片的 MIME 类型，如 "image/png"、"image/x-wmf"、"image/x-emf"
     * @param logicalWidth  图片在 HTML 中的逻辑显示宽度（CSS 像素），0 表示未知
     * @param logicalHeight 图片在 HTML 中的逻辑显示高度（CSS 像素），0 表示未知
     * @return Base64 编码的 Data URI 字符串，格式为 "data:&lt;mime&gt;;base64,&lt;data&gt;"；
     *         文件不存在或读取失败时返回空字符串
     */
    public static String toBase64DataUri(Path imagePath, String mimeType, int logicalWidth, int logicalHeight) {
        if (!Files.exists(imagePath)) {
            LOG.warning("Image file not found: " + imagePath);
            return "";
        }
        try {
            byte[] data = Files.readAllBytes(imagePath);
            String effectiveMime = mimeType;

            // WMF/EMF 格式浏览器无法直接渲染，自动转换为 PNG
            // 传入逻辑尺寸以保证转换后的 PNG 在高 DPI 屏幕上清晰
            if (WmfConverter.isWmfOrEmf(mimeType)) {
                byte[] png = WmfConverter.convertToPng(data, logicalWidth, logicalHeight);
                if (png != null) {
                    data = png;
                    // 转换成功后 MIME 类型需更新为 PNG，否则 Data URI 的类型声明与实际数据不匹配
                    effectiveMime = "image/png";
                } else {
                    LOG.warning("WMF→PNG conversion failed, embedding raw WMF (browsers may not display it): " + imagePath);
                }
            }

            // 将字节数据编码为 Base64 并拼接成 Data URI，用于 HTML 中 <img src="data:..."> 内联嵌入
            return "data:" + effectiveMime + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (IOException e) {
            LOG.warning("Failed to read image: " + imagePath + " - " + e.getMessage());
            return "";
        }
    }

    /**
     * 将图片文件复制到输出目录（用于 HTML 外部引用模式）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>解析目标路径并规范化，防止路径遍历攻击</li>
     *   <li>创建必要的父目录</li>
     *   <li>若图片为 WMF/EMF 格式，自动转换为 PNG 后保存，文件扩展名同步替换为 .png</li>
     *   <li>否则直接复制原始文件</li>
     * </ol>
     *
     * @param imagePath    源图片文件的路径
     * @param outputDir    输出目录的路径
     * @param relativePath 图片在输出目录中的相对路径，如 "media/image1.png"
     * @return 实际写入的目标文件路径；WMF/EMF 转换成功时返回 .png 文件路径；
     *         发生路径遍历或 IO 异常时返回原始 imagePath
     */
    public static Path copyToDir(Path imagePath, Path outputDir, String relativePath) {
        // 规范化路径以防止路径遍历：如果 resolve + normalize 后的目标路径
        // 不以 outputDir 开头，说明 relativePath 中包含 ".." 等逃逸成分
        Path target = outputDir.resolve(relativePath).normalize();
        if (!target.startsWith(outputDir)) {
            LOG.warning("Path traversal blocked: " + relativePath);
            return imagePath;
        }
        try {
            Files.createDirectories(target.getParent());

            // WMF/EMF 格式需要自动转换为 PNG 再保存，因为浏览器无法直接显示这些格式
            if (WmfConverter.isWmfOrEmfPath(imagePath)) {
                byte[] wmfData = Files.readAllBytes(imagePath);
                // 此处传入 0, 0 表示逻辑尺寸未知，WmfConverter 将使用图像原生尺寸
                byte[] png = WmfConverter.convertToPng(wmfData, 0, 0);
                if (png != null) {
                    // 将文件扩展名从 .wmf/.emf 替换为 .png，保持与实际格式一致
                    String pngName = relativePath.replaceAll("\\.(wmf|emf)$", ".png");
                    Path pngTarget = outputDir.resolve(pngName).normalize();
                    // 对替换扩展名后的路径再次进行路径遍历检查，防止恶意构造的替换结果逃逸
                    if (!pngTarget.startsWith(outputDir)) {
                        LOG.warning("Path traversal blocked: " + pngName);
                        return imagePath;
                    }
                    Files.createDirectories(pngTarget.getParent());
                    Files.write(pngTarget, png);
                    return pngTarget;
                }
            }

            // 非 WMF/EMF 格式或转换失败时，直接复制原始文件
            Files.copy(imagePath, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            LOG.warning("Failed to copy image: " + imagePath + " - " + e.getMessage());
            return imagePath;
        }
    }
}
