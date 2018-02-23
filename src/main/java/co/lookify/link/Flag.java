package co.lookify.link;

public class Flag {

	public static final int STRIP_UNLIKELYS = 1;

	public static final int WEIGHT_CLASSES = 1 << 1;

	public static final int CLEAN_CONDITIONALLY = 1 << 2;

	public static final int COMMENTS = 1 << 3;

	private int flags;

	public Flag() {
		flags = Flag.STRIP_UNLIKELYS  | Flag.WEIGHT_CLASSES | Flag.CLEAN_CONDITIONALLY | Flag.COMMENTS;
	}

	public boolean isActive(int flag) {
		return (flags & flag) > 0;
	}

	public void removeFlag(int flag) {
		this.flags = this.flags & ~flag;
	}

}
