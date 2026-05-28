package cn.p4u.smart;

public class DocxConversionException extends RuntimeException {

    public DocxConversionException(String filePath, Throwable cause) {
        super("Failed to convert: " + filePath, cause);
    }

    public DocxConversionException(String message) {
        super(message);
    }
}
