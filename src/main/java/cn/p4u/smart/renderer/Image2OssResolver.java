package cn.p4u.smart.renderer;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Resolves images by uploading to Aliyun OSS and returning a public HTTPS URL.
 * <p>
 * Each instance holds a long-lived OSS client. Call {@link #close()} when done.
 */
public final class Image2OssResolver implements ImageUriResolver, AutoCloseable {

    /** OSS connection parameters — immutable. */
    public record OssConfig(String endpoint, String bucket, String accessKey, String secretKey, String basePath) {
        public OssConfig {
            java.util.Objects.requireNonNull(endpoint, "endpoint");
            java.util.Objects.requireNonNull(bucket, "bucket");
            java.util.Objects.requireNonNull(accessKey, "accessKey");
            java.util.Objects.requireNonNull(secretKey, "secretKey");
        }

        /** Convenience constructor without basePath. */
        public OssConfig(String endpoint, String bucket, String accessKey, String secretKey) {
            this(endpoint, bucket, accessKey, secretKey, "");
        }
    }

    private final OSS client;
    private final String bucket;
    private final String endpoint;
    private final String basePath;

    public Image2OssResolver(OssConfig config) {
        this.client = new OSSClientBuilder().build(config.endpoint(), config.accessKey(), config.secretKey());
        this.bucket = config.bucket();
        this.endpoint = config.endpoint();
        this.basePath = config.basePath().isEmpty() ? "" :
                (config.basePath().endsWith("/") ? config.basePath() : config.basePath() + "/");
    }

    @Override
    public ResolveResult resolve(Path imagePath, String mimeType) throws IOException {
        byte[] data = Files.readAllBytes(imagePath);
        String fileName = imagePath.getFileName().toString();
        String objectKey = basePath + UUID.randomUUID() + "-" + fileName;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(mimeType);
        client.putObject(bucket, objectKey, new ByteArrayInputStream(data), metadata);
        String uri = "https://" + bucket + "." + endpoint + "/" + objectKey;
        return new ResolveResult(uri, mimeType);
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
