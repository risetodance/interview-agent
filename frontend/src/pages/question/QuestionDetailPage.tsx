import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ArrowLeft,
  Plus,
  Trash2,
  Edit3,
  Search,
  FileQuestion,
  Tag,
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

  // 难度标签样式
  const getDifficultyStyle = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'BASIC':
        return 'bg-green-100 text-green-700';
      case 'ADVANCED':
        return 'bg-yellow-100 text-yellow-700';
      case 'EXPERT':
        return 'bg-red-100 text-red-700';
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
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      {/* 页面头部 */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/questions')}
            className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-slate-600" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-slate-900">{bank?.name}</h1>
            <p className="text-slate-500 mt-1">
              {bank?.description || '暂无描述'} · {totalElements} 道题目
            </p>
          </div>
        </div>
        <button
          onClick={() => navigate(`/questions/bank/${bankId}/import`)}
          className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
        >
          <Plus className="w-5 h-5" />
          导入题目
        </button>
      </div>

      {/* 搜索和筛选 */}
      <div className="flex items-center gap-3 mb-6">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
          <input
            type="text"
            placeholder="搜索题目内容或答案..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="w-full pl-10 pr-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          />
        </div>
        <button
          onClick={handleSearch}
          className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors flex items-center gap-2"
        >
          <Search className="w-4 h-4" />
          搜索
        </button>
      </div>

      {/* 难度筛选 */}
      <div className="flex items-center gap-2 mb-4">
        <span className="text-sm text-slate-500 mr-2">难度：</span>
        <button
          onClick={() => handleDifficultyChange(null)}
          className={`px-3 py-1.5 rounded-lg text-sm transition-colors ${
            selectedDifficulty === null
              ? 'bg-primary-100 text-primary-700'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
          }`}
        >
          全部
        </button>
        <button
          onClick={() => handleDifficultyChange('BASIC')}
          className={`px-3 py-1.5 rounded-lg text-sm transition-colors ${
            selectedDifficulty === 'BASIC'
              ? 'bg-green-100 text-green-700'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
          }`}
        >
          基础
        </button>
        <button
          onClick={() => handleDifficultyChange('ADVANCED')}
          className={`px-3 py-1.5 rounded-lg text-sm transition-colors ${
            selectedDifficulty === 'ADVANCED'
              ? 'bg-yellow-100 text-yellow-700'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
          }`}
        >
          进阶
        </button>
        <button
          onClick={() => handleDifficultyChange('EXPERT')}
          className={`px-3 py-1.5 rounded-lg text-sm transition-colors ${
            selectedDifficulty === 'EXPERT'
              ? 'bg-red-100 text-red-700'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
          }`}
        >
          专家
        </button>
        {(searchKeyword || selectedDifficulty) && (
          <button
            onClick={() => {
              setSearchKeyword('');
              setSelectedDifficulty(null);
              loadQuestions(0, null, '');
            }}
            className="ml-2 px-3 py-1.5 text-sm text-slate-500 hover:text-slate-700 flex items-center gap-1"
          >
            <X className="w-4 h-4" />
            清除筛选
          </button>
        )}
      </div>

      {error && (
        <div className="mb-4 p-4 bg-red-50 text-red-600 rounded-lg">
          {error}
        </div>
      )}

      {/* 题目列表 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 题目列表 */}
        <div className="space-y-3">
          {loading && questions.length === 0 ? (
            <div className="text-center py-12">
              <Loader2 className="w-8 h-8 text-primary-500 animate-spin mx-auto mb-4" />
              <p className="text-slate-500">加载中...</p>
            </div>
          ) : questions.length === 0 ? (
            <div className="text-center py-12 bg-white rounded-xl border border-slate-200">
              <FileQuestion className="w-12 h-12 text-slate-300 mx-auto mb-4" />
              <p className="text-slate-500">暂无题目</p>
              <button
                onClick={() => navigate(`/questions/bank/${bankId}/import`)}
                className="mt-4 text-primary-600 hover:text-primary-700"
              >
                导入第一道题目
              </button>
            </div>
          ) : (
            <>
              {isRefreshing && (
                <div className="h-1 bg-primary-100 rounded-full overflow-hidden mb-3">
                  <div className="h-full bg-primary-500 animate-pulse" />
                </div>
              )}
              {questions.map((question) => (
                <div
                  key={question.id}
                  className={`bg-white rounded-xl border p-4 cursor-pointer transition-all ${
                    selectedQuestion?.id === question.id
                      ? 'border-primary-500 shadow-md'
                      : 'border-slate-200 hover:border-slate-300 hover:shadow-sm'
                  }`}
                  onClick={() => setSelectedQuestion(question)}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-2">
                        <span className={`px-2 py-0.5 rounded text-xs ${getDifficultyStyle(question.difficulty)}`}>
                          {getDifficultyText(question.difficulty)}
                        </span>
                        {question.tags && question.tags.length > 0 && (
                          <div className="flex items-center gap-1">
                            <Tag className="w-3 h-3 text-slate-400" />
                            <span className="text-xs text-slate-400">
                              {question.tags.slice(0, 2).join(', ')}
                            </span>
                          </div>
                        )}
                      </div>
                      <p className="text-slate-900 line-clamp-2">{question.content}</p>
                    </div>
                  </div>
                </div>
              ))}

              {/* 分页控件 */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between pt-4 mt-4 border-t border-slate-200">
                  <div className="text-sm text-slate-500">
                    第 {page + 1} / {totalPages} 页，共 {totalElements} 道题目
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => loadQuestions(page - 1)}
                      disabled={page === 0}
                      className="p-2 rounded-lg hover:bg-slate-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      <ChevronLeft className="w-5 h-5 text-slate-600" />
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
                            className={`w-8 h-8 rounded-lg text-sm font-medium transition-colors ${
                              page === pageNum
                                ? 'bg-primary-600 text-white'
                                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
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
                      className="p-2 rounded-lg hover:bg-slate-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      <ChevronRight className="w-5 h-5 text-slate-600" />
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        {/* 题目详情 */}
        <div className="bg-white rounded-xl border border-slate-200 p-6 h-fit sticky top-6">
          <AnimatePresence mode="wait">
            {selectedQuestion ? (
              <motion.div
                key={selectedQuestion.id}
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
              >
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <span className={`px-2 py-1 rounded text-sm ${getDifficultyStyle(selectedQuestion.difficulty)}`}>
                      {getDifficultyText(selectedQuestion.difficulty)}
                    </span>
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => navigate(`/questions/${selectedQuestion.id}/edit`)}
                      className="p-2 text-slate-400 hover:text-primary-600 hover:bg-slate-100 rounded-lg transition-colors"
                      title="编辑"
                    >
                      <Edit3 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => setDeleteDialog({ show: true, question: selectedQuestion })}
                      className="p-2 text-slate-400 hover:text-red-600 hover:bg-slate-100 rounded-lg transition-colors"
                      title="删除"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                <div className="space-y-4">
                  <div>
                    <h3 className="text-sm font-medium text-slate-500 mb-2">题目</h3>
                    <p className="text-slate-900 whitespace-pre-wrap">{selectedQuestion.content}</p>
                  </div>

                  <div>
                    <h3 className="text-sm font-medium text-slate-500 mb-2">答案</h3>
                    <div className="bg-slate-50 rounded-lg p-4">
                      <p className="text-slate-700 whitespace-pre-wrap">
                        {selectedQuestion.answer || '暂无答案'}
                      </p>
                    </div>
                  </div>

                  {selectedQuestion.tags && selectedQuestion.tags.length > 0 && (
                    <div>
                      <h3 className="text-sm font-medium text-slate-500 mb-2">标签</h3>
                      <div className="flex flex-wrap gap-2">
                        {selectedQuestion.tags.map((tag, i) => (
                          <span
                            key={i}
                            className="px-2 py-1 bg-slate-100 text-slate-600 rounded text-sm"
                          >
                            {tag}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </motion.div>
            ) : (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="text-center py-12 text-slate-400"
              >
                <FileQuestion className="w-12 h-12 mx-auto mb-4 opacity-50" />
                <p>选择一个题目查看详情</p>
              </motion.div>
            )}
          </AnimatePresence>
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
