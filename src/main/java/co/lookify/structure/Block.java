package co.lookify.structure;

import java.util.Date;
import java.util.Set;

import org.jsoup.nodes.Element;

public class Block {

	private String id;

	private String header;

	private Element el;

	private String content;

	private Person author;

	private Date date;

	private String direction;

	private Set<String> tags;

	public Set<String> getTags() {
		return tags;
	}

	public void setTags(Set<String> tags) {
		this.tags = tags;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getHeader() {
		return header;
	}

	public void setHeader(String header) {
		this.header = header;
	}

	public Person getAuthor() {
		return author;
	}

	public void setAuthor(Person author) {
		this.author = author;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getDirection() {
		return direction;
	}

	public void setDirection(String direction) {
		this.direction = direction;
	}

	public Element getEl() {
		return el;
	}

	public void setEl(Element el) {
		this.el = el;
	}

}
