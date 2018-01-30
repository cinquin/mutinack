package uk.org.cinquin.mutinack.misc_util;

public class SingleTimePrinter extends SingleTimeAction<String> {

	public SingleTimePrinter() {
		super(s -> Util.printUserMustSeeMessage(s));
	}

}
