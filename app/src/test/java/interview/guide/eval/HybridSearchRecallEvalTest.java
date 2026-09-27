package interview.guide.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 混合检索质量评测（手动运行，产出简历用的真实 Recall / MRR 数据）
 *
 * <p>方法：标准 QA 自检索。100 条 Java 面试 QA 作为文档入 vector_store（与生产知识块同表同链路），
 * 查询分两桶各 50 条：
 * <ul>
 *   <li>桶 A（qaId 0~49）语义变体：刻意换口语表述、不复读题干原词，模拟概念性提问——向量路强项</li>
 *   <li>桶 B（qaId 50~99）精确词面：直含术语 / 参数 / 组件名，模拟考点名精确检索——BM25 强项</li>
 * </ul>
 * 基线为普通 RAG（纯向量单路），对比 BM25 单路与 RRF 融合，指标 Recall@5 与 MRR。
 *
 * <p>运行方式（--no-daemon 保证环境变量透传到测试 JVM）：
 * <pre>RUN_EVAL=true ./gradlew --no-daemon :app:test --tests interview.guide.eval.HybridSearchRecallEvalTest</pre>
 * 评测数据带 metadata.eval=true 标记，结束自动清理，不污染生产数据。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class HybridSearchRecallEvalTest {

    private static final int TOP_K = 5;
    private static final int RRF_K = 60;

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 100 条标准 QA：{题干, 答案}，文档 = "问题：{题干}\n答案：{答案}"；0~49 为概念主题，50~99 为术语主题 */
    private static final List<String[]> QA = List.of(
        // ===== 桶 A 对应（qaId 0~49）：概念性主题 =====
        new String[]{"HashMap 在 JDK8 之后的底层结构做了什么优化", "数组 + 链表 + 红黑树：链表长度达到 8 且数组容量达到 64 时转红黑树，查询由 O(n) 优化到 O(log n)；扩容时元素要么留在原索引要么移动到原索引加 oldCap 的位置。"},
        new String[]{"synchronized 锁升级的过程", "无锁到偏向锁到轻量级锁到重量级锁：单线程访问用偏向锁，出现竞争升级为轻量级锁自旋，自旋失败膨胀为依赖操作系统 mutex 的重量级锁，锁只能升级不能降级。"},
        new String[]{"JVM 分代垃圾回收为什么分成年轻代和老年代", "对象存活周期差异大：绝大多数对象朝生夕死，年轻代用复制算法回收效率高；存活久的对象晋升老年代用标记整理，避免复制大对象的开销。"},
        new String[]{"MySQL InnoDB 为什么用 B+ 树而不是 B 树或哈希", "B+ 树非叶子节点只存键，扇出更大树更矮，磁盘 IO 次数少；叶子节点链表串联利于范围查询；哈希不支持范围扫描，B 树数据分散在所有节点扇出小。"},
        new String[]{"Redis 缓存雪崩的成因与应对", "大量 key 同时过期或 Redis 宕机导致请求全部打到数据库。应对：过期时间加随机抖动错峰、热点数据不过期或逻辑过期、集群高可用、限流降级兜底。"},
        new String[]{"TCP 三次握手为什么不能是两次", "两次握手无法防止历史重复连接请求：客户端旧的 SYN 延迟到达服务端会建立无效连接；三次握手让双方各自确认收发能力。"},
        new String[]{"Spring Bean 的生命周期", "实例化、属性填充、Aware 回调、BeanPostProcessor 前置、初始化、BeanPostProcessor 后置、使用、销毁回调，AOP 代理通常在后置处理器中生成。"},
        new String[]{"线程池的核心参数与执行流程", "corePoolSize、maximumPoolSize、keepAliveTime、workQueue、threadFactory、handler。提交任务先开核心线程，满了入队，队列满开非核心线程，到最大值触发拒绝策略。"},
        new String[]{"volatile 关键字的作用与局限", "保证可见性与禁止指令重排（内存屏障），但不保证原子性；典型场景是状态标志位与双检锁中的实例引用。"},
        new String[]{"MySQL 事务隔离级别与默认级别", "读未提交、读已提交、可重复读、串行化；InnoDB 默认可重复读，通过 MVCC 快照读与间隙锁防止幻读。"},
        new String[]{"Redis 持久化 RDB 与 AOF 的取舍", "RDB 定时快照文件小恢复快但可能丢最后一段数据；AOF 追加写命令可配每秒刷盘文件大恢复慢；4.0 后支持混合持久化。"},
        new String[]{"HTTPS 的握手过程", "客户端发随机数与加密套件，服务端返回证书，客户端验证后用公钥加密预主密钥，双方基于随机数推导会话密钥，后续对称加密通信。"},
        new String[]{"索引失效的常见场景", "对索引列做函数或运算、隐式类型转换、前导模糊查询、违反最左前缀、or 连接非索引列、优化器评估回表成本过高放弃索引。"},
        new String[]{"G1 收集器与 CMS 的区别", "G1 将堆划分为 Region 可预测停顿，标记整理不产生碎片；CMS 标记清除产生内存碎片，并发失败退化 Serial Old，JDK14 后移除。"},
        new String[]{"Redis 为什么快", "内存操作、单线程避免锁竞争、IO 多路复用 epoll、高效数据结构 SDS 跳表压缩列表，6.0 后网络 IO 多线程命令执行仍单线程。"},
        new String[]{"Spring 事务失效的场景", "方法非 public、同类内部调用绕过代理、异常被 catch 吞掉、受检异常未配置 rollbackFor、多线程调用、传播行为不当。"},
        new String[]{"TCP 与 UDP 的区别和选择", "TCP 面向连接可靠有序有拥塞控制；UDP 无连接不可靠延迟低开销小。文件支付用 TCP，实时音视频游戏用 UDP。"},
        new String[]{"ConcurrentHashMap JDK8 的并发实现", "取消分段锁，CAS 加 synchronized 锁单个桶头节点粒度更细；链表转红黑树；size 用 baseCount 加 CounterCell 分散计数，扩容多线程协助迁移。"},
        new String[]{"什么是 MVCC", "多版本并发控制：每行隐藏 trx_id 与 roll_pointer，undo log 构成版本链，读视图根据活跃事务判断可见版本，读不加锁不阻塞写。"},
        new String[]{"分布式锁用 Redis 怎么实现，有什么问题", "SET NX EX 加锁设过期，Lua 保证解锁原子，value 存唯一标识防误删；问题：业务超时锁失效需看门狗续期，主从切换锁丢失可用红锁或 zookeeper。"},
        new String[]{"JVM 调优的思路", "先定位再调优：jstat 看 GC 频率，jmap dump 分析对象；常用参数 -Xms -Xmx 设相等避免抖动，选 G1 或 ZGC，目标是降低 Full GC 与停顿。"},
        new String[]{"线程安全的单例怎么写", "双重检查锁 volatile 防指令重排，静态内部类利用类加载机制，枚举天然防反射与反序列化破坏。"},
        new String[]{"数据库连接池为什么要用", "复用连接避免握手开销、控制并发防打满数据库；HikariCP 用 FastList 与无锁 ConcurrentBag，性能最优。"},
        new String[]{"什么是一致性哈希", "将节点与 key 哈希到环上，key 顺时针找最近节点，节点增减只影响相邻区段，配合虚拟节点解决数据倾斜，用于分布式缓存与负载均衡。"},
        new String[]{"ThreadLocal 的原理与内存泄漏", "每个 Thread 持有 ThreadLocalMap，key 弱引用 value 强引用；线程池复用不 remove 会导致 value 无法回收，用完必须在 finally 中 remove。"},
        new String[]{"网关限流令牌桶与漏桶的区别", "漏桶恒定速率流出平滑突发；令牌桶按速率放令牌桶容量允许瞬时突发，更适合秒杀等突发场景。"},
        new String[]{"消息队列怎么保证消息不丢", "生产端确认与重试，Broker 主从同步刷盘或镜像队列，消费端手动 ack 幂等消费，配死信队列与对账补偿兜底。"},
        new String[]{"MySQL 主从复制的原理", "主库写 binlog，从库 IO 线程拉取写 relay log，SQL 线程重放；延迟用并行复制、半同步、强一致读走主库处理。"},
        new String[]{"AQS 的实现原理", "volatile state 表示同步状态，CLH 变体双向队列管理等待线程，获取失败入队 park，释放唤醒后继；ReentrantLock 与 Semaphore 都基于它。"},
        new String[]{"什么是 happens-before 规则", "JMM 定义的偏序关系：程序顺序、监视器锁、volatile、线程启动终止、传递性；存在 happens-before 则前者结果对后者可见。"},
        new String[]{"Spring 循环依赖三级缓存怎么解决", "一级成品二级早期引用三级 ObjectFactory；A 创建中暴露工厂，B 注入 A 的早期引用，A 完成后入一级。构造器与 prototype 无法解决。"},
        new String[]{"epoll 与 select 的区别", "select 每次全量拷贝 fd 集合线性扫描上限 1024；epoll 红黑树管理 fd，事件回调就绪链表只返回就绪 fd，适合海量连接。"},
        new String[]{"什么是 CAP 定理", "一致性、可用性、分区容错三者在网络分区下只能取二；分布式系统通常保 AP 或 CP，如注册中心 Nacos 支持 AP 与 CP 切换。"},
        new String[]{"JWT 的结构与优缺点", "头部、载荷、签名三段 Base64；无状态适合分布式多端，但签发后无法主动失效需配合 Redis 黑名单，载荷明文不能放敏感信息。"},
        new String[]{"MySQL 深分页怎么优化", "limit 深偏移扫描丢弃大量行；用游标法 where id 大于上次最大 id、延迟关联先查主键再回表、或覆盖索引。"},
        new String[]{"Redis 大 key 的危害与处理", "大 key 操作阻塞单线程、集群迁移卡顿、过期删除抖动；拆分为小 key、用 unlink 异步删除、渐进式迁移。"},
        new String[]{"什么是幻读，怎么防", "同一事务两次范围查询结果集不同；InnoDB 可重复读下快照读靠 MVCC 防幻读，当前读靠间隙锁 Next-Key Lock。"},
        new String[]{"HTTP 常见的请求方法与幂等性", "GET 查询幂等，PUT 全量更新幂等，DELETE 幂等，POST 非幂等；幂等性用于安全重试与接口设计。"},
        new String[]{"BIO NIO AIO 的区别", "BIO 一连接一线程阻塞；NIO 多路复用单线程管理多连接非阻塞；AIO 由操作系统回调完成，Linux 实现不成熟主流仍用 NIO。"},
        new String[]{"Spring 用了哪些设计模式", "工厂 BeanFactory、单例 Bean 默认作用域、代理 AOP、模板方法 JdbcTemplate、观察者事件机制、适配器 HandlerAdapter、策略 Resource 加载。"},
        new String[]{"数据库三范式与反范式", "一范式原子列、二范式消除部分依赖、三范式消除传递依赖；反范式适度冗余减少 join 提升读性能，互联网业务常混用。"},
        new String[]{"雪花算法的原理与时钟回拨问题", "时间戳加机器 id 加序列号生成趋势递增分布式 id；时钟回拨会导致重复，处理：等待追平、备用 id、异常位存储。"},
        new String[]{"什么是零拷贝", "传统 IO 四次拷贝四次上下文切换；零拷贝通过 mmap 或 sendfile 让数据不经用户态，Kafka 与 RocketMQ 大幅提升吞吐。"},
        new String[]{"死锁的四个必要条件与破坏", "互斥、持有并等待、不可剥夺、循环等待；破坏任一即可预防：一次性申请、可抢占锁、按序加锁、超时放弃。"},
        new String[]{"Redis 集群模式的数据分片", "16384 个哈希槽按 key 的 CRC16 分配到节点，客户端直连任意节点返回重定向，节点间 gossip 协议通信。"},
        new String[]{"数据库乐观锁与悲观锁", "悲观锁 select for update 先锁后操作适合写多；乐观锁版本号或 CAS 提交时校验适合读多，冲突多会大量重试。"},
        new String[]{"什么是内存屏障", "CPU 指令序列限制：禁止屏障两侧指令重排，强制刷写 store buffer 与 invalidate queue 保证可见性，volatile 的底层实现。"},
        new String[]{"接口幂等性怎么设计", "唯一索引防重复插入、token 机制先取令牌再校验消费、状态机约束流转、分布式锁、乐观锁版本控制。"},
        new String[]{"Netty 的线程模型", "主从 Reactor：boss 组接受连接注册到 worker 组，worker 处理读写，Pipeline 责任链编排处理器，无锁串行化避免竞争。"},
        new String[]{"GIL 是什么，对多线程的影响", "全局解释器锁同一时刻仅一个线程执行 Python 字节码；CPU 密集多线程无效用多进程，IO 密集协程或多线程仍有效。"},
        // ===== 桶 B 对应（qaId 50~99）：术语精确主题 =====
        new String[]{"ReentrantLock 的实现要点", "基于 AQS 的 state 与 CLH 队列，支持公平与非公平模式，可重入计数，Condition 等待队列配合 await signal。"},
        new String[]{"G1 的 Region 与 Humongous 对象", "G1 划分等大 Region，分代只是逻辑概念，Humongous 存放大对象跨越连续 Region，可预测停顿目标 PausePredictionModel。"},
        new String[]{"HikariCP 的 FastList 与 ConcurrentBag", "FastList 去掉范围检查按索引访问；ConcurrentBag 无锁借还连接，ThreadLocal 缓存优先减少竞争，这是它快的关键。"},
        new String[]{"paradedb 的 BM25 索引与 score 函数", "建 USING bm25 索引指定 key_field，查询用 @@@ 操作符，paradedb.score(id) 返回 BM25 相关性得分用于排序。"},
        new String[]{"pgvector 的 HNSW 索引与 cosine", "CREATE INDEX USING hnsw WITH (opclass vector_cosine_ops)，查询用 <=> 余弦距离操作符排序，近似检索高维向量。"},
        new String[]{"DashScope text-embedding-v4 的维度", "默认 1024 维，支持多语言中英混合，向量列维度需与模型一致否则插入失败。"},
        new String[]{"PostgresSaver 与 graphcheckpoint 表", "Spring AI Alibaba Graph 的 PostgresSaver 将工作流状态快照存 graphcheckpoint 表，threadId 隔离会话，支持从断点 next 节点续跑。"},
        new String[]{"Redisson 的看门狗 WatchDog 机制", "默认锁 30 秒，后台任务每 10 秒检查持有线程续期至 30 秒，防止业务未完成锁过期，显式传 TTL 则不启用。"},
        new String[]{"Redis Lua 脚本原子性与 evalSha", "Redis 单线程执行 Lua 脚本天然原子；脚本先 scriptLoad 取 SHA1，之后 evalSha 传摘要避免每次传输脚本文本。"},
        new String[]{"RRF 倒数排名融合公式", "score(d) 等于各路 1 除以 (k 加 rank)，k 常取 60 平滑头部差异，无需调权即可融合多路排序。"},
        new String[]{"CompletableFuture 的 allOf 与异常传播", "allOf 等待全部完成但异常包装在 CompletionException，需逐个 join 捕获；thenApply 不吞异常，exceptionally 兜底。"},
        new String[]{"ThreadLocalMap 的开放寻址与弱引用", "Entry 继承 WeakReference，key 被回收变 stale entry，清理靠 expungeStaleEntry 顺带清理，开放寻址线性探测。"},
        new String[]{"InnoDB 的 Next-Key Lock", "记录锁加间隙锁的组合，锁住索引记录及其前面的区间，可重复读级别下当前读防幻读，唯一索引命中退化为记录锁。"},
        new String[]{"undo log 与 redo log 的分工", "undo 逻辑日志记录反向操作用于回滚与 MVCC 版本链；redo 物理日志先写日志后写数据用于崩溃恢复，WAL 核心。"},
        new String[]{"MySQL binlog 三种格式与 GTID", "statement 记录 SQL 可能主从不一致，row 记录行变更数据量大最安全，mixed 自动切换；GTID 替代文件位点简化复制。"},
        new String[]{"Spring AI 的 Advisor 链", "Advisor 在 ChatClient 调用链前后拦截，如 Memory 与 RAG 检索增强，order 控制顺序，类似中间件洋葱模型。"},
        new String[]{"Spring AI ChatClient 的 Prompt options 热切换", "ChatClient 调用链会在 Prompt 注入缓存的旧 options，切换模型后用 delegate 的 model 名与温度强制覆盖保证一致。"},
        new String[]{"MCP 协议的 tools 与 sampling", "Model Context Protocol 标准化模型与外部工具交互：tools 服务端暴露可调用能力，sampling 允许服务端请求客户端补全。"},
        new String[]{"pg_search 的 JSONPath 过滤", "metadata::jsonb @@ '$.kb_id == \"1\"' 的 jsonpath 语法在 BM25 检索上叠加业务过滤，paradedb 支持。"},
        new String[]{"K8s 的 Deployment 与 StatefulSet", "Deployment 无状态副本滚动更新；StatefulSet 稳定网络标识与有序伸缩，配合 PVC 适合数据库类有状态负载。"},
        new String[]{"gossip 协议在 Redis Cluster 的作用", "节点间 PING PONG 传播集群拓扑与故障检测，半数以上主节点判定下线触发故障转移。"},
        new String[]{"Linux epoll 的 ET 与 LT 模式", "水平触发未处理完持续通知；边缘触发只在状态变化通知一次，必须非阻塞读写到 EAGAIN，减少事件唤醒。"},
        new String[]{"tcpdump 与 wireshark 的排查场景", "tcpdump 抓包过滤 host port 生成 pcap，wireshark 图形分析握手重传与窗口，排查连接重置与延迟。"},
        new String[]{"arthas 的常用命令", "dashboard 总览、thread 定位阻塞、watch 观察方法出入参、trace 耗时链路、heapdump 导出快照，在线排查利器。"},
        new String[]{"git 的 cherry-pick 与 rebase", "cherry-pick 摘取单个提交到当前分支；rebase 变基重放提交线性历史，黄金法则不 rebase 已推送公共分支。"},
        new String[]{"JVM 参数 Xms Xmx 与 SurvivorRatio", "初始与最大堆设相等避免动态扩容抖动；SurvivorRatio 新生代 eden 与两个 survivor 的比例默认 8:1:1。"},
        new String[]{"arthas ognl 表达式用法", "ognl 可静态访问与表达式求值，配合 vmtool 查找堆内实例，sc sm 查看类与方法签名。"},
        new String[]{"Nacos 的 AP 与 CP 模式", "临时实例走 Distro 协议 AP 最终一致，持久实例走 Raft CP 强一致，服务发现 AP 优先配置中心 CP。"},
        new String[]{"Seata AT 模式原理", "代理数据源生成前后镜像 undo log，一阶段提交二阶段异步删除镜像，回滚按镜像反向补偿，全局锁防脏写。"},
        new String[]{"ShardingSphere 分片键与广播表", "分片键决定数据路由，哈希取模或范围分片；广播表每个库全量冗余存字典类数据，绑定表避免笛卡尔 join。"},
        new String[]{"Canal 的工作原理", "伪装 MySQL slave 订阅 binlog，解析变更事件投递 MQ，下游消费实现缓存失效与异构同步。"},
        new String[]{"PromQL 的 rate 与 histogram_quantile", "rate 计算计数器每秒增速，histogram_quantile 由桶分布估算分位数如 P99，Grafana 常用聚合。"},
        new String[]{"Docker overlay 网络与 namespace", "容器网络命名空间由 veth pair 连接，overlay 网络跨主机 VXLAN 隧道，bridge 模式 NAT 端口映射。"},
        new String[]{"Go 的 GMP 调度模型", "G 协程 M 线程 P 处理器上下文，P 本地队列与全局队列窃取，阻塞时 M 让出 P，支撑海量轻量并发。"},
        new String[]{"Rust 的所有权与生命周期", "值有唯一所有权，move 语义转移，borrow 检查器编译期保证无数据竞争，生命周期标注引用有效范围。"},
        new String[]{"Kafka 的 ISR 与 acks 配置", "ISR 同步副本集合，acks 0 不等 1 等 leader all 等全部 ISR，min.insync.replicas 配合保证不丢。"},
        new String[]{"Elasticsearch 倒排索引与段合并", "分词构建 term 到文档的倒排，segment 不可变近实时搜索，后台 merge 合并小段减少句柄与查询开销。"},
        new String[]{"Raft 的选举与日志复制", "term 任期随机超时选举 leader，日志按 index 复制多数派确认提交，脑裂时旧 term 提交无效。"},
        new String[]{"netty 的 ByteBuf 与零拷贝", "读写双指针省 flip，池化 PooledByteBuf 减少 GC，CompositeByteBuf 逻辑合并免拷贝，slice duplicate 共享底层。"},
        new String[]{"Caffeine 的 W-TinyLFU 淘汰", "频率草图记录访问频率，新条目需击败窗口内候选才准入，兼顾频率与时效，命中率高于 LRU。"},
        new String[]{"Redis 的 SDS 与跳表", "SDS 记录长度 O(1) 获取、二进制安全、空间预分配；跳表多层索引平均 O(log N)，范围查询友好替代平衡树。"},
        new String[]{"Java21 虚拟线程的调度与钉住", "JVM 调度到载体平台线程，阻塞时代价小；synchronized 块内阻塞会钉住载体线程，建议改 ReentrantLock。"},
        new String[]{"React 的 Fiber 与 Hooks 规则", "Fiber 可中断渲染分片调度；Hooks 只能在顶层调用因为依赖链表顺序索引，不能条件分支。"},
        new String[]{"Vue3 的 Proxy 响应式与 patch", "Proxy 代理整个对象拦截读写收集依赖，编译时静态标记 patchFlag 跳过静态节点，diff 双端比对新旧 children。"},
        new String[]{"PGSQL 的 MVCC 与 vacuum", "多版本元组 xmin xmax，更新即插入新版本；死元组靠 vacuum 回收空间，autovacuum 阈值触发，长事务阻塞清理。"},
        new String[]{"PGSQL 的 explain analyze 与 buffers", "explain analyze 实际执行显示每步真实耗时，buffers 披露共享块命中与磁盘读，定位 IO 瓶颈。"},
        new String[]{"JFR 与 JMC 飞行记录", "Java Flight Recorder 低开销持续采集事件，JMC 图形分析分配热点锁竞争 GC，适合生产在线诊断。"},
        new String[]{"gradle configuration cache 与 build cache", "配置缓存序列化配置阶段结果，同配置跳过重算；构建缓存按任务输入哈希复用输出，CI 加速明显。"},
        new String[]{"terraform 的 state 与 provider", "state 记录基础设施实态与漂移检测，provider 对接各云 API 声明式 diff 应用，远程 backend 加锁协作。"},
        new String[]{"OpenTelemetry 的 trace 传播", "W3C traceparent 头跨服务传播 traceId spanId，collector 统一接收导出，span 单次调用记录 parentId 属性事件。"}
    );

    /** 桶 A：语义变体查询（对应 qaId 0~49，口语化换表述） */
    private static final List<String> QUERIES_SEMANTIC = List.of(
        "jdk1.8 之后 map 里面链表太长会怎么处理",
        "sync 锁从轻到重是怎么一步步变重的",
        "jvm 堆为什么切两半分开回收",
        "innodb 索引结构为啥不用哈希表",
        "redis 大量键同一时间过期打垮数据库怎么办",
        "tcp 建连为什么非要三个包两次不行吗",
        "spring 容器创建一个 bean 到能用的完整过程",
        "池子提交任务时先开线程还是先进队列",
        "volatile 能不能保证自增是对的",
        "innodb 默认隔离级别，幻读怎么防",
        "redis 掉电数据怎么恢复，两种方案怎么选",
        "https 证书校验完到对称加密中间发生了什么",
        "哪些写法会让索引白建了",
        "cms 被淘汰新一代收集器好在哪",
        "redis 单线程为啥还这么快",
        "加了注解事物没生效有哪些坑",
        "直播连麦用可靠传输还是不可靠的，为什么",
        "1.8 的并发 map 还分段吗",
        "innodb 读的时候怎么不加锁的",
        "redis 做分布式锁有什么坑",
        "线上频繁整堆回收从哪里入手查",
        "单例模式线程安全的几种写法",
        "为什么要池化数据库连接",
        "缓存集群加减节点数据怎么搬迁最少",
        "threadlocal 在线程池里的坑",
        "限流算法突发流量选哪种",
        "mq 怎么保证一条消息都不丢",
        "主库写入从库查不到怎么回事",
        "aqs 到底是个什么东西",
        "java 内存模型里可见性怎么判定",
        "spring 俩 bean 互相引用为啥不死锁",
        "高并发网络编程里两种多路复用怎么选",
        "分布式系统一致性可用性怎么取舍",
        "token 方案签发后想踢人下线怎么办",
        "列表翻页翻到很后面很慢怎么优化",
        "redis 里超大字段有什么问题",
        "同一事务两次查出来条数不一样怎么回事",
        "哪些请求方法是天然可以安全重试的",
        "同步阻塞和同步非阻塞 io 的区别",
        "spring 框架里都用了哪些经典设计模式",
        "表设计什么时候要故意留冗余",
        "全局唯一 id 机器时钟回拨了怎么办",
        "kafka 为什么快，和拷贝次数的关系",
        "死锁怎么形成的，怎么破",
        "redis 集群的数据是怎么分到各节点的",
        "并发扣减库存用悲观方式还是乐观方式",
        "volatile 底层靠什么指令实现的",
        "用户重复点提交怎么防",
        "netty 的线程分配是怎样的",
        "python 多线程为什么跑不满 CPU"
    );

    /** 桶 B：精确词面查询（对应 qaId 50~99，直含术语/组件名） */
    private static final List<String> QUERIES_EXACT = List.of(
        "ReentrantLock AQS 公平锁 Condition",
        "G1 Region Humongous 停顿预测",
        "HikariCP FastList ConcurrentBag",
        "paradedb BM25 score @@@",
        "pgvector HNSW cosine <=>",
        "text-embedding-v4 1024 维",
        "PostgresSaver graphcheckpoint threadId",
        "Redisson watchdog 看门狗续期",
        "Redis Lua evalSha scriptLoad",
        "RRF reciprocal rank fusion k=60",
        "CompletableFuture allOf CompletionException",
        "ThreadLocalMap WeakReference expungeStaleEntry",
        "Next-Key Lock 间隙锁 幻读",
        "undo log redo log WAL",
        "binlog statement row mixed GTID",
        "Spring AI Advisor ChatClient",
        "Prompt options 热切换 model 名",
        "MCP tools sampling 协议",
        "pg_search jsonpath kb_id 过滤",
        "Deployment StatefulSet PVC",
        "gossip PING PONG 故障转移",
        "epoll ET LT EAGAIN",
        "tcpdump pcap wireshark 重传",
        "arthas watch trace thread",
        "cherry-pick rebase 黄金法则",
        "-Xms -Xmx SurvivorRatio 8:1:1",
        "arthas ognl vmtool sc sm",
        "Nacos Distro Raft AP CP",
        "Seata AT undo log 全局锁",
        "ShardingSphere 分片键 广播表",
        "Canal binlog 伪装 slave",
        "PromQL rate histogram_quantile P99",
        "docker overlay veth VXLAN namespace",
        "Go GMP scheduler work stealing",
        "Rust borrow checker lifetime",
        "Kafka ISR acks min.insync.replicas",
        "Elasticsearch inverted index segment merge",
        "Raft term election log replication",
        "netty ByteBuf CompositeByteBuf slice",
        "Caffeine W-TinyLFU 频率草图",
        "Redis SDS ziplist skiplist",
        "Java21 virtual thread pinned synchronized",
        "React Fiber Hooks 链表顺序",
        "Vue3 Proxy patchFlag diff",
        "PGSQL vacuum autovacuum 死元组",
        "explain analyze buffers 共享块",
        "JFR JMC flight recorder",
        "gradle configuration cache build cache",
        "terraform state provider 后端加锁",
        "OpenTelemetry traceparent collector span"
    );

    @Test
    void evalRecall() {
        try {
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata::jsonb @@ '$.eval == true'");
            for (int i = 0; i < QA.size(); i++) {
                String doc = "问题：" + QA.get(i)[0] + "\n答案：" + QA.get(i)[1];
                float[] vec = embeddingModel.embed(doc);
                jdbcTemplate.update(
                        "INSERT INTO vector_store (content, metadata, embedding) VALUES (?, ?::json, ?::vector)",
                        doc, "{\"eval\": true, \"qaId\": " + i + "}", toVectorLiteral(vec));
            }
            System.out.println("[EVAL] 已入库 " + QA.size() + " 条评测文档（桶A 50 + 桶B 50）");

            int[] vecHit = {0, 0}, bm25Hit = {0, 0}, fuseHit = {0, 0}, wfuseHit = {0, 0};
            double[] vecMrr = {0, 0}, bm25Mrr = {0, 0}, fuseMrr = {0, 0}, wfuseMrr = {0, 0};
            int[] bucketSize = {QUERIES_SEMANTIC.size(), QUERIES_EXACT.size()};

            List<List<String>> buckets = List.of(QUERIES_SEMANTIC, QUERIES_EXACT);
            for (int b = 0; b < 2; b++) {
                List<String> queries = buckets.get(b);
                for (int i = 0; i < queries.size(); i++) {
                    int qaId = b * 50 + i;
                    String query = queries.get(i);
                    float[] qVec = embeddingModel.embed(query);

                    List<String> vecRank = jdbcTemplate.queryForList(
                            "SELECT metadata->>'qaId' FROM vector_store WHERE metadata::jsonb @@ '$.eval == true' " +
                                    "ORDER BY embedding <=> ?::vector LIMIT " + TOP_K, String.class, toVectorLiteral(qVec));
                    // paradedb @@@ 的查询解析器不接受 -、=、<、> 等符号，清洗为空格分词
                    String bm25Query = query.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
                    List<String> bm25Rank = jdbcTemplate.queryForList(
                            "SELECT metadata->>'qaId' FROM vector_store " +
                                    "WHERE content @@@ ? AND metadata::jsonb @@ '$.eval == true' " +
                                    "ORDER BY paradedb.score(id) DESC LIMIT " + TOP_K, String.class, bm25Query);
                    List<String> fuseRank = rrfFuse(vecRank, bm25Rank);
                    List<String> wfuseRank = weightedFuse(vecRank, bm25Rank);

                    String target = String.valueOf(qaId);
                    if (vecRank.contains(target)) { vecHit[b]++; vecMrr[b] += 1.0 / (vecRank.indexOf(target) + 1); }
                    if (bm25Rank.contains(target)) { bm25Hit[b]++; bm25Mrr[b] += 1.0 / (bm25Rank.indexOf(target) + 1); }
                    if (fuseRank.contains(target)) { fuseHit[b]++; fuseMrr[b] += 1.0 / (fuseRank.indexOf(target) + 1); }
                    if (wfuseRank.contains(target)) { wfuseHit[b]++; wfuseMrr[b] += 1.0 / (wfuseRank.indexOf(target) + 1); }
                }
            }

            String[] names = {"桶A 语义变体", "桶B 精确词面"};
            System.out.println("\n[EVAL] ===== 混合检索评测（n=100, top" + TOP_K + "）=====");
            for (int b = 0; b < 2; b++) {
                int n = bucketSize[b];
                System.out.printf("[EVAL] %s（n=%d）: 向量 R@%d=%.0f%% MRR=%.3f | BM25 R@%d=%.0f%% MRR=%.3f | 融合 R@%d=%.0f%% MRR=%.3f%n",
                        names[b], n, TOP_K, 100.0 * vecHit[b] / n, vecMrr[b] / n,
                        TOP_K, 100.0 * bm25Hit[b] / n, bm25Mrr[b] / n,
                        TOP_K, 100.0 * fuseHit[b] / n, fuseMrr[b] / n);
            }
            int total = bucketSize[0] + bucketSize[1];
            System.out.printf("[EVAL] 总体（n=%d）: 向量（普通RAG基线）R@%d=%.0f%% MRR=%.3f | BM25 R@%d=%.0f%% | 等权RRF R@%d=%.0f%% MRR=%.3f | 加权RRF R@%d=%.0f%% MRR=%.3f%n",
                    total, TOP_K, 100.0 * (vecHit[0] + vecHit[1]) / total, (vecMrr[0] + vecMrr[1]) / total,
                    TOP_K, 100.0 * (bm25Hit[0] + bm25Hit[1]) / total,
                    TOP_K, 100.0 * (fuseHit[0] + fuseHit[1]) / total, (fuseMrr[0] + fuseMrr[1]) / total,
                    TOP_K, 100.0 * (wfuseHit[0] + wfuseHit[1]) / total, (wfuseMrr[0] + wfuseMrr[1]) / total);
        } finally {
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata::jsonb @@ '$.eval == true'");
            System.out.println("[EVAL] 评测数据已清理");
        }
    }

    private List<String> rrfFuse(List<String> rankA, List<String> rankB) {
        return fuse(rankA, rankB, 1.0, 1.0);
    }

    /** 加权 RRF：主路（向量）权重高于补充路（BM25），抑制弱路噪声对排序的稀释 */
    private List<String> weightedFuse(List<String> rankA, List<String> rankB) {
        return fuse(rankA, rankB, 0.7, 0.3);
    }

    private List<String> fuse(List<String> rankA, List<String> rankB, double wA, double wB) {
        Map<String, Double> scores = new HashMap<>();
        for (int r = 0; r < rankA.size(); r++) {
            scores.merge(rankA.get(r), wA / (RRF_K + r + 1), Double::sum);
        }
        for (int r = 0; r < rankB.size(); r++) {
            scores.merge(rankB.get(r), wB / (RRF_K + r + 1), Double::sum);
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(TOP_K)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String toVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
