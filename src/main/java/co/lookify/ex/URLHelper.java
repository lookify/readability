package co.lookify.ex;

import java.net.URI;
import java.util.regex.Pattern;

public final class URLHelper {

	private static final Pattern ABSOLUTE_URI = Pattern.compile("^[a-zA-Z][a-zA-Z0-9\\+\\-\\.]*:");

	private URLHelper() {

	}

	public static String toAbsoluteURI(URI uri, String url) {
		if (ABSOLUTE_URI.matcher(url).find()) {
			return url;
		}
		if (url.length() >= 2 && "//".equals(url.substring(0, 2))) {
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
		
		int k = 0;
		String[] urlParts = url.split("/");
		int startSegment = ".".equals(urlParts[0]) ? 1 : 0;
		for(int i = startSegment; i < urlParts.length; i++) {
			if("..".equals(urlParts[i])) {
				k++;
			} else {
				break;
			}
		}
		
		String path = uri.getPath();
		if(path != null && path.length() > 0) {
			String[] pathParts = path.split("/");
			for(int i = 0; i < pathParts.length - k; i++) {
				sb.append("/");
				sb.append(pathParts[i]);	
			}
		}
		for(int i = startSegment + k; i < urlParts.length; i++) {
			sb.append("/");
			sb.append(urlParts[i]);
		}
		return sb.toString();
	}
}
