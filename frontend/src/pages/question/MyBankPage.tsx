import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Loader2,
} from 'lucide-react';
import {
  questionApi,
  CreateQuestionBankRequest,
} from '../../api/question';

const labelCls = 'block text-xs font-medium text-zinc-600 mb-1.5';
const inputCls =
  'w-full h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors';
const textareaCls =
  'w-full px-3 py-2 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors resize-none';

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
        <Loader2 className="w-6 h-6 text-primary-600 animate-spin" />
      </div>
    );
  }

  return (
    <div className="fade-in">
      {/* 页面头部 */}
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate('/questions')}
          className="p-2 -ml-2 hover:bg-zinc-100 rounded-md transition-colors"
          aria-label="返回题库列表"
        >
          <ArrowLeft className="w-5 h-5 text-zinc-500" />
        </button>
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">
            {isEdit ? '编辑题库' : '创建题库'}
          </h1>
          <p className="mt-0.5 text-sm text-zinc-500">
            {isEdit ? '修改题库名称与描述' : '新建一个属于你的面试题库'}
          </p>
        </div>
      </div>

      {/* 表单 */}
      <form onSubmit={handleSubmit} className="mt-6">
        {error && (
          <div className="mb-4 flex items-center gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="bg-white border border-zinc-200 rounded-lg">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">题库信息</h2>
            <span className="font-mono text-xs text-zinc-400">
              {isEdit ? `ID ${bankId}` : 'NEW'}
            </span>
          </div>

          <div className="p-5 space-y-5">
            {/* 题库名称 */}
            <div>
              <label htmlFor="name" className={labelCls}>
                题库名称 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                id="name"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="请输入题库名称"
                className={inputCls}
                maxLength={100}
              />
            </div>

            {/* 题库描述 */}
            <div>
              <label htmlFor="description" className={labelCls}>
                题库描述
              </label>
              <textarea
                id="description"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="请输入题库描述（可选）"
                rows={4}
                className={textareaCls}
                maxLength={500}
              />
              <p className="mt-1 text-right font-mono text-xs text-zinc-400 tabular-nums">
                {formData.description?.length || 0}/500
              </p>
            </div>
          </div>
        </div>

        {/* 操作按钮 */}
        <div className="mt-5 flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={() => navigate('/questions')}
            className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
          >
            取消
          </button>
          <button
            type="submit"
            disabled={saving}
            className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {saving && <Loader2 className="w-4 h-4 animate-spin" />}
            {saving ? '保存中…' : isEdit ? '保存修改' : '创建题库'}
          </button>
        </div>
      </form>
    </div>
  );
}
