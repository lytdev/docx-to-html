# ImageUriResolver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `ConversionConfig.ImageMode` with a pluggable `ImageUriResolver` interface, enabling base64 and Aliyun OSS backends via a single `resolve()` call in `HtmlRenderer`.

**Architecture:** Create `ImageUriResolver` interface with `ResolveResult` return type in the `renderer` package. Two implementations: `Image2Base64Resolver` (stateless, base64 encoding) and `Image2OssResolver` (upload to OSS, returns public URL). WMF/EMF→PNG conversion pulled out as a shared pre-step in `HtmlRenderer` before calling `resolve()`. `ConversionConfig` holds the resolver instead of the enum.

**Tech Stack:** JDK 21, Maven, aliyun-sdk-oss 3.17.4, JUnit Jupiter 5.11.4.

## Global Constraints

- `ImageUriResolver` interface must be in `cn.p4u.smart.renderer` package (not parser)
- `resolve(Path imagePath, String mimeType)` returns `ResolveResult(uri, mimeType)`
- WMF/EMF conversion is a shared pre-step before resolvers are called — resolvers don't handle it
- Math formula images (`renderMath`) are NOT changed by this refactoring
- Preexisting stubs in `cn.p4u.smart.parser` package must be deleted
- All existing tests must pass after the refactoring
- `ImageHandler` shrinks — `toBase64DataUri` and `copyToDir` logic moves out; WMF helpers stay

---

### Task 1: Delete old parser stubs + Create ImageUriResolver interface + ResolveResult

**Files:**
- Delete: `src/main/java/cn/p4u/smart/parser/ImageUriResolver.java`
- Delete: `src/main/java/cn/p4u/smart/parser/Image2Base64Resolver.java`
- Delete: `src/main/java/cn/p4u/smart/parser/Image2OssResolver.java`
- Create: `src/main/java/cn/p4u/smart/renderer/ImageUriResolver.java`

**Interfaces:**
- Consumes: nothing
- Produces: `ImageUriResolver.resolve(Path, String): ResolveResult`, `ResolveResult(String uri, String mimeType)` — used by Tasks 2-9

- [ ] **Step 1: Remove old stubs from parser package**

```bash
git rm src/main/java/cn/p4u/smart/parser/ImageUriResolver.java src/main/java/cn/p4u/smart/parser/Image2Base64Resolver.java src/main/java/cn/p4u/smart/parser/Image2OssResolver.java
```

- [ ] **Step 2: Create ImageUriResolver.java in renderer package**

```java
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
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/cn/p4u/smart/renderer/ImageUriResolver.java
git commit -m "refactor: move ImageUriResolver to renderer package, redesign interface
- Delete parser-package stubs (old InputStream-based API)
- New resolve(Path, String) returns ResolveResult record
- @FunctionalInterface so implementations can be lambdas"
```

---

### Task 2: Create Image2Base64Resolver

**Files:**
- Create: `src/main/java/cn/p4u/smart/renderer/Image2Base64Resolver.java`
- Delete: no test for this yet (covered in Task 10)

**Interfaces:**
- Consumes: `ImageUriResolver` interface, `ResolveResult` record (Task 1)
- Produces: `Image2Base64Resolver` — stateless, ready-to-use resolver

- [ ] **Step 1: Create the class**

```java
package cn.p4u.smart.renderer;

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

    @Override
    public ResolveResult resolve(Path imagePath, String mimeType) throws IOException {
        byte[] data = Files.readAllBytes(imagePath);
        String uri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
        return new ResolveResult(uri, mimeType);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/p4u/smart/renderer/Image2Base64Resolver.java
git commit -m "feat: add Image2Base64Resolver — base64 data URI resolver"
```

---

### Task 3: Add OSS SDK dependency + Create Image2OssResolver

**Files:**
- Modify: `pom.xml` — add aliyun-sdk-oss dependency
- Create: `src/main/java/cn/p4u/smart/renderer/Image2OssResolver.java`

