package uk.org.cinquin.mutinack.misc_util.exceptions;

public class FunctionalTestFailed extends RuntimeException {

	private static final long serialVersionUID = 7908031419117594998L;

	public FunctionalTestFailed() {
	}
	
	public FunctionalTestFailed(String message) {
		super(message);
	}
	
	public FunctionalTestFailed(String message, Throwable cause) {
		super(message, cause);
	}

}
