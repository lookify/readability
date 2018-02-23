package co.lookify.structure;

public class Social {

	private final String type;

	private String name;

	private String link;

	public Social(final String type) {
		this.type = type; // Objects.requireNonNull(type);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public String getType() {
		return type;
	}

	@Override
	public String toString() {
		return type + ": " + name;
	}

}
