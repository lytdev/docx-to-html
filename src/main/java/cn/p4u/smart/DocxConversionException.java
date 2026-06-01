package cn.p4u.smart;

/**
 * docx 转 HTML 过程中的统一异常类。
 * <p>
 * 核心职责：封装转换管线中各阶段（解压、解析、渲染）出现的错误，
 * 以 RuntimeException 形式向上抛出，简化调用方的异常处理。
 * <p>
 * 主要使用场景：DocxExtractor 解压失败、DocumentParser 解析 XML 出错、
 * HtmlRenderer 渲染异常等情况。
 */
public class DocxConversionException extends RuntimeException {

    /**
     * 构造带文件路径和原始异常的转换异常。
     * 异常消息格式为 "Failed to convert: {filePath}"。
     *
     * @param filePath 出错文件的路径，String 类型，用于定位问题来源
     * @param cause    原始异常，Throwable 类型，保留完整异常链以便排查
     */
    public DocxConversionException(String filePath, Throwable cause) {
        super("Failed to convert: " + filePath, cause);
    }

    /**
     * 构造带自定义消息的转换异常。
     *
     * @param message 异常描述信息，String 类型，直接作为异常消息
     */
    public DocxConversionException(String message) {
        super(message);
    }
}
