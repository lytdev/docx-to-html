package cn.p4u.dth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocxConversionExceptionTest {

    @Test
    void messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        DocxConversionException ex = new DocxConversionException("/path/to/file.docx", cause);
        assertTrue(ex.getMessage().contains("/path/to/file.docx"));
        assertSame(cause, ex.getCause());
    }
}
