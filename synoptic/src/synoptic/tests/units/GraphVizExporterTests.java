package synoptic.tests.units;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import synoptic.main.Main;
import synoptic.main.ParseException;
import synoptic.model.Event;
import synoptic.model.Graph;
import synoptic.model.EventNode;
import synoptic.model.export.GraphVizExporter;
import synoptic.tests.SynopticTest;

public class GraphVizExporterTests extends SynopticTest {
    GraphVizExporter exporter = null;

    @Override
    public void setUp() throws ParseException {
        super.setUp();
        exporter = new GraphVizExporter();
    }

    /**
     * Returns a canonical dot-format string representation of a graph that
     * encodes a path of events.
     * 
     * @param events
     *            The input event sequence
     * @return Dot-formatted representation of the event sequence graph.
     */
    public String getExportedPathGraph(String[] events) {
        List<EventNode> path = getLogEventPath(events);
        Graph<EventNode> g = new Graph<EventNode>();

        // Randomize the order in which we add events to the graph
        List<EventNode> pathCopy = new ArrayList<EventNode>();
        pathCopy.addAll(path);
        Collections.shuffle(pathCopy, Main.random);
        for (EventNode event : pathCopy) {
            g.add(event);
        }

        Event dummyAct = Event.newInitialEvent();
        g.setDummyInitial(new EventNode(dummyAct), defRelation);
        g.tagInitial(path.get(0), defRelation);

        for (int i = 0; i < path.size() - 1; i++) {
            EventNode event = path.get(i);
            event.addTransition(path.get(i + 1), defRelation);
        }
        return exporter.export(g);
    }

    /**
     * Make sure that canonical exporting results in same output for graphs
     * constructed from different nodes and in different orders.
     */
    @Test
    public void canonicalExportTest() {
        String gStr1 = getExportedPathGraph(new String[] { "a", "b", "c" });
        String gStr2 = getExportedPathGraph(new String[] { "a", "b", "c" });
        logger.fine(gStr1);
        logger.fine(gStr2);
        assertTrue(gStr1.equals(gStr2));

        // TODO: expand this to more complex graph topologies.
    }
}
