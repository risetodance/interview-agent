import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Plus,
  Trash2,
  Edit3,
  Search,
  Loader2,
  ChevronLeft,
  ChevronRight,
  X,
} from 'lucide-react';
import {
  questionApi,
  QuestionDTO,
  QuestionBankDTO,
  QuestionDifficulty,
  PageResponse,
} from '../../api/question';
import ConfirmDialog from '../../components/ConfirmDialog';

const PAGE_SIZE = 20;

export default function QuestionDetailPage() {
  const navigate = useNavigate();
  const { bankId } = useParams<{ bankId: string }>();
  const [bank, setBank] = useState<QuestionBankDTO | null>(null);
  const [questions, setQuestions] = useState<QuestionDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedDifficulty, setSelectedDifficulty] = useState<QuestionDifficulty | null>(null);
  const [selectedQuestion, setSelectedQuestion] = useState<QuestionDTO | null>(null);
  const [deleteDialog, setDeleteDialog] = useState<{
    show: boolean;
    question: QuestionDTO | null;
  }>({ show: false, question: null });
  const [deleting, setDeleting] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

  // 分页状态
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const bankIdNum = bankId ? parseInt(bankId, 10) : 0;

  // 加载题库信息
  const loadBank = async () => {
    try {
      const bankData = await questionApi.getBankById(bankIdNum);
      setBank(bankData);
    } catch (err) {
      console.error('加载题库失败', err);
    }
  };

  // 加载题目数据（分页）
  const loadQuestions = useCallback(async (
    pageNum: number,
    difficulty?: QuestionDifficulty | null,
    keyword?: string
  ) => {
    // 如果已有数据，显示刷新指示器
    const hasExistingData = questions.length > 0;
    if (hasExistingData) {
      setIsRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);

    try {
      const response: PageResponse<QuestionDTO> = await questionApi.getQuestionsByBankIdPaged(
        bankIdNum,
        pageNum,
        PAGE_SIZE,
        difficulty ?? selectedDifficulty ?? undefined,
        keyword ?? searchKeyword ?? undefined
      );

      setQuestions(response.content || []);
      setTotalPages(response.totalPages || 0);
      setTotalElements(response.totalElements || 0);
      setPage(pageNum);

      // 清空选中状态（如果当前页没有选中的题目）
      if (selectedQuestion) {
        const existsInCurrentPage = response.content.some(q => q.id === selectedQuestion.id);
        if (!existsInCurrentPage) {
          setSelectedQuestion(null);
        }
      }
    } catch (err) {
      setError('加载数据失败');
      console.error(err);
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  }, [bankIdNum, selectedQuestion]);

  useEffect(() => {
    loadBank();
    loadQuestions(0);
  }, [bankId]);

  // 搜索时重新加载第一页
  const handleSearch = () => {
    loadQuestions(0, selectedDifficulty, searchKeyword);
  };

  // 难度筛选时重新加载
  const handleDifficultyChange = (difficulty: QuestionDifficulty | null) => {
    setSelectedDifficulty(difficulty);
    loadQuestions(0, difficulty, searchKeyword);
  };

  // 处理删除题目
  const handleDeleteQuestion = async () => {
    if (!deleteDialog.question) return;

    try {
      setDeleting(true);
      await questionApi.deleteQuestion(deleteDialog.question.id);
      setDeleteDialog({ show: false, question: null });
      // 重新加载当前页
      loadQuestions(page);
    } catch (err) {
      console.error('删除题目失败', err);
    } finally {
      setDeleting(false);
    }
  };

  // 难度标签样式（语义色：基础 emerald / 进阶 amber / 专家 red）
  const getDifficultyStyle = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'BASIC':
        return 'text-emerald-700 bg-emerald-50 border-emerald-200';
      case 'ADVANCED':
        return 'text-amber-700 bg-amber-50 border-amber-200';
      case 'EXPERT':
        return 'text-red-700 bg-red-50 border-red-200';
    }
  };

  // 难度文本
  const getDifficultyText = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'BASIC':
        return '基础';
      case 'ADVANCED':
        return '进阶';
      case 'EXPERT':
        return '专家';
    }
  };

  if (loading && questions.length === 0) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-6 h-6 text-primary-600 animate-spin" />
      </div>
    );
  }

  // 难度筛选项
  const difficultyFilters: { value: QuestionDifficulty | null; label: string }[] = [
    { value: null, label: '全部' },
    { value: 'BASIC', label: '基础' },
    { value: 'ADVANCED', label: '进阶' },
    { value: 'EXPERT', label: '专家' },
  ];

  return (
    <div className="fade-in">
      {/* 页面头部 */}
      <div className="flex items-center justify-between gap-4 mb-6">
        <div className="flex items-center gap-3 min-w-0">
          <button
            onClick={() => navigate('/questions')}
            className="p-2 -ml-2 hover:bg-zinc-100 rounded-md transition-colors shrink-0"
            aria-label="返回题库列表"
          >
            <ArrowLeft className="w-5 h-5 text-zinc-500" />
          </button>
          <div className="min-w-0">
            <h1 className="text-xl font-semibold text-zinc-900 tracking-tight truncate">
              {bank?.name || '题目列表'}
            </h1>
            <p className="mt-0.5 text-sm text-zinc-500 truncate">
              {bank?.description || '暂无描述'} · 共{' '}
              <span className="font-mono tabular-nums">{totalElements}</span> 道题目
            </p>
          </div>
        </div>
        <button
          onClick={() => navigate(`/questions/bank/${bankId}/import`)}
          className="h-9 px-4 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors flex items-center gap-1.5 shrink-0"
        >
          <Plus className="w-4 h-4" />
          导入题目
        </button>
      </div>

      {/* 搜索和筛选 */}
      <div className="flex items-center gap-2 mb-3">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400" />
          <input
            type="text"
            placeholder="搜索题目内容或答案…"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="w-full h-9 pl-8 pr-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
          />
        </div>
        <button
          onClick={handleSearch}
          className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors shrink-0"
        >
          搜索
        </button>
      </div>

      {/* 难度筛选 */}
      <div className="flex items-center gap-2 mb-5 flex-wrap">
        <span className="text-xs text-zinc-500">难度</span>
        {difficultyFilters.map((filter) => (
          <button
            key={filter.label}
            onClick={() => handleDifficultyChange(filter.value)}
            className={`h-8 px-3 rounded-md border text-xs transition-colors ${
              selectedDifficulty === filter.value
                ? 'border-primary-600 bg-primary-600 text-white'
                : 'border-zinc-300 text-zinc-600 hover:border-zinc-400 hover:bg-zinc-50'
            }`}
          >
            {filter.label}
          </button>
        ))}
        {(searchKeyword || selectedDifficulty) && (
          <button
            onClick={() => {
              setSearchKeyword('');
              setSelectedDifficulty(null);
              loadQuestions(0, null, '');
            }}
            className="ml-1 h-8 px-2 flex items-center gap-1 text-xs text-zinc-500 hover:text-zinc-800 transition-colors"
          >
            <X className="w-3.5 h-3.5" />
            清除筛选
          </button>
        )}
      </div>

      {error && (
        <div className="mb-4 flex items-center gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* 主体：题目列表 + 题目详情 */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-5 items-start">
        {/* 题目列表 */}
        <div className="lg:col-span-3 bg-white border border-zinc-200 rounded-lg shadow-sm overflow-hidden">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">题目列表</h2>
            <span className="font-mono text-xs text-zinc-400 tabular-nums">
              {totalElements} 道
            </span>
          </div>

          {loading && questions.length === 0 ? (
            <div className="flex items-center justify-center py-14">
              <Loader2 className="w-6 h-6 text-primary-600 animate-spin" />
            </div>
          ) : questions.length === 0 ? (
            <p className="px-5 py-12 text-xs text-zinc-400 text-center">
              暂无题目，{' '}
              <button
                onClick={() => navigate(`/questions/bank/${bankId}/import`)}
                className="text-primary-700 hover:text-primary-800 underline underline-offset-2 transition-colors"
              >
                导入第一道题目
              </button>
            </p>
          ) : (
            <div>
              {isRefreshing && (
                <div className="h-0.5 bg-primary-100 overflow-hidden">
                  <div className="h-full bg-primary-500 animate-pulse" />
                </div>
              )}
              {questions.map((question) => (
                <button
                  key={question.id}
                  type="button"
                  className={`w-full text-left px-5 py-3.5 border-b border-zinc-100 last:border-b-0 transition-colors ${
                    selectedQuestion?.id === question.id
                      ? 'bg-primary-50/60'
                      : 'hover:bg-zinc-50'
                  }`}
                  onClick={() => setSelectedQuestion(question)}
                >
                  <div className="flex items-center gap-2 mb-1.5">
                    <span className="font-mono text-xs text-zinc-400 tabular-nums shrink-0">
                      #{question.id}
                    </span>
                    <span
                      className={`text-xs border rounded px-1.5 py-0.5 shrink-0 ${getDifficultyStyle(question.difficulty)}`}
                    >
                      {getDifficultyText(question.difficulty)}
                    </span>
                    {question.tags && question.tags.length > 0 && (
                      <span className="text-xs text-zinc-400 truncate">
                        {question.tags.slice(0, 2).join(' · ')}
                      </span>
                    )}
                  </div>
                  <p className="text-sm text-zinc-800 line-clamp-2">{question.content}</p>
                </button>
              ))}

              {/* 分页控件 */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between px-5 py-3 border-t border-zinc-100">
                  <span className="font-mono text-xs text-zinc-400 tabular-nums">
                    第 {page + 1} / {totalPages} 页 · 共 {totalElements} 道
                  </span>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => loadQuestions(page - 1)}
                      disabled={page === 0}
                      className="p-1.5 rounded-md hover:bg-zinc-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                      aria-label="上一页"
                    >
                      <ChevronLeft className="w-4 h-4 text-zinc-500" />
                    </button>
                    <div className="flex items-center gap-1">
                      {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                        let pageNum;
                        if (totalPages <= 5) {
                          pageNum = i;
                        } else if (page < 3) {
                          pageNum = i;
                        } else if (page > totalPages - 3) {
                          pageNum = totalPages - 5 + i;
                        } else {
                          pageNum = page - 2 + i;
                        }
                        return (
                          <button
                            key={pageNum}
                            onClick={() => loadQuestions(pageNum)}
                            className={`h-8 min-w-8 px-2 rounded-md text-xs font-mono tabular-nums transition-colors ${
                              page === pageNum
                                ? 'bg-primary-600 text-white'
                                : 'text-zinc-600 hover:bg-zinc-100'
                            }`}
                          >
                            {pageNum + 1}
                          </button>
                        );
                      })}
                    </div>
                    <button
                      onClick={() => loadQuestions(page + 1)}
                      disabled={page >= totalPages - 1}
                      className="p-1.5 rounded-md hover:bg-zinc-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                      aria-label="下一页"
                    >
                      <ChevronRight className="w-4 h-4 text-zinc-500" />
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* 题目详情 */}
        <div className="lg:col-span-2 bg-white border border-zinc-200 rounded-lg shadow-sm lg:sticky lg:top-0">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">题目详情</h2>
            {selectedQuestion && (
              <div className="flex items-center gap-0.5">
                <button
                  onClick={() => navigate(`/questions/${selectedQuestion.id}/edit`)}
                  className="p-1.5 text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 rounded-md transition-colors"
                  title="编辑"
                >
                  <Edit3 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setDeleteDialog({ show: true, question: selectedQuestion })}
                  className="p-1.5 text-zinc-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
                  title="删除"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>

          {selectedQuestion ? (
            <div className="p-5 space-y-5 fade-in" key={selectedQuestion.id}>
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <span className="font-mono text-xs text-zinc-400 tabular-nums">
                    #{selectedQuestion.id}
                  </span>
                  <span
                    className={`text-xs border rounded px-1.5 py-0.5 ${getDifficultyStyle(selectedQuestion.difficulty)}`}
                  >
                    {getDifficultyText(selectedQuestion.difficulty)}
                  </span>
                </div>
                <p className="text-sm text-zinc-900 whitespace-pre-wrap leading-relaxed">
                  {selectedQuestion.content}
                </p>
              </div>

              <div>
                <h3 className="text-xs font-medium text-zinc-500 mb-2">参考答案</h3>
                <div className="bg-zinc-50 border border-zinc-100 rounded-md p-4">
                  <p className="text-sm text-zinc-700 whitespace-pre-wrap">
                    {selectedQuestion.answer || '暂无答案'}
                  </p>
                </div>
              </div>

              {selectedQuestion.tags && selectedQuestion.tags.length > 0 && (
                <div>
                  <h3 className="text-xs font-medium text-zinc-500 mb-2">标签</h3>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedQuestion.tags.map((tag, i) => (
                      <span
                        key={i}
                        className="text-xs border border-zinc-200 bg-zinc-50 text-zinc-600 rounded px-1.5 py-0.5"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <p className="px-5 py-12 text-xs text-zinc-400 text-center">
              选择左侧题目查看详情
            </p>
          )}
        </div>
      </div>

      {/* 删除确认对话框 */}
      <ConfirmDialog
        open={deleteDialog.show}
        title="删除题目"
        message={`确定要删除这道题目吗？此操作无法恢复。`}
        onConfirm={handleDeleteQuestion}
        onCancel={() => setDeleteDialog({ show: false, question: null })}
        confirmText={deleting ? '删除中...' : '删除'}
        confirmVariant="danger"
        loading={deleting}
      />
    </div>
  );
}
