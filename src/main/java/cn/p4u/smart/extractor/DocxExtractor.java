package cn.p4u.smart.extractor;

import cn.p4u.smart.DocxConversionException;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .docx 文件解压器，负责将 .docx（ZIP 格式）解压到临时目录。
 * <p>
 * 核心职责：安全地将 .docx ZIP 包解压到系统临时目录，
 * 内置 zip-slip 路径遍历防护，防止恶意构造的 ZIP 文件写入临时目录之外。
 * <p>
 * 主要使用场景：DocxConverter.convert() 管线的第一阶段；
 * 调用方负责在转换完成后调用 cleanup() 清理临时目录。
 */
public final class DocxExtractor {

    // 私有构造，纯静态工具类
    private DocxExtractor() {}

    /**
     * 将 .docx 文件解压到系统临时目录下的 "docx2html-" 前缀子目录中。
     * <p>
     * 包含 zip-slip 防护：每个 ZIP 条目的目标路径必须在临时目录内，
     * 否则抛出 DocxConversionException 终止解压。
     *
     * @param docxPath 输入的 .docx 文件路径，Path 类型，必须存在
     * @return 解压后的临时目录路径，Path 类型
     * @throws DocxConversionException 文件不存在或解压过程中发生 I/O 错误
     */
    public static Path extract(Path docxPath) {
        // 校验输入文件是否存在
        if (!Files.exists(docxPath)) {
            throw new DocxConversionException(docxPath.toString(),
                    new NoSuchFileException(docxPath.toString()));
        }
        try {
            // 创建带 "docx2html-" 前缀的临时目录
            Path tempDir = Files.createTempDirectory("docx2html-");
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(docxPath));
            try {
                ZipEntry entry = zis.getNextEntry();
                // 遍历 ZIP 包中的每个条目
                while (entry != null) {
                    Path target = tempDir.resolve(entry.getName()).normalize();
                    // zip-slip 防护：目标路径不得逃逸临时目录
                    if (!target.startsWith(tempDir)) {
                        throw new DocxConversionException("Zip entry escapes temp dir: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        // 目录条目：创建对应目录结构
                        Files.createDirectories(target);
                    } else {
                        // 文件条目：先确保父目录存在，再复制文件内容
                        Files.createDirectories(target.getParent());
                        Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                    entry = zis.getNextEntry();
                }
            } finally {
                zis.close();
            }
            return tempDir;
        } catch (IOException e) {
            throw new DocxConversionException(docxPath.toString(), e);
        }
    }

    /**
     * 清理解压后的临时目录及其全部内容。
     * 采用 best-effort 策略，删除失败时静默忽略（不影响主流程）。
     *
     * @param extractedDir 要清理的临时目录路径，Path 类型
     */
    public static void cleanup(Path extractedDir) {
        try {
            // 使用 Apache Commons IO 递归删除目录
            FileUtils.deleteDirectory(extractedDir.toFile());
        } catch (IOException e) {
            // best effort：清理失败不影响主流程
        }
    }
}
