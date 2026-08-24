import { useState, useEffect } from 'react';
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
        className={`min-h-[38px] w-full border rounded-md px-2 py-1.5 flex items-center gap-1.5 flex-wrap transition-colors ${
          disabled
            ? 'bg-zinc-100 border-zinc-200 cursor-not-allowed'
            : 'bg-white border-zinc-300 hover:border-zinc-400 cursor-pointer'
        }`}
        onClick={() => !disabled && setIsOpen(!isOpen)}
      >
        {selectedBanks.length === 0 ? (
          <span className="text-sm text-zinc-400 px-1">点击选择题库</span>
        ) : (
          selectedBanks.map(bank => (
            <span
              key={bank.id}
              className="inline-flex items-center gap-1 px-1.5 py-0.5 border border-primary-200 bg-primary-50 text-primary-700 rounded text-xs"
            >
              {bank.name}
              {!disabled && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleSelectBank(bank.id);
                  }}
                  className="text-primary-400 hover:text-primary-800 transition-colors"
                  aria-label={`移除题库 ${bank.name}`}
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </span>
          ))
        )}
        {selectedBanks.length > 0 && selectedBanks.length < maxSelections && !disabled && (
          <span className="font-mono text-xs text-zinc-400 ml-auto tabular-nums">
            还可选 {maxSelections - selectedBanks.length} 个
          </span>
        )}
      </div>

      {/* 下拉选择框 */}
      {isOpen && !disabled && (
        <div className="absolute z-50 w-full mt-1.5 bg-white border border-zinc-200 rounded-md shadow-lg max-h-[400px] overflow-hidden fade-in">
          {/* 搜索框 */}
          <div className="p-2.5 border-b border-zinc-100">
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400" />
              <input
                type="text"
                placeholder="搜索题库…"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                className="w-full h-9 pl-8 pr-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors"
                onClick={(e) => e.stopPropagation()}
              />
            </div>
          </div>

          {/* 题库列表 */}
          <div className="overflow-y-auto max-h-[300px] p-2">
            {loading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-5 h-5 text-primary-600 animate-spin" />
              </div>
            ) : filteredBanks.length === 0 ? (
              <p className="py-8 text-xs text-zinc-400 text-center">暂无题库</p>
            ) : (
              <div className="space-y-0.5">
                {filteredBanks.map(bank => {
                  const isSelected = selectedBankIds.includes(bank.id);
                  const isDisabled = !isSelected && selectedBankIds.length >= maxSelections;

                  return (
                    <div
                      key={bank.id}
                      className={`flex items-center justify-between gap-3 px-3 py-2 rounded-md cursor-pointer transition-colors ${
                        isDisabled
                          ? 'opacity-50 cursor-not-allowed'
                          : isSelected
                          ? 'bg-primary-50'
                          : 'hover:bg-zinc-50'
                      }`}
                      onClick={(e) => {
                        e.stopPropagation();
                        if (!isDisabled) {
                          handleSelectBank(bank.id);
                        }
                      }}
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <BookOpen className="w-4 h-4 text-zinc-400 shrink-0" strokeWidth={1.75} />
                        <div className="min-w-0">
                          <div className="text-sm text-zinc-800 truncate">{bank.name}</div>
                          <div className="font-mono text-xs text-zinc-400 tabular-nums">
                            {bank.questionCount} 道
                            {bank.type === 'SYSTEM' ? ' · 系统题库' : ''}
                          </div>
                        </div>
                      </div>
                      {isSelected && (
                        <Check className="w-4 h-4 text-primary-600 shrink-0" strokeWidth={2} />
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* 底部提示 */}
          {filteredBanks.length > 0 && (
            <div className="px-3 py-2 border-t border-zinc-100 font-mono text-xs text-zinc-400 tabular-nums">
              已选择 {selectedBankIds.length}/{maxSelections} 个题库
            </div>
          )}
        </div>
      )}

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
