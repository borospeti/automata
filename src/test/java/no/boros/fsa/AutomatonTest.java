package no.boros.fsa;

import java.util.ArrayList;
import java.util.Collections;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.junit.Ignore;
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

    @Ignore @Test
    public void testGetFSA()
        throws Exception
    {
        Automaton a = new Automaton();
        ArrayList<BString> inputs = new ArrayList<>();
        InputStream fis = new FileInputStream("/usr/share/dict/words");
        BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
        String line;
        while ((line = br.readLine()) != null) {
            inputs.add(new BString(line));
        }
        br.close();

        Collections.sort(inputs);
        for (BString input : inputs) {
            a.insertSortedString(input);
        }

        FSA fsa = a.getFSA();
    }


}
