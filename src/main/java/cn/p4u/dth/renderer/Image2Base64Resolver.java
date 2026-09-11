package cn.p4u.dth.renderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Resolves images as base64 data URIs for inline embedding.
 * <p>
 * Stateless — one instance can serve the entire application lifetime.
 */
public final class Image2Base64Resolver implements ImageUriResolver {

    /** 创建无状态的 Base64 图片资源解析器。 */
    public Image2Base64Resolver() {}

    @Override
    public ResolveResult resolve(Path imagePath, String mimeType) throws IOException {
        byte[] data = Files.readAllBytes(imagePath);
        String uri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
        return new ResolveResult(uri, mimeType);
    }
}
