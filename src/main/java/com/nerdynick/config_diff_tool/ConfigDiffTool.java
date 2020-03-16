package com.nerdynick.config_diff_tool;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration2.Configuration;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.nerdynick.commons.configuration.utils.FileConfigUtils;

public class ConfigDiffTool {
	protected static final Splitter ConfigSplitter =Splitter.on(',').omitEmptyStrings().trimResults();
	
	protected static final Options options = new Options();
	static {
		options.addRequiredOption("l", "left", true, "Comma Seperated of configs on the left side of the diff");
		options.addRequiredOption("r", "right", true, "Comma Seperated of configs on the right side of the diff");
		options.addOption("o", "out", true, "Output File to write to");
		options.addOption("h", "help", false, "Print Help");
	}
	
	private final Configuration left;
	private final Configuration right;
	private List<String> _left;
	private List<String> _right;
	
	
	public ConfigDiffTool(Configuration left, Configuration right) {
		this.left = left;
		this.right = right;
	}
	
	public List<String> getLeft(){
		if(this._left == null) {
			this._left = getProps(left);
		}
		return _left;
	}
	public List<String> getRight(){
		if(this._right == null) {
			this._right = getProps(right);
		}
		return _right;
	}
	private List<String> getProps(Configuration conf){
		ArrayList<String> keys = Lists.newArrayList(conf.getKeys());
		Collections.sort(keys);
		
		final List<String> lines = new LinkedList<>();
		for(String key: keys) {
			lines.add(String.format("%s= %s", key, conf.getString(key)));
		}
		
		return lines;
	}
	
	public Patch<String> generatePatch() throws DiffException{
		return DiffUtils.diff(this.getLeft(), this.getRight());
	}
	
	public void consumeDiff(Consumer<AbstractDelta<String>> consumer) throws DiffException {
		final Patch<String> patch = this.generatePatch();
		patch.getDeltas().forEach(consumer);
		
	}
	public void consumeDiffReadable(Function<Boolean, String> oldTag, Function<Boolean, String> newTag, Consumer<DiffRow> consumer) throws DiffException {
		final DiffRowGenerator generator = DiffRowGenerator.create()
			.showInlineDiffs(true)
            .inlineDiffByWord(true)
            .oldTag(oldTag)
            .newTag(newTag)
            .build();
		
		final List<DiffRow> rows = generator.generateDiffRows(this.getLeft(), this.getRight());
		
		rows.forEach(consumer);
	}
	
	public static ConfigDiffTool build(String left, String right) throws IOException {
		final List<String> _left = ConfigSplitter.splitToList(left);
		final List<String> _right = ConfigSplitter.splitToList(right);
		return build(_left, _right);
	}
	
	public static ConfigDiffTool build(List<String> left, List<String> right) throws IOException {
		return new ConfigDiffTool(
			FileConfigUtils.newFileConfig(left.toArray(new String[left.size()])),
			FileConfigUtils.newFileConfig(right.toArray(new String[right.size()]))
		);
	}
	
	protected static void printHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(ConfigDiffTool.class.getSimpleName(), options);
	}
	
	

	public static void main(String[] args) throws Exception {
		final CommandLineParser parser = new DefaultParser();
		try {
			// parse the command line arguments
			final CommandLine line = parser.parse(options, args);
			if (line.hasOption('h')) {
				printHelp();
				System.exit(0);
			}
			
			ConfigDiffTool diffTool = ConfigDiffTool.build(line.getOptionValue('l'), line.getOptionValue('r'));
			
			final String outputFile = line.getOptionValue('o');
			if(!Strings.isNullOrEmpty(outputFile)) {
				final Path output = Paths.get(outputFile);
				Files.deleteIfExists(output);
				final BufferedWriter stream = Files.newBufferedWriter(output);
				stream.append("|left|right|");
				stream.newLine();
				stream.append("|----|-----|");
				stream.newLine();
				
				diffTool.consumeDiffReadable(
					b-> {
						return "~";
					},
					b -> {
						return "**";
					},
					r->{
						try {
							stream.append("|" + r.getOldLine() + "|" + r.getNewLine() + "|");
							stream.newLine();
						} catch (IOException e) {
							System.err.println(e.toString());
						}
					}
				);
				stream.close();
			} else {
				System.out.println("-\u001b[31mleft\u001b[0m");
				System.out.println("+\u001b[33mright\u001b[0m");
				System.out.println("---------------------");
				diffTool.consumeDiffReadable(
					b-> {
						return b ? "\u001b[31m" : "\u001b[0m";
					},
					b -> {
						return b ? "\u001b[33m" : "\u001b[0m";
					},r->{
						System.out.println("-" + r.getOldLine());
						System.out.println("+" + r.getNewLine());
					}
				);
			}
		} catch (ParseException exp) {
			// oops, something went wrong
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
			printHelp();
			System.exit(1);
		}
	}

}