**Interfaces:**
- Consumes: `ImageUriResolver` interface, `ResolveResult` record (Task 1)
- Produces: `Image2OssResolver(OssConfig)` — OSS-backed resolver

- [ ] **Step 1: Add OSS SDK to pom.xml**

Add inside `<dependencies>` block in `pom.xml`:

```xml
    <dependency>
      <groupId>com.aliyun.oss</groupId>
      <artifactId>aliyun-sdk-oss</artifactId>
      <version>3.17.4</version>
    </dependency>
```

- [ ] **Step 2: Verify dependency resolves**

```bash
mvn dependency:resolve -pl .
```
Expected: aliyun-sdk-oss resolves without errors.

- [ ] **Step 3: Create Image2OssResolver**

```java
package cn.p4u.smart.renderer;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;

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
        client.putObject(bucket, objectKey, new java.io.ByteArrayInputStream(data));
        String uri = "https://" + bucket + "." + endpoint + "/" + objectKey;
        return new ResolveResult(uri, mimeType);
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
mvn compile
```

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/cn/p4u/smart/renderer/Image2OssResolver.java
git commit -m "feat: add Image2OssResolver — Aliyun OSS image upload resolver"
```

---

### Task 4: Refactor ConversionConfig — replace ImageMode with ImageUriResolver

**Files:**
- Modify: `src/main/java/cn/p4u/smart/converter/ConversionConfig.java`

**Interfaces:**
- Consumes: `ImageUriResolver`, `ResolveResult` (Task 1)
- Produces: `ConversionConfig(ImageUriResolver, Path, boolean, String)` — BREAKING change for Tasks 5-9

- [ ] **Step 1: Rewrite ConversionConfig**

Remove `ImageMode` enum entirely. Replace `imageMode` + `imageOutputDir` fields with `ImageUriResolver imageUriResolver`. Update both constructors, getters, `equals`/`hashCode`/`toString`, and factory methods.

```java
package cn.p4u.smart.converter;

import cn.p4u.smart.renderer.ImageUriResolver;
import java.nio.file.Path;
import java.util.Objects;

public final class ConversionConfig {

    private final ImageUriResolver imageUriResolver;
    private final Path extractedDir;
    private final boolean keepTemp;
    private final String latexRenderUrl;

    public ConversionConfig(ImageUriResolver imageUriResolver, Path extractedDir,
                            boolean keepTemp) {
        this(imageUriResolver, extractedDir, keepTemp,
                "https://latex.codecogs.com/svg.image?{latex}");
    }

    public ConversionConfig(ImageUriResolver imageUriResolver, Path extractedDir,
                            boolean keepTemp, String latexRenderUrl) {
        this.imageUriResolver = imageUriResolver;
        this.extractedDir = extractedDir;
        this.keepTemp = keepTemp;
        this.latexRenderUrl = latexRenderUrl;
    }

    public ImageUriResolver imageUriResolver() { return imageUriResolver; }
    public Path extractedDir() { return extractedDir; }
    public boolean keepTemp() { return keepTemp; }
    public String latexRenderUrl() { return latexRenderUrl; }

    /** Convenience factory with Image2Base64Resolver as default. */
    public static ConversionConfig defaults() {
        return new ConversionConfig(new cn.p4u.smart.renderer.Image2Base64Resolver(), null, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversionConfig that)) return false;
        return keepTemp == that.keepTemp
                && Objects.equals(imageUriResolver, that.imageUriResolver)
                && Objects.equals(extractedDir, that.extractedDir)
                && Objects.equals(latexRenderUrl, that.latexRenderUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageUriResolver, extractedDir, keepTemp, latexRenderUrl);
    }

    @Override
    public String toString() {
        return "ConversionConfig[imageUriResolver=" + imageUriResolver.getClass().getSimpleName()
                + ", extractedDir=" + extractedDir
                + ", keepTemp=" + keepTemp
                + ", latexRenderUrl=" + latexRenderUrl + "]";
    }
}
```

Note: At this point `ConversionConfig` will NOT compile because callers still reference `imageMode()`, `imageOutputDir()`, `ImageMode`. That's expected — those will be fixed in Tasks 5-9.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/cn/p4u/smart/converter/ConversionConfig.java
git commit -m "refactor: replace ImageMode with ImageUriResolver in ConversionConfig
BREAKING: imageMode()/imageOutputDir() removed. Callers must adapt."
```

