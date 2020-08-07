package com.nerdynick.config_diff_tool;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.google.common.base.Splitter;
import com.nerdynick.commons.configuration.utils.FileConfigUtils;

import org.apache.commons.configuration2.Configuration;

public class ConfigDiff {
	protected static final Splitter ConfigSplitter =Splitter.on(',').omitEmptyStrings().trimResults();
	
	private final Set<String> allKeysUnique = new HashSet<>();
	private final List<String> allKeysSorted = new LinkedList<>();
	private final Configuration left;
	private final Configuration right;
	private List<String> _left;
	private List<String> _right;
	
	
	public ConfigDiff(final String leftConfig, final String rightConfig) throws IOException {
		this(ConfigSplitter.splitToList(leftConfig), ConfigSplitter.splitToList(rightConfig));
	}

	public ConfigDiff(final List<String> left, final List<String> right) throws IOException {
		this(left.toArray(new String[left.size()]), right.toArray(new String[right.size()]));
	}

	public ConfigDiff(final String[] left, final String[] right) throws IOException {
		this(FileConfigUtils.newFileConfig(left), FileConfigUtils.newFileConfig(right));
	}

	public ConfigDiff(final Configuration left, final Configuration right) {
		this.left = left;
		this.right = right;

		//Generate a collection of all unique configuration keys
		final Iterator<String> l = left.getKeys();
		while (l.hasNext()) {
			allKeysUnique.add(l.next());
		}

		final Iterator<String> r = right.getKeys();
		while (r.hasNext()) {
			allKeysUnique.add(r.next());
		}

		//Sort all the unique keys alphabetically
		this.allKeysSorted.addAll(this.allKeysUnique);
		Collections.sort(this.allKeysSorted);
	}

	/**
	 * Returned the Left side configs, formated as %s=%s.
	 * 
	 * @return
	 */
	public List<String> getLeft() {
		if (this._left == null) {
			this._left = getProps(left);
		}
		return _left;
	}

	/**
	 * Returned the Right side configs, formated as %s=%s.
	 * 
	 * @return
	 */
	public List<String> getRight() {
		if (this._right == null) {
			this._right = getProps(right);
		}
		return _right;
	}

	private List<String> getProps(final Configuration conf) {
		final List<String> lines = new LinkedList<>();
		for (final String key : this.allKeysSorted) {
			lines.add(String.format("%s=%s", key, conf.getString(key)));
		}
		return lines;
	}

	public Patch<String> generatePatch() throws DiffException {
		return DiffUtils.diff(this.getLeft(), this.getRight());
	}

	public void writeUnifiedDif(Writer stream, String leftHeader, String rightHeader, boolean includeColor) throws IOException, DiffException{
		stream.append("--- " + leftHeader);
		stream.append(System.lineSeparator());
		stream.append("+++ " + rightHeader);
		stream.append(System.lineSeparator());
		stream.append(System.lineSeparator());

		String oldLineStart = includeColor ? "\u001b[31m" : "";
		String oldLineEnd = includeColor ? "\u001b[0m" : "";
		String newLineStart = includeColor ? "\u001b[33m" : "";
		String newLineEnd = includeColor ? "\u001b[0m" : "";

		this.consumeDiffReadable(b -> {
			return b ? oldLineStart : oldLineEnd;
		}, b -> {
			return b ? newLineStart : newLineEnd;
		}, r -> {
			try {
				if (!r.getOldLine().equals(r.getNewLine())) {
					stream.append("-" + r.getOldLine());
					stream.append(System.lineSeparator());
					stream.append("+" + r.getNewLine());
					stream.append(System.lineSeparator());
				}
			} catch (final IOException e) {
				e.printStackTrace();
			}
		});
	}

	public void consumeDiff(final Consumer<AbstractDelta<String>> consumer) throws DiffException {
		final Patch<String> patch = this.generatePatch();
		patch.getDeltas().forEach(consumer);

	}

	public void consumeDiffReadable(final Function<Boolean, String> oldTag, final Function<Boolean, String> newTag, final Consumer<DiffRow> consumer) throws DiffException {
		final DiffRowGenerator generator = DiffRowGenerator.create()
			.showInlineDiffs(true)
            .inlineDiffByWord(true)
            .oldTag(oldTag)
			.newTag(newTag)
            .build();
		
		final List<DiffRow> rows = generator.generateDiffRows(this.getLeft(), this.getRight());
		
		rows.forEach(consumer);
	}
}
