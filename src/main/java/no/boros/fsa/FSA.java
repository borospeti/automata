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
