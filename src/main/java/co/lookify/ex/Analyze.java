package co.lookify.ex;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

import co.lookify.link.CandidateScore;
import co.lookify.link.Flag;
import co.lookify.structure.Block;
import co.lookify.structure.MetaData;
import co.lookify.structure.Page;
import co.lookify.structure.Person;

public class Analyze {
	private static final Pattern META_NAME = Pattern.compile("\\s*((twitter)\\s*:\\s*)?(description|title)\\s*$",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern META_PROPERTY = Pattern.compile("\\s*og\\s*:\\s*(description|title)\\s*$",
			Pattern.CASE_INSENSITIVE);

	private static final List<String> ALTER_TO_DIV_EXCEPTIONS = Arrays
			.asList(new String[] { "div", "article", "section", "p" });

	private int nbTopCandidates;

	private final Flag flag;

	private final Score score;

	private int curPageNum;

	private int wordThreshold;

	private int minConent = 100;

	public Analyze() {
		flag = new Flag();
		score = new Score(flag);
		nbTopCandidates = 5;
		curPageNum = 1;
		wordThreshold = 500;
	}

	public Page parse(final String url) throws IOException, URISyntaxException {
		URI uri = new URI(url);
		final Document doc;

		final String schema = uri.getScheme();
		final boolean file = schema == null || "file".equals(schema);
		if (file) {
			doc = Jsoup.parse(new File(url), StandardCharsets.UTF_8.name());
			
			// canonical
		} else {
			doc = Jsoup.connect(url).get();
		}
		
		final ScoreVisitor2 visitor = new ScoreVisitor2(uri, score, flag);

		final MetaData meta = getMetadata(doc);

		final Page page = new Page();
		page.setId(uri.toString());
		page.setMeta(meta);

		final Elements bodies = doc.getElementsByTag("body");
		if (!bodies.isEmpty()) {
			final Element body = bodies.first();
			// System.out.println(body.html());
			visitor.traverse(body);

			final Map<Element, CandidateScore> candidates = visitor.getCandidates();
			List<CandidateScore> sortedCandidates = new ArrayList<>(candidates.values());
			sortedCandidates.sort((c1, c2) -> {
				return Double.compare(c2.getScore(), c1.getScore());
			});

			// final String original = body.html();
			while (true) {

				// double sum = 0;
				// for (CandidateScore candidate : sortedCandidates) {
				// sum += candidate.getScore();
				// }
				// double mid = sum / sortedCandidates.size();
				//
				// int half = sortedCandidates.size() / 2;
				// double median = sortedCandidates.size() % 2 == 0
				// ? (sortedCandidates.get(half).getScore() +
				// sortedCandidates.get(half + 1).getScore()) / 2
				// : sortedCandidates.get(half).getScore();

				// final List<CandidateScore> topCandidates =
				// filterTopCandidates(candidates);

				final CandidateScore topCandidate = selectBestCandidate(candidates, sortedCandidates, body);
				final Cleaner cleaner = new Cleaner(score, flag);

				final Element articaleContent = makeArticleContent(topCandidate, candidates);
				cleaner.prepArticle(meta, articaleContent);

				if (curPageNum == 1) {
					// if (neededToCreateTopCandidate) {
					// TODO
					// } else {
					// TODO
					// }
				}
				// String articleDir = null;
				if (articaleContent.text().length() < wordThreshold) {
					final List<CandidateScore> listCandidates = selectListCandidates(sortedCandidates);
					if (listCandidates == null) {
						visitor.clear();

						if (flag.isActive(Flag.STRIP_UNLIKELYS)) {
							flag.removeFlag(Flag.STRIP_UNLIKELYS);

							visitor.scoreUnlikes();

							sortedCandidates = new ArrayList<>(candidates.values());
							sortedCandidates.sort((c1, c2) -> {
								return Double.compare(c2.getScore(), c1.getScore());
							});

						} else if (flag.isActive(Flag.WEIGHT_CLASSES)) {
							flag.removeFlag(Flag.WEIGHT_CLASSES);
						} else if (flag.isActive(Flag.CLEAN_CONDITIONALLY)) {
							flag.removeFlag(Flag.CLEAN_CONDITIONALLY);
						} else {
							return page;
						}
					} else {
						processItems(visitor, uri, cleaner, listCandidates, candidates, page);

						break;
					}
				} else {
					postProcessContent(uri, articaleContent);
					addBlock(visitor, page, topCandidate, articaleContent);


					final List<CandidateScore> listCandidates = selectListCandidates(sortedCandidates);
					if (listCandidates != null) {
						processItems(visitor, uri, cleaner, listCandidates, candidates, page);
					}

					break;
				}

			}
		}

		return page;
	}

	private void addBlock(ScoreVisitor2 visitor, Page page, CandidateScore candidate, Element content) {
		Person author = candidate.getAuthor();
		Date date = candidate.getDate();
		String dir = candidate.getDirection();
		Set<String> tags = candidate.getTags();
		if (author == null || date == null || tags == null) {
			List<Element> list = visitor.getRemovedElements(candidate.getElement());
			for (Element child : list) {
				Elements elements = child.children();
				for (Element sub : elements) {
					visitor.traverse(sub);
					if (author == null) {
						author = visitor.getAuthor();
					}
					if (date == null) {
						date = visitor.getDate();
					}
					if (tags == null) {
						tags = visitor.getTags();
					}
				}
				if (author != null && date != null && tags != null) {
					break;
				}
			}
		}
		final Block block = new Block();
		block.setAuthor(author);
		block.setDate(date);
		block.setDirection(dir);
		block.setTags(tags);
		block.setEl(content);
		
		Element cleanText = cleanContent(content, Whitelist.basic());
		block.setContent(cleanText.html());
		page.addBlock(block);
	}
	
	private Element cleanContent(Element content, Whitelist whiltelist) {
		Document dirt = Document.createShell("");
		dirt.body().appendChild(content);
		org.jsoup.safety.Cleaner cleaner = new org.jsoup.safety.Cleaner(whiltelist);
        Document clean = cleaner.clean(dirt);
		return clean.body();
	}
	
	private void processItems(final ScoreVisitor2 visitor, final URI uri, final Cleaner cleaner, final List<CandidateScore> listCandidates,
			final Map<Element, CandidateScore> candidates, final Page page) throws URISyntaxException {
		for (CandidateScore item : listCandidates) {
			final Element itemContent = makeArticleContent(item, candidates);
			cleaner.prepArticle(null, itemContent);
			if (itemContent.text().length() >= minConent ) {
				postProcessContent(uri, itemContent);
				addBlock(visitor, page, item, itemContent);
				
//				final Block block = new Block();
//				block.setAuthor(item.getAuthor());
//				block.setDate(item.getDate());
//				block.setContent(itemContent.html());
//				block.setDirection(item.getDirection());
//				page.addBlock(block);
			}
		}
	}

	private List<CandidateScore> selectListCandidates(List<CandidateScore> candidates) {
		final Map<String, List<CandidateScore>> combine = new HashMap<>();
		final double threshold = 10;
		for (CandidateScore candidate : candidates) {
			final String matchString = makeMatchString(candidate);
			if (matchString.length() > 0 && candidate.getElement().getElementsByTag("a").size() > 0) {
				List<CandidateScore> childs = combine.get(matchString);
				if (childs == null) {
					childs = new ArrayList<>();
					childs.add(candidate);
					combine.put(matchString, childs);
				} else {
					double lastScore = childs.get(childs.size() - 1).getScore();
					if (Math.abs(candidate.getScore() - lastScore) < threshold) {
						childs.add(candidate);
					}
				}
			}
		}
		List<CandidateScore> best = null;
		int max = -1;
		for (Map.Entry<String, List<CandidateScore>> entry : combine.entrySet()) {
			final List<CandidateScore> group = entry.getValue();
			if (group.size() >= 5) {
				if (max < group.size()) {
					max = group.size();
					best = group;
				} else if (max == group.size()) {
					double bestSum = sumScore(best);
					double groupSum = sumScore(group);
					if (groupSum > bestSum) {
						best = group;
					}
				}
			}
		}
		return best;
	}

	private double sumScore(List<CandidateScore> candidates) {
		double sum = 0;
		for (CandidateScore candidate : candidates) {
			sum += candidate.getScore();
		}
		return sum;
	}

	private String makeMatchString(CandidateScore candidate) {
		final StringBuilder builder = new StringBuilder();
		Element el = candidate.getElement();
		int depth = 0;
		while (el != null && depth < 3) {
			final String className = el.className().trim();
			if (className.length() > 0) {
				if (builder.length() > 0) {
					builder.append('.');
				}
				builder.append(el.tagName());
				builder.append('[');
				builder.append(className);
				builder.append(']');
			}
			el = el.children().first();
			depth++;
		}
		return builder.toString();
	}

	private void postProcessContent(URI uri, Element content) throws URISyntaxException {
		fixRelativeUris(uri, content);
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
					link.attr("href", URLHelper.toAbsoluteURI(url, href));
				}
			}
		}

		Elements imgs = content.getElementsByTag("img");
		for (Element img : imgs) {
			String src = img.attr("src");
			if (src != null) {
				img.attr("src", URLHelper.toAbsoluteURI(url, src));
			}
		}
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

	private Element makeArticleContent(CandidateScore topCandidate, final Map<Element, CandidateScore> candidates) {
		final Element articaleContent = new Element(Tag.valueOf("div"), "");
		// if(isPaging) {
		//
		// }
		final Element topElement = topCandidate.getElement();
		double siblingScoreThreshold = Math.max(10, topCandidate.getScore() * 0.2);
		final Element parentOfTopCandidate = topElement.parent();
		final String topClass = topElement.className();

		final Elements siblings = parentOfTopCandidate.children();

		for (int s = 0, sl = siblings.size(); s < sl; s++) {
			Element sibling = siblings.get(s);
			boolean append = false;

			if (sibling == topElement) {
				append = true;
			} else {
				int contentBonus = 0;
				final String siblingClass = sibling.className();
				if (topClass != null && topClass.trim().length() == 0 && topClass.equals(siblingClass)) {
					contentBonus += topCandidate.getScore() * 0.2;
				}

				final CandidateScore candidate = candidates.get(sibling);
				if (candidate != null && candidate.getScore() + contentBonus >= siblingScoreThreshold) {
					append = true;
				} else if ("p".equals(sibling.nodeName())) {
					final double linkDensity = score.getLinkDensity(sibling);
					final String nodeContent = sibling.text();
					final int nodeLength = nodeContent.length();

					if (nodeLength > 80 && linkDensity < 0.25) {
						append = true;
					} else if (nodeLength < 80 && nodeLength > 0 && linkDensity == 0
							&& !nodeContent.matches("\\.( |$)")) {
						append = true;
					}
				}
			}
			if (append) {
				final String tag = sibling.nodeName();
				if ("tr".equals(tag)) {
					final Elements childs = sibling.children();
					if (childs.size() == 1) {
						sibling = childs.first();
					} else {
						for (Element child : childs) {
							child.tagName("div");
						}
					}
				}
				if (!ALTER_TO_DIV_EXCEPTIONS.contains(sibling.nodeName())) {
					sibling.tagName("div");
				}

				articaleContent.appendChild(sibling);
				// s -= 1;
				// sl -= 1;
			}
		}

		Element cleanArticle = cleanContent(articaleContent, Whitelist.relaxed());
		return cleanArticle;
	}

