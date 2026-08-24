import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { questionApi, QuestionDifficulty } from '../../api/question';

const labelCls = 'block text-xs font-medium text-zinc-600 mb-1.5';
const inputCls =
  'w-full h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors';
const textareaCls =
  'w-full px-3 py-2 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors resize-none';

export default function QuestionEditPage() {
  const navigate = useNavigate();
  const { questionId } = useParams<{ questionId: string }>();
  const questionIdNum = questionId ? parseInt(questionId, 10) : 0;

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState<{
    content: string;
    answer: string;
    difficulty: QuestionDifficulty;
    tags: string;
  }>({
    content: '',
    answer: '',
    difficulty: 'ADVANCED',
    tags: '',
  });

  // 加载题目详情
  useEffect(() => {
    const loadQuestion = async () => {
      try {
        const question = await questionApi.getQuestionById(questionIdNum);
        if (question) {
          setFormData({
            content: question.content || '',
            answer: question.answer || '',
            difficulty: question.difficulty || 'ADVANCED',
            tags: question.tags?.join(', ') || '',
          });
        }
      } catch (err) {
        console.error('加载题目失败', err);
      } finally {
        setLoading(false);
      }
    };

    if (questionIdNum) {
      loadQuestion();
    }
  }, [questionIdNum]);

  // 保存题目
  const handleSave = async () => {
    try {
      setSaving(true);
      const tags = formData.tags
        .split(',')
        .map(t => t.trim())
        .filter(t => t);

      await questionApi.updateQuestion(questionIdNum, {
        content: formData.content,
        answer: formData.answer,
        difficulty: formData.difficulty,
        tags,
      });

      navigate(-1);
    } catch (err) {
      console.error('保存失败', err);
      alert('保存失败');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-6 h-6 text-primary-600 animate-spin" />
      </div>
    );
  }

  return (
    <div className="fade-in">
      {/* 页面头部 */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate(-1)}
          className="p-2 -ml-2 hover:bg-zinc-100 rounded-md transition-colors"
          aria-label="返回"
        >
          <ArrowLeft className="w-5 h-5 text-zinc-500" />
        </button>
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">编辑题目</h1>
          <p className="mt-0.5 text-sm text-zinc-500">修改题目内容、答案、难度与标签</p>
        </div>
      </div>

      {/* 题目表单 */}
      <div className="mt-6 bg-white border border-zinc-200 rounded-lg">
        <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
          <h2 className="text-sm font-medium text-zinc-900">题目信息</h2>
          <span className="font-mono text-xs text-zinc-400 tabular-nums">ID {questionId}</span>
        </div>

        <div className="p-5">
          {/* 题目内容 */}
          <div className="mb-5">
            <label htmlFor="content" className={labelCls}>
              题目内容 <span className="text-red-500">*</span>
            </label>
            <textarea
              id="content"
              value={formData.content}
              onChange={(e) => setFormData({ ...formData, content: e.target.value })}
              rows={4}
              className={textareaCls}
              placeholder="请输入题目内容"
            />
          </div>

          {/* 答案 */}
          <div className="mb-5">
            <label htmlFor="answer" className={labelCls}>
              参考答案
            </label>
            <textarea
              id="answer"
              value={formData.answer}
              onChange={(e) => setFormData({ ...formData, answer: e.target.value })}
              rows={6}
              className={textareaCls}
              placeholder="请输入参考答案"
            />
          </div>

          {/* 难度 + 标签 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <div>
              <label htmlFor="difficulty" className={labelCls}>
                难度
              </label>
              <select
                id="difficulty"
                value={formData.difficulty}
                onChange={(e) => setFormData({ ...formData, difficulty: e.target.value as QuestionDifficulty })}
                className={inputCls}
              >
                <option value="BASIC">基础</option>
                <option value="ADVANCED">进阶</option>
                <option value="EXPERT">专家</option>
              </select>
            </div>

            <div>
              <label htmlFor="tags" className={labelCls}>
                标签（用逗号分隔）
              </label>
              <input
                id="tags"
                type="text"
                value={formData.tags}
                onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                className={inputCls}
                placeholder="标签1, 标签2, 标签3"
              />
            </div>
          </div>
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="mt-5 flex items-center justify-end gap-3">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
        >
          取消
        </button>
        <button
          type="button"
          onClick={handleSave}
          disabled={saving || !formData.content.trim()}
          className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
        >
          {saving && <Loader2 className="w-4 h-4 animate-spin" />}
          {saving ? '保存中…' : '保存'}
        </button>
      </div>
    </div>
  );
}
