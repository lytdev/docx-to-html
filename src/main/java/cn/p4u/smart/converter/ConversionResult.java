package cn.p4u.smart.converter;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * 转换结果不可变值类，封装 DocxConverter.convert() 的输出。
 * <p>
 * 核心职责：承载转换后的 HTML 字符串和解压目录路径，
 * 使用 Optional 包装解压目录以区分"保留"和"已清理"两种情况。
 * <p>
 * 主要使用场景：DocxConverter.convert() 的返回值；
 * CliRunner 从中提取 HTML 内容和调试用的临时目录路径。
 */
public final class ConversionResult {

    /** 转换后的 HTML 字符串，不可为 null */
    private final String html;
    /** 解压目录路径，Optional 包装：keepTemp=true 时有值，否则为空 */

    private final Optional<Path> extractedDir;

    /**
     * 构造转换结果。
     *
     * @param html         转换后的 HTML 内容，String 类型，不可为 null
     * @param extractedDir 解压目录路径，Optional&lt;Path&gt; 类型，
     *                     keepTemp=true 时有值，否则为 Optional.empty()
     */
    public ConversionResult(String html, Optional<Path> extractedDir) {
        this.html = html;
        this.extractedDir = extractedDir;
    }

    /**
     * 获取转换后的 HTML 内容。
     *
     * @return HTML 字符串，String 类型，不会为 null
     */
    public String html() { return html; }

    /**
     * 获取解压目录路径。
     *
     * @return 解压目录的 Optional 包装，仅当 keepTemp=true 时有值
     */
    public Optional<Path> extractedDir() { return extractedDir; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversionResult)) return false;
        ConversionResult that = (ConversionResult) o;
        return Objects.equals(html, that.html)
                && Objects.equals(extractedDir, that.extractedDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(html, extractedDir);
    }

    @Override
    public String toString() {
        return "ConversionResult[html=" + html
                + ", extractedDir=" + extractedDir + "]";
    }
}
