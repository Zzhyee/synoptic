package synoptic.tests.units;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import synoptic.invariants.AlwaysFollowedInvariant;
import synoptic.invariants.AlwaysPrecedesInvariant;
import synoptic.invariants.CExamplePath;
import synoptic.invariants.ITemporalInvariant;
import synoptic.invariants.NeverFollowedInvariant;
import synoptic.invariants.TemporalInvariantSet;
import synoptic.invariants.miners.TransitiveClosureInvMiner;
import synoptic.main.Main;
import synoptic.main.ParseException;
import synoptic.main.TraceParser;
import synoptic.model.ChainsTraceGraph;
import synoptic.model.EventNode;
import synoptic.model.EventType;
import synoptic.model.Partition;
import synoptic.model.PartitionGraph;
import synoptic.model.StringEventType;
import synoptic.model.Transition;
import synoptic.model.interfaces.IGraph;
import synoptic.model.interfaces.INode;
import synoptic.tests.SynopticTest;
import synoptic.util.InternalSynopticException;

/**
 * Checks the FSM model checker against the NASA model checker to compare their
 * results for generating counter examples of temporal invariants on graphs.
 * This is a parameterized JUnit test -- tests in this class are run with
 * parameters generated by method annotated with @Parameters.
 * 
 * @author ivan
 */
@RunWith(value = Parameterized.class)
public class ModelCheckersTests extends SynopticTest {

    /**
     * Generates parameters for this unit test. The first instance of this test
     * (using first set of parameters) will run using the FSM checker, while the
     * second instance (using the second set of parameters) will run using the
     * NASA model checker.
     * 
     * @return The set of parameters to pass to the constructor the unit test.
     */
    @Parameters
    public static Collection<Object[]> data() {
        Object[][] data = new Object[][] { { true }, { false } };
        return Arrays.asList(data);
    }

    boolean useFSMChecker;

    public ModelCheckersTests(boolean useFSMChecker) {
        this.useFSMChecker = useFSMChecker;
    }

    @Before
    public void setUp() throws ParseException {
        super.setUp();
        synoptic.main.Main.options.useFSMChecker = this.useFSMChecker;
    }

    /**
     * Test that the graph g generates or not (depending on the value of
     * cExampleExists) a counter-example for invariant inv, which is exactly the
     * expectedPath through the graph g.
     */
    @SuppressWarnings("null")
    private static <T extends INode<T>> void testCExamplePath(IGraph<T> g,
            ITemporalInvariant inv, boolean cExampleExists, List<T> expectedPath)
            throws InternalSynopticException {

        TemporalInvariantSet invs = new TemporalInvariantSet();
        invs.add(inv);

        List<CExamplePath<T>> cexamples = invs.getAllCounterExamples(g);

        if (cexamples != null) {
            logger.info("model-checker counter-example:"
                    + cexamples.get(0).path);
        }

        if (!cExampleExists) {
            assertTrue(cexamples == null);
            return;
        }

        // Else, there should be just one counter-example
        assertTrue(cexamples != null);
        assertTrue(cexamples.size() == 1);
        List<T> cexamplePath = cexamples.get(0).path;

        // logger.info("model-checker counter-example:" + cexamplePath);
        logger.info("correct counter-example:" + expectedPath);

        // Check that the counter-example is of the right length.
        assertTrue(cexamplePath.size() == expectedPath.size());

        // Check that cexamplePath is exactly the expectedPath
        for (int i = 0; i < cexamplePath.size(); i++) {
            assertTrue(cexamplePath.get(i) == expectedPath.get(i));
        }
        return;
    }

    /**
     * Test that the list of events representing a linear graph generates or not
     * (depending on value of cExampleExists) a single counter-example for
     * invariant inv that includes the prefix of linear graph of length up to
     * cExampleIndex (which starts counting at 0 = INITIAL, and may index
     * TERMINAL).
     */
    private void testLinearGraphCExample(String[] events,
            ITemporalInvariant inv, boolean cExampleExists,
            int lastCExampleIndex) throws InternalSynopticException,
            ParseException {
        // Create the graph.
        ChainsTraceGraph g = genInitialLinearGraph(events);
        Set<EventNode> initNodes = g.getDummyInitialNodes();

        if (!cExampleExists) {
            // Don't bother constructing the counter-example path.
            testCExamplePath(g, inv, cExampleExists, null);
            return;
        }

        // There should be just one initial node.
        assertTrue(initNodes.size() == 1);

        // Build the expectedPath by traversing the entire graph.
        LinkedList<EventNode> expectedPath = new LinkedList<EventNode>();
        EventNode nextNode = initNodes.iterator().next();
        expectedPath.add(nextNode);
        for (int i = 1; i <= lastCExampleIndex; i++) {
            nextNode = nextNode.getTransitions().get(0).getTarget();
            expectedPath.add(nextNode);
        }
        testCExamplePath(g, inv, cExampleExists, expectedPath);
    }

