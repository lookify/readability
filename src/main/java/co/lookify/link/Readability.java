package co.lookify.link;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import co.lookify.structure.Block;
import co.lookify.structure.MetaData;
import co.lookify.structure.Page;

public class Readability {

	private static final Pattern WHITESPACE = Pattern.compile("^\\s*$");

	private static final Pattern BYLINE = Pattern.compile("byline|author|dateline|writtenby|p-author",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern META_NAME = Pattern.compile("\\s*((twitter)\\s*:\\s*)?(description|title)\\s*$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern META_PROPERTY = Pattern.compile("\\s*og\\s*:\\s*(description|title)\\s*$",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern UNLIKELY_CANDIDATES = Pattern.compile(
			"banner|breadcrumbs|combx|comment|community|cover-wrap|disqus|extra|foot|header|legends|menu|modal|related|remark|replies|rss|shoutbox|sidebar|skyscraper|social|sponsor|supplemental|ad-break|agegate|pagination|pager|popup|yom-remote",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern OK_MAYBE_ITS_CANDIDATE = Pattern.compile("and|article|body|column|main|shadow",
			Pattern.CASE_INSENSITIVE);

//	private static final Pattern NORMALIZE = Pattern.compile("\\s{2,}", Pattern.MULTILINE);

	private static final Pattern POSITIVE = Pattern.compile(
			"article|body|content|entry|hentry|h-entry|main|page|pagination|post|text|blog|story",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern NEGATIVE = Pattern.compile(
			"hidden|^hid$| hid$| hid |^hid |banner|combx|comment|com-|contact|foot|footer|footnote|masthead|media|meta|modal|outbrain|promo|related|scroll|share|shoutbox|sidebar|skyscraper|sponsor|shopping|tags|tool|widget",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern VIDEOS = Pattern.compile(
			"\\/\\/(www\\.)?(dailymotion|youtube|youtube-nocookie|player\\.vimeo)\\.com", Pattern.CASE_INSENSITIVE);

	private static final Pattern HAS_CONTENT = Pattern.compile("\\S$");
	//
	// private final static Pattern[] REGEXPS = new Pattern[] {
	// /* unlikelyCandidates */
	// UNLIKELY_CANDIDATES,
	// /* okMaybeItsACandidate */
	// OK_MAYBE_ITS_CANDIDATE,
	// /* positive */
	// POSITIVE,
	// /* negative */
	// NEGATIVE,
	// /* extraneous */
	// Pattern.compile("print|archive|comment|discuss|e[\\-]?mail|share|reply|all|login|sign|single|utility",
	// Pattern.CASE_INSENSITIVE),
	// /* byline */
	// BYLINE,
	// /* replaceFonts */
	// Pattern.compile("<(\\/?)font[^>]*>", Pattern.CASE_INSENSITIVE |
	// Pattern.MULTILINE),
	// /* normalize */
	// NORMALIZE,
	// /* videos */
	// VIDEOS,
	// /* nextLink */
	// Pattern.compile("(next|weiter|continue|>([^\\|]|$)|»([^\\|]|$))",
	// Pattern.CASE_INSENSITIVE),
	// /* prevLink */
	// Pattern.compile("(prev|earl|old|new|<|«)", Pattern.CASE_INSENSITIVE),
	// /* whitespace */
	// WHITESPACE,
	// /* hasContent */
	// HAS_CONTENT, };

	private static final List<String> DIV_TO_P_ELEMS = Arrays
			.asList(new String[] { "a", "blockquote", "dl", "div", "img", "ol", "p", "pre", "table", "ul", "select" });

	private static final List<String> ALTER_TO_DIV_EXCEPTIONS = Arrays
			.asList(new String[] { "div", "article", "section", "p" });

	private static final String[] PRESENTATIONAL_ATTRIBUTES = new String[] { "align", "background", "bgcolor", "border",
			"cellpadding", "cellspacing", "frame", "hspace", "rules", "style", "valign", "vspace" };

	private static final List<String> DEPRECATED_SIZE_ATTRIBUTE_ELEMS = Arrays
			.asList(new String[] { "TABLE", "TH", "TD", "HR", "PRE" });

	private static final List<String> TAGS_TO_SCORE = Arrays.asList("section,h2,h3,h4,h5,h6,p,td,pre".split(","));

	private int flags;

	private int nbTopCandidates;

	private int curPageNum;

	private String articleDir;

	private int wordThreshold;

	public Readability() {
		flags = Flag.STRIP_UNLIKELYS | Flag.WEIGHT_CLASSES | Flag.CLEAN_CONDITIONALLY | Flag.COMMENTS;
		nbTopCandidates = 5;
		curPageNum = 1;
		wordThreshold = 500;
	}

	private String load(URL uri) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(uri.openStream()))) {
			String line;
			while ((line = in.readLine()) != null) {
				if (sb.length() > 0) {
					sb.append("\n");
				}
				sb.append(line);
			}
		}
		return sb.toString();
	}

	public Page parse(String url) throws IOException, URISyntaxException {
		URL uri = new URL(url);
		String html = load(uri);
		return parse(uri.toURI(), html);
	}

	private Page parse(URI url, String html) {
		Document doc = Jsoup.parse(html);
		removeScripts(doc);
		removeStyles(doc);

		Elements bodys = doc.getElementsByTag("body");
		if (!bodys.isEmpty()) {
			Element body = bodys.first();
			replaceBrs(body);
		}

		Elements fonts = doc.getElementsByTag("font");
		// Tag tagSpan = Tag.valueOf("span");
		for (int i = fonts.size() - 1; i >= 0; i--) {
			Element font = fonts.get(i);
			font.tagName("span");
			// Element span = new Element(tagSpan, "");
			// for (Node child : font.childNodes()) {
			// span.appendChild(child);
			// }
			//
			// font.replaceWith(span);
		}

		MetaData meta = getMetadata(doc);
		Element content = grabContent(meta, doc);
		if (content == null) {
			return null;
		}

		postProcessContent(url, content);

		Page page = new Page();
		page.setId(url.toString());
		page.setMeta(meta);

		Block block = new Block();
		block.setContent(content.html());
		page.addBlock(block);

		return page;
	}

	private void postProcessContent(URI url, Element content) {
		fixRelativeUris(url, content);
	}

	private void fixRelativeUris(URI url, Element content) {
		Elements links = content.getElementsByTag("a");
		for (Element link : links) {
			String href = link.attr("href");
			if (href != null) {
				href = href.trim();
				if (href.startsWith("javascript:")) {
					TextNode text = new TextNode(link.text(), "");
					link.replaceWith(text);
				} else {
					link.attr("href", toAbsoluteURI(url, href));
				}
			}
		}

		Elements imgs = content.getElementsByTag("img");
		for (Element img : imgs) {
			String src = img.attr("src");
			if (src != null) {
				img.attr("src", toAbsoluteURI(url, src));
			}
		}
	}

	private static final Pattern ABSOLUTE_URI = Pattern.compile("^[a-zA-Z][a-zA-Z0-9\\+\\-\\.]*:");

	private String toAbsoluteURI(URI uri, String url) {
		if (ABSOLUTE_URI.matcher(url).matches()) {
			return url;
		}
		if ("//".equals(url.substring(0, 2))) {
			return uri.getScheme() + "://" + url.substring(2);
		}
		char start = url.charAt(0);
		if (start == '#') {
			return uri + url;
		}

		StringBuilder sb = new StringBuilder();
		String scheme = uri.getScheme();
		if (scheme != null) {
			sb.append(scheme);
			sb.append(':');
		}
		if (uri.isOpaque()) {
			sb.append(uri.getSchemeSpecificPart());
		} else {
			String host = uri.getHost();
			String authority = uri.getAuthority();

			if (host != null) {
				sb.append("//");

				String userInfo = uri.getUserInfo();
				if (userInfo != null) {
					sb.append(userInfo);
					sb.append('@');
				}
				boolean needBrackets = ((host.indexOf(':') >= 0) && !host.startsWith("[") && !host.endsWith("]"));
				if (needBrackets)
					sb.append('[');
				sb.append(host);
				if (needBrackets)
					sb.append(']');

				int port = uri.getPort();
				if (port != -1) {
					sb.append(':');
					sb.append(port);
				}
			} else if (authority != null) {
				sb.append("//");
				sb.append(authority);
			}
		}

		if (start == '/') {
			return sb + url;
		}

		String path = uri.getPath();
		if (path != null) {
			sb.append(path);
		}

		if (url.startsWith("./")) {
			return sb + url.substring(2);
		}

		return sb + url;
	}

	private List<Element> collectElementsToScore(Element node) {
		boolean stripUnlikelyCandidates = flagIsActive(Flag.STRIP_UNLIKELYS);
		List<Element> elementsToScore = new ArrayList<>();
		String articleByline = null;
		while (node != null) {
			String matchString = node.className() + " " + node.id();
			if (articleByline == null) {
				articleByline = getByline(node, matchString);
				if (articleByline != null) {
					node = removeAndGetNext(node);
					continue;
				}
			}

			if (stripUnlikelyCandidates) {
				if (UNLIKELY_CANDIDATES.matcher(matchString).find()
						&& !OK_MAYBE_ITS_CANDIDATE.matcher(matchString).find() && !"body".equals(node.tagName())
						&& !"a".equals(node.tagName())) {
					node = removeAndGetNext(node);
					continue;
				}
			}
			if (("div".equals(node.tagName()) || "section".equals(node.tagName()) || "header".equals(node.tagName())
					|| "h1".equals(node.tagName()) || "h2".equals(node.tagName()) || "h3".equals(node.tagName())
					|| "h4".equals(node.tagName()) || "h5".equals(node.tagName()) || "h6".equals(node.tagName()))
					&& isElementWithoutContent(node)) {
				node = removeAndGetNext(node);
				continue;
			}

			if (TAGS_TO_SCORE.contains(node.tagName()) /* && node.hasText() */) {
				elementsToScore.add(node);
			}

			if ("div".equals(node.tagName())) {
				if (hasSinglePInsideElement(node)) {
					Element child = node.child(0);
					node.replaceWith(child);
					node = child;
				} else if (!hasChildBlockElement(node)) {
					node = setNodeTag(node, "p");
					elementsToScore.add(node);
				} else {
					// TODO experimental
				}
			}
			node = getNextNode(node, false);
		}
		return elementsToScore;
	}

	private Element grabContent(MetaData meta, Element body) {
		// String html = body.html();

		boolean isPaging = false; // TODO page != null

		while (true) {
			final List<Element> elementsToScore = collectElementsToScore(body);
			final Map<Element, Double> candidates = new HashMap<>();
			for (Element elementToScore : elementsToScore) {
				processElement(elementToScore, body, candidates);
			}
			final List<CandidateScore> topCandidates = filterTopCandidates(candidates);
			final CandidateScore topCandidate = selectBestCandidate(topCandidates, body);
			/*
			 * boolean afterCandidate = false; for(CandidateScore candidate :
			 * candidates) { if(topCandidate == candidate) { afterCandidate =
			 * true; } else if(afterCandidate) { System.out.println(); } }
			 */
			final Element articaleContent = makeArticleContent(topCandidate);
			prepArticle(meta, articaleContent);

			if (curPageNum == 1) {
				// if (neededToCreateTopCandidate) {
				// TODO
				// } else {
				// TODO
				// }
			}

			if (articaleContent.text().length() < wordThreshold) {
				// page.innerHTML = pageCacheHtml;
				if (flagIsActive(Flag.STRIP_UNLIKELYS)) {
					removeFlag(Flag.STRIP_UNLIKELYS);
				} else if (flagIsActive(Flag.WEIGHT_CLASSES)) {
					removeFlag(Flag.WEIGHT_CLASSES);
				} else if (flagIsActive(Flag.CLEAN_CONDITIONALLY)) {
					removeFlag(Flag.CLEAN_CONDITIONALLY);
				} else {
					return null;
				}
			} else {
				List<Element> ancestors = new ArrayList<>();
				ancestors.add(topCandidate.getElement().parent());
				ancestors.add(topCandidate.getElement());
				ancestors.addAll(getNodeAncestors(topCandidate.getElement().parent(), 3));
				for (Element ancestor : ancestors) {
					String dir = ancestor.attr("dir");
					if (dir != null) {
						articleDir = dir;
					}
				}
				return articaleContent;
			}
		}
	}

	private Element makeArticleContent(CandidateScore topCandidate) {
		final Element articaleContent = new Element(Tag.valueOf("div"), "");
		// if(isPaging) {
		//
		// }
		double siblingScoreThreshold = Math.max(10, topCandidate.getScore() * 0.2);
		final Element parentOfTopCandidate = topCandidate.getElement().parent();
		final String topClass = topCandidate.getElement().className();

		final Elements siblings = parentOfTopCandidate.children();

		for (int s = 0, sl = siblings.size(); s < sl; s++) {
			Element sibling = siblings.get(s);
			boolean append = false;

			if (sibling == topCandidate.getElement()) {
				append = true;
			} else {
				int contentBonus = 0;
				String siblingClass = sibling.className();
				if (topClass != null && topClass.trim().length() == 0 && topClass.equals(siblingClass)) {
					contentBonus += topCandidate.getScore() * 0.2;
				}

				if (false) {
					// TODO
				} else if ("p".equals(sibling.nodeName())) {
					double linkDensity = getLinkDensity(sibling);
					String nodeContent = sibling.text();
					int nodeLength = nodeContent.length();

					if (nodeLength > 80 && linkDensity < 0.25) {
						append = true;
					} else if (nodeLength < 80 && nodeLength > 0 && linkDensity == 0
							&& !nodeContent.matches("\\.( |$)")) {
						append = true;
					}
				}
			}
			if (append) {
				if (!ALTER_TO_DIV_EXCEPTIONS.contains(sibling.nodeName())) {
					sibling = setNodeTag(sibling, "div");
				}

				articaleContent.appendChild(sibling);
				s -= 1;
				sl -= 1;
			}
		}

		return articaleContent;
	}

	private CandidateScore selectBestCandidate(List<CandidateScore> topCandidates, Element body) {
		CandidateScore topCandidate = null;
		if (!topCandidates.isEmpty()) {
			topCandidate = topCandidates.get(0);
		}

		boolean neededToCreateTopCandidate = false;

		if (topCandidate == null || "body".equals(topCandidate.getElement().tagName())) {
			topCandidate = new CandidateScore(new Element(Tag.valueOf("div"), ""), 0);
			neededToCreateTopCandidate = true;

			for (Element child : body.children()) {
				topCandidate.getElement().appendChild(child);
			}
			topCandidate.setScore(initializeNode(topCandidate.getElement(), 0));
		} else {
			List<List<Element>> alternativeCandidateAncestors = new ArrayList<>();
			for (int i = 1; i < topCandidates.size(); i++) {
				CandidateScore top = topCandidates.get(i);
				if (top.getScore() / topCandidate.getScore() >= 0.75) {
					List<Element> candidateAncestors = getNodeAncestors(top.getElement(), 0);
					alternativeCandidateAncestors.add(candidateAncestors);
				}
			}

			final int minTopCanditates = 3;
			if (alternativeCandidateAncestors.size() >= minTopCanditates) {
				Element parentOfTopCandidate = topCandidate.getElement().parent();
				while ("body".equals(parentOfTopCandidate.tagName())) {
					int listsContainingThisAncestor = 0;
					for (int ancestorIndex = 0; ancestorIndex < alternativeCandidateAncestors.size()
							&& listsContainingThisAncestor < minTopCanditates; ancestorIndex++) {
						if (alternativeCandidateAncestors.get(ancestorIndex).contains(parentOfTopCandidate)) {
							listsContainingThisAncestor++;
						}
					}
					if (listsContainingThisAncestor >= minTopCanditates) {
						// TODO what about score?
						topCandidate = new CandidateScore(parentOfTopCandidate, -1);
						break;
					}
					parentOfTopCandidate = parentOfTopCandidate.parent();
				}
			}
			if (topCandidate.getScore() == -1) {
				topCandidate.setScore(initializeNode(topCandidate.getElement(), 0));
			}
		}
		return topCandidate;
	}

	private List<CandidateScore> filterTopCandidates(Map<Element, Double> candidates) {
		LinkedList<CandidateScore> topCandidates = new LinkedList<>();
		for (Map.Entry<Element, Double> entry : candidates.entrySet()) {
			Element candidate = entry.getKey();
			double score = entry.getValue();
			score = score * (1 - getLinkDensity(candidate));
			// entry.setScore(score);
			entry.setValue(score);

			for (int t = 0; t < nbTopCandidates; t++) {
				CandidateScore topCandidate = t < topCandidates.size() ? topCandidates.get(t) : null;
				if (topCandidate == null || score > topCandidate.getScore()) {
					CandidateScore elementScore = new CandidateScore(candidate, score);
					topCandidates.addFirst(elementScore);

					if (topCandidates.size() > nbTopCandidates) {
						topCandidates.pollLast();
					}

					break;
				}
			}
		}
		return topCandidates;
	}

	private void prepArticle(MetaData meta, Element articaleContent) {
		cleanStyles(articaleContent);

		markDataTables(articaleContent);
		cleanConditionally(articaleContent, "from");
		cleanConditionally(articaleContent, "fieldset");
		clean(articaleContent, "object");
		clean(articaleContent, "embed");
		clean(articaleContent, "h1");
		clean(articaleContent, "footer");

		for (Element child : articaleContent.children()) {
			cleanMatchedNodes(child, Pattern.compile("share"));
		}

		Elements h2 = articaleContent.getElementsByTag("h2");
		if (h2.size() == 1) {
			Element header = h2.get(0);
			String title = meta.getTitle();
			double lengthSimilarRate = (double) (header.text().length() - title.length()) / title.length();
			if (Math.abs(lengthSimilarRate) < 0.5) {
				boolean titlesMatch = false;
				if (lengthSimilarRate > 0) {
					titlesMatch = header.text().contains(title);
				} else {
					titlesMatch = title.contains(header.text());
				}
				if (titlesMatch) {
					this.clean(articaleContent, "h2");
				}
			}
		}

		clean(articaleContent, "iframe");
		clean(articaleContent, "input");
		clean(articaleContent, "textarea");
		clean(articaleContent, "select");
		clean(articaleContent, "button");

		cleanHeaders(articaleContent);

		cleanConditionally(articaleContent, "table");
		cleanConditionally(articaleContent, "ul");
		cleanConditionally(articaleContent, "div");

		removeNodes(articaleContent.getElementsByTag("p"), (paragraph) -> {
			int imgCount = paragraph.getElementsByTag("img").size();
			int embedCount = paragraph.getElementsByTag("embed").size();
			int objectCount = paragraph.getElementsByTag("object").size();
			int iframeCount = paragraph.getElementsByTag("iframe").size();
			int totalCount = imgCount + embedCount + objectCount + iframeCount;
			return totalCount == 0 && !paragraph.hasText();
		});
	}

	private void cleanHeaders(Element el) {
		for (int headerIndex = 1; headerIndex < 3; headerIndex++) {
			removeNodes(el.getElementsByTag("h" + headerIndex), (header) -> {
				return getClassWeight(header) < 0;
			});
		}
	}

	private void cleanMatchedNodes(Element el, Pattern pattern) {
		Element endOfSearchMarkerNode = getNextNode(el, true);
		Element next = getNextNode(el, false);

		while (next != null && next.equals(endOfSearchMarkerNode)) {
			if (pattern.matcher(next.className() + " " + next.id()).matches()) {
				next = removeAndGetNext(next);
			} else {
				next = getNextNode(next, false);
			}
		}
	}

	private void clean(Element element, String tag) {
		boolean isEmbed = "object".equals(tag) || "embed".equals(tag) || "iframe".equals(tag);

		removeNodes(element.getElementsByTag(tag), (node) -> {
			if (isEmbed) {
				List<Attribute> attributes = node.attributes().asList();
				StringBuilder values = new StringBuilder();
				for (int i = 0; i < attributes.size(); i++) {
					if (i != 0) {
						values.append('|');
					}
					values.append(attributes.get(i).getValue());
				}
				if (VIDEOS.matcher(values.toString()).matches()) {
					return false;
				}

				// TODO use inner html
				if (VIDEOS.matcher(node.html()).matches()) {
					return false;
				}
			}
			return true;
		});
	}

	private void cleanConditionally(Element element, String tag) {
		if (!flagIsActive(Flag.CLEAN_CONDITIONALLY)) {
			return;
		}

		boolean isList = "ul".equals(tag) || "ol".equals(tag);
		Elements tagElements = element.getElementsByTag(tag);
		removeNodes(tagElements, (node) -> {
			if (hasAncestorTag(node, "table", -1, (child) -> {
				// TODO
				return false;
			})) {
				return false;
			}
			double weight = getClassWeight(node);
			// double contentScore = 0.0;
			if (weight < 0) {
				return true;
			}
			if (getCharCount(node, ",") < 10) {
				int pCount = node.getElementsByTag("p").size();
				int imgCount = node.getElementsByTag("img").size();
				int liCount = node.getElementsByTag("li").size() - 100;
				int inputCount = node.getElementsByTag("input").size();

				int embedCount = 0;
				Elements embeds = node.getElementsByTag("embed");
				for (int i = 0; i < embeds.size(); i++) {
					String src = embeds.get(i).attr("src");
					if (src != null && !VIDEOS.matcher(src).matches()) {
						embedCount++;
					}
				}

				double linkDensity = getLinkDensity(node);
				int contentLength = node.text().length();

				return (imgCount > 1 && pCount / (double) imgCount < 0.5 && !hasAncestorTag(node, "figure", 3, null))
						|| (!isList && liCount > pCount) || (inputCount > Math.floor(pCount / 3.0))
						|| (!isList && contentLength < 25 && (imgCount == 0 || imgCount > 2)
								&& !hasAncestorTag(node, "figure", 3, null))
						|| (!isList && weight < 25 && linkDensity > 0.2) || (weight >= 25 && linkDensity > 0.5)
						|| ((embedCount == 1 && contentLength < 75) || embedCount > 1);

			}

			return false;
		});
	}

	private int getCharCount(Element node, String word) {
		if (word == null) {
			word = ",";
		}
		String[] tokens = node.text().split(word);
		return tokens.length - 1;
	}

	private boolean hasAncestorTag(Element node, String tagName, int maxDepth, Predicate<Element> predicate) {
		int depth = 0;
		while (node.parent() != null) {
			if (maxDepth > 0 && depth > maxDepth) {
				return false;
			}
			node = node.parent();
			if (tagName.equals(node.tagName()) && (predicate == null || predicate.test(node))) {
				return true;
			}
			depth++;
		}
		return false;
	}

	private void removeNodes(Elements elements, Predicate<Element> predicate) {
		for (int i = elements.size() - 1; i >= 0; i--) {
			Element node = elements.get(i);
			Element parentNode = node.parent();
			if (parentNode != null) {
				if (predicate == null || predicate.test(node)) {
					node.remove();
				}
			}
		}
	}

	private void markDataTables(Element node) {
		Elements tables = node.getElementsByTag("table");
		for (int i = 0; i < tables.size(); i++) {
			Element table = tables.get(i);
			String role = table.attr("role");
			if ("presentation".equals(role)) {
				// table.readabilityDataTable = false;
				continue;
			}
			String datatable = table.attr("datatable");
			if ("0".equals(datatable)) {
				// table.readabilityDataTable = false;
				continue;
			}
			String summary = table.attr("summary");
			if (summary != null) {
				// table.readabilityDataTable = true;
				continue;
			}
			Elements captions = table.getElementsByTag("caption");
			if (!captions.isEmpty() && captions.first().childNodeSize() > 0) {
				// table.readabilityDataTable = true;
				continue;
			}
			String[] dataTableDescendants = new String[] { "col", "colgroup", "tfoot", "thead", "th" };
			for (String tag : dataTableDescendants) {
				if (!table.getElementsByTag(tag).isEmpty()) {
					// table.readabilityDataTable = true;
					continue;
				}
			}
			if (!table.getElementsByTag("table").isEmpty()) {
				// table.readabilityDataTable = false;
				continue;
			}

			int[] sizeInfo = this.getRowAndColumnCount(table);
			if (sizeInfo[0] >= 10 || sizeInfo[1] > 4) {
				// table._readabilityDataTable = true;
				continue;
			}
			// Now just go by size entirely:
			// table._readabilityDataTable = sizeInfo[0] * sizeInfo[1] > 10;
		}
	}

	private int[] getRowAndColumnCount(Element table) {
		int rows = 0;
		int columns = 0;
		Elements trs = table.getElementsByTag("tr");
		for (int i = 0; i < trs.size(); i++) {
			Element tr = trs.get(i);
			String rowspanAttr = tr.attr("rowspan");
			int rowspan = 1;
			if (!"".equals(rowspanAttr)) {
				rowspan = Math.max(Integer.valueOf(rowspanAttr), 1);
			}
			rows += rowspan;

			int columnsInThisRow = 0;
			Elements cells = tr.getElementsByTag("td");
			for (int j = 0; j < cells.size(); j++) {
				String colspanAttr = cells.get(j).attr("colspan");
				int colspan = 1;
				if (!"".equals(colspanAttr)) {
					colspan = Math.max(Integer.valueOf(colspanAttr), 1);
				}
				columnsInThisRow += colspan;
			}
			columns = Math.max(columns, columnsInThisRow);
		}
		return new int[] { rows, columns };
	}

	private void cleanStyles(Element element) {
		if (element == null || "svg".equals(element.tagName())) {
			return;
		}

		for (int i = 0; i < PRESENTATIONAL_ATTRIBUTES.length; i++) {
			element.removeAttr(PRESENTATIONAL_ATTRIBUTES[i]);
		}

		if (DEPRECATED_SIZE_ATTRIBUTE_ELEMS.contains(element.tagName())) {
			element.removeAttr("width");
			element.removeAttr("height");
		}

		for (Element cur : element.children()) {
			cleanStyles(cur);
		}
	}

	private Element setNodeTag(Element node, String tag) {
		node.tagName(tag);
		return node;
	}

	private void processElement(Element elementToScore, Element body, Map<Element, Double> candidates) {
		if (elementToScore.parent() == null) {
			return;
		}

		String innerText = elementToScore.text();
		if (innerText.length() < 25) {
			return;
		}

		List<Element> ancestors = getNodeAncestors(elementToScore, 3);
		if (ancestors.isEmpty()) {
			return;
		}
		// Add the paragraph itself and points for any commas within this
		// paragraph.
		int contentScore = 1 + innerText.split(",").length;

		// For every 100 characters in this paragraph, add another point. Up to
		// 3 points.
		contentScore += Math.min(Math.floor(innerText.length() / 100), 3);

		for (int level = 0; level < ancestors.size(); level++) {
			Element ancesor = ancestors.get(level);
			if (ancesor.tagName() == null) {
				break;
			}

			Double score = candidates.get(ancesor);
			if (score == null) {
				score = initializeNode(ancesor, 0);
				// candidates.add(candidate);
			}

			// CandidateScore candidate = new CandidateScore(ancesor, 0);
			// if (!candidates.contains(candidate)) {
			// initializeNode(candidate, candidate.getScore());
			// candidates.add(candidate);
			// }

			if (level == 1) {
				// candidate.setScore(contentScore / 2.0);
				score += contentScore / 2.0;
			} else if (level == 0) {
				score += contentScore;
			} else {
				score += contentScore / (level * 3.0);
				// candidate.setScore(contentScore / (level * 3.0));
			}
			candidates.put(ancesor, score);
		}
	}

	private double getLinkDensity(Element element) {
		int textLength = element.text().length();
		if (textLength == 0) {
			return 0;
		}
		int linkLength = 0;
		Elements links = element.getElementsByTag("a");
		for (Element link : links) {
			linkLength += link.text().length();
		}
		return linkLength / (double) textLength;
	}

	private double initializeNode(Element node, double score) {
		String tag = node.tagName();
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

		return score;
	}

	private double getClassWeight(Element node) {
		if (!flagIsActive(Flag.WEIGHT_CLASSES)) {
			return 0;
		}

		int weight = 0;

		String className = node.className();
		if (className != null && className.trim().length() > 0) {
			if (NEGATIVE.matcher(className).matches()) {
				weight -= 25;
			}

			if (POSITIVE.matcher(className).matches()) {
				weight += 25;
			}
		}
		String id = node.id();
		if (id != null && id.trim().length() > 0) {
			if (NEGATIVE.matcher(id).matches()) {
				weight -= 25;
			}

			if (POSITIVE.matcher(id).matches()) {
				weight += 25;
			}
		}
		return weight;
	}

	private boolean flagIsActive(int flag) {
		return (flags & flag) > 0;
	}

	private void removeFlag(int flag) {
		this.flags = this.flags & ~flag;
	}

	private List<Element> getNodeAncestors(Element node, int maxDepth) {
		int i = 0;
		List<Element> ancestors = new ArrayList<>();
		while (node.parent() != null && i < maxDepth) {
			node = node.parent();
			ancestors.add(node);
			i++;
		}
		return ancestors;
	}

	// private String getInnerText(Element element, boolean normalizeSpaces) {
	// String textContent = element.text().trim();
	// if (normalizeSpaces) {
	// return NORMALIZE.matcher(textContent).replaceAll(" ");
	// }
	//
	// return textContent;
	// }

	private boolean hasChildBlockElement(Element element) {
		for (Element child : element.children()) {
			if (DIV_TO_P_ELEMS.indexOf(child.tagName()) != -1 || hasChildBlockElement(child)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasSinglePInsideElement(Element element) {
		Elements childs = element.children();

		if (childs.size() == 1 && "p".equals(childs.get(0).tagName())) {
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

	private boolean isElementWithoutContent(Node node) {
		if (node instanceof Element) {
			Element el = (Element) node;
			if (!el.hasText()) {
				Elements child = el.children();
				return child.size() == 0
						|| (child.size() == el.getElementsByTag("br").size() + el.getElementsByTag("hr").size());
			}
		}

		return false;
	}

	private Element removeAndGetNext(Element node) {
		Element next = getNextNode(node, true);
		node.remove();
		return next;
	}

	private Element getNextNode(Element node, boolean ignore) {
		final Elements childs = node.children();
		if (!ignore && childs.size() > 0) {
			return childs.get(0);
		}
		Element sibling = node.nextElementSibling();
		if (sibling != null) {
			return sibling;
		}
		do {
			node = node.parent();
			sibling = node == null ? null : node.nextElementSibling();
		} while (node != null && sibling == null);
		return sibling;
	}

	private String getByline(Element node, String matchString) {
		String rel = node.attr("rel");
		if (("author".equals(rel) || BYLINE.matcher(matchString).find()) && isValidByline(node.text())) {
			return node.text().trim();
		}
		return null;
	}

	private boolean isValidByline(String text) {
		text = text.trim();
		return text.length() > 0 && text.length() < 100;
	}

	private MetaData getMetadata(Element doc) {
		Elements metas = doc.select("meta");
		MetaData metaData = new MetaData();

		Map<String, String> values = new HashMap<>();
		for (Element meta : metas) {
			String name = meta.attr("name");
			String property = meta.attr("property");

			if ("author".equals(name) || "author".equals(property)) {
				metaData.setByline(meta.attr("content"));
			} else {
				String key = null;
				if (META_NAME.matcher(name).matches()) {
					key = name;
				} else if (META_PROPERTY.matcher(property).matches()) {
					key = property;
				}
				if (key != null) {
					String content = meta.attr("content");
					if (content != null) {
						name = name.toLowerCase().replace("\\s", "");
						values.put(name, content.trim());
					}
				}

			}
		}

		String excerpt = null;
		if (values.containsKey("description")) {
			excerpt = values.get("description");
		} else if (values.containsKey("og:description")) {
			excerpt = values.get("og:description");
		} else if (values.containsKey("twitter:description")) {
			excerpt = values.get("twitter:description");
		}
		metaData.setExcerpt(excerpt);

		String title = getTitle(doc);
		if (title == null) {
			if (values.containsKey("og:title")) {
				title = values.get("og:title");
			} else if (values.containsKey("twitter:title")) {
				title = values.get("twitter:title");
			}
		}

		metaData.setTitle(title);

		return metaData;
	}

	private String getTitle(Element doc) {
		Element title = doc.select("title").first();
		String titleContent = null;
		String orinialTitle = null;
		if (title != null) {
			titleContent = title.text().trim();
			orinialTitle = titleContent;

			boolean titleHasSeperator = false;
			if (titleContent.matches(" [\\|\\-\\\\/>»] ")) {
				titleHasSeperator = titleContent.matches(" [\\\\/>»] ");
				titleContent = orinialTitle.replace("(.*)[\\|\\-\\\\/>»] .*", "$1");

				if (wordCount(titleContent) < 3) {
					titleContent = orinialTitle.replace("[^\\|\\-\\\\/>»]*[\\|\\-\\\\/>»](.*)", "$1");
				}
			} else if (titleContent.indexOf(": ") != -1) {
				Elements headers1 = doc.select("h1");
				Elements headers2 = doc.select("h2");
				boolean match = false;
				for (Element header : headers1) {
					if (titleContent.equals(header.text())) {
						match = true;
						break;
					}
				}
				if (!match) {
					for (Element header : headers2) {
						if (titleContent.equals(header.text())) {
							match = true;
							break;
						}
					}
				}
				if (!match) {
					titleContent = orinialTitle.substring(orinialTitle.lastIndexOf(":") + 1);
					if (wordCount(titleContent) < 3) {
						titleContent = orinialTitle.substring(orinialTitle.indexOf(":") + 1);
					}
				}
			} else if (titleContent.length() > 150 || titleContent.length() < 15) {
				Elements headers = doc.select("h1");
				if (headers.size() == 1) {
					titleContent = headers.first().text();
				}
			}

			titleContent = titleContent.trim();
			int count = wordCount(titleContent);
			if (count <= 4
					&& (!titleHasSeperator || count != wordCount(orinialTitle.replace("[\\|\\-\\\\/>»]+", "")) - 1)) {
				titleContent = orinialTitle;
			}
		}
		return titleContent;
	}

	private int wordCount(String txt) {
		return txt.split("\\s+").length;
	}

	private void replaceBrs(Element body) {
		Elements brs = body.select("br");
		for (Element br : brs) {
			Node next = br.nextSibling();
			boolean replaced = false;

			next = nextElement(next);
			while (next != null && next instanceof Element && "br".equals(((Element) next).tagName())) {
				replaced = true;
				Node nextSib = next.nextSibling();
				next.remove();
				// next = nextSib;
				next = nextElement(nextSib);
			}

			if (replaced) {
				Element text = new Element(Tag.valueOf("p"), "");
				br.replaceWith(text);

				next = text.nextSibling();
				while (next != null) {
					if (next instanceof Element && "br".equals(((Element) next).tag())) {
						Node nextElem = nextElement(next);
						if (nextElem != null && nextElem instanceof Element
								&& "br".equals(((Element) nextElem).tag())) {
							break;
						}
					}

					Node sibling = next.nextSibling();
					text.appendChild(next);
					next = sibling;
				}
			}

		}
	}

	private Node nextElement(Node node) {
		Node next = node;

		while (next != null && (next instanceof TextNode)
				&& WHITESPACE.matcher(((TextNode) node).getWholeText()).matches()) {
			next = next.nextSibling();
		}
		return next;
	}

	private void removeStyles(Element doc) {
		Elements styles = doc.select("style");
		styles.remove();
	}

	private void removeScripts(Element doc) {
		Elements scripts = doc.select("script");
		scripts.remove();

		Elements noscripts = doc.select("noscript");
		noscripts.remove();
	}

	public static void main(String[] args) throws IOException, URISyntaxException {
		Readability read = new Readability();
		Page page = read.parse("https://99bitcoins.com/buy-dogecoin/");
		System.out.println(page.getFirstBlockContent());
	}
}
