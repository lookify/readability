package co.lookify.structure;

import java.util.Map;

public class MetaData {

	private String byline;

	private String excerpt;

	private String title;

	private Map<String, String> feeds;

	public String getByline() {
		return byline;
	}

	public void setByline(String byline) {
		this.byline = byline;
	}

	public String getExcerpt() {
		return excerpt;
	}

	public void setExcerpt(String excerpt) {
		this.excerpt = excerpt;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Map<String, String> getFeeds() {
		return feeds;
	}

	public void setFeeds(Map<String, String> feeds) {
		this.feeds = feeds;
	}

}
