package co.lookify.ex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import co.lookify.link.Flag;

public class ScoreVisitor {

	private static final List<String> TAGS_TO_SCORE = Arrays.asList("section,h2,h3,h4,h5,h6,p,td,pre".split(","));

	private static final List<String> DIV_TO_P_ELEMS = Arrays
			.asList(new String[] { "a", "blockquote", "dl", "div", "img", "ol", "p", "pre", "table", "ul", "select" });

	private static final Pattern HAS_CONTENT = Pattern.compile("\\S$");

	private static final Pattern AUTHOR = Pattern.compile("byline|author|dateline|writtenby|p-author",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern UNLIKELY_CANDIDATES = Pattern.compile(
			"banner|breadcrumbs|combx|comment|community|cover-wrap|disqus|extra|foot|header|legends|menu|modal|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern OK_MAYBE_ITS_CANDIDATE = Pattern.compile("and|article|body|column|main|shadow",
			Pattern.CASE_INSENSITIVE);

	private static final int ANCESTOR_COUNT = 3;

	private Map<Element, Double> candidates;

	// private StringBuilder text;
	// private LinkedList<Integer> textLength;

	private LinkedList<Element> ancestors;

	private String author;

	private final Flag flag;

	private final Score score;

	public ScoreVisitor(final Score score, final Flag flag) {
		this.score = Objects.requireNonNull(score);
		this.flag = Objects.requireNonNull(flag);
		candidates = new HashMap<>();
		// text = new StringBuilder();
		// textLength = new LinkedList<>();
		ancestors = new LinkedList<>();
	}

	public void traverse(final Node root) {
		Node node = root;
		while (node != null) {
			head(node);
			if (node.childNodeSize() > 0) {
				node = node.childNode(0);
			} else {
				node = tail(node);
				if (node == null || node == root) {
					break;
				}
			}
		}
	}

	// private Node nextEnd(Node node) {
	// Node next = null;
	// while (node.nextSibling() == null) {
	// next = tail(node);
	// if (next == null) {
	// node = node.parent();
	// } else {
	// node = next;
	// }
	// }
	// return node;
	// }

	private void head(Node node) {
		if (node instanceof Element) {
			ancestors.add((Element) node);
		}
	}

	private Node tail(Node node) {
		Node next = null;
		if (node instanceof TextNode) {
			final TextNode textNode = (TextNode) node;
			final String textInNode = textNode.text().trim();
			if (textInNode.length() == 0) {
				next = next(textNode);
				textNode.remove();
			}
			// else {
			// textLength.add(text.length());
			// text.append(textInNode);
			// }
		} else if (node instanceof Element) {
			ancestors.pollLast();

			final Element el = (Element) node;
			final String tag = el.tagName();

			final boolean stripUnlikelyCandidates = flag.isActive(Flag.STRIP_UNLIKELYS);

			final String matchString = el.className() + " " + el.id();
			final String text = el.text();
			if (text.length() == 0) {
				next = next(node);
				node.remove();
				return next;
			} else if (isAuthor(el, matchString, text)) {
				author = text;
				next = next(node);
				node.remove();
			} else if (stripUnlikelyCandidates && UNLIKELY_CANDIDATES.matcher(matchString).find()
					&& !OK_MAYBE_ITS_CANDIDATE.matcher(matchString).find() && !"body".equals(tag) && !"a".equals(tag)) {
				next = next(node);
				node.remove();
			} else if (ancestors.size() >= ANCESTOR_COUNT && text.length() > 25) {
				if (TAGS_TO_SCORE.contains(tag)) {
					addAndScore(el, text);
				} else if ("div".equals(tag)) {
					final Elements childs = el.children();
					if (hasSinglePInsideElement(el, childs)) {
						Element child = childs.first();
						node.replaceWith(child);
					} else if (!hasChildBlockElement(childs)) {
						el.tagName("p");
						addAndScore(el, text);
					} else {
						// TODO experimental
					}
				}
			}
			// decrease text
			// if (!textLength.isEmpty()) {
			// final int lastLength = textLength.pollLast();
			// text.setLength(lastLength);
			// }
		}
		if (next == null) {
			return next(node);
		}
		return next;
	}

	private Node next(Node node) {
		if (node == null) {
			return null;
		}
		if (node.nextSibling() == null) {
			node = tail(node.parent());
			return node;
		}
		return node.nextSibling();
	}

	private void addAndScore(final Element el, final String text) {
		// Add the paragraph itself and points for any commas within this
		// paragraph.
		int contentScore = 1 + text.toString().split(",").length;

		// For every 100 characters in this paragraph, add another point. Up to
		// 3 points.
		contentScore += Math.min(Math.floor(text.length() / 100), 3);

		int level = 0;
		for (int i = ancestors.size() - 1; i >= ancestors.size() - ANCESTOR_COUNT; i--) {
			Element ancesor = ancestors.get(i);
			Double points = candidates.get(ancesor);
			if (points == null) {
				points = score.initializeNode(ancesor, 0);
			}
			if (level == 1) {
				points += contentScore / 2.0;
			} else if (level == 0) {
				points += contentScore;
			} else {
				points += contentScore / (level * 3.0);
			}
			candidates.put(ancesor, points);
			level++;
		}
	}

	public Map<Element, Double> getCandidates() {
		return candidates;
	}

	private boolean isAuthor(final Element el, final String matchString, final String text) {
		final String rel = el.attr("rel");
		return ("author".equals(rel) || AUTHOR.matcher(matchString).find()) && text.length() < 100;
	}

	private boolean hasSinglePInsideElement(Element element, Elements childs) {
		if (childs.size() == 1 && "p".equals(childs.first().tagName())) {
			for (Node child : element.childNodes()) {
				if (child instanceof TextNode) {
					if (HAS_CONTENT.matcher(((TextNode) child).getWholeText()).matches()) {
						return false;
					}
				}
			}
			return true;
		}

		return false;
	}

	private boolean hasChildBlockElement(Elements childes) {
		for (Element child : childes) {
			if (DIV_TO_P_ELEMS.contains(child.tagName()) || hasChildBlockElement(child.children())) {
				return true;
			}
		}
		return false;
	}

	private boolean isElementWithoutContent(Element el) {
		if (!el.hasText()) {
			Elements child = el.children();
			return child.size() == 0
					|| (child.size() == el.getElementsByTag("br").size() + el.getElementsByTag("hr").size());
		}

		return false;
	}

}
