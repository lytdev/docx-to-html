package cn.p4u.smart.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class GuiRunnerTest {

    @Test
    void deriveOutputPath_changesExtensionToHtml() {
        Path input = Paths.get("C:\\Docs\\report.docx");
        Path output = GuiRunner.deriveOutputPath(input);
        assertEquals(Paths.get("C:\\Docs\\report.html"), output);
    }

    @Test
    void deriveOutputPath_handlesUppercaseExtension() {
        Path input = Paths.get("C:\\Docs\\REPORT.DOCX");
        Path output = GuiRunner.deriveOutputPath(input);
        assertEquals(Paths.get("C:\\Docs\\REPORT.html"), output);
    }

    @Test
    void deriveOutputPath_handlesMultipleDots() {
        Path input = Paths.get("C:\\Docs\\my.file.v2.docx");
        Path output = GuiRunner.deriveOutputPath(input);
        assertEquals(Paths.get("C:\\Docs\\my.file.v2.html"), output);
    }

    @Test
    void deriveOutputPath_preservesParentDirectory() {
        Path input = Paths.get("report.docx");
        Path output = GuiRunner.deriveOutputPath(input);
        assertEquals(Paths.get("report.html"), output);
    }
}