---

### Task 5: Refactor HtmlRenderer — integrate ImageUriResolver

**Files:**
- Modify: `src/main/java/cn/p4u/smart/renderer/HtmlRenderer.java`

**Interfaces:**
- Consumes: `ConversionConfig.imageUriResolver()` (Task 4), `ImageUriResolver.resolve()` (Task 1), `WmfConverter`, `ImageHandler.isWmfOrEmf` (existing)
- Produces: `renderImage()` calls `resolver.resolve()` uniformly; WMF pre-processing extracted

- [ ] **Step 1: Rewrite renderImage()**

Replace lines 487-524 (entire `renderImage` method body) with:

```java
    private static void renderImage(StringBuilder sb, ImageElement img, ConversionConfig config) {
        sb.append("<img");
        Path mediaPath = resolveMediaPath(img.mediaPath(), config);
        if (mediaPath == null || !Files.exists(mediaPath)) {
            sb.append("><span style=\"color: #999; font-style: italic;\">[image not found]</span>");
            return;
        }

        boolean isWmf = WmfConverter.isWmfOrEmf(img.mimeType());
        int pxW = img.width() > 0 ? emusToPx(img.width()) : 0;
        int pxH = img.height() > 0 ? emusToPx(img.height()) : 0;

        ImageUriResolver resolver = config.imageUriResolver();
        if (isWmf) {
            // Shared pre-step: convert WMF/EMF to PNG before resolving
            try {
                byte[] raw = Files.readAllBytes(mediaPath);
                byte[] png = WmfConverter.convertToPng(raw, pxW, pxH);
                if (png != null) {
                    Path tmpFile = Files.createTempFile("wmf2png", ".png");
                    Files.write(tmpFile, png);
                    try {
                        ResolveResult result = resolver.resolve(tmpFile, "image/png");
                        sb.append(" src=\"").append(escapeAttr(result.uri())).append("\"");
                    } finally {
                        Files.deleteIfExists(tmpFile);
                    }
                } else {
                    // WMF conversion failed — try resolving raw file as fallback
                    ResolveResult result = resolver.resolve(mediaPath, img.mimeType());
                    sb.append(" src=\"").append(escapeAttr(result.uri())).append("\"");
                }
            } catch (IOException e) {
                LOG.warning("WMF pre-processing failed for " + mediaPath + ": " + e.getMessage());
                sb.append("><span style=\"color: #999; font-style: italic;\">[image conversion failed]</span>");
                return;
            }
        } else {
            // Non-WMF: resolve directly
            try {
                ResolveResult result = resolver.resolve(mediaPath, img.mimeType());
                sb.append(" src=\"").append(escapeAttr(result.uri())).append("\"");
            } catch (IOException e) {
                LOG.warning("Image resolve failed for " + mediaPath + ": " + e.getMessage());
                sb.append("><span style=\"color: #999; font-style: italic;\">[image resolve failed]</span>");
                return;
            }
        }

        if (img.width() > 0) sb.append(" width=\"").append(pxW).append("\"");
        if (img.height() > 0) sb.append(" height=\"").append(pxH).append("\"");
        if (img.wrapMode() == WrapMode.LEFT) sb.append(" style=\"float: left;\"");
        else if (img.wrapMode() == WrapMode.RIGHT) sb.append(" style=\"float: right;\"");
        else if (img.wrapMode() == WrapMode.TOP_AND_BOTTOM) sb.append(" style=\"display: block; margin: auto;\"");
        sb.append(">");
    }
```

