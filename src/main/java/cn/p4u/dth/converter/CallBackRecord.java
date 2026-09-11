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
   * 创建单个内容项的解析结果。
   *
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

  /** @return 内容项序号，从 1 开始 */
  public int line() { return line; }

  /** @return 内容类型，目前为 {@code paragraph} 或 {@code table} */
  public String type() { return type; }

  /** @return 已解析的数据 */
  public T data() { return data; }

  /** @return 内容项序号，从 1 开始 */
  public int getLine() { return line; }

  /** @return 内容类型，目前为 {@code paragraph} 或 {@code table} */
  public String getType() { return type; }

  /** @return 已解析的数据 */
  public T getData() { return data; }
}
