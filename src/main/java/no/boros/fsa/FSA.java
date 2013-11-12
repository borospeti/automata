package no.boros.fsa;

import java.util.ArrayList;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


/**
 * A compact representation of a deterministic finite state automaton.
 */
public class FSA
{
    /**
     * Magic number identifying .fsa files.
     */
    private static final int FSA_MAGIC = 0x62D80AB5;

    /**
     * Chunk size when writing / reading fsa files (in terms of entries, which may be 1 or 4 bytes).
     */
    private static final int CHUNK_SIZE = 1048576;

    /**
     * Transition symbols and states. A valid transition from state ST with symbol SY exists iff
     * transSymbols[ST + SY] == SY. In this case, transStates[ST + SY] gives the destination state.
     * transSymbols[ST + FINAL_SYMBOL] == FINAL_SYMBOL means that the state is a final state.
     */
    private final byte[] transSymbols;
    private final int[] transStates;

    /**
     * FSA start state.
     */
    private final int startState;

    /**
     * Package-private constructor. The only two ways to construct an FSA is either from
     * a finalized Automaton via the getFSA() method, or reading it from a file with
     * FSA.read(...).
     *
     * @param symbols  compacted symbol array
     * @param states   compacted state array
     * @param start    start state
     */
    FSA(byte[] symbols, int[] states, int start)
    {
        transSymbols = symbols;
        transStates = states;
        startState = start;
    }

    /**
     * Class for performing lookups in the FSA. A typical use pattern is as follows:
     *
     *     FSA fsa = FSA.read("very_important_words.fsa");
     *     ...
     *     FSA.State state = fsa.start();
     *     String word = "addlepated";
     *     state.consume(word);
     *     if (state.isFinal()) {
     *         System.out.println("'" + word + "' is an important word.");
     *     } else if (state.isValid()) {
     *         System.out.println("'" + word + "' is the prefix of an important word.");
     *     }
     */
    public class State
    {
        int state = startState;
        boolean valid = true;

        /**
         * Private default constructor.
         * Use the FSA.start() or State.clone() to get a new State instance.
         */
        private State()
        {
        }

        /**
         * Clone a state to a new object. This is useful if one needs to test several
         * endings with a common prefix.
         *
         * @return  new state object which is an exact copy of the original
         */
        public State clone()
        {
            State cl = new State();
            cl.state = state;
            cl.valid = valid;
            return cl;
        }

        /**
         * Returns true if the current state is valid, i.e. the symbols consumed
         * so far constitute a prefix of a string which is accepted bye the automaton
         * (possibly with an empty suffix).
         *
         * @return  true if the state is valid
         */
        public boolean isValid()
        {
            return valid;
        }

        /**
         * Returns true if the current state is final (aka accepting) state, that is
         * the symbols consumed so far constitute a string which is accepted bye the
         * automaton.
         *
         * @return  true if the state is final
         */
        public boolean isFinal()
        {
            return valid && (transSymbols[state + (Automaton.FINAL_SYMBOL & 0xff)] == Automaton.FINAL_SYMBOL);
        }

        /**
         * Consume a single symbol - make a transition to the next state given
         * the selected symbol.
         *
         * @param symbol  transition symbol
         * @return  true if the new state is valid
         * @throws IllegalArgumentException  if the symbol is 0x00 or 0xFF, which are reserved (and also
         *                                   illegal as part of an UTF-8 encoded string)
         */
        public boolean consume(byte symbol)
        {
            if (symbol == 0 || symbol == -1) {
                throw new IllegalArgumentException("Bytes 0x00 and 0xFF are reserved for internal use.");
            }

            if (valid && transSymbols[state + (symbol & 0xff)] == symbol) {
                state = transStates[state + (symbol & 0xff)];
            } else {
                valid = false;
            }

            return valid;
        }

        /**
         * Consume a string of symbols - make a transition to the next state given
         * the selected symbol until the end of the string is reached, or a transition
         * to an invalid state is made (string is not a valid prefix anymore).
         *
         * @param bString  transition string
         * @return  true if the whole string has been consumed and the new state is valid
         * @throws IllegalArgumentException  if the string contains a 0x00 or 0xFF byte, which are reserved (and also
         *                                   illegal as part of an UTF-8 encoded string)
         */
        public boolean consume(BString bString)
        {
            if (!valid) return false;

            for (int i = 0; i < bString.length(); ++i) {
                byte symbol = bString.byteAt(i);
                if (symbol == 0 || symbol == -1) {
                    throw new IllegalArgumentException("Bytes 0x00 and 0xFF are reserved for internal use.");
                }
                if (transSymbols[state + (symbol & 0xff)] == symbol) {
                    state = transStates[state + (symbol & 0xff)];
                } else {
                    valid = false;
                    break;
                }
            }

            return valid;
        }

        /**
         * Convenience method to consume Java Strings directly. The String is converted
         * to an UTF-8 BString.
         * @see FSA.State#consume(BString) consume(BString)
         *
         * @param string  transition string
         * @return  true if the whole string has been consumed and the new state is valid
         */
        public boolean consume(String string)
        {
            return consume(new BString(string));
        }
    }

    /**
     * Initialize a new FSA.State to the start state of the automaton.
     *
     * @return  a new FSA.State initialized to the start state of the automaton
     */
    public State start()
    {
        return new State();
    }

