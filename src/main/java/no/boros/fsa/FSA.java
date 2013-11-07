package no.boros.fsa;

import java.util.ArrayList;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


public class FSA
{
    private static final int FSA_MAGIC = 0x62D80AB5;
    private static final int CHUNK_SIZE = 1048576;

    private final byte[] transSymbols;
    private final int[] transStates;
    private final int startState;

    /**
     * Package-private constructor.
     */
    FSA(byte[] symbols, int[] states, int start)
    {
        transSymbols = symbols;
        transStates = states;
        startState = start;
    }

    public class State
    {
        int state = startState;
        boolean valid = true;

        private State()
        {
        }

        public boolean isValid()
        {
            return valid;
        }

        public boolean isFinal()
        {
            return valid && (transSymbols[state + (Automaton.FINAL_SYMBOL & 0xff)] == Automaton.FINAL_SYMBOL);
        }

        public void consume(byte symbol)
        {
            if (!valid) return;

            if (transSymbols[state + (symbol & 0xff)] == symbol) {
                state = transStates[state + (symbol & 0xff)];
            } else {
                valid = false;
            }
        }

        public void consume(BString bString)
        {
            if (!valid) return;

            for (int i = 0; i < bString.length(); ++i) {
                byte symbol = bString.byteAt(i);
                if (transSymbols[state + (symbol & 0xff)] == symbol) {
                    state = transStates[state + (symbol & 0xff)];
                } else {
                    valid = false;
                    return;
                }
            }
        }

        public void consume(String string)
        {
            consume(new BString(string));
        }
    }

    public State start()
    {
        return new State();
    }

    public boolean lookup(BString bString)
    {
        State state = start();
        state.consume(bString);
        return state.isFinal();
    }

    public boolean lookup(String string)
    {
        return lookup(new BString(string));
    }

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


    // mainly for debugging


    public void dumpDict()
    {
        ArrayList<Byte> word = new ArrayList<>();
        dumpDict(startState, word);
    }

    private void dumpDict(int state, ArrayList<Byte> word)
    {
        for (int symbol = 1; symbol < 256; ++symbol) {
            if ((transSymbols[state + symbol] & 0xff) == symbol) {
                if (symbol == (Automaton.FINAL_SYMBOL & 0xff)) {
                    try {
                        byte[] temp = new byte[word.size()];
                        for (int i = 0; i < word.size(); ++i) {
                            temp[i] = word.get(i);
                        }
                        System.out.println(new String(temp, "utf-8"));
                    } catch (java.io.UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    word.add((byte)symbol);
                    dumpDict(transStates[state + symbol], word);
                    word.remove(word.size() - 1);
                }
            }
        }
    }


}
