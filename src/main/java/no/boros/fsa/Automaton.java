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
        @Override
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

        /**
         * Returns the transition at the specified position in the list.
         *
         * @param i  index of transition to return
         * @return  the transition at the specified position
         * @throws IndexOutOfRangeException if the index is out of range
         */
        public Transition get(int i)
        {
            return list.get(i);
        }

        /**
         * Returns the last transition in the list, or null if the list contains no transitions.
         *
         * @return  the transition at the last position, or null if the list is empty
         */
        public Transition getLast()
        {
            if (list.size() > 0) return list.get(list.size() - 1);
            return null;
        }

        /**
         * Find the transition corresponding to the specified symbol. If no such transition
         * is found in the list, null is returned.
         *
         * @param sy  the symbol to look up
         * @return  the transition correcponding to the symbol, or null if no such transition is found
         */
        public Transition find(byte sy)
        {
            for (Transition t : list) {
                if (t.symbol == sy) return t;
            }
            return null;
        }

        /**
         * Append a new transition at the end of the list.
         *
         * @param symbol  transition symbol
         * @param state   destination state
         */
        public void append(byte symbol, State state)
        {
            list.add(new Transition(symbol, state));
        }

        /**
         * Return the list of transitions as a collection.
         *
         * @return  a collection of the transitions
         */
        public Collection<Transition> getTransitions()
        {
            return list;
        }

        /**
         * Returns a string representation of the transition list.
         *
         * @return  a String object representing the transition list.
         */
        @Override
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

    /**
     * Helper class for representing a state in the automaton.
     */
    private class State
    {
        private final int id;
        private final TransitionList transitionList = new TransitionList();

        /**
         * Construct a state object with an empty transition list.
         */
        public State()
        {
            id = stateCount++;
        }

        /**
         * Returns true if the state is a final state, i.e. it has a transition corresponding
         * to the FINAL_SYMBOL.
         *
         * @return  true is the state is final, false otherwise
         */
        public boolean isFinal()
        {
            return getChild(FINAL_SYMBOL) != null;
        }

        /**
         * Returns true child corresponding to the specified symbol, or null if no such
         * child exists.
         *
         * @param sy  transition symbol
         * @return  the child corresponding to the specified symbol, or null if no such child exists
         */
        public State getChild(byte sy)
        {
            Transition t = transitionList.find(sy);
            if (null == t ) return null;
            return t.state;
        }

        /**
         * Returns the destination state corresponding to the last transition in the
         * transition list. FINAL_SYMBOL is not considered as a valid transition in this context.
         *
         * @return  the last (valid) child
         */
        public State getLastChild()
        {
            Transition t = transitionList.getLast();
            if (t != null && t.symbol != FINAL_SYMBOL) return t.state;
            return null;
        }

        /**
         * Update the last child (the last transition on the transition list), if such a child exists.
         *
         * @param st  new destination state for the last transition
         */
        public void updateLastChild(State st)
        {
            Transition t = transitionList.getLast();
            if (t != null) {
                t.state = st;
            }
        }

        /**
         * Add an empty child - a transition to a new empty state - at the end of the transition list.
         *
         * @param sy  new transition symbol
         * @return  the newly created empty state
         */
        public State addEmptyChild(byte sy)
        {
            State child = new State();
            transitionList.append(sy, child);
            return child;
        }

        /**
         * Add the specified child - a transition to the given state - at the end of the transition list.
         *
         * @param sy     new transition symbol
         * @param state  destination state
         * @return  the child state
         */
        public State addChild(byte sy, State child)
        {
            transitionList.append(sy, child);
            return child;
        }

        /**
         * Returns the transition list of this state.
         *
         * @return  the state's transition list
         */
        public TransitionList getTransitionList()
        {
            return transitionList;
        }

        /**
         * Returns the ID of this state.
         *
         * @return  the state's ID
         */
        public int getId()
        {
            return id;
        }

        /**
         * Returns a string representation of this state.
         *
         * @return  a String object representing this state
         */
        @Override
        public String toString()
        {
            return "S" + id + ":" + transitionList.toString();
        }

        /**
         * Two states are by definition equal iff their transition lists are equal.
         *
         * @param other  the other state to compare this state to
         * @return  true if the two states have equivalent transition lists
         */
        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;
            if (!(other instanceof State)) return false;

            return getTransitionList().equals(((State)other).getTransitionList());
        }

        /**
         * Override hash code so it returns the same code for equivalent states.
         *
         * @return  hash code
         */
        @Override
        public int hashCode()
        {
            return getTransitionList().hashCode();
        }
    }

    /**
     * A mapping of transition lists to states. Also, in the end a register of all states.
     */
    private HashMap<TransitionList, State> register = new HashMap<>();

    /**
     * The start state of the automaton.
     */
    private State qStart = new State();

    /**
     * The ultimate final state of the automaton (final states define a transition to this state
     * corresponding to the FINAL_SYMBOL).
     */
    private State qFinal = null; // Created when first final state is reached.

    /**
     * The previus input which have been added to the automaton.
     */
    private BString previousInput = null;

    /**
     * Finalized state; after the automaton has been finalized (minimized) it is not possible to
     * add further input strings.
     */
    private boolean finalized = false;

    /**
     * POJO for registering common prefix (length and last state).
     */
    private static class CommonPrefix
    {
        public final int length;
        public final State lastState;

        public CommonPrefix(int l, State s)
        {
            length = l;
            lastState = s;
        }
    }

    /**
     * Determine the length and last state of the common prefix between
     * the current input and the automaton which is already constructed.
     *
     * @param input  the current input string
     * @return  the length and last state of the common prefix (CommonPrefix object)
     */
    private CommonPrefix getCommonPrefix(BString input)
    {
        int i = 0;
        State state = qStart;
        while (i < input.length()) {
            State next = state.getChild(input.byteAt(i));
            if (next == null) return new CommonPrefix(i, state);
            state = next;
            ++i;
        }
        return new CommonPrefix(i, state);
    }

    /**
     * Recursively replace or register the states of the automaton. Only the last
     * child of each state can possibly be changed. After the call has recursively
     * replaced or registered grand*children, the last child is replaced if there is
     * another state with an equivalent transition list already in the register, or
     * it is added to the register otherwise.
     *
     * Registered states are not modified any more.
     *
     * @param state  the state to recursively replace or register
     */
    private void replaceOrRegister(State state)
    {
        State child = state.getLastChild();
        if (child == null) return; // the state has no children

        replaceOrRegister(child);

        State otherChild = register.get(child.getTransitionList());
        if (otherChild != null && otherChild.equals(child)) {
            state.updateLastChild(otherChild);
        }
        else {
            register.put(child.getTransitionList(), child);
        }
    }

    /**
     * Add states for a new suffix in the automaton.
     *
     * @param state  the state at which the suffix transitions start
     * @param suffix  the suffix string
     */
    private void addSuffix(State state, BString suffix)
    {
        State current = state;
        State child;

        for (int l = 0; l < suffix.length(); ++l) {
            child = current.addEmptyChild(suffix.byteAt(l));
            current = child;
        }

        // Add a final transition at the end of the suffix (indication that this state is a final state).
        if (null == qFinal) {
            qFinal = current.addEmptyChild(FINAL_SYMBOL);
        } else {
            current.addChild(FINAL_SYMBOL, qFinal);
        }
    }

    /**
     * Convenience method, which takes a Java String as parameter and converts it to
     * BString before inserting it to the automaton.
     *
     * @param input  the input string (String object)
     * @throws IllegalArgumentException  if the input string in not in proper sorted order
     * @throws IllegalStateException  if an attempt is made to insert a new string into a finalized automaton
     */
    public void insertSortedString(String input)
    {
        insertSortedString(new BString(input));
    }

    /**
     * Insert a sorted string into the automaton. The sorting order must be as defined by
     * the natural ordering of the BString class.
     *
     * @param input  the input string
     * @throws IllegalArgumentException  if the input string in not in proper sorted order
     * @throws IllegalStateException  if an attempt is made to insert a new string into a finalized automaton
     */
    public void insertSortedString(BString input)
    {
        if (finalized) {
            throw new IllegalStateException("Automaton is finalized, cannot insert more strings.");
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

        CommonPrefix commonPrefix = getCommonPrefix(input);
        BString currentSuffix = input.substring(commonPrefix.length, input.length());
        replaceOrRegister(commonPrefix.lastState);
        addSuffix(commonPrefix.lastState, currentSuffix);
    }

    /**
     * Finalizes (minimizes) the automaton. This will recursively register all states starting from
     * the start state, so further insertion of strings into this automaton will not be
     * possible. If the automaton is already finalized, the method has no effect.
     */
    public void finalize()
    {
        if (!finalized) {
            replaceOrRegister(qStart);
            // Complete the register by registering the start state as well.
            register.put(qStart.getTransitionList(), qStart);
            finalized = true;
        }
    }

    /**
     * Create a compact FSA representation of the minimized automaton. The compact representation
     * stores the states (transition lists), which are sparse arrays, packed together. The method
     * does not guarantee a minimal representation, but in most cases the pack ratio is over 99%.
     *
     * @return  the compact FSA representation of the automaton
     */
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


    //
    // The following methods are mainly for testing and debugging.
    //

    /**
     * Dump a string representation of the automaton to System.out.
     */
    public void dump()
    {
        System.out.println("Start: " + qStart);
        System.out.println("Final: " + qFinal);
        for (Map.Entry<TransitionList, State>  entry : register.entrySet()) {
            System.out.println(entry.getValue());
        }
    }

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
        getDictionary(dict, qStart, word);
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
    private void getDictionary(ArrayList<String> dict, State state, ArrayList<Byte> word)
    {
        TransitionList tlist = state.getTransitionList();
        for (Transition t : tlist.getTransitions()) {
            if (t.symbol == FINAL_SYMBOL) {
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
                word.add(t.symbol);
                getDictionary(dict, t.state, word);
                word.remove(word.size() - 1);
            }
        }
    }
}
