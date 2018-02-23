package co.lookify.ex;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import co.lookify.link.CandidateScore;
import co.lookify.link.Flag;
import co.lookify.structure.Person;
import co.lookify.structure.Social;

public class ScoreVisitor2 {

	public static final DateTimeFormatter DATE_TIME;
	static {
		DATE_TIME = new DateTimeFormatterBuilder().parseCaseInsensitive().append(DateTimeFormatter.ISO_LOCAL_DATE)
				.appendLiteral(' ').append(DateTimeFormatter.ISO_LOCAL_TIME).toFormatter();
	}

	private static final List<String> TAGS_TO_SCORE = Arrays.asList("section,h2,h3,h4,h5,h6,p,td,pre".split(","));

	private static final List<String> DIV_TO_P_ELEMS = Arrays
			.asList(new String[] { "a", "blockquote", "dl", "div", "img", "ol", "p", "pre", "table", "ul", "select" });

	private static final Pattern HAS_CONTENT = Pattern.compile("\\S$");

	private static final Pattern TIMESTAMP = Pattern.compile("timestamp");

	private static final Pattern AUTHOR = Pattern.compile("byline|author|dateline|writtenby|p-author",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern UNLIKELY_CANDIDATES = Pattern.compile(
			"banner|breadcrumbs|combx|comment|community|cover-wrap|disqus|extra|foot|header|legends|menu|modal|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern OK_MAYBE_ITS_CANDIDATE = Pattern.compile("and|article|body|column|main|shadow",
			Pattern.CASE_INSENSITIVE);

	private static final int ANCESTOR_COUNT = 3;

	private static final List<String> HEADERS = Arrays.asList("h1,h2,h3,h4,h5,h6".split(","));

	private Map<Element, CandidateScore> candidates;

	// private StringBuilder text;
	// private LinkedList<Integer> textLength;

	private LinkedList<Element> ancestors;

	private final Flag flag;

	private final Score score;

	private final URI uri;

	private final Map<Element, Element> unlikelyCandidates;

	private LinkedList<State> states;

	private Person author;

	private Date date;

	private String direction;

	private String title;

	private static class State {
		Person author;

		Date date;

		String direction;

		String title;
	}

	public ScoreVisitor2(final URI uri, final Score score, final Flag flag) {
		this.uri = Objects.requireNonNull(uri);
		this.score = Objects.requireNonNull(score);
		this.flag = Objects.requireNonNull(flag);
		candidates = new HashMap<>();
		// text = new StringBuilder();
		// textLength = new LinkedList<>();
		ancestors = new LinkedList<>();
		unlikelyCandidates = new HashMap<>();

		states = new LinkedList<>();
	}

	public void traverse(final Node root) {
		Node node = root;
		while (node != null) {
			node = head(node);
			if (node == null) {
				break;
			} else if (node.childNodeSize() > 0) {
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

	private Node removeAndGetNext(Node node) {
		final Node next = next(node);
		node.remove();
		return head(next);
	}

	private Node head(final Node node) {
		if (node == null) {
			return null;
		}

		Node next = node;
		if (node instanceof Element) {
			final Element el = (Element) node;
			final String tag = el.tagName();
			final String text = el.text();
			final String matchString = el.className() + " " + el.id();
			direction = el.attr("dir");
			final boolean stripUnlikelyCandidates = flag.isActive(Flag.STRIP_UNLIKELYS);

			if (HEADERS.contains(tag)) {
				title = el.text();
			}

			if (text.length() == 0) {
				return removeAndGetNext(node);
			} else if (isAuthor(el, matchString, text)) {
				author = extractAuthor(el);
				date = extractDate(el);

				// if (date != null) {
				// dates.add(date);
				// }

				return removeAndGetNext(node);
			} else if ("time".equals(tag) && TIMESTAMP.matcher(matchString).find()) {
				date = extractDate(el);

				// if (date != null) {
				// dates.add(date);
				// }

			} else if (stripUnlikelyCandidates && UNLIKELY_CANDIDATES.matcher(matchString).find()
					&& !OK_MAYBE_ITS_CANDIDATE.matcher(matchString).find() && !"body".equals(tag) && !"a".equals(tag)) {
				next = next(node);

				Element parent = el.parent();
				unlikelyCandidates.put(el, parent);

				node.remove();
				return head(next);
			} else if (ancestors.size() >= ANCESTOR_COUNT && text.length() > 25) {
				if (TAGS_TO_SCORE.contains(tag)) {
					addAndScore(el, text);
					// next = next(node);
					// return head(next);
				} else if ("div".equals(tag)) {
					final Elements childs = el.children();
					if (hasSinglePInsideElement(el, childs)) {
						Element child = childs.first();
						node.replaceWith(child);
					} else if (!hasChildBlockElement(childs)) {
						el.tagName("p");

						addAndScore(el, text);

						next = next(node);
						return head(next);
					} else {
						// TODO experimental
					}
				}
			}
			ancestors.add((Element) node);
		} else if (node instanceof TextNode) {
			final TextNode textNode = (TextNode) node;
			final String textInNode = textNode.text().trim();
			next = next(textNode);
			if (textInNode.length() == 0) {
				textNode.remove();
			}
			return head(next);
		} else if (node instanceof Comment) {
			next = next(node);
			node.remove();
			return head(next);
		}
		return next;
	}

	private String getHostFromUrl(String url) {
		if (url.startsWith("http://")) {
			return url.substring("http://".length(), url.indexOf('/', "http://".length()));
		} else if (url.startsWith("https://")) {
			return url.substring("https://".length(), url.indexOf('/', "https://".length()));
		}
		return null;
	}

	private Date extractDate(final Element el) {
		Element node = el;
		Date date = null;
		int depth = 0;
		while (node != null) {
			final String tag = node.tagName();
			if ("time".equals(tag) || TIMESTAMP.matcher(node.className() + " " + node.id()).find()) {

				String datetime = node.attr("datetime");
				if ((datetime == null || datetime.length() == 0) && node.children().size() == 1) {
					datetime = node.text();
				}
				if (datetime.length() > 0) {
					try {
						ZoneId zone = ZoneId.systemDefault();
						if (datetime.contains(":")) {
							LocalDateTime parsedTime = LocalDateTime.parse(datetime, DATE_TIME);
							date = Date.from(parsedTime.atZone(zone).toInstant());
						} else {
							LocalDate parsedDate = LocalDate.parse(datetime);
							date = Date.from(parsedDate.atStartOfDay(zone).toInstant());
						}
						break;
					} catch (Exception e) {
						// no errors
					}
				}
			}

			Elements childs = node.children();
			if (childs.size() > 0) {
				node = childs.first();
				depth++;
			} else {
				while (node.nextElementSibling() == null) {
					node = node.parent();
					depth--;
					if (node == null || depth <= 0) {
						node = null;
						break;
					}
				}
				if (node != null) {
					node = node.nextElementSibling();
				}
			}
		}
		return date;
	}

	private String getOnlyUppercase(int start, String txt) {
		int index = -1;
		int end = -1;
		for (int i = start; i < txt.length(); i++) {
			if (Character.isUpperCase(txt.charAt(i))) {
				if (index == -1) {
					index = i;
				}
				i++;
				while (i < txt.length() && txt.charAt(i) != ' ') {
					i++;
				}
				end = i - 1;
			}
		}
		if (index == -1) {
			return null;
		}
		return txt.substring(index, end);
	}

	private String extractName(final String txt) {
		String fullName = null;
		if (txt.length() > 0) {
			int index = txt.indexOf("by ");
			if (index == 0 || (index > 0 && txt.charAt(index - 1) == ' ')) {
				fullName = getOnlyUppercase(index + "by ".length(), txt);
				if (fullName == null) {
					fullName = txt.substring(index + "by ".length());
				}
			} else {
				index = txt.indexOf('@');
				int end = txt.length();
				if (index == -1) {
					fullName = getOnlyUppercase(0, txt);
				} else {
					end = txt.indexOf(' ', index + 1);
					if (end == -1) {
						end = txt.length();
					}
				}
				if (index != -1) {
					fullName = txt.substring(index, end);
				}
			}
		}
		return fullName;
	}

	private Person extractAuthor(final Element el) {
		Element node = el;
		Person person = null;
		int depth = 0;
		while (node != null) {
			final String tag = node.tagName();
			final Elements childs = node.children();
			if ("a".equals(tag) && node.hasText()) {
				if (person == null) {
					person = new Person();
				}
				final String fullName = extractName(node.text());
				if (fullName != null) {
					String href = URLHelper.toAbsoluteURI(uri, node.attr("href"));
					String type = getHostFromUrl(href);
					char first = fullName.length() > 0 ? fullName.charAt(0) : ' ';

					if (type == null) {
						type = uri.getHost();

						if (first != '@' && person.getFullName() != null) {
							person.setFullName(fullName);
						}
					}

					if (first == '@') {
						person.addSocial(type, fullName.substring(1), href);
					} else {
						person.addSocial(type, fullName, href);
					}
				}
			} else if (childs.size() == 0) {
				final String txt = node.text();
				final String fullName = extractName(txt);
				if (fullName != null) {
					if (person == null) {
						person = new Person();
					}
					person.setFullName(fullName);
				}
			}

			if (childs.size() > 0) {
				node = childs.first();
				depth++;
			} else {
				while (node.nextElementSibling() == null) {
					node = node.parent();
					depth--;
					if (node == null || depth <= 0) {
						node = null;
						break;
					}
				}
				if (node != null) {
					node = node.nextElementSibling();
				}
			}
		}

		if (person != null && person.getFullName() == null && person.getSocials() != null
				&& !person.getSocials().isEmpty()) {
			for (Social social : person.getSocials().values()) {
				if (social.getName().charAt(0) != '@' && social.getName().indexOf(" ") >= 0) {
					person.setFullName(social.getName());
					break;
				}
			}
		}
		return person;
	}

	private Node tail(Node node) {
		if (node instanceof Element) {
			ancestors.pollLast();
		}
		return next(node);
	}

	private Node next(Node node) {
		if (node == null) {
			return null;
		}
		if (node.nextSibling() == null) {

			// load prev state
			final State state = states.removeLast();
			author = author == null ? state.author : author;
			date = date == null ? state.date : date;
			direction = direction == null ? state.direction : direction;
			title = title == null ? state.title : title;

			node = tail(node.parent());
			return node;
		}

		final State prevState = states.getLast();

		// save state
		final State state = new State();
		state.author = author;
		state.date = date;
		state.direction = direction;
		state.title = title;
		states.addLast(state);

		author = prevState.author;
		date = prevState.date;
		direction = prevState.direction;
		title = prevState.title;

		// // save state
		// final State state = new State();
		// state.author = author;
		// state.date = date;
		// state.direction = direction;
		// state.title = title;
		// states.addLast(state);
		// author = null;
		// date = null;
		// direction = null;
		// title = null;

		return node.nextSibling();
	}

	private void addAndScore(final Element el, final String text) {
		// Add the paragraph itself and points for any commas within this
		// paragraph.
		// For every 100 characters in this paragraph, add another point. Up to
		// 3 points.
		final double contentScore = 1 + text.split(",").length + Math.min(Math.floor(text.length() / 100.0), 3.0);

		int level = 0;
		for (int i = ancestors.size() - 1; i >= ancestors.size() - ANCESTOR_COUNT; i--) {
			final Element ancesor = ancestors.get(i);
			if (ancesor.children().size() == 1) {
				if (level == 0) {
					addCandidate(contentScore, el, level);
				}
			} else {
				addCandidate(contentScore, ancesor, level);
			}
			level++;
		}
	}

	private void addCandidate(final double contentScore, final Element ancesor, final int level) {
		CandidateScore candidate = candidates.get(ancesor);
		double points = 0.0;
		if (candidate == null) {
			candidate = new CandidateScore(ancesor, 0);
			points = score.initializeNode(ancesor, 0.0);

			candidates.put(ancesor, candidate);
		} else {
			points = candidate.getScore();
		}
		if (level == 0) {
			points += contentScore;
		} else if (level == 1) {
			points += contentScore / 2.0;
		} else {
			points += contentScore / (level * 3.0);
		}

		candidate.setScore(points);

		if (date != null && candidate.getDate() == null) {
			candidate.setDate(date);
		}

		if (author != null && candidate.getAuthor() == null) {
			candidate.setAuthor(author);
		}

		if (direction != null && candidate.getDirection() == null) {
			candidate.setDirection(direction);
		}

		if (title != null && candidate.getTitle() == null) {
			candidate.setTitle(title);
		}
	}

	public Map<Element, CandidateScore> getCandidates() {
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

	public void clear() {
		// candidates.clear();
		ancestors.clear();
		author = null;
		date = null;
	}

	public void scoreUnlikes() {
		for (Map.Entry<Element, Element> entry : unlikelyCandidates.entrySet()) {
			final Element el = entry.getKey();
			Element parent = entry.getValue();
			parent.appendChild(el); // TODO add right position
			author = null;
			date = null;
			ancestors.clear();

			for (int i = 0; i < 3 && parent != null; i++) {
				ancestors.addFirst(parent);
				parent = parent.parent();
			}
			traverse(el);
		}
	}

	// private boolean isElementWithoutContent(Element el) {
	// if (!el.hasText()) {
	// Elements child = el.children();
	// return child.size() == 0
	// || (child.size() == el.getElementsByTag("br").size() +
	// el.getElementsByTag("hr").size());
	// }
	//
	// return false;
	// }

}
