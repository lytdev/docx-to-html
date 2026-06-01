package cn.p4u.smart.converter;

import cn.p4u.smart.DocxConversionException;
import cn.p4u.smart.extractor.DocxExtractor;
import cn.p4u.smart.model.DocumentModel;
import cn.p4u.smart.parser.DocumentParser;
import cn.p4u.smart.renderer.HtmlRenderer;

import java.nio.file.Path;
import java.util.Optional;

/**
 * docx 转 HTML 的高层入口类，串联完整的转换管线。
 * <p>
 * 核心职责：按顺序执行三阶段管线（解压 → 解析 → 渲染），
 * 管理临时目录的生命周期，并将原始配置与解析阶段的实际路径合并。
 * <p>
 * 主要使用场景：作为项目唯一的公开 API 入口，CliRunner 和测试代码均通过此类执行转换。
 */
public final class DocxConverter {

    // 私有构造，纯静态工具类
    private DocxConverter() {}

    /**
     * 执行 .docx 到 HTML 的完整转换。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>调用 DocxExtractor.extract() 将 .docx 解压到临时目录</li>
     *   <li>将解压目录路径注入 ConversionConfig，构建 effectiveConfig</li>
     *   <li>调用 DocumentParser.parse() 将 XML 解析为 DocumentModel</li>
     *   <li>调用 HtmlRenderer.render() 将 DocumentModel 渲染为 HTML 字符串</li>
     *   <li>如果不保留临时目录，在 finally 块中调用 DocxExtractor.cleanup() 清理</li>
     * </ol>
     *
     * @param docxPath 输入的 .docx 文件路径，Path 类型，必须存在否则抛出异常
     * @param config   转换配置，ConversionConfig 类型，控制图片模式和临时文件行为
     * @return 转换结果，包含 HTML 字符串和可选的解压目录路径，ConversionResult 类型
     * @throws DocxConversionException 文件不存在、解压失败、解析失败等情况
     */
    public static ConversionResult convert(Path docxPath, ConversionConfig config) {
        // 阶段一：解压 .docx 到临时目录
        Path extractedDir = DocxExtractor.extract(docxPath);
        try {
            // 构建包含实际解压路径的有效配置（原始 config 的 extractedDir 通常为 null）
            ConversionConfig effectiveConfig = new ConversionConfig(
                    config.imageMode(),
                    config.imageOutputDir(),
                    extractedDir,
                    config.keepTemp()
            );
            // 阶段二：解析 XML 文件构建文档模型
            DocumentModel model = DocumentParser.parse(extractedDir);
            // 阶段三：渲染文档模型为 HTML
            String html = HtmlRenderer.render(model, effectiveConfig);
            // 只有 keepTemp=true 时才在结果中返回解压目录路径
            Optional<Path> dir = config.keepTemp() ? Optional.of(extractedDir) : Optional.<Path>empty();
            return new ConversionResult(html, dir);
        } finally {
            // 不保留临时目录时，确保清理解压文件
            if (!config.keepTemp()) {
                DocxExtractor.cleanup(extractedDir);
            }
        }
    }
}
