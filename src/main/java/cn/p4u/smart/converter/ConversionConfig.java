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
        return "ConversionConfig[imageUriResolver=" + (imageUriResolver != null
                    ? imageUriResolver.getClass().getSimpleName() : "null")
                + ", extractedDir=" + extractedDir
                + ", keepTemp=" + keepTemp
                + ", latexRenderUrl=" + latexRenderUrl + "]";
    }
}