    /**
     * Convenience method to look up an entire string. It is equivalent to
     *     State state = FSA.start();
     *     state.consume(bString);
     *     return state.isFinal();
     * @see FSA.State#consume(BString) consume(BString)
     *
     * @param bString  byte string to look up
     * @return  true if the string is accepted by the automaton
     * @throws IllegalArgumentException  if the string contains a 0x00 or 0xFF byte, which are reserved (and also
     *                                   illegal as part of an UTF-8 encoded string)
     */
    public boolean lookup(BString bString)
    {
        State state = start();
        state.consume(bString);
        return state.isFinal();
    }

    /**
     * Convenience method to look up an entire Java String. The string is converted
     * to an UTF-8 byte string.
     * @see FSA#lookup(BString) lookup(BString)
     *
     * @param string  Java string to look up
     * @return  true if the string is accepted by the automaton
     */
    public boolean lookup(String string)
    {
        return lookup(new BString(string));
    }

    /**
     * Write the compact automaton to a file.
     *
     * @param fileName  the name of the file
     * @throws IOException  if the operation failed
     */
    public void write(String fileName)
        throws IOException
    {
        RandomAccessFile out = null;
        try {
            out = new RandomAccessFile(fileName, "rw");
            FileChannel file = out.getChannel();

            long offset = 0L;
            int headerSize = 4 * 3;
            ByteBuffer header = file.map(FileChannel.MapMode.READ_WRITE, offset, headerSize);
            header.putInt(FSA_MAGIC);
            header.putInt(transSymbols.length);
            header.putInt(startState);
            offset += headerSize;

            for (int pos = 0; pos < transSymbols.length; ) {
                int chunk = pos + CHUNK_SIZE < transSymbols.length ? CHUNK_SIZE : transSymbols.length - pos;
                ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, offset, chunk);
                buf.put(transSymbols, pos, chunk);
                pos += chunk;
                offset += chunk;
            }

            for (int pos = 0; pos < transStates.length; ) {
                int chunk = pos + CHUNK_SIZE < transStates.length ? CHUNK_SIZE : transSymbols.length - pos;
                ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, offset, chunk * 4);
                for (int i = pos; i < pos + chunk; ++i) {
                    buf.putInt(transStates[i]);
                }
                pos += chunk;
                offset += chunk * 4;
            }

            file.close();
        } catch (IOException e) {
            throw e;
        } finally {
            if (out != null) out.close();
        }
    }

    /**
     * Read a compact automaton form a file into a newly created FSA object.
     *
     * @param fileName  the name of the file
     * @return  the new FSA object
     * @throws IOException  if the operation failed
     */
    public static FSA read(String fileName)
        throws IOException
    {
        RandomAccessFile in = null;
        byte[] symbols;
        int[] states;
        int start;
        try {
            in = new RandomAccessFile(fileName, "r");
            FileChannel file = in.getChannel();

            long offset = 0L;
            int headerSize = 4 * 3;
            ByteBuffer header = file.map(FileChannel.MapMode.READ_ONLY, offset, headerSize);
            int magic = header.getInt();
            int length = header.getInt();
            start = header.getInt();
            if (magic != FSA_MAGIC) throw new IOException("Invalid FSA file - wrong magic.");
            if (start < 0 || length < start + 256) throw new IOException("Invalid FSA file - wrong magic.");
            offset += headerSize;

            symbols = new byte[length];
            for (int pos = 0; pos < length; ) {
                int chunk = pos + CHUNK_SIZE < length ? CHUNK_SIZE : length - pos;
                ByteBuffer buf = file.map(FileChannel.MapMode.READ_ONLY, offset, chunk);
                buf.get(symbols, pos, chunk);
                pos += chunk;
                offset += chunk;
            }

            states = new int[length];
            for (int pos = 0; pos < length; ) {
                int chunk = pos + CHUNK_SIZE < length ? CHUNK_SIZE : length - pos;
                ByteBuffer buf = file.map(FileChannel.MapMode.READ_ONLY, offset, chunk * 4);
                for (int i = pos; i < pos + chunk; ++i) {
                  states[i] = buf.getInt();
                }
                pos += chunk;
                offset += chunk * 4;
            }

            file.close();
        } catch (IOException e) {
            throw e;
        } finally {
            if (in != null) in.close();
        }

        return new FSA(symbols, states, start);
    }


    //
    // The following methods are mainly for testing and debugging.
    //

    /**
     * Return an ArrayList of Strings which represent the dictionary of this automaton.
     *
     * Note that for large automata the dictionary may potentially much larger in size.
     *
     * @return  the dictionary of the automaton
     */
    public ArrayList<String> getDictionary()
    {
        ArrayList<String> dict = new ArrayList<>();
        ArrayList<Byte> word = new ArrayList<>();
        getDictionary(dict, startState, word);
        return dict;
    }

    /**
     * Recursively create the right language of the specified state,
     * with the given prefix.
     *
     * @param dict   the ArrayList for collecting the dictionary entries
     * @param state  starting state
     * @param word   prefix
     */
    private void getDictionary(ArrayList<String> dict, int state, ArrayList<Byte> word)
    {
        for (int symbol = 1; symbol < 256; ++symbol) {
            if ((transSymbols[state + symbol] & 0xff) == symbol) {
                if (symbol == (Automaton.FINAL_SYMBOL & 0xff)) {
                    try {
                        byte[] temp = new byte[word.size()];
                        for (int i = 0; i < word.size(); ++i) {
                            temp[i] = word.get(i);
                        }
                        dict.add(new String(temp, "utf-8"));
                    } catch (java.io.UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    word.add((byte)symbol);
                    getDictionary(dict, transStates[state + symbol], word);
                    word.remove(word.size() - 1);
                }
            }
        }
    }
}
