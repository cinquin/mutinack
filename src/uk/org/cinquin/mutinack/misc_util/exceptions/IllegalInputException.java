package uk.org.cinquin.mutinack.misc_util.exceptions;

public class IllegalInputException extends RuntimeException {
	private static final long serialVersionUID = 1215436196681641265L;

	public IllegalInputException() {
	}

	public IllegalInputException(String message) {
		super(message);
	}

	public IllegalInputException(String message, Throwable cause) {
		super(message, cause);
	}
}