Also remove the unused imports at the top if `ConversionConfig.ImageMode` was imported.

- [ ] **Step 2: Ensure LOG field exists**

Add to the class (if not already present):
```java
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(HtmlRenderer.class.getName());
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile
```
Expected: Only failures are from other callers that still reference `ImageMode`/`imageOutputDir` (CliRunner, GuiRunner, DocxConverter, tests). Those get fixed in Tasks 6-10.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/cn/p4u/smart/renderer/HtmlRenderer.java
git commit -m "refactor: integrate ImageUriResolver in HtmlRenderer.renderImage()
- Replace ImageMode branch with single resolver.resolve() call
- Extract WMF/EMF->PNG as shared pre-step before resolve()"
```

---

### Task 6: Refactor DocxConverter — pass resolver through effectiveConfig

**Files:**
- Modify: `src/main/java/cn/p4u/smart/converter/DocxConverter.java`

**Interfaces:**
- Consumes: `ConversionConfig` new constructor (Task 4)
- Produces: `convert()` passes the resolver through to the effective config

- [ ] **Step 1: Update effectiveConfig construction**

Change lines 47-52 from:
```java
            ConversionConfig effectiveConfig = new ConversionConfig(
                    config.imageMode(),
                    config.imageOutputDir(),
                    extractedDir,
                    config.keepTemp()
            );
```
to:
```java
            ConversionConfig effectiveConfig = new ConversionConfig(
                    config.imageUriResolver(),
                    extractedDir,
                    config.keepTemp(),
                    config.latexRenderUrl()
            );
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```
Expected: Only failures from CliRunner and GuiRunner remain. DocxConverter now compiles.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/p4u/smart/converter/DocxConverter.java
git commit -m "refactor: pass ImageUriResolver through DocxConverter effectiveConfig"
```

---

### Task 7: Shrink ImageHandler — remove toBase64DataUri and copyToDir

**Files:**
- Modify: `src/main/java/cn/p4u/smart/renderer/ImageHandler.java`

**Interfaces:**
- Consumes: nothing new
- Produces: `ImageHandler` keeps only `isWmfOrEmf()`, `isWmfOrEmfPath()` (used by WmfConverter test/rendering)

- [ ] **Step 1: Remove toBase64DataUri methods and copyToDir**

Delete the following methods from `ImageHandler`:
- `toBase64DataUri(Path, String)` — lines 40-42
- `toBase64DataUri(Path, String, int, int)` — lines 63-91
- `copyToDir(Path, Path, String)` — lines 110-148

Delete the `LOG` field (no longer used by remaining code).

Delete `import java.util.Base64;` (no longer needed).

Keep `isWmfOrEmf(String)` and `isWmfOrEmfPath(Path)` — these are used elsewhere.

Keep the class-level Javadoc but update it to reflect the reduced scope.

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/p4u/smart/renderer/ImageHandler.java
git commit -m "refactor: shrink ImageHandler — remove base64/copy logic now in resolvers"
```

---

### Task 8: Update CliRunner — new resolver flags

**Files:**
- Modify: `src/main/java/cn/p4u/smart/cli/CliRunner.java`

**Interfaces:**
- Consumes: `ConversionConfig` new constructor (Task 4), `Image2Base64Resolver`, `Image2OssResolver`
- Produces: CLI accepts `--image-resolver` flag with optional OSS sub-flags

- [ ] **Step 1: Replace image-mode and image-dir flags**

Remove:
```java
@Option(names = "--image-mode", ...)
private String imageMode;
@Option(names = "--image-dir", ...)
private String imageDir;
```

Add:
```java
    @Option(names = "--image-resolver", description = "Image resolver: base64 (default) or oss",
            defaultValue = "base64")
    private String imageResolver;

    @Option(names = "--oss-endpoint", description = "OSS endpoint (required when --image-resolver=oss)")
    private String ossEndpoint;

    @Option(names = "--oss-bucket", description = "OSS bucket name (required when --image-resolver=oss)")
    private String ossBucket;

    @Option(names = "--oss-access-key", description = "OSS access key (required when --image-resolver=oss)")
    private String ossAccessKey;

    @Option(names = "--oss-secret-key", description = "OSS secret key (required when --image-resolver=oss)")
    private String ossSecretKey;

    @Option(names = "--oss-base-path", description = "OSS object key prefix")
    private String ossBasePath;
