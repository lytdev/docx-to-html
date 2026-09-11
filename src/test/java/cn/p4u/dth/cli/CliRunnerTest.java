package cn.p4u.dth.cli;

import cn.p4u.dth.renderer.WmfConversionStrategy;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliRunnerTest {

  @Test
  void deriveOutputPath_changesExtensionToHtml() {
    assertEquals(Paths.get("report.html"), CliRunner.deriveOutputPath(Paths.get("report.docx")));
  }

  @Test
  void deriveOutputPath_keepsDirectory() {
    assertEquals(Paths.get("data", "report.html"),
        CliRunner.deriveOutputPath(Paths.get("data", "report.docx")));
  }

  @Test
  void deriveOutputPath_handlesUppercaseExtension() {
    assertEquals(Paths.get("REPORT.html"), CliRunner.deriveOutputPath(Paths.get("REPORT.DOCX")));
  }

  @Test
  void deriveOutputPath_nonDocx_appendsHtml() {
    assertEquals(Paths.get("notes.txt.html"), CliRunner.deriveOutputPath(Paths.get("notes.txt")));
  }

  @Test
  void parse_positionalInput() {
    CliRunner.Options opts = CliRunner.Options.parse(new String[]{"a.docx"});
    assertEquals(Paths.get("a.docx"), opts.input);
    assertNull(opts.output);
    assertFalse(opts.stdout);
  }

  @Test
  void parse_inputAndOutputFlags() {
    CliRunner.Options opts = CliRunner.Options.parse(new String[]{"-i", "a.docx", "-o", "b.html"});
    assertEquals(Paths.get("a.docx"), opts.input);
    assertEquals(Paths.get("b.html"), opts.output);
  }

  @Test
  void parse_inlineEqualsForm() {
    CliRunner.Options opts = CliRunner.Options.parse(
        new String[]{"--input=a.docx", "--output=b.html", "--stdout"});
    assertEquals(Paths.get("a.docx"), opts.input);
    assertEquals(Paths.get("b.html"), opts.output);
    assertTrue(opts.stdout);
  }

  @Test
  void parse_wmfStrategy_caseInsensitive() {
    CliRunner.Options opts = CliRunner.Options.parse(
        new String[]{"a.docx", "--wmf-strategy", "imagemagick"});
    assertEquals(WmfConversionStrategy.IMAGEMAGICK, opts.wmfStrategy);
  }

  @Test
  void parse_defaultWmfStrategyIsAuto() {
    CliRunner.Options opts = CliRunner.Options.parse(new String[]{"a.docx"});
    assertEquals(WmfConversionStrategy.AUTO, opts.wmfStrategy);
  }

  @Test
  void parse_helpFlag() {
    CliRunner.Options opts = CliRunner.Options.parse(new String[]{"--help"});
    assertTrue(opts.help);
  }

  @Test
  void parse_unknownFlagThrows() {
    assertThrows(RuntimeException.class,
        () -> CliRunner.Options.parse(new String[]{"--bogus"}));
  }

  @Test
  void parse_missingValueThrows() {
    assertThrows(RuntimeException.class,
        () -> CliRunner.Options.parse(new String[]{"a.docx", "--input"}));
  }

  @Test
  void parse_unknownWmfStrategyThrows() {
    assertThrows(RuntimeException.class,
        () -> CliRunner.Options.parse(new String[]{"a.docx", "--wmf-strategy", "bogus"}));
  }

  @Test
  void run_helpPrintsUsageAndReturnsZero() {
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    int code = CliRunner.run(new String[]{"--help"},
        new PrintStream(outBuf, true, StandardCharsets.UTF_8),
        new PrintStream(new ByteArrayOutputStream()));
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("用法:"));
  }

  @Test
  void run_missingInputReturnsTwo() {
    int code = CliRunner.run(new String[]{},
        new PrintStream(new ByteArrayOutputStream()),
        new PrintStream(new ByteArrayOutputStream()));
    assertEquals(2, code);
  }

  @Test
  void run_nonexistentInputReturnsTwo() {
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    int code = CliRunner.run(new String[]{"__no_such_file__.docx"},
        new PrintStream(new ByteArrayOutputStream()),
        new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    assertEquals(2, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("不存在"));
  }
}
