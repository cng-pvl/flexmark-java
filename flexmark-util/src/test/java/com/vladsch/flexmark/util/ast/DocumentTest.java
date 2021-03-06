package com.vladsch.flexmark.util.ast;

import com.vladsch.flexmark.util.options.MutableDataSet;
import com.vladsch.flexmark.util.sequence.BasedSequenceImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DocumentTest {

    @Test
    public void testLineNumberWithUnixEol() {
        Document document = new Document(new MutableDataSet(), BasedSequenceImpl.of("Hello\nWorld"));

        assertEquals(0, (int) document.getLineNumber(0));
        assertEquals(0, (int) document.getLineNumber(5));
        assertEquals(1, (int) document.getLineNumber(6));
        assertEquals(1, (int) document.getLineNumber(10));
    }

    @Test
    public void testLineNumberWithUnixEol2() {
        Document document = new Document(new MutableDataSet(), BasedSequenceImpl.of("Hello\n\nWorld"));

        assertEquals(0, (int) document.getLineNumber(0));
        assertEquals(0, (int) document.getLineNumber(5));
        assertEquals(1, (int) document.getLineNumber(6));
        assertEquals(2, (int) document.getLineNumber(7));
        assertEquals(2, (int) document.getLineNumber(8));
        assertEquals(2, (int) document.getLineNumber(10));
    }

    @Test
    public void testLineNumberWithWindowsEol() {
        Document document = new Document(new MutableDataSet(), BasedSequenceImpl.of("Hello\r\nWorld"));

        assertEquals(0, (int) document.getLineNumber(0));
        assertEquals(0, (int) document.getLineNumber(5));
        assertEquals(0, (int) document.getLineNumber(6));
        assertEquals(1, (int) document.getLineNumber(7));
        assertEquals(1, (int) document.getLineNumber(11));
    }

    @Test
    public void testLineNumberWithWindowsEol2() {
        Document document = new Document(new MutableDataSet(), BasedSequenceImpl.of("Hello\r\n\r\nWorld"));

        assertEquals(0, (int) document.getLineNumber(0));
        assertEquals(0, (int) document.getLineNumber(5));
        assertEquals(0, (int) document.getLineNumber(6));
        assertEquals(1, (int) document.getLineNumber(7));
        assertEquals(1, (int) document.getLineNumber(8));
        assertEquals(2, (int) document.getLineNumber(9));
        assertEquals(2, (int) document.getLineNumber(10));
        assertEquals(2, (int) document.getLineNumber(11));
    }

    @Test
    public void testLineNumberWithOldMacEol() {
        Document document = new Document(new MutableDataSet(), BasedSequenceImpl.of("Hello\rWorld"));

        assertEquals(0, (int) document.getLineNumber(0));
        assertEquals(0, (int) document.getLineNumber(5));
        assertEquals(1, (int) document.getLineNumber(6));
        assertEquals(1, (int) document.getLineNumber(10));
    }

    @Test
    public void testLineNumberWithOldMacEol2() {
        Document document = new Document(new MutableDataSet(), BasedSequenceImpl.of("Hello\r\rWorld"));

        assertEquals(0, (int) document.getLineNumber(0));
        assertEquals(0, (int) document.getLineNumber(5));
        assertEquals(1, (int) document.getLineNumber(6));
        assertEquals(2, (int) document.getLineNumber(7));
        assertEquals(2, (int) document.getLineNumber(8));
        assertEquals(2, (int) document.getLineNumber(10));
    }
}
