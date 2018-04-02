package co.lookify.structure;

import java.util.ArrayList;
import java.util.List;

public class Page {
	private String id;

	private MetaData meta;

	private List<Block> blocks;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public MetaData getMeta() {
		return meta;
	}

	public void setMeta(MetaData meta) {
		this.meta = meta;
	}

	public List<Block> getBlocks() {
		return blocks;
	}

	public void setBlocks(List<Block> blocks) {
		this.blocks = blocks;
	}

	public void addBlock(Block block) {
		if (blocks == null) {
			blocks = new ArrayList<>();
		}
		blocks.add(block);
	}

	public String getFirstBlockContent() {
		if (blocks != null && !blocks.isEmpty()) {
			return blocks.get(0).getContent();
		}
		return null;
	}

	public String getContent() {
		StringBuilder builder = new StringBuilder();
		for (Block block : blocks) {
			if (builder.length() > 0) {
				builder.append(" ");
			}
			builder.append(block.getContent());
		}
		return builder.toString();
	}

	public String getTitle() {
		return meta == null ? null : meta.getTitle();
	}

}