    /**
     * The list of partially ordered events is condensed into a partition graph
     * (the most compressed model). This graph is then checked for existence or
     * not (depending on value of cExampleExists) of a counter-example for
     * invariant inv specified by cExampleLabels. The format for each event
     * string in the events array (?<TYPE>) with "^--$" as the partitions
     * separator; the format for each element in the counter-example path is
     * (?<TYPE>). <br />
     * <br/>
     * NOTE: We get away with just TYPE for specifying the counter-example
     * because we will deal with the initial partition graph -- where there is
     * exactly one node for each event type. <br />
     * <br />
     * NOTE: INITIAL is always included, therefore cExampleLabels should not
     * include it. However, if TERMINAL is to be included, it should be
     * specified in cExampleLabels.
     * 
     * @throws Exception
     */
    private static void testPartitionGraphCExample(String[] events,
            ITemporalInvariant inv, boolean cExampleExists,
            List<EventType> cExampleLabels) throws Exception {

        TraceParser parser = new TraceParser();
        parser.addRegex("^(?<TYPE>)$");
        parser.addPartitionsSeparator("^--$");
        PartitionGraph pGraph = genInitialPartitionGraph(events, parser,
                new TransitiveClosureInvMiner());

        exportTestGraph(pGraph, 1);

        if (!cExampleExists) {
            // If there no cExample then there's no reason to build a path.
            testCExamplePath(pGraph, inv, cExampleExists, null);
            return;
        }

        // There should be just one initial node.
        Set<Partition> initNodes = pGraph.getDummyInitialNodes();
        assertTrue(initNodes.size() == 1);

        LinkedList<Partition> expectedPath = new LinkedList<Partition>();
        Partition nextNode = initNodes.iterator().next();

        // Build the expectedPath by traversing the graph, starting from the
        // initial node by finding the appropriate partition at each hop by
        // matching on the label of each partition.
        expectedPath.add(nextNode);
        nextCExampleHop:
        for (int i = 0; i < cExampleLabels.size(); i++) {
            EventType nextLabel = cExampleLabels.get(i);
            for (Transition<Partition> transition : nextNode.getTransitions()) {
                for (EventNode event : transition.getTarget().getEventNodes()) {
                    if (event.getEType().equals(nextLabel)) {
                        nextNode = transition.getTarget();
                        expectedPath.add(nextNode);
                        continue nextCExampleHop;
                    }
                }
            }
            Assert.fail("Unable to locate transition from "
                    + nextNode.toString() + " to a partition with label "
                    + nextLabel.toString());
        }
        testCExamplePath(pGraph, inv, cExampleExists, expectedPath);
    }

    // //////////////////////////// AFby:

