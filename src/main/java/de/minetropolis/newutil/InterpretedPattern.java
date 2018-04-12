package de.minetropolis.newutil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.minetropolis.groups.*;

public class InterpretedPattern {
	private Map<String, String> endStrings = new HashMap<String, String>() {
		private static final long serialVersionUID = 1L;
		{
			put(";?(", ")");
			put(";>(", ")<;");
		};
	};

	public List<Group> groups = new ArrayList<>();
	public String pattern, target, assertion;

	public InterpretedPattern(String pattern) {
		this.pattern = pattern;
	}

	public InterpretedPattern(String pattern, String target, String assertion) {
		this.pattern = pattern;
		this.target = target;
		this.assertion = assertion;
	}

	public InterpretedPattern fill(String target, String assertion) {
		this.target = target;
		this.assertion = assertion;
		return this;
	}

	public InterpretedPattern compile() {
		try {
			generateGroups();
		} catch (UnbalancedBracketException e) {
			e.printStackTrace();
			return null;
		} catch (MalformedAutoconvert e) {
			e.printStackTrace();
			return null;
		}

		groups.forEach(Group::apply);
		buildPattern();
		return this;
	}

	private void generateGroups() throws UnbalancedBracketException, MalformedAutoconvert {
		int i = 0;
		int start = 0;
		while (i < pattern.length()) {
			int special = (pattern.indexOf(";?(", i) == -1) ? Integer.MAX_VALUE : pattern.indexOf(";?(", i);
			int autoconvert = (pattern.indexOf(";>(", i) == -1) ? Integer.MAX_VALUE : pattern.indexOf(";>(", i);
			if (Math.min(special, autoconvert) == Integer.MAX_VALUE) {
				if(start < pattern.length())
				groups.add(new Escaped(pattern.substring(start, pattern.length())));
				return;
			}
			i = Math.min(special, autoconvert);
			if (Statics.isEscaped(pattern, i)) {
				i -= removeSlashes(i) - 1;
				continue;
			}
			i -= removeSlashes(i);

			if (start != i)
				groups.add(new Escaped(pattern.substring(start, i)));

			if (pattern.charAt(i + 1) == '>')
				i = processAutoconvert(i, endStrings.get(pattern.substring(i, i + 3)));
			else
				i = processSpecial(i);
			start = i;
		}
	}

	private void buildPattern() {
		pattern = groups.stream().filter(group -> group.getType() != GroupType.NORMAL).map(group -> group.getContent()).collect(Collectors.joining());
	}

	private int processSpecial(int pos) throws UnbalancedBracketException {
		int depth = 0;
		List<List<Integer>> brackets = new ArrayList<>();
		brackets.add(new ArrayList<>());
		int i;
		for (i = pos + 3; i < pattern.length(); i++) {
			if (pattern.charAt(i) == '(' && !Statics.isEscaped(pattern, i)) {
				brackets.get(depth).add(i);
				depth++;
				if (depth > brackets.size() - 1)
					brackets.add(new ArrayList<>());
			}
			if (pattern.charAt(i) == ')' && !Statics.isEscaped(pattern, i)) {
				depth--;
				if (depth == -1)
					break;
				brackets.get(depth).add(i + 1);
			}

		}
		if (depth > -1)
			throw new UnbalancedBracketException();
		if (pattern.substring(pos).startsWith(";?(?:"))
			groups.add(new Noncapturing(pattern.substring(pos, i + 1)));
		else
			groups.add(new Special(pattern.substring(pos, i + 1)));
		makeNormals(brackets);
		return i + 1;
	}

	private void makeNormals(List<List<Integer>> brackets) {
		for (List<Integer> bracket : brackets) {
			for (int i = 0; i < bracket.size(); i += 2) {
				if (pattern.substring(bracket.get(i), bracket.get(i) + 3).equals("(?:")) {
					bracket.remove(i);
					bracket.remove(i);
				}
			}
		}
		for (int i = 0; i < brackets.size(); i++) {
			while (brackets.get(i).size() > 2) {
				brackets.add(new ArrayList<>());
				int last = brackets.size() - 1;
				brackets.get(last).add(brackets.get(i).get(2));
				brackets.get(last).add(brackets.get(i).get(3));
				brackets.get(i).remove(2);
				brackets.get(i).remove(2);
			}
		}
		brackets = brackets.stream().filter(bracket -> !bracket.isEmpty()).collect(Collectors.toList());
		brackets.sort(new Comparator<List<Integer>>() {
			@Override
			public int compare(List<Integer> o1, List<Integer> o2) {
				return o1.get(0).compareTo(o2.get(0));
			}
		});
		brackets.forEach(bracket -> groups.add(new Normal(pattern.substring(bracket.get(0), bracket.get(1)))));
	}

	private int processAutoconvert(int pos, String endString) throws MalformedAutoconvert {
		Matcher matcher = Pattern.compile(";>(?:\\((?:\".+?\"\\|)+?\".+?\"\\)\\|)*?\\((?:\".+?\"\\|)+?\".+?\"\\)<;").matcher(pattern.substring(pos));
		if (!matcher.find())
			throw new MalformedAutoconvert();

		groups.add(new Autoconvert(matcher.group()));
		return pos + matcher.end();
	}

	private int removeSlashes(int pos) {
		String removed = Statics.removeSlashes(pattern, pos);
		int i = pattern.length() - removed.length();
		pattern = removed;
		return i;
	}

}

class MalformedAutoconvert extends Exception {
	private static final long serialVersionUID = 2574787183605527217L;

}

class UnbalancedBracketException extends Exception {
	private static final long serialVersionUID = -5565000941909376422L;

}