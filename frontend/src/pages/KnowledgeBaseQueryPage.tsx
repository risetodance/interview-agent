import { useEffect, useState, useRef, useTransition, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Virtuoso, type VirtuosoHandle } from 'react-virtuoso';
import { knowledgeBaseApi, type KnowledgeBaseItem, type SortOption } from '../api/knowledgebase';
import { ragChatApi, type RagChatSessionListItem } from '../api/ragChat';
import { formatDateOnly } from '../utils/date';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import CodeBlock from '../components/CodeBlock';
import {
  Plus,
  Trash2,
  ChevronLeft,
  ChevronRight,
  Edit,
  Pin,
  Brain,
  ChevronDown,
  Loader2,
} from 'lucide-react';

interface KnowledgeBaseQueryPageProps {
  onBack: () => void;
  onUpload: () => void;
}

interface Message {
  id?: number;
  type: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

interface ThinkBlock {
  content: string;       // think 内容
  isComplete: boolean;   // 是否收到闭标签
}

interface CategoryGroup {
  name: string;
  items: KnowledgeBaseItem[];
  isExpanded: boolean;
}

export default function KnowledgeBaseQueryPage({ onBack, onUpload }: KnowledgeBaseQueryPageProps) {
  // 知识库状态
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [selectedKbIds, setSelectedKbIds] = useState<Set<number>>(new Set());
  const [loadingList, setLoadingList] = useState(true);

  // 搜索和排序状态
  const [searchKeyword, setSearchKeyword] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('time');
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set(['未分类']));

  // 右侧面板状态
  const [rightPanelOpen, setRightPanelOpen] = useState(true);

  // 左侧面板状态（对话历史）
  const [leftPanelOpen, setLeftPanelOpen] = useState(true);

  // 会话状态
  const [sessions, setSessions] = useState<RagChatSessionListItem[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [currentSessionTitle, setCurrentSessionTitle] = useState<string>('');
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sessionDeleteConfirm, setSessionDeleteConfirm] = useState<{ id: number; title: string } | null>(null);
  const [editingSessionTitle, setEditingSessionTitle] = useState<{ id: number; title: string } | null>(null);
  const [newSessionTitle, setNewSessionTitle] = useState('');

