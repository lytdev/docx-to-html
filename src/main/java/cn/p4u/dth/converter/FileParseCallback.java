package cn.p4u.dth.converter;

/**
 * DOCX 转换进度回调。
 *
 * <p>回调在执行转换的当前线程中同步触发。一个段落或表格成功解析后，
 * {@link #onLineParsed(int, int, CallBackRecord)} 会立即收到通知。</p>
 *
 * @param <T> 单个解析结果的数据类型
 */
@FunctionalInterface
public interface FileParseCallback<T> {

  /**
   * @param count 已成功解析的数量，从 1 递增
   * @param total 本次需要解析的总数量
   * @param record 本次解析完成的内容项
   */
  void onLineParsed(int count, int total, CallBackRecord<T> record);

  /** 解析或转换出现异常时调用，line 为当前内容项序号；解压阶段失败时为 0。 */
  default void onError(Exception ex, int line) {
    System.err.println("第" + line + "项出错：" + ex.getMessage());
  }

  /** 所有内容解析并渲染为 HTML 后调用。 */
  default void onComplete(int total, String message) {
    System.out.println("文件处理完成");
  }
}
