package cn.p4u.smart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocxConversionExceptionTest {

    @Test
    void messageAndCause() {
        var cause = new RuntimeException("root");
        var ex = new DocxConversionException("/path/to/file.docx", cause);
        assertTrue(ex.getMessage().contains("/path/to/file.docx"));
        assertSame(cause, ex.getCause());
    }
}
