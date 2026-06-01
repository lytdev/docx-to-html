package cn.p4u.smart.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * JDK 11+ API 的 JDK 8 兼容替换工具类。
 * <p>
 * 核心职责：为运行在 JDK 8 上的代码提供 JDK 9/11 才引入的常用 API 的等价实现，
 * 包括 null 输出流、流传输、文件读写字符串等。
 * <p>
 * 主要使用场景：项目源码目标为 Java 8，无法直接调用 JDK 9+ API，
 * 全项目统一使用本类替代对这些 API 的直接调用。
 */
public final class Jdk8Helpers {

    // 私有构造，禁止实例化工具类
    private Jdk8Helpers() {}

    // ---- OutputStream.nullOutputStream() (JDK 11) ----

    /**
     * 返回一个丢弃所有写入数据的 OutputStream，等价于 JDK 11 的 OutputStream.nullOutputStream()。
     * 用于需要输出流但不关心输出内容的场景（如吞掉进程 stderr）。
     *
     * @return 丢弃数据的 OutputStream 单例
     */
    public static OutputStream nullOutputStream() {
        return NullOutputStream.INSTANCE;
    }

    /**
     * 内部空输出流实现，所有写入操作均被丢弃。
     */
    private static final class NullOutputStream extends OutputStream {
        // 单例实例
        static final NullOutputStream INSTANCE = new NullOutputStream();

        @Override
        public void write(int b) { /* 丢弃写入的字节 */ }

        @Override
        public void write(byte[] b, int off, int len) { /* 丢弃批量写入的字节 */ }
    }

    // ---- InputStream.transferTo(OutputStream) (JDK 9) ----

    /**
     * 将输入流的所有字节传输到输出流，等价于 JDK 9 的 InputStream.transferTo()。
     * 使用 8KB 缓冲区逐块读写，直到输入流结束。
     *
     * @param in  源输入流，InputStream 类型，调用方负责关闭
     * @param out 目标输出流，OutputStream 类型，调用方负责关闭
     * @return 传输的总字节数，long 类型
     * @throws IOException 读写过程中发生 I/O 错误
     */
    public static long transferTo(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192]; // 8KB 缓冲区，平衡内存占用和吞吐量
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            total += n;
        }
        return total;
    }

    // ---- Files.writeString (JDK 11) ----

    /**
     * 将字符串内容以 UTF-8 编码写入文件，等价于 JDK 11 的 Files.writeString()。
     * 如果文件已存在则覆盖。
     *
     * @param path    目标文件路径，Path 类型
     * @param content 要写入的内容，String 类型
     * @throws IOException 写入过程中发生 I/O 错误
     */
    public static void writeString(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Files.readString (JDK 11) ----

    /**
     * 以 UTF-8 编码读取文件的全部内容为字符串，等价于 JDK 11 的 Files.readString()。
     *
     * @param path 源文件路径，Path 类型
     * @return 文件的全部文本内容，String 类型
     * @throws IOException 读取过程中发生 I/O 错误
     */
    public static String readString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
