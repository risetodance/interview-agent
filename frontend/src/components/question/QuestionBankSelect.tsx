import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  BookOpen,
  Check,
  Search,
  Loader2,
  X,
} from 'lucide-react';
import {
  questionApi,
  QuestionBankDTO,
} from '../../api/question';

interface QuestionBankSelectProps {
  selectedBankIds: number[];
  onChange: (bankIds: number[]) => void;
  maxSelections?: number;
  disabled?: boolean;
}

export default function QuestionBankSelect({
  selectedBankIds,
  onChange,
  maxSelections = 5,
  disabled = false,
}: QuestionBankSelectProps) {
  const [banks, setBanks] = useState<QuestionBankDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [isOpen, setIsOpen] = useState(false);

  // 加载题库列表
  useEffect(() => {
    loadBanks();
  }, []);

  const loadBanks = async () => {
    try {
      setLoading(true);
      const data = await questionApi.getUserBanks();
      setBanks(data);
    } catch (err) {
      console.error('加载题库失败', err);
    } finally {
      setLoading(false);
    }
  };

  // 过滤题库
  const filteredBanks = banks.filter(bank =>
    bank.name.toLowerCase().includes(searchKeyword.toLowerCase()) ||
    (bank.description?.toLowerCase().includes(searchKeyword.toLowerCase()))
  );

  // 处理选择题库
  const handleSelectBank = (bankId: number) => {
    if (disabled) return;

    if (selectedBankIds.includes(bankId)) {
      // 取消选择
      onChange(selectedBankIds.filter(id => id !== bankId));
    } else {
      // 新增选择
      if (selectedBankIds.length < maxSelections) {
        onChange([...selectedBankIds, bankId]);
      }
    }
  };

  // 获取选中的题库
  const selectedBanks = banks.filter(b => selectedBankIds.includes(b.id));

  return (
    <div className="relative">
      {/* 已选题库显示 */}
      <div
        className={`min-h-[48px] border rounded-lg p-2 cursor-pointer flex items-center gap-2 flex-wrap ${
          disabled
            ? 'bg-slate-50 border-slate-200 cursor-not-allowed'
            : 'bg-white border-slate-200 hover:border-slate-300'
        }`}
        onClick={() => !disabled && setIsOpen(!isOpen)}
      >
        {selectedBanks.length === 0 ? (
          <span className="text-slate-400">点击选择题库</span>
        ) : (
          selectedBanks.map(bank => (
            <span
              key={bank.id}
              className="inline-flex items-center gap-1 px-2 py-1 bg-primary-100 text-primary-700 rounded text-sm"
            >
              {bank.name}
              {!disabled && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleSelectBank(bank.id);
                  }}
                  className="hover:text-primary-900"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </span>
          ))
        )}
        {selectedBanks.length > 0 && selectedBanks.length < maxSelections && !disabled && (
          <span className="text-xs text-slate-400 ml-auto">
            还可以选择 {maxSelections - selectedBanks.length} 个
          </span>
        )}
      </div>

      {/* 下拉选择框 */}
      <AnimatePresence>
        {isOpen && !disabled && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="absolute z-50 w-full mt-2 bg-white border border-slate-200 rounded-lg shadow-lg max-h-[400px] overflow-hidden"
          >
            {/* 搜索框 */}
            <div className="p-3 border-b border-slate-200">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <input
                  type="text"
                  placeholder="搜索题库..."
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  onClick={(e) => e.stopPropagation()}
                />
              </div>
            </div>

            {/* 题库列表 */}
            <div className="overflow-y-auto max-h-[300px] p-2">
              {loading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="w-6 h-6 text-primary-500 animate-spin" />
                </div>
              ) : filteredBanks.length === 0 ? (
                <div className="text-center py-8 text-slate-400">
                  暂无题库
                </div>
              ) : (
                <div className="space-y-1">
                  {filteredBanks.map(bank => {
                    const isSelected = selectedBankIds.includes(bank.id);
                    const isDisabled = !isSelected && selectedBankIds.length >= maxSelections;

                    return (
                      <div
                        key={bank.id}
                        className={`flex items-center justify-between p-3 rounded-lg cursor-pointer transition-colors ${
                          isDisabled
                            ? 'opacity-50 cursor-not-allowed'
                            : isSelected
                            ? 'bg-primary-50'
                            : 'hover:bg-slate-50'
                        }`}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (!isDisabled) {
                            handleSelectBank(bank.id);
                          }
                        }}
                      >
                        <div className="flex items-center gap-3">
                          <div className={`p-2 rounded-lg ${bank.type === 'SYSTEM' ? 'bg-blue-100' : 'bg-green-100'}`}>
                            <BookOpen className={`w-4 h-4 ${bank.type === 'SYSTEM' ? 'text-blue-600' : 'text-green-600'}`} />
                          </div>
                          <div>
                            <div className="font-medium text-slate-900">{bank.name}</div>
                            <div className="text-xs text-slate-500">
                              {bank.questionCount} 道题目
                              {bank.type === 'SYSTEM' ? ' · 系统题库' : ''}
                            </div>
                          </div>
                        </div>
                        {isSelected && (
                          <div className="w-5 h-5 rounded-full bg-primary-600 flex items-center justify-center">
                            <Check className="w-3 h-3 text-white" />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 底部提示 */}
            {filteredBanks.length > 0 && (
              <div className="p-3 border-t border-slate-200 text-xs text-slate-400">
                已选择 {selectedBankIds.length}/{maxSelections} 个题库
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* 点击外部关闭 */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40"
          onClick={() => setIsOpen(false)}
        />
      )}
    </div>
  );
}
