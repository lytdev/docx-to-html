package cn.p4u.dth.converter;

import java.util.Objects;

/**
 * 单个 DOCX 内容项的解析结果。
 *
 * @param <T> 解析结果的数据类型
 */
public final class CallBackRecord<T> {
  private final int line;
  private final String type;
  private final T data;

  /**
   * @param line 内容项序号，从 1 开始
   * @param type 内容类型，目前为 {@code paragraph} 或 {@code table}
   * @param data 已解析的数据
   */
  public CallBackRecord(int line, String type, T data) {
    if (line < 1) {
      throw new IllegalArgumentException("line must be greater than 0");
    }
    this.line = line;
    this.type = Objects.requireNonNull(type, "type");
    this.data = Objects.requireNonNull(data, "data");
  }

  public int line() { return line; }
  public String type() { return type; }
  public T data() { return data; }

  /** JavaBean 风格访问方法，便于 Spring 等框架使用。 */
  public int getLine() { return line; }
  public String getType() { return type; }
  public T getData() { return data; }
}
