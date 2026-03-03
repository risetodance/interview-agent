import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Save,
  Loader2,
} from 'lucide-react';
import {
  questionApi,
  CreateQuestionBankRequest,
} from '../../api/question';

export default function MyBankPage() {
  const navigate = useNavigate();
  const { bankId } = useParams<{ bankId: string }>();
  const isEdit = !!bankId;

  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formData, setFormData] = useState<CreateQuestionBankRequest>({
    name: '',
    description: '',
  });

  // 加载题库数据（编辑模式）
  useEffect(() => {
    if (isEdit && bankId) {
      loadBank();
    }
  }, [bankId]);

  const loadBank = async () => {
    try {
      setLoading(true);
      const bank = await questionApi.getBankById(parseInt(bankId!, 10));
      setFormData({
        name: bank.name,
        description: bank.description || '',
      });
    } catch (err) {
      setError('加载题库失败');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  // 处理表单提交
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.name.trim()) {
      setError('请输入题库名称');
      return;
    }

    try {
      setSaving(true);
      setError(null);

      if (isEdit && bankId) {
        await questionApi.updateBank(parseInt(bankId, 10), formData);
      } else {
        await questionApi.createBank(formData);
      }

      navigate('/questions');
    } catch (err: any) {
      setError(err.message || (isEdit ? '更新题库失败' : '创建题库失败'));
      console.error(err);
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
    <div className="max-w-2xl mx-auto p-6">
      {/* 页面头部 */}
      <div className="flex items-center gap-4 mb-6">
        <button
          onClick={() => navigate('/questions')}
          className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-slate-600" />
        </button>
        <h1 className="text-2xl font-bold text-slate-900">
          {isEdit ? '编辑题库' : '创建题库'}
        </h1>
      </div>

      {/* 表单 */}
      <form onSubmit={handleSubmit} className="space-y-6">
        {error && (
          <div className="p-4 bg-red-50 text-red-600 rounded-lg">
            {error}
          </div>
        )}

        <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-6">
          {/* 题库名称 */}
          <div>
            <label htmlFor="name" className="block text-sm font-medium text-slate-700 mb-2">
              题库名称 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="name"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder="请输入题库名称"
              className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              maxLength={100}
            />
          </div>

          {/* 题库描述 */}
          <div>
            <label htmlFor="description" className="block text-sm font-medium text-slate-700 mb-2">
              题库描述
            </label>
            <textarea
              id="description"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              placeholder="请输入题库描述（可选）"
              rows={4}
              className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
              maxLength={500}
            />
            <p className="mt-1 text-sm text-slate-400">
              {formData.description?.length || 0}/500
            </p>
          </div>
        </div>

        {/* 操作按钮 */}
        <div className="flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={() => navigate('/questions')}
            className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
          >
            取消
          </button>
          <button
            type="submit"
            disabled={saving}
            className="flex items-center gap-2 px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
          >
            {saving ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" />
                保存中...
              </>
            ) : (
              <>
                <Save className="w-5 h-5" />
                {isEdit ? '保存修改' : '创建题库'}
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