//	private List<CandidateScore> filterTopCandidates(Map<Element, Double> candidates) {
//		LinkedList<CandidateScore> topCandidates = new LinkedList<>();
//		for (Map.Entry<Element, Double> entry : candidates.entrySet()) {
//			Element candidate = entry.getKey();
//			double score = entry.getValue();
//
//			for (int t = 0; t < nbTopCandidates; t++) {
//				CandidateScore topCandidate = t < topCandidates.size() ? topCandidates.get(t) : null;
//				if (topCandidate == null || score > topCandidate.getScore()) {
//					CandidateScore elementScore = new CandidateScore(candidate, score);
//					topCandidates.addFirst(elementScore);
//
//					if (topCandidates.size() > nbTopCandidates) {
//						topCandidates.pollLast();
//					}
//
//					break;
//				}
//			}
//		}
//		return topCandidates;
//	}

	private CandidateScore selectBestCandidate(final Map<Element, CandidateScore> candidates,
			final List<CandidateScore> topCandidates, final Element body) {
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

			topCandidate.setScore(score.initializeNode(topCandidate.getElement(), 0));
		} else {
			List<List<Element>> alternativeCandidateAncestors = new ArrayList<>();
			for (int i = 1; i < Math.min(topCandidates.size(), nbTopCandidates); i++) {
				CandidateScore top = topCandidates.get(i);
				if (top.getScore() / topCandidate.getScore() >= 0.75) {
					List<Element> candidateAncestors = getNodeAncestors(top.getElement(), 0);
					alternativeCandidateAncestors.add(candidateAncestors);
				}
			}

			final int minTopCanditates = 3;
			Element parentOfTopCandidate;
			if (alternativeCandidateAncestors.size() >= minTopCanditates) {
				parentOfTopCandidate = topCandidate.getElement().parent();
				while ("body".equals(parentOfTopCandidate.tagName())) {
					int listsContainingThisAncestor = 0;
					for (int ancestorIndex = 0; ancestorIndex < alternativeCandidateAncestors.size()
							&& listsContainingThisAncestor < minTopCanditates; ancestorIndex++) {
						if (alternativeCandidateAncestors.get(ancestorIndex).contains(parentOfTopCandidate)) {
							listsContainingThisAncestor++;
						}
					}
					if (listsContainingThisAncestor >= minTopCanditates) {
						topCandidate = new CandidateScore(parentOfTopCandidate, -1);
						break;
					}
					parentOfTopCandidate = parentOfTopCandidate.parent();
				}
			}
			if (topCandidate.getScore() == -1) {
				topCandidate.setScore(score.initializeNode(topCandidate.getElement(), 0));
			}

			// Because of our bonus system, parents of candidates might have
			// scores
			// themselves. They get half of the node. There won't be nodes with
			// higher
			// scores than our topCandidate, but if we see the score going *up*
			// in the first
			// few steps up the tree, that's a decent sign that there might be
			// more content
			// lurking in other places that we want to unify in. The sibling
			// stuff
			// below does some of that - but only if we've looked high enough up
			// the DOM
			// tree.
			parentOfTopCandidate = topCandidate.getElement().parent();
			double lastScore = topCandidate.getScore();
			// The scores shouldn't get too low.
			double scoreThreshold = lastScore / 3.0;

			while (!"body".equals(parentOfTopCandidate.tagName())) {
				CandidateScore parentScore = candidates.get(parentOfTopCandidate);
				if (parentScore == null) {
					parentOfTopCandidate = parentOfTopCandidate.parent();
					continue;
				} else if (parentScore.getScore() < scoreThreshold) {
					break;
				} else if (parentScore.getScore() > lastScore) {
					// Alright! We found a better parent to use.
					topCandidate = parentScore;
					break;
				}
				lastScore = parentScore.getScore();
				parentOfTopCandidate = parentOfTopCandidate.parent();
			}

			// If the top candidate is the only child, use parent instead. This
			// will help sibling
			// joining logic when adjacent content is actually located in
			// parent's sibling node.
			parentOfTopCandidate = topCandidate.getElement().parent();

			while (!"body".equals(parentOfTopCandidate.tagName()) && parentOfTopCandidate.children().size() == 1) {
				CandidateScore parentScore = candidates.get(parentOfTopCandidate);
				if (parentScore == null) {
					parentScore = new CandidateScore(parentOfTopCandidate, 0);
					parentScore.setScore(score.initializeNode(parentOfTopCandidate, 0));
				}
				topCandidate = parentScore;
				parentOfTopCandidate = parentOfTopCandidate.parent();
			}
		}
		return topCandidate;
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
	
	public void setMinConent(int minConent) {
		this.minConent = minConent;
	}

}
