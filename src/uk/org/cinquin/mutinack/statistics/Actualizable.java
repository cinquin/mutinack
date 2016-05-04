package uk.org.cinquin.mutinack.statistics;

/**
 * Marks statistics objects on which it is necessary to call
 * actualize before serialization.
 * @author olivier
 *
 */
public interface Actualizable {
	void actualize();
}
