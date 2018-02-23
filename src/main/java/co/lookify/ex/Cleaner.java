package co.lookify.ex;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import co.lookify.link.Flag;
import co.lookify.structure.MetaData;

public class Cleaner {

	private static final String[] PRESENTATIONAL_ATTRIBUTES = new String[] { "align", "background", "bgcolor", "border",
			"cellpadding", "cellspacing", "frame", "hspace", "rules", "style", "valign", "vspace" };

	private static final List<String> DEPRECATED_SIZE_ATTRIBUTE_ELEMS = Arrays
			.asList(new String[] { "table", "th", "td", "hr", "pre" });

	private static final Pattern VIDEOS = Pattern.compile(
			"\\/\\/(www\\.)?(dailymotion|youtube|youtube-nocookie|player\\.vimeo)\\.com", Pattern.CASE_INSENSITIVE);

	private static final Pattern SHARE = Pattern.compile("share");

	private static final String DATA_TABLE = "cleanerDataTable";

	private final Score score;

	private final Flag flag;

	public Cleaner(final Score score, final Flag flag) {
		this.score = Objects.requireNonNull(score);
		this.flag = Objects.requireNonNull(flag);
	}

	public void prepArticle(MetaData meta, Element articaleContent) {
		cleanStyles(articaleContent);

		markDataTables(articaleContent);
		cleanConditionally(articaleContent, "form");
		cleanConditionally(articaleContent, "fieldset");
		clean(articaleContent, "object");
		clean(articaleContent, "embed");
		clean(articaleContent, "h1");
		clean(articaleContent, "footer");

		for (Element child : articaleContent.children()) {
			cleanMatchedNodes(child, SHARE);
		}
		if (meta == null) {
			// TODO
		} else {
			Elements h2 = articaleContent.getElementsByTag("h2");
			if (h2.size() == 1) {
				Element header = h2.first();
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

	private void markDataTables(final Element node) {
		final Elements tables = node.getElementsByTag("table");
		for (int i = 0; i < tables.size(); i++) {
			final Element table = tables.get(i);
			final String role = table.attr("role");
			if ("presentation".equals(role)) {
				// table.readabilityDataTable = false;
				continue;
			}
			final String datatable = table.attr("datatable");
			if ("0".equals(datatable)) {
				// table.readabilityDataTable = false;
				continue;
			}
			final String summary = table.attr("summary");
			if (summary != null) {
				table.attr(DATA_TABLE, Boolean.TRUE.toString());
				// table.readabilityDataTable = true;
				continue;
			}
			final Elements captions = table.getElementsByTag("caption");
			if (!captions.isEmpty() && captions.first().childNodeSize() > 0) {
				table.attr(DATA_TABLE, Boolean.TRUE.toString());
				// table.readabilityDataTable = true;
				continue;
			}
			final String[] dataTableDescendants = new String[] { "col", "colgroup", "tfoot", "thead", "th" };
			for (String tag : dataTableDescendants) {
				if (!table.getElementsByTag(tag).isEmpty()) {
					table.attr(DATA_TABLE, Boolean.TRUE.toString());
					// table.readabilityDataTable = true;
					continue;
				}
			}
			if (!table.getElementsByTag("table").isEmpty()) {
				// table.readabilityDataTable = false;
				continue;
			}

			int[] sizeInfo = getRowAndColumnCount(table);
			if (sizeInfo[0] >= 10 || sizeInfo[1] > 4) {
				table.attr(DATA_TABLE, Boolean.TRUE.toString());
				// table._readabilityDataTable = true;
				continue;
			}
			// Now just go by size entirely:
			if (sizeInfo[0] * sizeInfo[1] > 10) {
				table.attr(DATA_TABLE, Boolean.TRUE.toString());
			}
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

	private void cleanHeaders(Element el) {
		for (int headerIndex = 1; headerIndex < 3; headerIndex++) {
			removeNodes(el.getElementsByTag("h" + headerIndex), (header) -> {
				return score.getClassWeight(header) < 0;
			});
		}
	}

	private void cleanMatchedNodes(Element el, Pattern pattern) {
		Element endOfSearchMarkerNode = getNextNode(el, true);
		Element next = getNextNode(el, false);

		while (next != null && next.equals(endOfSearchMarkerNode)) {
			if (pattern.matcher(next.className() + " " + next.id()).matches()) {
				final Element nextNode = getNextNode(next, true);
				next.remove();
				next = nextNode;
			} else {
				next = getNextNode(next, false);
			}
		}
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
		if (!flag.isActive(Flag.CLEAN_CONDITIONALLY)) {
			return;
		}

		boolean isList = "ul".equals(tag) || "ol".equals(tag);
		Elements tagElements = element.getElementsByTag(tag);
		removeNodes(tagElements, (node) -> {
			if (hasAncestorTag(node, "table", -1, (child) -> {
				final String dataTable = child.attr(DATA_TABLE);
				return Boolean.valueOf(dataTable);
			})) {
				return false;
			}
			double weight = score.getClassWeight(node);
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

				double linkDensity = score.getLinkDensity(node);
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

	private int getCharCount(Element node, String word) {
		if (word == null) {
			word = ",";
		}
		String[] tokens = node.text().split(word);
		return tokens.length - 1;
	}

	private void removeNodes(Elements elements, Predicate<Element> predicate) {
		for (int i = elements.size() - 1; i >= 0; i--) {
			Element node = elements.get(i);
			Element parentNode = node.parent();
			if (parentNode != null) {
				if (predicate == null || predicate.test(node)) {
					node.remove();

					while (parentNode.children().size() == 0) {
						node = parentNode;
						parentNode = parentNode.parent();
						if (parentNode == null) {
							break;
						}
						node.remove();
					}
				}
			}
		}
	}
}
