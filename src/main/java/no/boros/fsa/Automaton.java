package no.boros.fsa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


/**
 * Implementation of Daciuk et.al.'s algorithm,
 * "Incremental Construction of Minimal Acyclic Finite-State Automata"
 * (http://www.eti.pg.gda.pl/~jandac/daciuk98.ps.gz)
 * Currently, this implementation suports automata construction from sorted
 * data only.
 *
 * This class is not thread safe, and meant to be used single threaded only.
 */
public class Automaton
{
    /**
     * Special symbol used for indication final (accepting) states.
     * 0xff is used as it is not a valid UTF-8 byte (and 0x00 which
     * is also invalid in UTF-8 is used for indication empty cells
     * in the compacted representation.
     */
    public static final byte FINAL_SYMBOL = (byte)0xff;

    /**
     * Backwards search offset when trying to insert a new state in the
     * compact representation. Differente values have been tested, and
     * increasing this over 512 does not give significant reduction in
     * size.
     */
    private static final int SEARCH_OFFSET = 512;

    /**
     * State counter, used for assigning unique state IDs.
     */
    private int stateCount = 0;

    /**
     * Helper class representing a transition from one state to another.
     * Pretty much a POJO with a comparator, so no getters/setters for
     * the two simple public data fields.
     */
    private class Transition
        implements Comparable<Transition>
    {
        /**
         * Symbol assigned to the transition - this will never change.
         */
        public final byte symbol;
        /**
         * Destination state - this may get updated during the minimalization
         * process.
         */
        public State state;

        /**
         * Create a new transition with a given symbol to the given state.
         *
         * @param symbol  transition symbol
         * @param state   destination state
         */
        public Transition(byte symbol, State state)
        {
            this.symbol = symbol;
            this.state = state;
        }

        /**
         * Comparison method, defines a sort order for transitions.
         * If the symbols of the two transitions differ, their ordering decides the
         * ordering of the transitions. If the symbols are equal, the ids of the
         * destination states is used. If those are also equal, the transitions
         * are equal.
         *
         * @param other  transition to compare this object to
         * @return  -1, 0 or 1 if this transition is less than, equal to or greater than the other
         * @throws NullPointerException  if other is null
         */
        @Override
        public int compareTo(Transition other)
        {
            if (this == other) return 0;
            int bComp = BString.compareBytes(this.symbol, other.symbol);
            if (bComp != 0) return bComp;
            if (this.state.id < other.state.id) return -1;
            if (this.state.id > other.state.id) return 1;
            return 0;
        }

        /**
         * Override equals() to make it consistent with compareTo().
         *
         * @param otherObject  object to check for eqaulity
         * @return  true if otherObject is not null, and is a Transition with the same symbol
         *          and destination state
         */
        @Override
        public boolean equals(Object otherObject)
        {
            return (otherObject instanceof Transition) && (compareTo((Transition)otherObject) == 0);
        }

        /**
         * Override hashCode() for Transition objects, so that the hash codes are equal
         * for two Transitions for which equals() returns true.
         *
         * @return  hash code for the Transition
         */
        @Override
            public int hashCode()
        {
            return 256 * state.id + (symbol & 0xff);
        }

        /**
         * Return a representation of this transition.
         *
         * @return  a String object representing the transition
         */
        public String toString()
        {
            return "(" + BString.byteToString(symbol) + "->S" + state.getId() + ")";
        }
    }

