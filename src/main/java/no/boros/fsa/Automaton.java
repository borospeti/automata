package no.boros.fsa;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Automaton
{
    public static final byte FINAL_SYMBOL = (byte)0xff;

    private int stateCount = 0;

    private class Transition
        implements Comparable<Transition>
    {
        public final byte symbol;
        public State state;

        public Transition(byte symbol, State state)
        {
            this.symbol = symbol;
            this.state = state;
        }

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

        public String toString()
        {
            return "(" + BString.byteToString(symbol) + "->S" + state.getId() + ")";
        }
    }

    private class TransitionList
        implements Comparable<TransitionList>
    {
        private ArrayList<Transition> list = new ArrayList<>();

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

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof TransitionList)) return false;
            return compareTo((TransitionList)other) == 0;
        }

        @Override
        public int hashCode()
        {
            int code = 1;
            for (Transition t : list) {
                code = (code * 17 + t.symbol + 256 * t.state.hashCode()) % 67108864;
            }
            return code;
        }

        public boolean isEmpty()
        {
            return list.size() == 0;
        }

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
            if (!(other instanceof State)) return false;
            return getTransitionList().equals(((State)other).getTransitionList());
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
        replaceOrRegister(qStart);
        finalized = true;
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