  // 消息状态
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);

  // 是否正在等待首字符响应
  const [waitingForFirstChunk, setWaitingForFirstChunk] = useState(false);

  // Think 标签展开状态
  const [expandedThinks, setExpandedThinks] = useState<Set<string>>(new Set());

  // refs
  const virtuosoRef = useRef<VirtuosoHandle>(null);
  const rafRef = useRef<number>();

  const [, startTransition] = useTransition();

  useEffect(() => {
    loadKnowledgeBases();
    loadSessions();
  }, []);

  useEffect(() => {
    if (!searchKeyword) {
      loadKnowledgeBases();
    }
  }, [sortBy]);

  const loadKnowledgeBases = async () => {
    setLoadingList(true);
    try {
      // 问答助手只显示向量化完成的知识库
      const list = await knowledgeBaseApi.getAllKnowledgeBases(sortBy, 'COMPLETED');
      setKnowledgeBases(list);
    } catch (err) {
      console.error('加载知识库列表失败', err);
    } finally {
      setLoadingList(false);
    }
  };

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      loadKnowledgeBases();
      return;
    }
    setLoadingList(true);
    try {
      const list = await knowledgeBaseApi.search(searchKeyword.trim());
      setKnowledgeBases(list);
    } catch (err) {
      console.error('搜索知识库失败', err);
    } finally {
      setLoadingList(false);
    }
  };

  const groupedKnowledgeBases = useMemo((): CategoryGroup[] => {
    const groups: Map<string, KnowledgeBaseItem[]> = new Map();

    knowledgeBases.forEach(kb => {
      const category = kb.category || '未分类';
      if (!groups.has(category)) {
        groups.set(category, []);
      }
      groups.get(category)!.push(kb);
    });

    const result: CategoryGroup[] = [];
    const sortedCategories = Array.from(groups.keys()).sort((a, b) => {
      if (a === '未分类') return 1;
      if (b === '未分类') return -1;
      return a.localeCompare(b);
    });

    sortedCategories.forEach(name => {
      result.push({
        name,
        items: groups.get(name)!,
        isExpanded: expandedCategories.has(name),
      });
    });

    return result;
  }, [knowledgeBases, expandedCategories]);

  const toggleCategory = (category: string) => {
    setExpandedCategories(prev => {
      const next = new Set(prev);
      if (next.has(category)) {
        next.delete(category);
      } else {
        next.add(category);
      }
      return next;
    });
  };

  const loadSessions = async () => {
    setLoadingSessions(true);
    try {
      const list = await ragChatApi.listSessions();
      setSessions(list);
    } catch (err) {
      console.error('加载会话列表失败', err);
    } finally {
      setLoadingSessions(false);
    }
  };

  const handleToggleKb = (kbId: number) => {
    setSelectedKbIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(kbId)) {
        newSet.delete(kbId);
      } else {
        newSet.add(kbId);
      }
      if (newSet.size !== prev.size && currentSessionId) {
        setCurrentSessionId(null);
        setCurrentSessionTitle('');
        setMessages([]);
      }
      return newSet;
    });
  };

  const handleNewSession = () => {
    setCurrentSessionId(null);
    setCurrentSessionTitle('');
    setMessages([]);
  };

  const handleLoadSession = async (sessionId: number) => {
    try {
      const detail = await ragChatApi.getSessionDetail(sessionId);
      setCurrentSessionId(detail.id);
      setCurrentSessionTitle(detail.title);
      setSelectedKbIds(new Set(detail.knowledgeBases.map(kb => kb.id)));
      setMessages(detail.messages.map(m => ({
        id: m.id,
        type: m.type,
        content: m.content,
        timestamp: new Date(m.createdAt),
      })));
    } catch (err) {
      console.error('加载会话失败', err);
    }
  };

  const handleDeleteSession = async () => {
    if (!sessionDeleteConfirm) return;
    try {
      await ragChatApi.deleteSession(sessionDeleteConfirm.id);
      await loadSessions();
      if (currentSessionId === sessionDeleteConfirm.id) {
        handleNewSession();
      }
      setSessionDeleteConfirm(null);
    } catch (err) {
      console.error('删除会话失败', err);
    }
  };

  const handleEditSessionTitle = (sessionId: number, currentTitle: string) => {
    setEditingSessionTitle({ id: sessionId, title: currentTitle });
    setNewSessionTitle(currentTitle);
  };

  const handleSaveSessionTitle = async () => {
    if (!editingSessionTitle || !newSessionTitle.trim()) return;
    try {
      await ragChatApi.updateSessionTitle(editingSessionTitle.id, newSessionTitle.trim());
      await loadSessions();
      if (currentSessionId === editingSessionTitle.id) {
        setCurrentSessionTitle(newSessionTitle.trim());
      }
      setEditingSessionTitle(null);
      setNewSessionTitle('');
    } catch (err) {
      console.error('更新会话标题失败', err);
    }
  };

  const handleTogglePin = async (sessionId: number, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await ragChatApi.togglePin(sessionId);
      await loadSessions();
    } catch (err) {
      console.error('切换置顶状态失败', err);
    }
  };

  const formatMarkdown = (text: string): string => {
    if (!text) return '';
    return text
      // 处理转义换行符
      .replace(/\\n/g, '\n')
      // 确保标题 # 后有空格
      .replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2')
      // 确保有序列表数字后有空格（如 1.xxx -> 1. xxx）
      .replace(/^(\s*)(\d+)\.([^\s\n])/gm, '$1$2. $3')
      // 确保无序列表 - 或 * 后有空格
      .replace(/^(\s*[-*])([^\s\n-])/gm, '$1 $2')
      // 压缩多余空行
      .replace(/\n{3,}/g, '\n\n');
  };

  const handleSubmitQuestion = async () => {
    if (!question.trim() || selectedKbIds.size === 0 || loading) return;

    const userQuestion = question.trim();
    setQuestion('');
    setLoading(true);

    let sessionId = currentSessionId;
    if (!sessionId) {
      try {
        const session = await ragChatApi.createSession(Array.from(selectedKbIds));
        sessionId = session.id;
        setCurrentSessionId(sessionId);
        setCurrentSessionTitle(session.title);
        // 创建会话后立即刷新会话列表
        await loadSessions();
      } catch (err) {
        console.error('创建会话失败', err);
        setLoading(false);
        return;
      }
    }

    const userMessage: Message = {
      type: 'user',
      content: userQuestion,
      timestamp: new Date(),
    };
    setMessages(prev => [...prev, userMessage]);

    const assistantMessage: Message = {
      type: 'assistant',
      content: '',
      timestamp: new Date(),
    };
    setMessages(prev => [...prev, assistantMessage]);

    // 重置等待状态
    setWaitingForFirstChunk(true);

    let fullContent = '';
    let hasReceivedFirstChunk = false;
    const updateAssistantMessage = (content: string) => {
      setMessages(prev => {
        const newMessages = [...prev];
        const lastIndex = newMessages.length - 1;
        if (lastIndex >= 0 && newMessages[lastIndex].type === 'assistant') {
          newMessages[lastIndex] = {
            ...newMessages[lastIndex],
            content: content,
          };
        }
        return newMessages;
      });
    };

    try {
      await ragChatApi.sendMessageStream(
        sessionId,
        userQuestion,
        (chunk: string) => {
          // 首字符到达
          if (!hasReceivedFirstChunk) {
            hasReceivedFirstChunk = true;
            setWaitingForFirstChunk(false);
          }
          fullContent += chunk;
          if (rafRef.current) {
            cancelAnimationFrame(rafRef.current);
          }
          rafRef.current = requestAnimationFrame(() => {
            startTransition(() => {
              updateAssistantMessage(fullContent);
            });
          });
        },
        () => {
          setLoading(false);
          setWaitingForFirstChunk(false);
          loadSessions();
        },
        (error: Error) => {
          console.error('流式查询失败:', error);
          updateAssistantMessage(fullContent || error.message || '回答失败，请重试');
          setLoading(false);
          setWaitingForFirstChunk(false);
        }
      );
    } catch (err) {
      console.error('发起流式查询失败:', err);
      updateAssistantMessage(err instanceof Error ? err.message : '回答失败，请重试');
      setLoading(false);
      setWaitingForFirstChunk(false);
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const formatTimeAgo = (dateStr: string): string => {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes} 分钟前`;
    if (hours < 24) return `${hours} 小时前`;
    if (days < 7) return `${days} 天前`;
    return formatDateOnly(dateStr);
  };

  // 解析 think 标签（支持流式传输）
  // 返回: main=主内容, thinks=完成的 think 块, streamingThink=未完成的 think 内容
  const parseThinkBlocks = (content: string): { main: string; thinks: ThinkBlock[]; streamingThink: string | null } => {
    const thinks: ThinkBlock[] = [];
    let streamingThink: string | null = null;

    // 查找所有完整的 think 块
    const completeRegex = /<think>([\s\S]*?)<\/think>/g;
    let match;
    let mainContent = content;

    while ((match = completeRegex.exec(content)) !== null) {
      thinks.push({ content: match[1].trim(), isComplete: true });
    }

    // 检查是否有未闭合的 think 标签
    const openTag = '<think>';
    const closeTag = '</think>';
    const openIndex = content.indexOf(openTag);

    if (openIndex !== -1) {
      const closeIndex = content.lastIndexOf(closeTag);
      if (closeIndex === -1 || closeIndex < openIndex) {
        // 没有闭标签或闭标签在开标签之前，属于流式传输中的 think
        streamingThink = content.slice(openIndex + openTag.length);
        mainContent = content.slice(0, openIndex);
      } else {
        // 有闭标签，主内容是闭标签之后的部分
        mainContent = content.slice(closeIndex + closeTag.length);
      }
    } else {
      mainContent = content;
    }

    return {
      main: mainContent.trim(),
      thinks,
      streamingThink: streamingThink ? streamingThink.trim() : null,
    };
  };

  // 切换 think 展开状态
  const toggleThink = (msgIndex: number, thinkIndex: number) => {
    setExpandedThinks(prev => {
      const next = new Set(prev);
      const key = `${msgIndex}-${thinkIndex}`;
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const inputCls =
    'w-full h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors disabled:bg-zinc-100';

  return (
    <div className="fade-in w-full flex flex-col h-[var(--workspace-h)]">
      {/* 页头 */}
      <div className="flex items-end justify-between mb-5">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">知识库问答</h1>
          <p className="mt-1 text-sm text-zinc-500">选择知识库，向 AI 提问</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={onUpload}
            className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
          >
            上传知识库
          </button>
          <button
            onClick={onBack}
            className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
          >
            返回管理
          </button>
        </div>
      </div>

      <div className="flex gap-4 flex-1 min-h-0">
        {/* 左侧：对话历史 */}
        {leftPanelOpen && (
          <div className="shrink-0 w-64 flex flex-col fade-in">
            <div className="flex-1 min-h-0 bg-white border border-zinc-200 rounded-lg shadow-sm flex flex-col">
              <div className="flex items-center justify-between h-[46px] px-4 border-b border-zinc-100 shrink-0">
                <h2 className="text-sm font-medium text-zinc-900">对话历史</h2>
                <div className="flex items-center gap-0.5">
                  <button
                    onClick={handleNewSession}
                    disabled={selectedKbIds.size === 0}
                    className="p-1.5 text-zinc-400 hover:text-primary-700 hover:bg-zinc-100 rounded-md transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                    title="新建对话"
                  >
                    <Plus className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => setLeftPanelOpen(false)}
                    className="p-1.5 text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 rounded-md transition-colors"
                    title="收起"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                </div>
              </div>

              <div className="flex-1 min-h-0 overflow-y-auto p-2">
                {loadingSessions ? (
                  <div className="flex justify-center py-6">
                    <Loader2 className="w-4 h-4 text-primary-600 animate-spin" />
                  </div>
                ) : sessions.length === 0 ? (
                  <p className="px-2 py-6 text-xs text-zinc-400 text-center">暂无对话历史</p>
                ) : (
                  <div className="space-y-1">
                    {sessions.map((session) => (
                      <div
                        key={session.id}
                        onClick={() => handleLoadSession(session.id)}
                        className={`p-2.5 rounded-md cursor-pointer transition-colors group ${
                          currentSessionId === session.id
                            ? 'bg-primary-50 border border-primary-400'
                            : 'border border-transparent hover:bg-zinc-50'
                        }`}
                      >
                        <div className="flex items-start justify-between gap-1">
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1">
                              {session.isPinned && (
                                <Pin className="w-3 h-3 text-primary-600 fill-primary-600 shrink-0" />
                              )}
                              <p
                                className={`text-sm truncate ${
                                  currentSessionId === session.id
                                    ? 'font-medium text-zinc-900'
                                    : 'text-zinc-700'
                                }`}
                              >
                                {session.title}
                              </p>
                            </div>
                            <p className="font-mono text-xs text-zinc-400 mt-1">
                              {formatTimeAgo(session.updatedAt)}
                            </p>
                          </div>
                          <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button
                              onClick={(e) => handleTogglePin(session.id, e)}
                              className={`p-1 rounded transition-colors ${
                                session.isPinned
                                  ? 'text-primary-600 hover:text-primary-700'
                                  : 'text-zinc-400 hover:text-zinc-700'
                              }`}
                              title={session.isPinned ? '取消置顶' : '置顶'}
                            >
                              <Pin className={`w-3.5 h-3.5 ${session.isPinned ? 'fill-primary-600' : ''}`} />
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleEditSessionTitle(session.id, session.title);
                              }}
                              className="p-1 text-zinc-400 hover:text-zinc-700 rounded transition-colors"
                              title="编辑标题"
                            >
                              <Edit className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                setSessionDeleteConfirm({ id: session.id, title: session.title });
                              }}
                              className="p-1 text-zinc-400 hover:text-red-600 rounded transition-colors"
                              title="删除"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* 左侧面板收起时的展开按钮 */}
        {!leftPanelOpen && (
          <button
            onClick={() => setLeftPanelOpen(true)}
            className="shrink-0 w-10 bg-white border border-zinc-200 rounded-lg shadow-sm flex items-center justify-center hover:bg-zinc-50 transition-colors"
            title="展开对话历史"
          >
            <ChevronRight className="w-4 h-4 text-zinc-400" />
          </button>
        )}

        {/* 中间：聊天区域 */}
        <div className="flex-1 min-w-0 flex flex-col">
          <div className="bg-white border border-zinc-200 rounded-lg shadow-sm flex flex-col flex-1 min-h-0">
            {selectedKbIds.size > 0 ? (
              <>
                {/* 会话信息 */}
                <div className="px-5 py-3 border-b border-zinc-100 shrink-0">
                  <div className="flex items-center justify-between gap-3 flex-wrap">
                    <h2 className="text-sm font-medium text-zinc-900 truncate">
                      {currentSessionTitle || (selectedKbIds.size === 1
                        ? knowledgeBases.find(kb => kb.id === Array.from(selectedKbIds)[0])?.name || '新对话'
                        : `${selectedKbIds.size} 个知识库 · 新对话`)}
                    </h2>
                    <div className="flex flex-wrap gap-1 justify-end">
                      {Array.from(selectedKbIds).map(kbId => {
                        const kb = knowledgeBases.find(k => k.id === kbId);
                        return kb ? (
                          <span
                            key={kbId}
                            className="text-xs text-zinc-600 bg-zinc-50 border border-zinc-200 rounded px-1.5 py-0.5 max-w-[160px] truncate"
                          >
                            {kb.name}
                          </span>
                        ) : null;
                      })}
                    </div>
                  </div>
                </div>

                {/* 消息列表 */}
                <div className="flex-1 min-h-0 relative">
                  {messages.length === 0 ? (
                    <div className="absolute inset-0 flex items-center justify-center">
                      <p className="text-xs text-zinc-400">输入问题开始提问</p>
                    </div>
                  ) : (
                    <Virtuoso
                      ref={virtuosoRef}
                      data={messages}
                      initialTopMostItemIndex={messages.length - 1}
                      followOutput="smooth"
                      className="h-full w-full"
                      itemContent={(index, msg) => (
                        <div className="pb-4 px-5 first:pt-4">
                          <div className={`flex ${msg.type === 'user' ? 'justify-end' : 'justify-start'}`}>
                            <div
                              className={`max-w-[85%] rounded-lg px-4 py-3 ${
                                msg.type === 'user'
                                  ? 'bg-primary-600 text-white'
                                  : 'bg-white border border-zinc-200 text-zinc-800'
                              }`}
                            >
                              {msg.type === 'user' ? (
                                <p className="whitespace-pre-wrap leading-relaxed text-sm">{msg.content}</p>
                              ) : (
                                (() => {
                                  const { main, thinks, streamingThink } = parseThinkBlocks(msg.content);
                                  const msgIndex = index;
                                  const isLastMessage = loading && index === messages.length - 1;
                                  const isWaiting = isLastMessage && waitingForFirstChunk && !msg.content;

                                  // 如果是新消息且没有内容且正在等待，显示加载指示器
                                  if (isWaiting) {
                                    return (
                                      <div className="flex items-center gap-2.5 py-1">
                                        <Loader2 className="w-4 h-4 text-primary-600 animate-spin" />
                                        <span className="text-sm text-zinc-500">AI 思考中…</span>
                                      </div>
                                    );
                                  }

                                  return (
                                    <>
                                      {/* 思考过程区域 - 默认展开 */}
                                      {(thinks.length > 0 || streamingThink) && (
                                        <div className="mb-3 space-y-2">
                                          {/* 完成的 think 块 */}
                                          {thinks.map((think, i) => {
                                            const key = `${msgIndex}-${i}`;
                                            // 新消息的 think 默认展开
                                            const isAutoExpanded = isLastMessage && !expandedThinks.has(key);
                                            const isExpanded = isAutoExpanded || expandedThinks.has(key);
                                            return (
                                              <div key={i} className="border border-zinc-200 rounded-md overflow-hidden">
                                                <button
                                                  onClick={() => toggleThink(msgIndex, i)}
                                                  className="w-full flex items-center justify-between px-3 py-2 bg-zinc-50 hover:bg-zinc-100 transition-colors text-left"
                                                >
                                                  <div className="flex items-center gap-2">
                                                    <Brain className="w-3.5 h-3.5 text-zinc-400" />
                                                    <span className="text-xs font-medium text-zinc-600">
                                                      AI 思考过程
                                                    </span>
                                                  </div>
                                                  <ChevronDown className={`w-3.5 h-3.5 text-zinc-400 transition-transform ${isExpanded ? 'rotate-180' : ''}`} />
                                                </button>
                                                {isExpanded && (
                                                  <div className="p-3 bg-zinc-50 text-xs text-zinc-600 fade-in">
                                                    <ReactMarkdown
                                                      remarkPlugins={[remarkGfm]}
                                                      components={{
                                                        code: ({ className, children }) => {
                                                          const match = /language-(\w+)/.exec(className || '');
                                                          const isInline = !match;
                                                          if (isInline) {
                                                            return (
                                                              <code className="bg-zinc-200 text-zinc-700 px-1 py-0.5 rounded text-xs font-normal">
                                                                {children}
                                                              </code>
                                                            );
                                                          }
                                                          return (
                                                            <CodeBlock language={match[1]}>
                                                              {String(children).replace(/\n$/, '')}
                                                            </CodeBlock>
                                                          );
                                                        },
                                                        pre: ({ children }) => <>{children}</>,
                                                      }}
                                                    >
                                                      {think.content}
                                                    </ReactMarkdown>
                                                  </div>
                                                )}
                                              </div>
                                            );
                                          })}
                                          {/* 流式传输中的 think（没有闭标签） */}
                                          {streamingThink && (
                                            <div className="border border-primary-200 rounded-md overflow-hidden bg-primary-50/50">
                                              <div className="flex items-center gap-2 px-3 py-2 bg-primary-50">
                                                <Brain className="w-3.5 h-3.5 text-primary-600" />
                                                <span className="text-xs font-medium text-primary-700">
                                                  AI 思考中…
                                                </span>
                                              </div>
                                              <div className="p-3 text-xs text-zinc-600">
                                                <ReactMarkdown
                                                  remarkPlugins={[remarkGfm]}
                                                  components={{
                                                    code: ({ className, children }) => {
                                                      const match = /language-(\w+)/.exec(className || '');
                                                      const isInline = !match;
                                                      if (isInline) {
                                                        return (
                                                          <code className="bg-zinc-200 text-zinc-700 px-1 py-0.5 rounded text-xs font-normal">
                                                            {children}
                                                          </code>
                                                        );
                                                      }
                                                      return (
                                                        <CodeBlock language={match[1]}>
                                                          {String(children).replace(/\n$/, '')}
                                                        </CodeBlock>
                                                      );
                                                    },
                                                    pre: ({ children }) => <>{children}</>,
                                                  }}
                                                >
                                                  {streamingThink}
                                                </ReactMarkdown>
                                                {isLastMessage && (
                                                  <span className="inline-block w-0.5 h-3.5 bg-primary-600 ml-1 animate-pulse align-middle" />
                                                )}
                                              </div>
                                            </div>
                                          )}
                                        </div>
                                      )}
                                      <div className="prose prose-zinc prose-sm max-w-none
                                        prose-headings:text-zinc-900 prose-headings:font-semibold prose-headings:mb-2 prose-headings:mt-4
                                        prose-p:leading-7 prose-p:text-zinc-700 prose-p:mb-3
                                        prose-strong:text-zinc-900 prose-strong:font-semibold
                                        prose-ul:my-3 prose-ol:my-3
                                        prose-li:my-1 prose-li:leading-7
                                        prose-code:bg-zinc-100 prose-code:text-primary-700 prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded-md prose-code:before:content-none prose-code:after:content-none prose-code:font-normal
                                        marker:text-primary-600">
                                        <ReactMarkdown
                                          remarkPlugins={[remarkGfm]}
                                          components={{
                                            // 自定义代码块渲染
                                            code: ({ className, children }) => {
                                              const match = /language-(\w+)/.exec(className || '');
                                              const isInline = !match;

                                              if (isInline) {
                                                return (
                                                  <code className="bg-zinc-100 text-primary-700 px-1.5 py-0.5 rounded-md text-[13px] font-normal">
                                                    {children}
                                                  </code>
                                                );
                                              }

                                              // 代码块使用 CodeBlock 组件
                                              return (
                                                <CodeBlock language={match[1]}>
                                                  {String(children).replace(/\n$/, '')}
                                                </CodeBlock>
                                              );
                                            },
                                            // 禁用默认 pre 渲染，由 CodeBlock 处理
                                            pre: ({ children }) => <>{children}</>,
                                          }}
                                        >
                                          {formatMarkdown(main)}
                                        </ReactMarkdown>
                                      </div>
                                    </>
                                  );
                                })()
                              )}
                            </div>
                          </div>
                        </div>
                      )}
                    />
                  )}
                </div>

                {/* 输入区域 */}
                <div className="px-5 py-4 border-t border-zinc-100 shrink-0">
                  <div className="flex gap-2">
                    <input
                      type="text"
                      value={question}
                      onChange={(e) => setQuestion(e.target.value)}
                      onKeyPress={(e) => e.key === 'Enter' && !e.shiftKey && handleSubmitQuestion()}
                      placeholder="输入问题，Enter 发送"
                      className={`${inputCls} flex-1`}
                      disabled={loading}
                    />
                    <button
                      onClick={handleSubmitQuestion}
                      disabled={!question.trim() || selectedKbIds.size === 0 || loading}
                      className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
                    >
                      发送
                    </button>
                  </div>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center">
                <p className="text-xs text-zinc-400">请先在右侧选择知识库，再开始提问</p>
              </div>
            )}
          </div>
        </div>

        {/* 右侧：知识库选择 */}
        {rightPanelOpen && (
          <div className="shrink-0 w-[280px] flex flex-col fade-in">
            <div className="flex-1 min-h-0 bg-white border border-zinc-200 rounded-lg shadow-sm flex flex-col">
              <div className="flex items-center justify-between h-[46px] px-4 border-b border-zinc-100 shrink-0">
                <h2 className="text-sm font-medium text-zinc-900">选择知识库</h2>
                <button
                  onClick={() => setRightPanelOpen(false)}
                  className="p-1.5 text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 rounded-md transition-colors"
                  title="收起"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>

              {/* 搜索框 */}
              <div className="flex gap-2 px-4 pt-4">
                <input
                  type="text"
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
                  placeholder="搜索知识库…"
                  className={`${inputCls} flex-1`}
                />
                <button
                  onClick={handleSearch}
                  className="h-9 px-3 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors shrink-0"
                >
                  搜索
                </button>
              </div>

              {/* 排序 */}
              <div className="px-4 pt-3 pb-2 shrink-0">
                <select
                  value={sortBy}
                  onChange={(e) => {
                    setSortBy(e.target.value as SortOption);
                    setSearchKeyword('');
                  }}
                  className="w-full h-9 px-2.5 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 cursor-pointer focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
                >
                  <option value="time">时间排序</option>
                  <option value="size">大小排序</option>
                  <option value="access">访问排序</option>
                  <option value="question">提问排序</option>
                </select>
              </div>

              {/* 知识库列表 */}
              <div className="flex-1 min-h-0 overflow-y-auto px-4 pb-4">
                {loadingList ? (
                  <div className="flex justify-center py-6">
                    <Loader2 className="w-4 h-4 text-primary-600 animate-spin" />
                  </div>
                ) : knowledgeBases.length === 0 ? (
                  <div className="py-6 text-center">
                    <p className="text-xs text-zinc-400">{searchKeyword ? '未找到匹配的知识库' : '暂无可用知识库'}</p>
                    {!searchKeyword && (
                      <button
                        onClick={onUpload}
                        className="mt-3 text-xs text-primary-700 hover:text-primary-800 hover:underline underline-offset-2 transition-colors"
                      >
                        立即上传
                      </button>
                    )}
                  </div>
                ) : (
                  <div className="space-y-2">
                    {groupedKnowledgeBases.map((group) => (
                      <div key={group.name} className="border border-zinc-200 rounded-md overflow-hidden">
                        <button
                          onClick={() => toggleCategory(group.name)}
                          className="w-full flex items-center justify-between px-3 py-2 bg-zinc-50 hover:bg-zinc-100 transition-colors"
                        >
                          <div className="flex items-center gap-1.5">
                            <ChevronRight
                              className={`w-3.5 h-3.5 text-zinc-400 transition-transform ${group.isExpanded ? 'rotate-90' : ''}`}
                            />
                            <span className="text-xs font-medium text-zinc-700">{group.name}</span>
                          </div>
                          <span className="font-mono text-xs text-zinc-400 tabular-nums">{group.items.length}</span>
                        </button>

                        {group.isExpanded && (
                          <div className="p-1.5 space-y-1 fade-in">
                            {group.items.map((kb) => (
                              <div
                                key={kb.id}
                                onClick={() => handleToggleKb(kb.id)}
                                className={`p-2 rounded-md cursor-pointer transition-colors ${
                                  selectedKbIds.has(kb.id)
                                    ? 'bg-primary-50 border border-primary-400'
                                    : 'border border-transparent hover:bg-zinc-50'
                                }`}
                              >
                                <div className="flex items-center gap-2">
                                  <input
                                    type="checkbox"
                                    checked={selectedKbIds.has(kb.id)}
                                    onChange={() => handleToggleKb(kb.id)}
                                    onClick={(e) => e.stopPropagation()}
                                    className="w-3.5 h-3.5 accent-primary-600 shrink-0 cursor-pointer"
                                  />
                                  <span className="font-medium text-zinc-800 text-xs truncate flex-1">{kb.name}</span>
                                </div>
                                <p className="font-mono text-xs text-zinc-400 mt-0.5 ml-[22px] tabular-nums">
                                  {formatFileSize(kb.fileSize)}
                                </p>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* 右侧面板收起时的展开按钮 */}
        {!rightPanelOpen && (
          <button
            onClick={() => setRightPanelOpen(true)}
            className="shrink-0 w-10 bg-white border border-zinc-200 rounded-lg shadow-sm flex items-center justify-center hover:bg-zinc-50 transition-colors"
            title="展开知识库面板"
          >
            <ChevronLeft className="w-4 h-4 text-zinc-400" />
          </button>
        )}
      </div>

      {/* 删除会话确认弹窗 */}
      <DeleteConfirmDialog
        open={!!sessionDeleteConfirm}
        item={sessionDeleteConfirm ? { id: 0, title: sessionDeleteConfirm.title } : null}
        itemType="对话"
        onConfirm={handleDeleteSession}
        onCancel={() => setSessionDeleteConfirm(null)}
      />

      {/* 编辑会话标题弹窗 */}
      {editingSessionTitle && (
        <>
          <div
            className="fixed inset-0 bg-zinc-900/40 z-50"
            onClick={() => {
              setEditingSessionTitle(null);
              setNewSessionTitle('');
            }}
          />
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div
              className="bg-white border border-zinc-200 rounded-lg shadow-lg max-w-md w-full p-5 fade-in"
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className="text-sm font-semibold text-zinc-900">编辑对话标题</h3>
              <input
                type="text"
                value={newSessionTitle}
                onChange={(e) => setNewSessionTitle(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSaveSessionTitle()}
                placeholder="请输入新标题"
                className={`${inputCls} mt-4`}
                autoFocus
              />
              <div className="mt-5 flex justify-end gap-2">
                <button
                  onClick={() => {
                    setEditingSessionTitle(null);
                    setNewSessionTitle('');
                  }}
                  className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
                >
                  取消
                </button>
                <button
                  onClick={handleSaveSessionTitle}
                  disabled={!newSessionTitle.trim()}
                  className="h-9 px-4 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  保存
                </button>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
