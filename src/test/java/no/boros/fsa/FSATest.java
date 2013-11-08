package no.boros.fsa;

import java.util.ArrayList;
import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.*;

public class FSATest
{
    @Test
    public void testInsert()
        throws IOException
    {
        Automaton a = new Automaton();
        a.insertSortedString("böfc mufc");
        a.insertSortedString("böfc");
        a.insertSortedString("mufc böfc");
        a.insertSortedString("mufc");
        FSA fsa = a.getFSA();

        ArrayList<String> dict = fsa.getDictionary();
        assertEquals(4, dict.size());
        assertEquals("böfc mufc", dict.get(0));
        assertEquals("böfc", dict.get(1));
        assertEquals("mufc böfc", dict.get(2));
        assertEquals("mufc", dict.get(3));
    }

    @Test
    public void testLookup()
    {
        Automaton a = new Automaton();
        a.insertSortedString("böfc mufc");
        a.insertSortedString("böfc");
        a.insertSortedString("mufc böfc");
        a.insertSortedString("mufc");
        a.finalize();

        FSA fsa = a.getFSA();
        assertTrue(fsa.lookup("böfc mufc"));
        assertTrue(fsa.lookup("böfc"));
        assertTrue(fsa.lookup("mufc böfc"));
        assertTrue(fsa.lookup("mufc"));

        assertFalse(fsa.lookup("böfcmufc"));
        assertFalse(fsa.lookup("muf"));
        assertFalse(fsa.lookup("mufcc"));
        assertFalse(fsa.lookup("foobar"));
    }

    @Test
    public void testConsume()
    {
        Automaton a = new Automaton();
        a.insertSortedString("böfc mufc");
        a.insertSortedString("böfc");
        a.insertSortedString("mufc böfc");
        a.insertSortedString("mufc");
        a.finalize();

        FSA fsa = a.getFSA();
        FSA.State state = fsa.start();
        state.consume("b");
        assertTrue(state.isValid());
        assertFalse(state.isFinal());
        state.consume("ö");
        assertTrue(state.isValid());
        assertFalse(state.isFinal());
        state.consume("f");
        assertTrue(state.isValid());
        assertFalse(state.isFinal());
        state.consume("c");
        assertTrue(state.isValid());
        assertTrue(state.isFinal());
        state.consume(" ");
        assertTrue(state.isValid());
        assertFalse(state.isFinal());
        state.consume("mu");
        assertTrue(state.isValid());
        assertFalse(state.isFinal());
        state.consume("fc");
        assertTrue(state.isValid());
        assertTrue(state.isFinal());
        state.consume("x");
        assertFalse(state.isValid());
        assertFalse(state.isFinal());
    }

    @Test
    public void testReadWrite()
        throws IOException
    {
        Automaton a = new Automaton();
        a.insertSortedString("böfc mufc");
        a.insertSortedString("böfc");
        a.insertSortedString("mufc böfc");
        a.insertSortedString("mufc");
        FSA fsa = a.getFSA();
        ArrayList<String> dict = fsa.getDictionary();
        fsa.write("target/testfsa.fsa");
        FSA fsa2 = FSA.read("target/testfsa.fsa");
        ArrayList<String> dict1 = fsa2.getDictionary();
        assertEquals(4, dict.size());
        assertEquals(dict.size(), dict1.size());
        for (int i = 0; i < dict.size(); ++i) {
            assertEquals(dict.get(i), dict1.get(i));
        }
    }

}