    /**
     * Tests that a linear graph with a cycle does _not_ generate an AFby
     * c-example. This demonstrates why we need "<> TERMINAL ->" as the prefix
     * in the AFby LTL formula -- without this prefix this tests fails.
     * 
     * @throws Exception
     */
    @Test
    public void NoAFbyLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "x", "a", "c", "x", "a", "y", "b", "w" };

        ITemporalInvariant inv = new AlwaysFollowedInvariant(
                new StringEventType("a"), new StringEventType("b"),
                SynopticTest.defRelation);

        testPartitionGraphCExample(events, inv, false, null);
    }

    /**
     * Tests that a linear graph with a cycle does generate an AFby c-example.
     * This tests the LTL formula that includes an "eventually TERMINAL" clause
     * to permit only those counter-examples that reach the TERMINAL node.
     * 
     * @throws Exception
     */
    @Test
    public void AFbyLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "x", "a", "b", "x", "a", "y", "w",
                "--", "x", "a", "y", "w" };

        ITemporalInvariant inv = new AlwaysFollowedInvariant(
                new StringEventType("a"), new StringEventType("b"),
                SynopticTest.defRelation);

        List<EventType> cExampleLabels = stringsToStringEventTypes(new String[] {
                "x", "a", "y", "w" });
        cExampleLabels.add(StringEventType.newTerminalStringEventType());
        testPartitionGraphCExample(events, inv, true, cExampleLabels);
    }

    /**
     * Tests that a linear graph does not generate an AFby c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void NoAFbyLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "a", "x", "y", "b" };
        ITemporalInvariant inv = new AlwaysFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, false, 0);
    }

    /**
     * Tests that a linear graph does generate an AFby c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void AFbyLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "a", "x", "y", "z" };
        ITemporalInvariant inv = new AlwaysFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, true, 5);
    }

    // //////////////////////////// NFby:

    /**
     * Tests that a linear graph with a cycle does not generate an NFby
     * c-example.
     * 
     * @throws Exception
     */
    @Test
    public void NoNFbyLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "a", "c", "a", "d", "e" };

        ITemporalInvariant inv = new NeverFollowedInvariant(
                new StringEventType("a"), new StringEventType("b"),
                SynopticTest.defRelation);

        testPartitionGraphCExample(events, inv, false, null);
    }

    /**
     * Tests that a linear graph with a cycle does generate an NFby c-example.
     * 
     * @throws Exception
     */
    @Test
    public void NFbyLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "a", "c", "d", "a", "c", "d", "b" };

        ITemporalInvariant inv = new NeverFollowedInvariant(
                new StringEventType("a"), new StringEventType("b"),
                SynopticTest.defRelation);

        List<EventType> cExampleLabels = null;

        if (Main.options.useFSMChecker) {
            cExampleLabels = stringsToStringEventTypes(new String[] { "a", "c",
                    "d", "b" });
        } else {
            cExampleLabels = stringsToStringEventTypes(new String[] { "a", "c",
                    "d", "a", "c", "d", "b" });
        }
        // NOTE: NFby c-examples do not need to end with a TERMINAL node
        testPartitionGraphCExample(events, inv, true, cExampleLabels);
    }

    /**
     * Tests that a linear graph does not generate an NFby c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void NoNFbyLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "a", "x", "y", "z" };
        ITemporalInvariant inv = new NeverFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, false, 0);
    }

    /**
     * Tests that a linear graph does generate an NFby c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void NFbyLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "a", "x", "y", "z", "b" };
        ITemporalInvariant inv = new NeverFollowedInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, true, 5);
    }

    // //////////////////////////// AP:

    /**
     * Tests that a linear graph with a cycle does not generate an AP c-example.
     * 
     * @throws Exception
     */
    @Test
    public void NoAPLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "a", "c", "a", "b" };

        ITemporalInvariant inv = new AlwaysPrecedesInvariant(
                new StringEventType("a"), new StringEventType("b"),
                SynopticTest.defRelation);

        testPartitionGraphCExample(events, inv, false, null);
    }

    /**
     * Tests that a linear graph with a cycle does generate an AP c-example.
     * 
     * @throws Exception
     */
    @Test
    public void APLinearGraphWithCycleTest() throws Exception {
        String[] events = new String[] { "z", "x", "z", "b" };

        ITemporalInvariant inv = new AlwaysPrecedesInvariant(
                new StringEventType("a"), new StringEventType("b"),
                SynopticTest.defRelation);
        List<EventType> cExampleLabels = stringsToStringEventTypes(new String[] {
                "z", "b" });
        testPartitionGraphCExample(events, inv, true, cExampleLabels);
    }

    /**
     * Tests that a linear graph does not generate an AP c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void NoAPLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "x", "a", "x", "y", "b" };
        ITemporalInvariant inv = new AlwaysPrecedesInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, false, 0);
    }

    /**
     * Tests that a linear graph does generate an AP c-example.
     * 
     * @throws InternalSynopticException
     * @throws ParseException
     */
    @Test
    public void APLinearGraphTest() throws InternalSynopticException,
            ParseException {
        // logger.info("Using the FSMChecker: " + Main.useFSMChecker);
        String[] events = new String[] { "x", "y", "z", "b", "a" };
        ITemporalInvariant inv = new AlwaysPrecedesInvariant("a", "b",
                SynopticTest.defRelation);
        testLinearGraphCExample(events, inv, true, 4);
    }

    // /////////////////////////

    // compareViolations is not used above because the NASA and FSM checkers
    // often produce different, but correct, violating paths. This function
    // might be useful for testing for violations before and after modifying a
    // graph in some specific way.

    /**
     * Compares counter-example violations paths generated by the two model
     * checkers. We compare the two model checkers by parsing events from the
     * same log (using the defParser) and then model checking the resulting
     * graphs for invariants using both types of model checkers.
     * 
     * @param <T>
     *            types of nodes in the graph
     * @param invs
     *            the invariants for which counter-examples should be produced
     * @param graph
     *            the graph over which counter-examples should be produced
     */
    // private <T extends INode<T>> void compareViolations(
    // List<ITemporalInvariant> invs, IGraph<T> graph) {
    // GraphLTLChecker<T> ch = new GraphLTLChecker<T>();
    //
    // List<BinaryInvariant> bitSetInput = new ArrayList<BinaryInvariant>();
    // for (ITemporalInvariant tinv : invs) {
    // bitSetInput.add((BinaryInvariant) tinv);
    // }
    // List<BinaryInvariant> violated = FsmModelChecker.runBitSetChecker(
    // bitSetInput, graph);
    //
    // for (int i = 0; i < invs.size(); i++) {
    // BinaryInvariant inv = (BinaryInvariant) invs.get(i);
    // RelationPath<T> path = ch.getCounterExample(inv, graph);
    // RelationPath<T> fsm_path = FsmModelChecker.getCounterExample(inv,
    // graph);
    //
    // assertTrue(fsm_path == null == (path == null));
    //
    // if (fsm_path != null) {
    // logger.fine("both found " + inv);
    // logger.fine("fsm_path.size = " + fsm_path.path.size());
    // logger.fine("path.size = " + path.path.size());
    // assertTrue(fsm_path.path.size() == path.path.size());
    // assertTrue(path.path.get(path.path.size() - 1).isTerminal());
    // }
    // assertTrue(path != null == violated.contains(inv));
    // }
    // }
}
