package no.boros.fsa;

import org.junit.Test;
import static org.junit.Assert.*;

public class FSATest
{
    @Test
    public void testInsert()
    {
        Automaton a = new Automaton();
        a.insertSortedString("böfc mufc");
        a.insertSortedString("böfc");
        a.insertSortedString("mufc böfc");
        a.insertSortedString("mufc");
        FSA fsa = a.getFSA();
        fsa.dumpDict();
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


}
