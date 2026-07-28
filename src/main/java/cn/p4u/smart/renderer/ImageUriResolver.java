package cn.p4u.smart.renderer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Pluggable image URI resolver — replaces hardcoded ImageMode enum.
 * <p>
 * Implementations encode a file as a URI string for use in HTML img src
 * attributes. Strategies include base64 data URIs and cloud storage URLs
 * (e.g. Aliyun OSS).
 * <p>
 * Each implementation owns its lifecycle and configuration; HtmlRenderer
 * calls {@link #resolve(Path, String)} without knowing the backend.
 */
@FunctionalInterface
public interface ImageUriResolver {

    /**
     * Resolve an image file to a URI suitable for an HTML src attribute.
     *
     * @param imagePath absolute filesystem path to the image file
     * @param mimeType  image MIME type (e.g. "image/png", "image/x-wmf")
     * @return a ResolveResult with the URI and effective MIME type
     * @throws IOException if file read, encoding, or upload fails
     */
    ResolveResult resolve(Path imagePath, String mimeType) throws IOException;

    /**
     * Immutable result returned by {@link #resolve(Path, String)}.
     *
     * @param uri      the resolved URI string for the src attribute
     * @param mimeType effective MIME type — may differ from input if the
     *                 resolver performed format conversion (e.g. WMF->PNG)
     */
    record ResolveResult(String uri, String mimeType) {}
}
