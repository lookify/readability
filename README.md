# Readability
Readable text extraction from HTML
```
<dependency>
    <groupId>co.lookify</groupId>
    <artifactId>readability</artifactId>
    <version>1.4</version>
</dependency>
```

# Start using
```
Analyze read = new Analyze();
Page page = read.parse("https://techcrunch.com/2018/04/05/machine-learning-zone-openai-competition-takes-on-sonic-the-hedgehog/");

System.out.println("Title: " + page.getTitle());
System.out.println("Byline: " + page.getMeta().getByline());
System.out.println("Excerpt: " + page.getMeta().getExcerpt());

final List<Block> blocks = page.getBlocks();
for (Block block : blocks) {
	System.out.println("====================================");
	System.out.println("id: " + block.getId());
	System.out.println("header: " + block.getHeader());
	System.out.println("author: " + block.getAuthor());
	System.out.println("date: " + block.getDate());
	System.out.println("tags: " + block.getTags());
	System.out.println(block.getContent());
}
```

## License

    Copyright (c) 2018 Vertex IT SIA

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
