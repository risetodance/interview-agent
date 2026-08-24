import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Plus,
  Trash2,
  Edit3,
  Eye,
  FileQuestion,
  Search,
  Loader2,
  AlertCircle,
} from 'lucide-react';
import {
  questionApi,
  QuestionBankDTO,
} from '../../api/question';
import DeleteConfirmDialog from '../../components/DeleteConfirmDialog';

interface BankListPageProps {
  onSelectBank?: (bankId: number) => void;
  onViewQuestions?: (bankId: number) => void;
  selectable?: boolean;
}

export default function BankListPage({
  onSelectBank,
  onViewQuestions,
  selectable = false
}: BankListPageProps) {
  const navigate = useNavigate();
  const [banks, setBanks] = useState<QuestionBankDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [deleteDialog, setDeleteDialog] = useState<{
    show: boolean;
    bank: QuestionBankDTO | null;
  }>({ show: false, bank: null });
  const [deleting, setDeleting] = useState(false);

  // 加载题库列表
  const loadBanks = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await questionApi.getUserBanks();
      setBanks(data);
    } catch (err) {
      setError('加载题库列表失败');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadBanks();
  }, []);

  // 过滤题库
  const filteredBanks = banks.filter(bank =>
    bank.name.toLowerCase().includes(searchKeyword.toLowerCase()) ||
    (bank.description?.toLowerCase().includes(searchKeyword.toLowerCase()))
  );

  // 系统题库和用户题库分开
  const systemBanks = filteredBanks.filter(b => b.type === 'SYSTEM');
  const userBanks = filteredBanks.filter(b => b.type === 'USER');

  // 处理选择题库
  const handleSelectBank = (bank: QuestionBankDTO) => {
    if (selectable && onSelectBank) {
      onSelectBank(bank.id);
    }
  };

  // 处理查看题目
  const handleViewQuestions = (bankId: number) => {
    if (onViewQuestions) {
      onViewQuestions(bankId);
    } else {
      navigate(`/questions/bank/${bankId}`);
    }
  };

  // 处理删除题库
  const handleDeleteBank = async () => {
    if (!deleteDialog.bank) return;

    try {
      setDeleting(true);
      await questionApi.deleteBank(deleteDialog.bank.id);
      setDeleteDialog({ show: false, bank: null });
      loadBanks();
    } catch (err) {
      console.error('删除题库失败', err);
    } finally {
      setDeleting(false);
    }
  };

  // 题库卡片组件
  const BankCard = ({ bank }: { bank: QuestionBankDTO }) => (
    <div
      className={`bg-white border border-zinc-200 rounded-lg p-5 transition-colors ${
        selectable ? 'cursor-pointer hover:border-primary-400' : 'hover:border-zinc-300'
      }`}
      onClick={() => handleSelectBank(bank)}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-medium text-zinc-900 truncate">{bank.name}</h3>
            <span
              className={`text-xs border rounded px-1.5 py-0.5 shrink-0 ${
                bank.type === 'SYSTEM'
                  ? 'text-zinc-500 bg-zinc-50 border-zinc-200'
                  : 'text-primary-700 bg-primary-50 border-primary-200'
              }`}
            >
              {bank.type === 'SYSTEM' ? '系统题库' : '我的题库'}
            </span>
          </div>
          <p className="mt-1.5 text-sm text-zinc-500 line-clamp-2">
            {bank.description || '暂无描述'}
          </p>
        </div>
        {bank.type === 'USER' && !selectable && (
          <div className="flex items-center gap-0.5 shrink-0">
            <button
              onClick={(e) => {
                e.stopPropagation();
                navigate(`/questions/bank/${bank.id}/edit`);
              }}
              className="p-1.5 text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 rounded-md transition-colors"
              title="编辑"
            >
              <Edit3 className="w-4 h-4" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                setDeleteDialog({ show: true, bank });
              }}
              className="p-1.5 text-zinc-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
              title="删除"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>

      <div className="mt-4 pt-4 border-t border-zinc-100 flex items-center justify-between gap-2">
        <span className="flex items-center gap-1.5 font-mono text-xs text-zinc-400 tabular-nums">
          <FileQuestion className="w-3.5 h-3.5" />
          {bank.questionCount} 道题目
        </span>
        <button
          onClick={(e) => {
            e.stopPropagation();
            handleViewQuestions(bank.id);
          }}
          className="flex items-center gap-1 text-xs text-primary-700 hover:text-primary-800 transition-colors"
        >
          <Eye className="w-3.5 h-3.5" />
          查看题目
        </button>
      </div>
    </div>
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-6 h-6 text-primary-600 animate-spin" />
      </div>
    );
  }

  return (
    <div className="fade-in">
      {/* 页面标题和操作栏 */}
      <div className="flex items-end justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">题库管理</h1>
          <p className="mt-1 text-sm text-zinc-500">管理您的面试题库</p>
        </div>
        <button
          onClick={() => navigate('/questions/bank/create')}
          className="h-9 px-4 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors flex items-center gap-1.5"
        >
          <Plus className="w-4 h-4" />
          创建题库
        </button>
      </div>

      {/* 搜索栏 */}
      <div className="relative mb-5">
        <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400" />
        <input
          type="text"
          placeholder="搜索题库名称或描述…"
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          className="w-full h-9 pl-8 pr-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
        />
      </div>

      {error && (
        <div className="mb-4 flex items-center gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700">
          <AlertCircle className="w-4 h-4 shrink-0" />
          {error}
        </div>
      )}

      {/* 题库列表 */}
      {filteredBanks.length === 0 ? (
        <div className="bg-white border border-zinc-200 rounded-lg">
          <p className="px-5 py-10 text-xs text-zinc-400 text-center">
            暂无题库，{' '}
            <button
              onClick={() => navigate('/questions/bank/create')}
              className="text-primary-700 hover:text-primary-800 underline underline-offset-2 transition-colors"
            >
              创建第一个题库
            </button>
          </p>
        </div>
      ) : (
        <div className="space-y-8">
          {/* 系统题库 */}
          {systemBanks.length > 0 && (
            <div>
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-sm font-medium text-zinc-900">系统题库</h2>
                <span className="font-mono text-xs text-zinc-400 tabular-nums">
                  {systemBanks.length} 个
                </span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {systemBanks.map(bank => (
                  <BankCard key={bank.id} bank={bank} />
                ))}
              </div>
            </div>
          )}

          {/* 用户题库 */}
          {userBanks.length > 0 && (
            <div>
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-sm font-medium text-zinc-900">我的题库</h2>
                <span className="font-mono text-xs text-zinc-400 tabular-nums">
                  {userBanks.length} 个
                </span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {userBanks.map(bank => (
                  <BankCard key={bank.id} bank={bank} />
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* 删除确认对话框 */}
      <DeleteConfirmDialog
        open={deleteDialog.show}
        item={deleteDialog.bank}
        itemType="题库"
        loading={deleting}
        onConfirm={handleDeleteBank}
        onCancel={() => setDeleteDialog({ show: false, bank: null })}
      />
    </div>
  );
}
