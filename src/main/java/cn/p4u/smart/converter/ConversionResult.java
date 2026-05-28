package cn.p4u.smart.converter;

import java.nio.file.Path;
import java.util.Optional;

public record ConversionResult(
        String html,
        Optional<Path> extractedDir
) {}
