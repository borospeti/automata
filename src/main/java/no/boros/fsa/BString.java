package no.boros.fsa;

/**
 * A byte string class. Basically the UTF-8 representation of a string,
 * with slightly special comparators. Bytes are treated as unsigned 8-bit
 * integers and compared accordingly. String are compared byte by byte,
 * and of two strings where one is the prefix of the other, the shorter
 * comes after the longer in the ordering.
 *
 * A substing operator is also supported, which uses the same byte array
 * as backing store, just sets start/end/length appropriately. The substring
 * indexes can be anywhere inside the string without taking UTF-8 character
 * boundaries into consideration, so it is not guaranteed that the resulting
 * substring is a valid UTF-8 string.
 */
public class BString
    implements Comparable<BString>
{
    private final byte[] bytes;
    private final int start;
    private final int end;
    private final int length;


    /**
     * Default constructor, creates an empy BString.
     */
    public BString()
    {
        bytes = new byte[0];
        start = 0;
        end = 0;
        length = 0;
    }

    /**
     * Construct a BSstring from a java String.
     *
     * @param s  input string
     */
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

    /**
     * Private constructor, used by the substring operation.
     *
     * @param base   byte array backing this bstring
     * @param start  index of first byte of the bstring within the array
     * @param end    index of last byte + 1 of the bstring within the array
     */
    private BString(BString base, int start, int end)
    {
        bytes = base.bytes;
        this.start = start;
        this.end = end;
        this.length = end - start;
    }

    /**
     * Substring operation. Returns a new Private constructor, used bye the substring operation.
     *
     * @param start  start of substring within the original string
     * @param end    end of substring (last byte + 1) within the original string
     * @return  a new bstring representing the substring of the original string
     * @throws IndexOutOfBoundsException  if the one of the indexes is negative,
     *                                    greater than the length of the bstring,
     *                                    or start > end
     */
    public BString substring(int start, int end)
    {
        if (start < 0 || this.start + end > this.end || start > end) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + start + ", " + end);
        }
        return new BString(this, this.start + start, this.start + end);
    }

    /**
     * Get the length of the bstring in bytes. This may differ from the number of
     * characters in the string, as UTF-8 characters may take up 1-4 bytes.
     *
     * @return  the length of the string (number of bytes)
     */
    public int length()
    {
        return length;
    }

    /**
     * Return the byte at position i in the bstring.
     *
     * @return  the byte at position i in the bstring
     * @throws IndexOutOfBoundsException  if the index is negative, or not less than the length of the bstring
     */
    public byte byteAt(int i)
    {
        if (i >= length) throw new IndexOutOfBoundsException("Index out of bounds: " + i);
        return bytes[start + i];
    }

    /**
     * Convert the bstring to a java String. If the bstring is not a valid UTF-8
     * string, the invalid characters will be replaced with the charset's default
     * replacement string.
     *
     * @return  A String object representing the bstring.
     */
    public String toString()
    {
        try {
            return new String(bytes, start, length, "utf-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper method for comapring bytes. This is a workaround for Java
     * lacking unsigned byte type. :-/
     * The ordering is 0 < 1 < ... < 127 < -128 < -127 < ... < -2 < -1
     * Returns -1, 0, 1 if left byte is less, equal, or greater than right
     * byte, respectively.
     *
     * @param a  left byte to compare
     * @param b  right byte to compare
     * @return  -1, 0, or 1 if a<b, a==b or a>b, according to unsigned byte ordering
     */
    public static int compareBytes(byte a, byte b)
    {
        return Integer.compare(a & 0xff, b & 0xff);
    }

    /**
     * Convert one byte to a String representation. Bytes in the range 32..127
     * are converted directly to an ASCII character, bytes outside thir range
     * will represented as a hex string 0xXX.
     *
     * @param b  input byte
     * @return  hex representation of the input
     */
    public static String byteToString(byte b)
    {
        if (32 <= b && b <= 127) {
            return Character.toString((char)b);
        }
        return String.format("0x%02x", b & 0xff);
    }

    /**
     * Compare this BString to another BSTring. The comparison is done byte for byte,
     * until the two stings differ at one position, in which case the comparison result
     * is the the result of the comparison of the actual bytes. If all bytes are equal
     * until the end of one of the bstrings is reached, the longer string comes first
     * in the ordering (i.e. "ballpark" < "ball"). If both strings represent the same
     * sequence of bytes, they are equal.
     *
     * @param other  BString object to compare this object to
     * @return  -1, 0, 1 if the this object is less than, equal to, or greater than the other object
     * @throws NullPointerException  if other is null
     */
    @Override
    public int compareTo(BString other)
    {
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

    /**
     * Override equals() to have the same semantics as compareTo().
     *
     * @param otherObject  object to check for eqaulity
     * @return  true if otherObject is not null, and is a BSTring representing the
     *          same sequence of bytes
     */
    @Override
    public boolean equals(Object otherObject)
    {
        if (this == otherObject) return true;
        return (otherObject instanceof BString) && (compareTo((BString)otherObject) == 0);
    }

    /**
     * Overriding hashCode() for BString objects, so that the hash codes are equal
     * for two BStrings for which equals() returns true. The implementation is
     * similar to the hashCode() of the String class.
     *
     * @return  hash code for the BString
     */
    @Override
    public int hashCode()
    {
        int hash = 0;
        for (int i = start; i < end; ++i) {
            hash = 31 * hash + (bytes[i] & 0xff);
        }
        return hash;
    }

}
