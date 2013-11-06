package no.boros.fsa;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutomatonTest
{
    @Test
    public void testInsert()
    {
        Automaton a = new Automaton();
        a.insertSortedString("böfc mufc");
        a.insertSortedString("böfc");
        a.insertSortedString("mufc böfc");
        a.insertSortedString("mufc");
        a.finalize();

        // a.dump();
        // a.traverse();
        a.dumpDict();
    }

    @Test
    public void testInsertToFinalized()
    {
        Automaton a = new Automaton();
        a.insertSortedString("böfc mufc");
        a.insertSortedString("mufc böfc");
        a.finalize();

        try {
            a.insertSortedString("xxx foobar");
            fail("Should have thrown IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    @Test
    public void testInsertOutOfOrder()
    {
        Automaton a = new Automaton();
        a.insertSortedString("böfc mufc");
        a.insertSortedString("mufc böfc");

        try {
            a.insertSortedString("foobar");
            fail("Should have thrown IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }


}
