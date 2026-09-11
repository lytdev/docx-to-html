package cn.p4u.dth.model;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void buildMinimalDocument() {
        TextRun run = new TextRun("Hello", new FontSpec("SimSun", "12pt", "#000000", null, null),
                true, false, false, false, null, null, false, false, "");
        ParagraphBlock para = new ParagraphBlock("", null, null, null, Arrays.asList(run));
        DocumentModel doc = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(para), null);

        assertEquals(1, doc.content().size());
        assertTrue(doc.content().get(0) instanceof ParagraphBlock);
        ParagraphBlock p = (ParagraphBlock) doc.content().get(0);
        assertEquals("Hello", ((TextRun) p.elements().get(0)).text());
    }

    @Test
    void buildTableDocument() {
        TextRun run = new TextRun("cell", new FontSpec("SimSun", "10pt", "#000000", null, null),
                false, false, false, false, null, null, false, false, "");
        ParagraphBlock cellPara = new ParagraphBlock("", null, null, null, Arrays.asList(run));
        TableCell cell = new TableCell(Arrays.asList(cellPara), 1, 1, "100px",
                new BorderSpec("8", "#000"), new BorderSpec("8", "#000"),
                new BorderSpec("8", "#000"), new BorderSpec("8", "#000"),
                null, true);
        TableRow row = new TableRow(Arrays.asList(cell), "30", null);
        TableBlock table = new TableBlock(Arrays.asList(row), "200px",
                new BorderSpec("8", "#000"), new BorderSpec("8", "#000"),
                new BorderSpec("8", "#000"), new BorderSpec("8", "#000"),
                BorderSpec.NONE, BorderSpec.NONE, true);
        DocumentModel doc = new DocumentModel(Collections.<String, StyleDef>emptyMap(), Arrays.asList(table), null);

        assertEquals(1, doc.content().size());
        assertTrue(doc.content().get(0) instanceof TableBlock);
    }
}