```

- [ ] **Step 2: Replace ImageMode resolution logic**

Remove:
```java
import cn.p4u.smart.converter.ConversionConfig.ImageMode;
...
        ImageMode mode = "link".equalsIgnoreCase(imageMode) ? ImageMode.LINK : ImageMode.BASE64;
        ConversionConfig config = new ConversionConfig(mode, Paths.get(imageDir), null, keepTemp);
```

Add:
```java
import cn.p4u.smart.renderer.Image2Base64Resolver;
import cn.p4u.smart.renderer.Image2OssResolver;
...
        ImageUriResolver resolver;
        if ("oss".equalsIgnoreCase(imageResolver)) {
            if (ossEndpoint == null || ossBucket == null || ossAccessKey == null || ossSecretKey == null) {
                System.err.println("Error: --oss-endpoint, --oss-bucket, --oss-access-key, and --oss-secret-key are required when --image-resolver=oss");
                return 1;
            }
            Image2OssResolver.OssConfig ossConfig = new Image2OssResolver.OssConfig(
                    ossEndpoint, ossBucket, ossAccessKey, ossSecretKey,
                    ossBasePath != null ? ossBasePath : "");
            resolver = new Image2OssResolver(ossConfig);
        } else {
            resolver = new Image2Base64Resolver();
        }
        ConversionConfig config = new ConversionConfig(resolver, null, keepTemp);
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile
```
Expected: Only GuiRunner and test compilation failures remain. CliRunner now compiles.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/cn/p4u/smart/cli/CliRunner.java
git commit -m "feat: replace --image-mode with --image-resolver in CliRunner
Support base64 (default) and oss resolver with --oss-* flags"
```

---

### Task 9: Update GuiRunner — adapt to new ConversionConfig

**Files:**
- Modify: `src/main/java/cn/p4u/smart/gui/GuiRunner.java`

**Interfaces:**
- Consumes: `ConversionConfig.defaults()` (Task 4)
- Produces: GUI uses default resolver

- [ ] **Step 1: Replace base64Defaults() call**

Change line 163 from:
```java
                return DocxConverter.convert(selectedFile, ConversionConfig.base64Defaults());
```
to:
```java
                return DocxConverter.convert(selectedFile, ConversionConfig.defaults());
```

