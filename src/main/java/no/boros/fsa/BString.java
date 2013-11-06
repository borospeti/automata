package no.boros.fsa;

class BString
    implements Comparable
{
    private final byte[] bytes;
    private final int start;
    private final int end;
    private final int length;

    public BString()
    {
        bytes = new byte[0];
        start = 0;
        end = 0;
        length = 0;
    }

    public BString(String s)
    {
        try {
            bytes = s.getBytes("utf-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        start = 0;
        end = bytes.length;
        length = end;
    }

    private BString(BString base, int start, int end)
    {
        bytes = base.bytes;
        this.start = start;
        this.end = end;
        this.length = end - start;
    }

    public BString substring(int start, int end)
    {
        if (start < 0 || this.start + end > this.end || start > end) {
            throw new IllegalArgumentException("Index out of range");
        }
        return new BString(this, this.start + start, this.start + end);
    }

    public int length()
    {
        return length;
    }

    public byte byteAt(int i)
    {
        if (i >= length) throw new IllegalArgumentException("Index out of range");
        return bytes[start + i];
    }


    public String toString()
    {
        try {
            return new String(bytes, start, length, "utf-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A workaround for Java lacking unsigned byte :-/
     * The ordering is 0 < 1 < ... < 127 < -128 < -127 < ... < -2 < -1
     */
    public static int compareBytes(byte a, byte b)
    {
        return Integer.compare(a & 0xff, b & 0xff);
    }

    public static String byteToString(byte b)
    {
        if (32 <= b && b <= 127) {
            return Character.toString((char)b);
        }
        return String.format("0x%02x", b & 0xff);
    }

    @Override
    public int compareTo(Object otherObject)
    {
        BString other = (BString)otherObject;

        // same object
        if (other == this) return 0;

        int p = start;
        int pOther = other.start;

        while (true) {
            // same length, all bytes matched
            if (p == end && pOther == other.end) return 0;

            // a bit unusual ordering here; of two strings where one is the prefix of the other,
            // the longest comes first
            if (p == end) return +1;
            if (pOther == other.end) return -1;

            int bComp = compareBytes(bytes[p], other.bytes[pOther]);

            if (bComp != 0) return bComp;

            ++p;
            ++pOther;
        }
    }

}
