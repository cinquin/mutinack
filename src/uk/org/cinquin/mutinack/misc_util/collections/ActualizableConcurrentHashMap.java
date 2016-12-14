package uk.org.cinquin.mutinack.misc_util.collections;

import java.util.concurrent.ConcurrentHashMap;

import uk.org.cinquin.mutinack.statistics.Actualizable;

public class ActualizableConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> implements Actualizable {

	private static final long serialVersionUID = -5306301173156950409L;

	@Override
	public void actualize() {
		forEachEntry(1_000, e -> {
			if (Actualizable.class.isAssignableFrom(e.getKey().getClass())) {
				((Actualizable) e.getKey()).actualize();
			}
			if (Actualizable.class.isAssignableFrom(e.getValue().getClass())) {
				((Actualizable) e.getValue()).actualize();
			}
		});
	}

}
