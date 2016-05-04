package uk.org.cinquin.mutinack.misc_util.exceptions;

public class TestRunFailure extends RuntimeException {

	private static final long serialVersionUID = -9047673019594517174L;

	public TestRunFailure() {
	}
	
	public TestRunFailure(String message) {
		super(message);
	}
	
	public TestRunFailure(String message, Throwable cause) {
		super(message, cause);
	}

}
