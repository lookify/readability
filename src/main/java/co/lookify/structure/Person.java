package co.lookify.structure;

import java.util.HashMap;
import java.util.Map;

public class Person {

	private String fullName;

	private Map<String, Social> socials;

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public void addSocial(final String type, final String name, final String link) {
		if (socials == null) {
			socials = new HashMap<>();
		}
		Social social = socials.get(type);
		if (social == null) {
			social = new Social(type);
			socials.put(type, social);
		}
		social.setName(name);
		social.setLink(link);
	}

	public Map<String, Social> getSocials() {
		return socials;
	}

	@Override
	public String toString() {
		return fullName + ", socials=" + socials;
	}
}
