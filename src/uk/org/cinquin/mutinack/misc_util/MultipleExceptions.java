package uk.org.cinquin.mutinack.misc_util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class MultipleExceptions extends RuntimeException {
	
	private static final long serialVersionUID = -3843826759182832179L;
	List<Throwable> causeList;
	public MultipleExceptions(List<Throwable> causeList) {
		super();
		this.causeList = causeList;
	}
	
	@Override
	public String getMessage() {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PrintStream outPS = new PrintStream(outStream);
        outPS.print(causeList.size() + " exceptions received:");
        for (Throwable t: causeList) {
        	outPS.print(" " + t.getMessage());
        }
        outPS.println();
        int index = 0;
        for (Throwable t: causeList) {
        	index++;
        	outPS.println("Exception " + index + ": " + t.getMessage());
        	t.printStackTrace(outPS);
        	outPS.println("--------------");
        }

        return outStream.toString();
	}
}
