package cn.p4u.dth.extractor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 一次 DOCX 解压结果的资源句柄。
 *
 * <p>它把“解压目录”和“如何清理目录”放在同一个对象中。配合 try-with-resources 使用时，
 * 即使解析或渲染抛出异常，close() 仍会执行，从而避免临时文件泄漏。</p>
 */
public final class ExtractedDocx implements AutoCloseable {
  private final Path rootDirectory;
  private boolean closed;

  ExtractedDocx(Path rootDirectory) {
    this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
  }

  /** 返回 document.xml、styles.xml 等文件所在的解压根目录。 */
  public Path rootDirectory() {
    return rootDirectory;
  }

  /**
   * 删除本次调用创建的临时目录。重复调用 close() 是安全的。
   */
  @Override
  public void close() {
    if (!closed) {
      closed = true;
      DocxExtractor.cleanup(rootDirectory);
    }
  }
}