    /**
     * Helper class for transition lists.
     */
    private class TransitionList
        implements Comparable<TransitionList>
    {
        private ArrayList<Transition> list = new ArrayList<>();

        /**
         * Define a natural ordering for transition lists. Two states are equal iff
         *  - they are both final or non-final
         *  - they have the same number of transitions
         *  - corresponding transitions have the same symbols
         *  - corresponding transitions lead to states with the same right languages
         * (Daciuk et.al, page 5)
         *
         * In practice, this means the two lists have the same number of Transtion objects, and
         * these objects are equal. (Final/non-final status is represented by a special transition
         * using FINAL_SYMBOL.)
         *
         * Given that the automaton is constructed from sorted input strings, the transactions
         * on the list are guaranteed to be sorted in increasing order.
         *
         * Of non-equal transition lists, the one containing less transitions comes first in the
         * ordering. If the lists are of equal length, the first differing transition decides.
         *
         * @param other  transition list to compare this object to
         * @return  -1, 0 or 1 if this transition list is less than, equal to or greater than the other
         * @throws NullPointerException  if other is null
         */
        @Override
        public int compareTo(TransitionList other)
        {
            if (this == other) return 0;
            if (this.list.size() < other.list.size()) return -1;
            if (this.list.size() > other.list.size()) return 1;
            for (int i = 0; i < this.list.size(); ++i) {
                int tComp = this.list.get(i).compareTo(other.list.get(i));
                if (0 != tComp) return tComp;
            }
            return 0;
        }

        /**
         * Override equals() to make it consistent with compareTo().
         *
         * @param otherObject  object to check for eqaulity
         * @return  true if otherObject is not null, and is a TransitionList with the same Transitions
         */
        @Override
        public boolean equals(Object other)
        {
            return (other instanceof TransitionList) && (compareTo((TransitionList)other) == 0);
        }

        /**
         * Override hashCode() for TransitionList objects, so that the hash codes are equal
         * for two TransitionLists for which equals() returns true.
         *
         * @return  hash code for the TransitionList
         */
        @Override
        public int hashCode()
        {
            int hash = 0;
            for (Transition t : list) {
                hash = hash * 17 + t.hashCode();
            }
            return hash;
        }

        /**
         * Returns true if the transition list is empty (contains no transitions).
         *
         * @return true if the transition list contains no transitions
         */
        public boolean isEmpty()
        {
            return list.size() == 0;
        }

        /**
         * Returns the size of the transition list (contained number of transitions).
         *
         * @return the number of transitions on the list
         */
        public int size()
        {
            return list.size();
        }

        public Transition get(int i)
        {
            return list.get(i);
        }

        public Transition getLast()
        {
            if (list.size() > 0) return list.get(list.size() - 1);
            return null;
        }

        public Transition find(byte sy)
        {
            for (Transition t : list) {
                if (t.symbol == sy) return t;
            }
            return null;
        }

        public void append(byte symbol, State state)
        {
            list.add(new Transition(symbol, state));
        }

        public Collection<Transition> getTransitions()
        {
            return list;
        }

        public String toString()
        {
            StringBuffer buf = new StringBuffer("[");
            boolean comma = false;
            for (Transition t : list) {
                if (comma) buf.append(",");
                buf.append(t.toString());
                comma = true;
            }
            buf.append("]");
            return buf.toString();
        }
    }

    private class State
    {
        private final int id;
        private final TransitionList transitionList = new TransitionList();

        public State()
        {
            id = stateCount++;
        }

        public boolean isFinal()
        {
            return getChild(FINAL_SYMBOL) != null;
        }

        public boolean hasChildren()
        {
            return !transitionList.isEmpty();
        }

        public State getChild(byte sy)
        {
            Transition t = transitionList.find(sy);
            if (null == t ) return null;
            return t.state;
        }

        public State getLastChild()
        {
            Transition t = transitionList.getLast();
            if (t != null && t.symbol != FINAL_SYMBOL) return t.state;
            return null;
        }

        public void updateLastChild(State st)
        {
            Transition t = transitionList.getLast();
            if (t != null) {
                t.state = st;
            }
        }

        public State addEmptyChild(byte sy)
        {
            State child = new State();
            transitionList.append(sy, child);
            return child;
        }

        public State addChild(byte sy, State child)
        {
            transitionList.append(sy, child);
            return child;
        }

        public TransitionList getTransitionList()
        {
            return transitionList;
        }

        public int getId()
        {
            return id;
        }

        public String toString()
        {
            return "S" + id + ":" + transitionList.toString();
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;
            if (!(other instanceof State)) return false;

            return getTransitionList().equals(((State)other).getTransitionList());
        }

        @Override
        public int hashCode()
        {
            return getTransitionList().hashCode();
        }
    }


    private HashMap<TransitionList, State> register = new HashMap<>();
    private State qStart = new State();
    private State qFinal = null;
    private BString previousInput = null;
    private boolean finalized = false;
    // PackedAutomaton packed;       /**< Packed automaton.             */

    private int getCPLength(BString input)
    {
        int l = 0;
        State state = qStart;
        while (l < input.length()) {
            state = state.getChild(input.byteAt(l));
            if (state == null) return l;
            l++;
        }
        return l;
    }

    private State getCPLastState(BString input)
    {
        int l = 0;
        State state = qStart;
        while (l < input.length()) {
            State next = state.getChild(input.byteAt(l));
            if (next == null) return state;
            state=next;
            l++;
        }
        return state;
    }

    private void replaceOrRegister(State state)
    {
        State child = state.getLastChild();
        if (child != null) {
            //System.out.println("DEBUG: examining=" + state);
            if (child.hasChildren()) {
                replaceOrRegister(child);
            }
            State otherChild = register.get(child.getTransitionList());
            //System.out.println("DEBUG: " + otherChild + " " + (otherChild != null && otherChild.equals(child) ? "=" : "!") + "= " + child);
            //System.out.println("DEBUG: before=" + state);
            if (otherChild != null && otherChild.equals(child)) {
                state.updateLastChild(otherChild);
            }
            else {
                register.put(child.getTransitionList(), child);
            }
            //System.out.println("DEBUG: after=" + state);
        }
    }

