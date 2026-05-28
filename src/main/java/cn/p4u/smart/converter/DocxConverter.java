package cn.p4u.smart.converter;

import cn.p4u.smart.DocxConversionException;
import cn.p4u.smart.extractor.DocxExtractor;
import cn.p4u.smart.model.DocumentModel;
import cn.p4u.smart.parser.DocumentParser;
import cn.p4u.smart.renderer.HtmlRenderer;

import java.nio.file.Path;
import java.util.Optional;

public final class DocxConverter {

    private DocxConverter() {}

    public static ConversionResult convert(Path docxPath, ConversionConfig config) {
        Path extractedDir = DocxExtractor.extract(docxPath);
        try {
            var effectiveConfig = new ConversionConfig(
                    config.imageMode(),
                    config.imageOutputDir(),
                    extractedDir,
                    config.keepTemp()
            );
            DocumentModel model = DocumentParser.parse(extractedDir);
            String html = HtmlRenderer.render(model, effectiveConfig);
            Optional<Path> dir = config.keepTemp() ? Optional.of(extractedDir) : Optional.empty();
            return new ConversionResult(html, dir);
        } finally {
            if (!config.keepTemp()) {
                DocxExtractor.cleanup(extractedDir);
            }
        }
    }
}
