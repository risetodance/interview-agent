# Input Data
请根据以下候选人简历内容，生成共 {questionCount} 个主问题。
每个主问题必须额外生成 {followUpCount} 个追问。

## 问题分布要求
| 类型 | 数量 | 说明 |
|------|------|------|
| 项目经历 (PROJECT) | {projectCount} 题 | 基于简历中的具体项目深度提问 |
| MySQL | {mysqlCount} 题 | 索引、事务、SQL 优化等 |
| Redis | {redisCount} 题 | 数据结构、缓存策略、分布式锁等 |
| Java 基础 (JAVA_BASIC) | {javaBasicCount} 题 | 面向对象、JVM、异常处理等 |
| Java 集合 (JAVA_COLLECTION) | {javaCollectionCount} 题 | 集合框架、源码原理等 |
| Java 并发 (JAVA_CONCURRENT) | {javaConcurrentCount} 题 | 线程、锁、并发工具类等 |
| Spring/Spring Boot | {springCount} 题 | IoC/AOP、自动配置等 |

## 候选人简历
---简历内容开始---
{resumeText}
---简历内容结束---

## 历史提问记录
以下是该候选人已经回答过的问题列表，请在生成新问题时**尽可能避免重复**（除非是需要从不同维度深度追问同一主题）：
---历史提问开始---
{historicalQuestions}
---历史提问结束---

## 输出要求
请严格按照 JSON 格式输出问题列表，直接输出 JSON 对象，不要包含任何 Markdown 代码块标签。
