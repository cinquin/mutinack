package uk.org.cinquin.mutinack.statistics.json;

import java.io.Serializable;
import java.util.List;

import uk.org.cinquin.mutinack.Parameters;

public class JsonRoot implements Serializable {
	private static final long serialVersionUID = -5856926265963435703L;
	
	public String mutinackVersion;
	public Parameters parameters;
	public List<ParedDownMutinack> samples;
}