    private void addSuffix(State state, BString suffix)
    {
        State current = state;
        State child;

        for (int l = 0; l < suffix.length(); ++l) {
            child = current.addEmptyChild(suffix.byteAt(l));
            current = child;
        }

        if (null == qFinal) {
            qFinal = current.addEmptyChild(FINAL_SYMBOL);
        } else {
            current.addChild(FINAL_SYMBOL, qFinal);
        }
    }

    public void insertSortedString(String input)
    {
        insertSortedString(new BString(input));
    }

    public void insertSortedString(BString input)
    {
        if (finalized) {
            throw new IllegalArgumentException("Automaton is finalized, cannot insert more strings.");
        }

        if (null != previousInput) {
            if (previousInput.compareTo(input) == 0) return;  // already inserted
            if (previousInput.compareTo(input) == 1) {
                // trying to insert a string in the wrong order
                throw new IllegalArgumentException("Out-of-order string inserted, '" +
                                                   previousInput.toString()  + "' > '" +
                                                   input.toString() + "'.");
            }
        }
        previousInput = input;

        State lastState = getCPLastState(input);
        BString currentSuffix = input.substring(getCPLength(input), input.length());

        if (lastState.hasChildren()) {
            replaceOrRegister(lastState);
        }
        addSuffix(lastState,currentSuffix);
    }

    public void finalize()
    {
        if (!finalized) {
            replaceOrRegister(qStart);
            register.put(qStart.getTransitionList(), qStart);
            finalized = true;
        }
    }


    public FSA getFSA()
    {
        finalize();

        BitSet offsetTable = new BitSet();
        BitSet transTable = new BitSet();
        HashMap<State, Integer> offsetMap = new HashMap<>(register.size());
        int lastOffset = 0;

        for (Map.Entry<TransitionList, State>  entry : register.entrySet()) {
            int offset = transTable.length() - SEARCH_OFFSET;
            if (offset < 0) offset = 0;
            State state = entry.getValue();
            while (true) {
                if (!offsetTable.get(offset) &&
                    !transTable.get(offset + (state.getTransitionList().get(0).symbol &0xff))) {
                    boolean ok = true;
                    for (int i = 1; i < state.getTransitionList().size(); ++i) {
                        if (transTable.get(offset + (state.getTransitionList().get(i).symbol &0xff))) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) break;
                }
                ++offset;
            }
            for (int i = 0; i < state.getTransitionList().size(); ++i) {
                transTable.set(offset + (state.getTransitionList().get(i).symbol &0xff));
            }
            offsetTable.set(offset);
            offsetMap.put(state, offset);
            if (lastOffset < offset) lastOffset = offset;
        }

        byte[] symbols = new byte[lastOffset + 256];
        int[] states = new int[lastOffset + 256];
        for (Map.Entry<TransitionList, State>  entry : register.entrySet()) {
            State state = entry.getValue();
            int offset = offsetMap.get(state);
            for (int i = 0; i < state.getTransitionList().size(); ++i) {
                Transition t = state.getTransitionList().get(i);
                symbols[offset + (t.symbol & 0xff)] = t.symbol;
                Integer tOffset = offsetMap.get(t.state);
                states[offset + (t.symbol & 0xff)] = tOffset != null ? tOffset : -1;
            }
        }

        return new FSA(symbols, states, offsetMap.get(qStart));
    }



    // mostly for debugging


    public void dump()
    {
        System.out.println("Start: " + qStart);
        System.out.println("Final: " + qFinal);
        for (Map.Entry<TransitionList, State>  entry : register.entrySet()) {
            System.out.println(entry.getValue());
        }
    }

    public void traverse()
    {
        dfs(qStart);
    }

    private void dfs(State state)
    {
        System.out.println(state);
        TransitionList tlist = state.getTransitionList();
        for (Transition t : tlist.getTransitions()) {
            dfs(t.state);
        }
    }

    public void dumpDict()
    {
        ArrayList<Byte> word = new ArrayList<>();
        dumpDict(qStart, word);
    }

    private void dumpDict(State state, ArrayList<Byte> word)
    {
        TransitionList tlist = state.getTransitionList();
        for (Transition t : tlist.getTransitions()) {
            if (t.symbol == FINAL_SYMBOL) {
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
                word.add(t.symbol);
                dumpDict(t.state, word);
                word.remove(word.size() - 1);
            }
        }
    }

    public int numRegStates()
    {
        return register.size();
    }
}
