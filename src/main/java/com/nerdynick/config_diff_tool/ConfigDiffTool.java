package com.nerdynick.config_diff_tool;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
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
import com.nerdynick.commons.configuration.utils.FileConfigUtils;

public class ConfigDiffTool {
	protected static final Splitter ConfigSplitter =Splitter.on(',').omitEmptyStrings().trimResults();
	
	protected static final Options options = new Options();
	static {
		options.addRequiredOption("l", "left", true, "Comma Seperated of configs on the left side of the diff");
		options.addRequiredOption("r", "right", true, "Comma Seperated of configs on the right side of the diff");
		options.addOption("o", "out", true, "Optional: Output file to also write too");
		options.addOption("h", "help", false, "Print Help");
	}
	
	private final Set<String> allKeysUnique = new HashSet<>();
	private final List<String> allKeysSorted = new LinkedList<>();
	private final Configuration left;
	private final Configuration right;
	private List<String> _left;
	private List<String> _right;
	
	
	public ConfigDiffTool(Configuration left, Configuration right) {
		this.left = left;
		this.right = right;
		
		final Iterator<String> l = left.getKeys();
		while(l.hasNext()) {
			allKeysUnique.add(l.next());
		}
		
		final Iterator<String> r = right.getKeys();
		while(r.hasNext()) {
			allKeysUnique.add(r.next());
		}
		
		this.allKeysSorted.addAll(this.allKeysUnique);
		Collections.sort(this.allKeysSorted);
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
		final List<String> lines = new LinkedList<>();
		for(String key: this.allKeysSorted) {
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
				stream.append("<< left ("+line.getOptionValue('l')+")");
				stream.newLine();
				stream.append(">> right ("+line.getOptionValue('r')+")");
				stream.newLine();
				stream.newLine();
				
				diffTool.consumeDiffReadable(
					b-> {
						return b ? "<<" : ">>";
					},
					b -> {
						return b ? ">>" : "<<";
					},r->{
						if(!r.getOldLine().equals(r.getNewLine())) {
							try {
								stream.append("<< " + r.getOldLine());
								stream.newLine();
								stream.append(">> " + r.getNewLine());
								stream.newLine();
								stream.newLine();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				);
				stream.close();
			}
			
			System.out.println("<<\u001b[31mleft ("+line.getOptionValue('l')+")\u001b[0m");
			System.out.println(">>\u001b[33mright ("+line.getOptionValue('r')+")\u001b[0m");
			System.out.println("---------------------");
			diffTool.consumeDiffReadable(
				b-> {
					return b ? "\u001b[31m" : "\u001b[0m";
				},
				b -> {
					return b ? "\u001b[33m" : "\u001b[0m";
				},r->{
					if(!r.getOldLine().equals(r.getNewLine())) {
						System.out.println("<<" + r.getOldLine());
						System.out.println(">>" + r.getNewLine());
					}
				}
			);
		} catch (ParseException exp) {
			// oops, something went wrong
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
			printHelp();
			System.exit(1);
		}
	}

}
