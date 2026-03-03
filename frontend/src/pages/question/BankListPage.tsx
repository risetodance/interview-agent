import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  BookOpen,
  Plus,
  Trash2,
  Edit3,
  Eye,
  FileQuestion,
  Search,
  Loader2,
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
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className={`bg-white rounded-xl shadow-sm border border-slate-200 p-5 hover:shadow-md transition-shadow ${
        selectable ? 'cursor-pointer hover:border-primary-300' : ''
      }`}
      onClick={() => handleSelectBank(bank)}
    >
      <div className="flex items-start justify-between">
        <div className="flex items-start gap-3">
          <div className={`p-2 rounded-lg ${bank.type === 'SYSTEM' ? 'bg-blue-100' : 'bg-green-100'}`}>
            <BookOpen className={`w-5 h-5 ${bank.type === 'SYSTEM' ? 'text-blue-600' : 'text-green-600'}`} />
          </div>
          <div>
            <h3 className="font-medium text-slate-900">{bank.name}</h3>
            <p className="text-sm text-slate-500 mt-1">
              {bank.description || '暂无描述'}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1">
          {bank.type === 'USER' && !selectable && (
            <>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  navigate(`/questions/bank/${bank.id}/edit`);
                }}
                className="p-2 text-slate-400 hover:text-primary-600 hover:bg-slate-100 rounded-lg transition-colors"
                title="编辑"
              >
                <Edit3 className="w-4 h-4" />
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setDeleteDialog({ show: true, bank });
                }}
                className="p-2 text-slate-400 hover:text-red-600 hover:bg-slate-100 rounded-lg transition-colors"
                title="删除"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </>
          )}
        </div>
      </div>

      <div className="mt-4 flex items-center justify-between">
        <div className="flex items-center gap-4 text-sm text-slate-500">
          <span className="flex items-center gap-1">
            <FileQuestion className="w-4 h-4" />
            {bank.questionCount} 道题目
          </span>
          <span className={`px-2 py-0.5 rounded text-xs ${
            bank.type === 'SYSTEM' ? 'bg-blue-100 text-blue-600' : 'bg-green-100 text-green-600'
          }`}>
            {bank.type === 'SYSTEM' ? '系统题库' : '我的题库'}
          </span>
        </div>
        <button
          onClick={(e) => {
            e.stopPropagation();
            handleViewQuestions(bank.id);
          }}
          className="flex items-center gap-1 text-sm text-primary-600 hover:text-primary-700"
        >
          <Eye className="w-4 h-4" />
          查看题目
        </button>
      </div>
    </motion.div>
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      {/* 页面标题和操作栏 */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">题库管理</h1>
          <p className="text-slate-500 mt-1">管理您的面试题库</p>
        </div>
        <button
          onClick={() => navigate('/questions/bank/create')}
          className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
        >
          <Plus className="w-5 h-5" />
          创建题库
        </button>
      </div>

      {/* 搜索栏 */}
      <div className="relative mb-6">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
        <input
          type="text"
          placeholder="搜索题库..."
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          className="w-full pl-10 pr-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
        />
      </div>

      {error && (
        <div className="mb-4 p-4 bg-red-50 text-red-600 rounded-lg">
          {error}
        </div>
      )}

      {/* 题库列表 */}
      {filteredBanks.length === 0 ? (
        <div className="text-center py-12">
          <BookOpen className="w-12 h-12 text-slate-300 mx-auto mb-4" />
          <p className="text-slate-500">暂无题库</p>
          <button
            onClick={() => navigate('/questions/bank/create')}
            className="mt-4 text-primary-600 hover:text-primary-700"
          >
            创建第一个题库
          </button>
        </div>
      ) : (
        <div className="space-y-8">
          {/* 系统题库 */}
          {systemBanks.length > 0 && (
            <div>
              <h2 className="text-lg font-semibold text-slate-900 mb-4">系统题库</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {systemBanks.map(bank => (
                  <BankCard key={bank.id} bank={bank} />
                ))}
              </div>
            </div>
          )}

          {/* 用户题库 */}
          {userBanks.length > 0 && (
            <div>
              <h2 className="text-lg font-semibold text-slate-900 mb-4">我的题库</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
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
