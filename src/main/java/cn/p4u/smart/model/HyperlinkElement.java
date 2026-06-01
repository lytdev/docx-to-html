package cn.p4u.smart.model;

import java.util.List;
import java.util.Objects;

/**
 * 超链接元素不可变值类，表示段落中的一个超链接。
 * <p>
 * 核心职责：封装超链接的目标 URL 及其包含的文本运行列表，
 * 保留链接内文本的完整格式信息。
 * <p>
 * 主要使用场景：DocumentParser 解析 w:hyperlink 时，通过 r:id 从关系文件
 * 解析目标 URL，并递归解析内部的 w:r 元素为 TextRun 列表；
 * HtmlRenderer 将其渲染为 &lt;a&gt; 标签，对每个 TextRun 渲染内部 &lt;span&gt;。
 */
public final class HyperlinkElement implements ParagraphElement {

    /** 超链接目标 URL，String 类型，可能为 null（如锚点链接无 r:id） */
    private final String url;
    /** 链接内的文本运行列表，List&lt;TextRun&gt; 类型，不可为 null 但可能为空列表 */
    private final List<TextRun> runs;

    /**
     * 构造超链接元素。
     *
     * @param url  目标 URL，String 类型，可为 null
     * @param runs 链接内的文本运行列表，List&lt;TextRun&gt; 类型，不可为 null
     */
    public HyperlinkElement(String url, List<TextRun> runs) {
        this.url = url;
        this.runs = runs;
    }

    public String url() { return url; }
    public List<TextRun> runs() { return runs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HyperlinkElement)) return false;
        HyperlinkElement that = (HyperlinkElement) o;
        return Objects.equals(url, that.url)
                && Objects.equals(runs, that.runs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, runs);
    }

    @Override
    public String toString() {
        return "HyperlinkElement[url=" + url + ", runs=" + runs + "]";
    }
}
