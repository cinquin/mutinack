package uk.org.cinquin.mutinack.misc_util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

public class LineSampler {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		/*try(FileReader fr = new FileReader(new File(args[0]));
				BufferedReader br = new BufferedReader(fr);
				Stream<String> lines = br.lines()) {
			lines.forEach(action);*/
		final long interval = Long.valueOf(args[0]);
		final long chunkLength = Long.valueOf(args[1]);

		long timeSinceLast = 0;

		long leftToPrintInCurrentChunk = 0;
		long nextChunkStart = 0;
		long currentLine = 0;
		@SuppressWarnings("resource")
		final Scanner input = new Scanner(System.in);
		while (input.hasNextLine()) {
			final String line = input.nextLine();
			final long currentTime = System.currentTimeMillis();
			if (((interval > 0) && currentLine == nextChunkStart) || ((interval < 0) && currentTime > (timeSinceLast + (-interval) * 1_000))) {
				leftToPrintInCurrentChunk = chunkLength;
				if (interval < 0) {
					timeSinceLast = currentTime;
				} else
					nextChunkStart += interval;
				System.out.println("Line " + currentLine + ":");
			}
			if (leftToPrintInCurrentChunk > 0) {
				System.out.println(line);
				leftToPrintInCurrentChunk--;
				if (leftToPrintInCurrentChunk == 0) {
					System.out.println("-----------------------------");
				}
			}
			currentLine++;
		}
	}
}
