package cn.p4u.smart.renderer;

import java.nio.file.Path;

/**
 * WMF/EMF 格式检测工具类。
 *
 * <p>核心职责：提供静态方法判断 MIME 类型或文件路径是否为 WMF/EMF 格式。
 * 原始图片处理的 base64 编码和文件复制逻辑已迁移至 {@link ImageUriResolver} 实现类。</p>
 *
 * <p>主要使用场景：{@link HtmlRenderer} 和 {@link WmfConverter} 测试/渲染中
 * 判断是否需要执行 WMF→PNG 格式转换。</p>
 */
public final class ImageHandler {

    private ImageHandler() {}

    /**
     * 判断给定 MIME 类型是否为 WMF 或 EMF 格式。
     *
     * @param mimeType 图像的 MIME 类型字符串，例如 "image/x-wmf"
     * @return 如果是 WMF 或 EMF 类型返回 true，否则返回 false
     */
    public static boolean isWmfOrEmf(String mimeType) {
        return WmfConverter.isWmfOrEmf(mimeType);
    }

    /**
     * 判断给定文件路径的扩展名是否为 .wmf 或 .emf。
     *
     * @param path 文件路径，可为 null
     * @return 如果路径以 .wmf 或 .emf 结尾（不区分大小写）返回 true；路径为 null 时返回 false
     */
    public static boolean isWmfOrEmfPath(Path path) {
        return WmfConverter.isWmfOrEmfPath(path);
    }
}
