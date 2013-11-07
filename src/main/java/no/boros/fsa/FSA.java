package no.boros.fsa;

import java.util.ArrayList;


public class FSA
{
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
