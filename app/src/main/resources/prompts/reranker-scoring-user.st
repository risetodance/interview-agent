用户查询关键词：{{keywords}}

请对以下搜索结果进行相关性打分（0-10分）：

{{candidates}}

返回格式要求：
- 直接输出 JSON 对象，不要包含 Markdown 代码块标签（如 ``` json）
- 对象包含一个 results 字段，值为 JSON 数组
- 按原始索引顺序返回所有搜索结果的打分

字段说明：
- results：数组，包含所有搜索结果的打分，顺序与输入的搜索结果一致
- source：字符串，该搜索结果的来源，只能取以下三个枚举值之一：
    - questionBank：来自题库全文搜索
    - knowledgeBase：来自知识库向量搜索
    - webSearch：来自网络搜索
- index：整数，该搜索结果在输入候选列表中的原始位置（从 0 开始，与输入顺序一一对应）
- score：浮点数，相关性分数（0-10），10 分为高度相关，0 分为完全不相关

输出格式示例：
{
  "results": [
    {"source": "questionBank", "index": 0, "score": 8.5},
    {"source": "knowledgeBase", "index": 1, "score": 6.0}
  ]
}