# ImageUriResolver — Replace ImageMode with Pluggable Image Resolution

## Goal

Replace `ConversionConfig.ImageMode` enum (BASE64/LINK) with a pluggable `ImageUriResolver` interface, allowing image resolution strategies to be swapped (base64 encoding, OSS upload, or future backends) without changing `HtmlRenderer` or `ConversionConfig`.

## Scope

- Create `ImageUriResolver` interface with `ResolveResult` return type
- Create `Image2Base64Resolver` (extracts base64 logic from `ImageHandler`)
- Create `Image2OssResolver` (uploads to Aliyun OSS, returns public URL)
- Refactor `ConversionConfig` to hold `ImageUriResolver` instead of `ImageMode` + `imageOutputDir`
- Simplify `HtmlRenderer.renderImage()` to a single `resolve()` call
- Add OSS SDK dependency to `pom.xml`
- Update `CliRunner` flags (`--image-resolver base64|oss` + `--oss-*` options)
- Update `GuiRunner` to use default resolver
- Pull WMF/EMF→PNG conversion out as a shared pre-step in `HtmlRenderer`, before resolver call
- `ImageHandler` shrinks — `toBase64DataUri` logic moves to `Image2Base64Resolver`, `copyToDir` removed
- `ImageHandler` keeps WMF-related helpers (`isWmfOrEmf`, `isWmfOrEmfPath`) shared
- Math formula images (`renderMath`) keep using `ImageHandler.toBase64DataUri` directly for now (VML fallback images are always inline)

## Design

### 1. Core Interface & Types

New file: `src/main/java/cn/p4u/smart/renderer/ImageUriResolver.java`

```java
public record ResolveResult(String uri, String mimeType) {}

public interface ImageUriResolver {
    ResolveResult resolve(Path imagePath, String mimeType) throws IOException;
}
```

`mimeType` in `ResolveResult` may differ from input — e.g., WMF input → `"image/png"` output.

### 2. Image2Base64Resolver

New file: `src/main/java/cn/p4u/smart/renderer/Image2Base64Resolver.java`

Stateless. Reads file bytes, Base64-encodes, returns `data:<mime>;base64,...` data URI.

### 3. Image2OssResolver

New file: `src/main/java/cn/p4u/smart/renderer/Image2OssResolver.java`

```java
public record OssConfig(String endpoint, String bucket, String accessKey, String secretKey) {}
```

Constructor `Image2OssResolver(OssConfig)`. `resolve()` uploads file bytes to OSS, returns `https://<bucket>.<endpoint>/<objectKey>` URL. Generates unique object keys (UUID + original filename).

### 4. WMF/EMF Pre-processing (in HtmlRenderer)

```java
// In renderImage() — before calling resolver:
if (WmfConverter.isWmfOrEmf(img.mimeType())) {
    byte[] raw = Files.readAllBytes(mediaPath);
    byte[] png = WmfConverter.convertToPng(raw, pxW, pxH);
    Path tmpFile = Files.createTempFile("wmf2png", ".png");
    Files.write(tmpFile, png);
    try {
        ResolveResult result = config.imageUriResolver().resolve(tmpFile, "image/png");
        // ... use result.uri() and result.mimeType()
    } finally {
        Files.deleteIfExists(tmpFile);
    }
} else {
    ResolveResult result = config.imageUriResolver().resolve(mediaPath, img.mimeType());
    // ... use result.uri() and result.mimeType()
}
```

### 5. ConversionConfig Changes

| Removed | Added |
|---------|-------|
| `ImageMode imageMode` field + enum | `ImageUriResolver imageUriResolver` field |
| `Path imageOutputDir` field | — |
| `imageMode()` getter | `imageUriResolver()` getter |
| `imageOutputDir()` getter | — |
| `base64Defaults()` | `defaults()` (uses `Image2Base64Resolver`) |
| `linkDefaults()` | — (caller constructs with `Image2OssResolver`) |

No `ImageMode` left. Constructor first param becomes `ImageUriResolver`.

### 6. HtmlRenderer Changes

`renderImage()`: replace the `if (BASE64) ... else ...` branch with the WMF check + single `resolver.resolve()` call (see section 4). No more direct calls to `ImageHandler.toBase64DataUri()` or `ImageHandler.copyToDir()` from `renderImage()`.

`renderMath()`: unchanged — math VML fallback images always use base64 inline (not related to image mode).

### 7. CLI Changes

Replace `--image-mode base64|link` and `--image-dir` with:

```
--image-resolver base64|oss (default: base64)
--oss-endpoint <endpoint>
--oss-bucket <bucket>
--oss-access-key <key>
--oss-secret-key <secret>
```

`--oss-*` flags only parsed when `--image-resolver oss`.

### 8. OSS SDK

Add `aliyun-sdk-oss` 3.x to `pom.xml`. Only `Image2OssResolver` references it.

## Files Summary

| Action | File |
|--------|------|
| Create | `src/main/java/cn/p4u/smart/renderer/ImageUriResolver.java` |
| Create | `src/main/java/cn/p4u/smart/renderer/Image2Base64Resolver.java` |
| Create | `src/main/java/cn/p4u/smart/renderer/Image2OssResolver.java` |
| Modify | `src/main/java/cn/p4u/smart/converter/ConversionConfig.java` |
| Modify | `src/main/java/cn/p4u/smart/renderer/HtmlRenderer.java` |
| Modify | `src/main/java/cn/p4u/smart/renderer/ImageHandler.java` |
| Modify | `src/main/java/cn/p4u/smart/cli/CliRunner.java` |
| Modify | `src/main/java/cn/p4u/smart/gui/GuiRunner.java` |
| Modify | `src/main/java/cn/p4u/smart/converter/DocxConverter.java` |
| Modify | `pom.xml` (add OSS SDK) |
| Update | tests under `src/test/` |

## Tests

- `Image2Base64ResolverTest` — base64 encoding correctness, missing file
- `Image2OssResolverTest` — (optional, needs OSS credentials)
- Update `HtmlRendererTest`, `DocxConverterTest` for new `ConversionConfig` constructor
- Update `ImageHandlerTest` — remove `copyToDir` tests, keep WMF tests
