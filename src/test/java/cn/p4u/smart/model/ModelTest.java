package cn.p4u.smart.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void buildMinimalDocument() {
        var run = new TextRun("Hello", new FontSpec("SimSun", "12pt", "#000000"),
                true, false, false, false, null, null, false, false, "");
        var para = new ParagraphBlock("", null, null, List.of(run));
        var doc = new DocumentModel(java.util.Map.of(), List.of(para));

        assertEquals(1, doc.content().size());
        assertInstanceOf(ParagraphBlock.class, doc.content().getFirst());
        var p = (ParagraphBlock) doc.content().getFirst();
        assertEquals("Hello", ((TextRun) p.elements().getFirst()).text());
    }

    @Test
    void buildTableDocument() {
        var run = new TextRun("cell", new FontSpec("SimSun", "10pt", "#000000"),
                false, false, false, false, null, null, false, false, "");
        var cellPara = new ParagraphBlock("", null, null, List.of(run));
        var cell = new TableCell(List.of(cellPara), 1, 1, "100px",
                "1px", "#000", null, true);
        var row = new TableRow(List.of(cell), "30px");
        var table = new TableBlock(List.of(row), "200px", "1px", "#000", true);
        var doc = new DocumentModel(java.util.Map.of(), List.of(table));

        assertEquals(1, doc.content().size());
        assertInstanceOf(TableBlock.class, doc.content().getFirst());
    }
}
