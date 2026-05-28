package cn.p4u.smart.converter;

import java.nio.file.Path;
import java.nio.file.Paths;

public record ConversionConfig(
        ImageMode imageMode,
        Path imageOutputDir,
        Path extractedDir,
        boolean keepTemp
) {
    public enum ImageMode { BASE64, LINK }

    public static ConversionConfig base64Defaults() {
        return new ConversionConfig(ImageMode.BASE64, Paths.get("images"), null, false);
    }

    public static ConversionConfig linkDefaults(Path imageOutputDir) {
        return new ConversionConfig(ImageMode.LINK, imageOutputDir, null, false);
    }
}
