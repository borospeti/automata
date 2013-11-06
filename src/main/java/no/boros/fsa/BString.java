package no.boros.fsa;

class BString
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
}

