package com.nerdynick.config_diff_tool;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.base.Strings;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ConfigDiffTool {
	protected static final Options options = new Options();
	static {
		options.addRequiredOption("l", "left", true, "Comma Seperated of configs on the left side of the diff");
		options.addRequiredOption("r", "right", true, "Comma Seperated of configs on the right side of the diff");
		options.addOption("o", "out", true, "Optional: Output file to also write too");
		options.addOption("i", "include", false, "Include unchanged configs in output");
		options.addOption("h", "help", false, "Print Help");
	}
	
	protected static void printHelp() {
		final HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(ConfigDiffTool.class.getSimpleName(), options);
	}

	public static void main(final String[] args) throws Exception {
		final CommandLineParser parser = new DefaultParser();
		try {
			// parse the command line arguments
			final CommandLine line = parser.parse(options, args);
			if (line.hasOption('h')) {
				printHelp();
				System.exit(0);
			}

			final ConfigDiff diffTool = new ConfigDiff(line.getOptionValue('l'), line.getOptionValue('r'));

			final String outputFile = line.getOptionValue('o');
			if (!Strings.isNullOrEmpty(outputFile)) {
				final Path output = Paths.get(outputFile);
				Files.deleteIfExists(output);

				final BufferedWriter stream = Files.newBufferedWriter(output);
				diffTool.writeUnifiedDif(stream, line.getOptionValue('l'), line.getOptionValue('r'), false);
				stream.close();
			}

			final Writer stdStream = new OutputStreamWriter(System.out);
			diffTool.writeUnifiedDif(stdStream, line.getOptionValue('l'), line.getOptionValue('r'), true);
			stdStream.flush();
		} catch (final ParseException exp) {
			// oops, something went wrong
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
			printHelp();
			System.exit(1);
		}
	}
}