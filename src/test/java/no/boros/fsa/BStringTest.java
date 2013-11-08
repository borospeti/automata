package no.boros.fsa;

import org.junit.Test;
import static org.junit.Assert.*;

public class BStringTest
{
    @Test
    public void testInit()
    {
        String foo = "böfc mufc";
        BString bFoo = new BString(foo);
        String sFoo = bFoo.toString();

        assertEquals(foo, sFoo);
    }

    @Test
    public void testSubstring()
    {
        // note that 'ö' takes two bytes in utf-8
        String foo = "böfc mufc";
        BString bFoo = new BString(foo);
        BString bFoo1 = bFoo.substring(0,5);
        BString bFoo2 = bFoo.substring(6,10);

        assertEquals(10, bFoo.length());
        assertEquals(5, bFoo1.length());
        assertEquals(4, bFoo2.length());

        String sFoo1 = bFoo1.toString();
        String sFoo2 = bFoo2.toString();

        assertEquals("böfc", sFoo1);
        assertEquals("mufc", sFoo2);
    }

    @Test
    public void testSort()
    {
        assertEquals(0, new BString().compareTo(new BString()));
        assertEquals(0, new BString("").compareTo(new BString("")));

        assertEquals(+1, new BString("").compareTo(new BString("a")));
        assertEquals(-1, new BString("a").compareTo(new BString("")));

        assertEquals(0, new BString("alma").compareTo(new BString("alma")));
        assertEquals(-1, new BString("alma").compareTo(new BString("szilva")));
        assertEquals(1, new BString("szilva").compareTo(new BString("alma")));

        assertEquals(1, new BString("alma").compareTo(new BString("almaszilva")));

        assertEquals(-1, new BString("almoe").compareTo(new BString("almö")));
        assertEquals(1, new BString("almö").compareTo(new BString("almoe")));
    }

    @Test
    public void testByteAt()
    {
        String foo = "böfc mufc";
        BString bFoo = new BString(foo);

        assertEquals(98, bFoo.byteAt(0));
        assertEquals(-61, bFoo.byteAt(1));
        assertEquals(-74, bFoo.byteAt(2));
        assertEquals(102, bFoo.byteAt(3));
        assertEquals(99, bFoo.byteAt(4));
        assertEquals(32, bFoo.byteAt(5));
        assertEquals(109, bFoo.byteAt(6));
        assertEquals(117, bFoo.byteAt(7));
        assertEquals(102, bFoo.byteAt(8));
        assertEquals(99, bFoo.byteAt(9));
        try {
            assertEquals(0, bFoo.byteAt(10));
            fail("Should have thrown IndexOutOfBoundsException.");
        } catch (IndexOutOfBoundsException e) {
            // ok
        }
    }

}
