package cn.p4u.dth.extractor;

import cn.p4u.dth.DocxConversionException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .docx 文件解压器，负责将 .docx（ZIP 格式）解压到临时目录。
 * <p>
 * 核心职责：安全地将 .docx ZIP 包解压到系统临时目录或调用方指定的临时目录，
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
     * @param docxStream 输入的 .docx 数据流；本方法读取但不关闭该流
     * @return 解压后的临时目录路径，Path 类型
     * @throws DocxConversionException 解压过程中发生 I/O 错误
     */
    public static Path extract(InputStream docxStream) {
        Objects.requireNonNull(docxStream, "docxStream");
        return extractInternal(docxStream, null);
    }

    /**
     * 将 .docx 文件解压到指定临时目录下的唯一子目录中。
     * <p>
     * 本方法会在 {@code tempRoot} 下创建带 {@code docx2html-} 前缀的子目录，
     * 避免覆盖调用方目录中的已有文件。转换完成后只需对返回路径调用
     * {@link #cleanup(Path)}，不会删除 {@code tempRoot} 本身。
     *
     * @param docxStream 输入的 .docx 数据流；本方法读取但不关闭该流
     * @param tempRoot   存放解压临时文件的根目录；不存在时自动创建
     * @return 实际存放解压内容的唯一临时子目录
     * @throws NullPointerException   docxStream 或 tempRoot 为 null
     * @throws DocxConversionException 创建目录或解压过程中发生 I/O 错误
     */
    public static Path extract(InputStream docxStream, Path tempRoot) {
        Objects.requireNonNull(docxStream, "docxStream");
        Objects.requireNonNull(tempRoot, "tempRoot");
        return extractInternal(docxStream, tempRoot);
    }

    /**
     * 解压到系统临时目录并返回可自动清理的资源句柄。
     *
     * <p>推荐在业务代码中使用 try-with-resources 管理这个对象。</p>
     *
     * @param docxStream 输入的 DOCX 数据流；本方法读取但不关闭该流
     * @return 可自动清理解压目录的资源句柄
     * @throws DocxConversionException 解压过程中发生 I/O 错误
     */
    public static ExtractedDocx open(InputStream docxStream) {
        return new ExtractedDocx(extract(docxStream));
    }

    /**
     * 解压到指定临时目录根路径，并返回可自动清理的资源句柄。
     *
     * @param docxStream 输入的 DOCX 数据流；本方法读取但不关闭该流
     * @param tempRoot 存放解压临时文件的根目录
     * @return 可自动清理解压目录的资源句柄
     * @throws DocxConversionException 创建目录或解压过程中发生 I/O 错误
     */
    public static ExtractedDocx open(InputStream docxStream, Path tempRoot) {
        return new ExtractedDocx(extract(docxStream, tempRoot));
    }

    private static Path extractInternal(InputStream docxStream, Path tempRoot) {
        Path tempDir = null;
        try {
            // 未指定根目录时使用系统临时目录；指定时在该目录中创建唯一子目录
            if (tempRoot == null) {
                tempDir = Files.createTempDirectory("docx2html-");
            } else {
                Path normalizedRoot = tempRoot.toAbsolutePath().normalize();
                Files.createDirectories(normalizedRoot);
                tempDir = Files.createTempDirectory(normalizedRoot, "docx2html-");
            }
            // 关闭 ZipInputStream 时不关闭由调用方持有的原始输入流
            InputStream nonClosingStream = new FilterInputStream(docxStream) {
                @Override
                public void close() {
                    // caller owns docxStream
                }
            };
            try (ZipInputStream zis = new ZipInputStream(nonClosingStream)) {
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
            }
            return tempDir;
        } catch (DocxConversionException e) {
            cleanup(tempDir);
            throw e;
        } catch (IOException e) {
            cleanup(tempDir);
            throw new DocxConversionException("Failed to extract DOCX stream", e);
        }
    }

    /**
     * 清理解压后的临时目录及其全部内容。
     * 采用 best-effort 策略，删除失败时静默忽略（不影响主流程）。
     *
     * @param extractedDir 要清理的临时目录路径，Path 类型
     */
    public static void cleanup(Path extractedDir) {
        if (extractedDir == null || !Files.exists(extractedDir)) return;
        try {
            Files.walkFileTree(extractedDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // best effort：清理失败不影响主流程
        }
    }
}
