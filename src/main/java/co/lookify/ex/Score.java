package co.lookify.ex;

import java.util.Objects;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import co.lookify.link.Flag;

public class Score {

	private static final Pattern POSITIVE = Pattern.compile(
			"article|body|content|entry|hentry|h-entry|main|page|pagination|post|text|blog|story",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern NEGATIVE = Pattern.compile(
			"hidden|^hid$| hid$| hid |^hid |banner|combx|comment|com-|contact|foot|footer|footnote|masthead|media|meta|modal|outbrain|promo|related|scroll|share|shoutbox|sidebar|skyscraper|sponsor|shopping|tags|tool|widget",
			Pattern.CASE_INSENSITIVE);

	// private static final Pattern NORMALIZE = Pattern.compile("\\s{2,}",
	// Pattern.MULTILINE);

	private final Flag flag;

	public Score(final Flag flag) {
		this.flag = Objects.requireNonNull(flag);
	}

	public double initializeNode(final Element node, double score) {
		final String tag = node.tagName();
		switch (tag) {
		case "div":
			score += 5;
			break;

		case "pre":
		case "td":
		case "blockquote":
			score += 3;
			break;

		case "address":
		case "ol":
		case "ul":
		case "dl":
		case "dd":
		case "dt":
		case "li":
		case "form":
			score -= 3;
			break;

		case "h1":
		case "h2":
		case "h3":
		case "h4":
		case "h5":
		case "h6":
		case "th":
			score -= 5;
			break;
		}
		score += getClassWeight(node);
		score = score * (1 - getLinkDensity(node));
		return score;
	}

	// public String getInnerText(Element element, boolean normalizeSpaces) {
	// String textContent = element.text().trim();
	// if (normalizeSpaces) {
	// return NORMALIZE.matcher(textContent).replaceAll(" ");
	// }
	// return textContent;
	// }

	public double getLinkDensity(Element element) {
		int textLength = element.text().length();

		// TODO should check for length, assume candidate should have length
		// more then 0
		// if (textLength == 0) {
		// return 0;
		// }

		int linkLength = 0;
		Elements links = element.getElementsByTag("a");
		for (Element link : links) {
			linkLength += link.text().length();
		}
		return (double) linkLength / (double) textLength;
	}

	public double getClassWeight(Element node) {
		if (!flag.isActive(Flag.WEIGHT_CLASSES)) {
			return 0;
		}
		int weight = 0;

		String className = node.className();
		if (className != null && className.trim().length() > 0) {
			if (NEGATIVE.matcher(className).find()) {
				weight -= 25;
			}

			if (POSITIVE.matcher(className).find()) {
				weight += 25;
			}
		}
		String id = node.id();
		if (id != null && id.trim().length() > 0) {
			if (NEGATIVE.matcher(id).find()) {
				weight -= 25;
			}

			if (POSITIVE.matcher(id).find()) {
				weight += 25;
			}
		}
		return weight;
	}
}
