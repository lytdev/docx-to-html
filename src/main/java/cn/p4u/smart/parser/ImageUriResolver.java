package cn.p4u.smart.parser; /**
 * 图片上传处理
 */
public interface ImageUriResolver {
    /**
     * 处理图片逻辑
     *
     * @param inputStream
     * @param fileName
     * @return
     */
    String resolve(InputStream inputStream, String fileName);

}