Remove `import cn.p4u.smart.converter.ConversionConfig.ImageMode` if present (not present in current code — GuiRunner doesn't import ImageMode).

- [ ] **Step 2: Verify compilation**

```bash
mvn compile
```
Expected: BUILD SUCCESS — all main source files compile. Only test compilation failures remain.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/p4u/smart/gui/GuiRunner.java
git commit -m "refactor: adapt GuiRunner to new ConversionConfig.defaults()"
```

---

### Task 10: Update tests

**Files:**
- Modify: `src/test/java/cn/p4u/smart/renderer/ImageHandlerTest.java` — remove copyToDir test
- Modify: `src/test/java/cn/p4u/smart/converter/DocxConverterTest.java` — adapt constructor
- Modify: `src/test/java/cn/p4u/smart/renderer/HtmlRendererTest.java` — adapt ConversionConfig usage
- Modify: `src/test/java/cn/p4u/smart/CliConvertTest.java` — adapt if needed
- Modify: `src/test/java/cn/p4u/smart/integration/FullPipelineTest.java` — adapt ConversionConfig usage

**Interfaces:**
- Consumes: all completed tasks
- Produces: `mvn test` passes

- [ ] **Step 1: Update ImageHandlerTest.java**

Remove the `copiesImageToOutputDir` test (lines 31-39) — `copyToDir` no longer exists.

The `base64EncodesImage` test — `ImageHandler.toBase64DataUri` no longer exists. Replace it with a test for `Image2Base64Resolver`:

```java
package cn.p4u.smart.renderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void base64EncodesImage() throws Exception {
        Path imgFile = tempDir.resolve("test.png");
        Files.write(imgFile, new byte[]{(byte)0x89, 'P', 'N', 'G'});
        Image2Base64Resolver resolver = new Image2Base64Resolver();
        ImageUriResolver.ResolveResult result = resolver.resolve(imgFile, "image/png");
        assertTrue(result.uri().startsWith("data:image/png;base64,"));
        assertTrue(result.uri().length() > "data:image/png;base64,".length());
        assertEquals("image/png", result.mimeType());
    }

    @Test
    void returnsEmptyForMissingFile() {
        // This test no longer applies to ImageHandler directly.
        // Move to Image2Base64ResolverTest if needed, or assert IOException.
        assertThrows(java.io.IOException.class, () -> {
            new Image2Base64Resolver().resolve(Paths.get("/nonexistent/image.png"), "image/png");
        });
    }
}
```

- [ ] **Step 2: Update DocxConverterTest.java**

At line 22: `ConversionConfig.base64Defaults()` → `ConversionConfig.defaults()`

At line 50: `ConversionConfig.base64Defaults()` → `ConversionConfig.defaults()`

At lines 35-38, rewrite the test that uses `new ConversionConfig(ImageMode.BASE64, Paths.get("images"), extractedDir, false)`:
Change from:
```java
            ConversionConfig config = new ConversionConfig(
                    ConversionConfig.ImageMode.BASE64,
                    Paths.get("images"),
                    extractedDir,
                    false
            );
```
To:
```java
            ConversionConfig config = new ConversionConfig(
                    new cn.p4u.smart.renderer.Image2Base64Resolver(),
                    extractedDir,
                    false
            );
```

- [ ] **Step 3: Update HtmlRendererTest.java**

All 14 references to `ConversionConfig.base64Defaults()` → `ConversionConfig.defaults()`.

- [ ] **Step 4: Update CliConvertTest.java**

At line 21: Replace:
```java
    ConversionConfig.ImageMode mode = ConversionConfig.ImageMode.BASE64;
```
With (inline the resolver construction):
```java
    cn.p4u.smart.renderer.Image2Base64Resolver resolver = new cn.p4u.smart.renderer.Image2Base64Resolver();
```

And at line 23: Replace:
```java
    ConversionConfig config = new ConversionConfig(mode, Paths.get(imageDir), null, keepTemp);
```
With:
```java
    ConversionConfig config = new ConversionConfig(resolver, null, keepTemp);
```

- [ ] **Step 5: Update FullPipelineTest.java**

All 3 references to `ConversionConfig.base64Defaults()` → `ConversionConfig.defaults()`.

- [ ] **Step 4: Run all tests**

```bash
mvn test
```
Expected: All tests pass (105/106 — same pre-existing CliConvertTest fixture error allowed).

Iterate on any compilation/test failures.

- [ ] **Step 5: Commit**

```bash
git add src/test/
git commit -m "test: update tests for ImageUriResolver refactoring"
```

---

### Task 11: Cleanup — remove ImageHandler import from HtmlRenderer if stale

**Files:**
- Verify: `src/main/java/cn/p4u/smart/renderer/HtmlRenderer.java`

- [ ] **Step 1: Check for dead imports in HtmlRenderer**

If `ImageHandler` is no longer imported/used in `HtmlRenderer`, remove the unused import. The `WmfConverter` and `ImageUriResolver` imports should be present.

- [ ] **Step 2: Final build and test**

```bash
mvn test
```
Expected: BUILD SUCCESS, 105/106 pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/p4u/smart/renderer/HtmlRenderer.java
git commit -m "chore: clean up unused imports in HtmlRenderer"
```
