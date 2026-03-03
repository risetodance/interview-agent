import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowLeft, Save, Loader2 } from 'lucide-react';
import { questionApi, QuestionDifficulty } from '../../api/question';

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
    difficulty: 'MEDIUM',
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
            difficulty: question.difficulty || 'MEDIUM',
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
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto p-6">
      {/* 页面头部 */}
      <div className="flex items-center gap-4 mb-6">
        <button
          onClick={() => navigate(-1)}
          className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-slate-600" />
        </button>
        <h1 className="text-2xl font-bold text-slate-900">编辑题目</h1>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-white rounded-xl border border-slate-200 p-6"
      >
        {/* 题目内容 */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-slate-700 mb-2">
            题目内容
          </label>
          <textarea
            value={formData.content}
            onChange={(e) => setFormData({ ...formData, content: e.target.value })}
            rows={4}
            className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
            placeholder="请输入题目内容"
          />
        </div>

        {/* 答案 */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-slate-700 mb-2">
            参考答案
          </label>
          <textarea
            value={formData.answer}
            onChange={(e) => setFormData({ ...formData, answer: e.target.value })}
            rows={6}
            className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
            placeholder="请输入参考答案"
          />
        </div>

        {/* 难度 */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-slate-700 mb-2">
            难度
          </label>
          <select
            value={formData.difficulty}
            onChange={(e) => setFormData({ ...formData, difficulty: e.target.value as QuestionDifficulty })}
            className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          >
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </div>

        {/* 标签 */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-slate-700 mb-2">
            标签（用逗号分隔）
          </label>
          <input
            type="text"
            value={formData.tags}
            onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
            className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            placeholder="标签1, 标签2, 标签3"
          />
        </div>

        {/* 操作按钮 */}
        <div className="flex items-center justify-end gap-3">
          <button
            onClick={() => navigate(-1)}
            className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
          >
            取消
          </button>
          <button
            onClick={handleSave}
            disabled={saving || !formData.content.trim()}
            className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
          >
            {saving ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <Save className="w-5 h-5" />
            )}
            保存
          </button>
        </div>
      </motion.div>
    </div>
  );
}
