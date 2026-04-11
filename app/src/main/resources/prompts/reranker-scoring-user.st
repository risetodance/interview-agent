用户查询关键词：{{keywords}}

请对以下搜索结果进行相关性打分（0-10分）：

{{candidates}}

返回格式要求：
- 直接输出 JSON 数组，不要包含 Markdown 代码块标签（如 ``` json）
- 每个元素包含：source(来源，枚举值：questionBank/knowledgeBase/webSearch)、index(原始索引，从0开始)、score(相关性分数，0-10的浮点数)
- 按原始索引顺序返回所有结果的打分

JSON 数组元素格式：
- source: 字符串，来源类型
- index: 整数，原始索引
- score: 浮点数，相关性分数（0-10）