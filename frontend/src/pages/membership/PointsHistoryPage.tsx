import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Loader2,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { pointsApi, PointsHistoryResponse, PointsRecordDTO, PointsRecordType } from '../../api/points';

// 积分记录类型文本
function getPointsTypeText(type: PointsRecordType): string {
  switch (type) {
    case 'SIGN_IN':
      return '每日签到';
    case 'COMPLETE_INTERVIEW':
      return '完成面试';
    case 'SHARE_KB':
      return '分享知识库';
    case 'EXCHANGE':
      return '积分兑换';
    default:
      return '其他';
  }
}

// 格式化日期时间
function formatDateTime(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// 积分记录行组件
function PointsRecordRow({ record }: { record: PointsRecordDTO }) {
  const isPositive = record.points > 0;

  return (
    <tr className="hover:bg-zinc-50 transition-colors">
      <td className="px-5 py-3.5">
        <p className="text-sm text-zinc-800">{getPointsTypeText(record.type)}</p>
        <p className="mt-0.5 text-xs text-zinc-400">{record.description}</p>
      </td>
      <td className="px-5 py-3.5">
        <span
          className={`font-mono text-sm font-medium tabular-nums ${
            isPositive ? 'text-emerald-700' : 'text-red-700'
          }`}
        >
          {isPositive ? '+' : ''}{record.points}
        </span>
      </td>
      <td className="px-5 py-3.5 font-mono text-xs text-zinc-400 whitespace-nowrap">
        {formatDateTime(record.createdAt)}
      </td>
    </tr>
  );
}

// 分页组件
function Pagination({
  currentPage,
  totalPages,
  totalElements,
  onPageChange,
}: {
  currentPage: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}) {
  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    const maxVisible = 5;

    if (totalPages <= maxVisible) {
      for (let i = 0; i < totalPages; i++) {
        pages.push(i);
      }
    } else {
      if (currentPage < 3) {
        for (let i = 0; i < 4; i++) {
          pages.push(i);
        }
        pages.push('...');
        pages.push(totalPages - 1);
      } else if (currentPage > totalPages - 3) {
        pages.push(0);
        pages.push('...');
        for (let i = totalPages - 4; i < totalPages; i++) {
          pages.push(i);
        }
      } else {
        pages.push(0);
        pages.push('...');
        for (let i = currentPage - 1; i <= currentPage + 1; i++) {
          pages.push(i);
        }
        pages.push('...');
        pages.push(totalPages - 1);
      }
    }

    return pages;
  };

  return (
    <div className="flex items-center justify-between px-5 py-3 border-t border-zinc-100">
      <p className="text-xs text-zinc-500">
        共 <span className="font-mono text-zinc-700 tabular-nums">{totalElements}</span> 条 · 第{' '}
        <span className="font-mono text-zinc-700 tabular-nums">{currentPage + 1}</span> /{' '}
        <span className="font-mono text-zinc-700 tabular-nums">{totalPages}</span> 页
      </p>
      <div className="flex items-center gap-1">
        {/* 上一页 */}
        <button
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 0}
          className="h-8 w-8 flex items-center justify-center border border-zinc-300 rounded-md text-zinc-500 hover:bg-zinc-50 hover:border-zinc-400 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          aria-label="上一页"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>

        {/* 页码 */}
        {getPageNumbers().map((page, index) =>
          typeof page === 'number' ? (
            <button
              key={index}
              onClick={() => onPageChange(page)}
              className={`h-8 min-w-[32px] px-1.5 rounded-md text-sm font-medium border transition-colors ${
                currentPage === page
                  ? 'border-primary-600 bg-primary-600 text-white'
                  : 'border-zinc-300 text-zinc-600 hover:bg-zinc-50 hover:border-zinc-400'
              }`}
            >
              {page + 1}
            </button>
          ) : (
            <span key={index} className="px-1.5 text-sm text-zinc-400">
              {page}
            </span>
          )
        )}

        {/* 下一页 */}
        <button
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage === totalPages - 1}
          className="h-8 w-8 flex items-center justify-center border border-zinc-300 rounded-md text-zinc-500 hover:bg-zinc-50 hover:border-zinc-400 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          aria-label="下一页"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

export default function PointsHistoryPage() {
  const navigate = useNavigate();
  const [history, setHistory] = useState<PointsHistoryResponse | null>(null);
  const [points, setPoints] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const pageSize = 10;

  // 加载数据
  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const [historyData, pointsData] = await Promise.all([
        pointsApi.getPointsHistory(currentPage, pageSize),
        pointsApi.getPoints(),
      ]);
      setHistory(historyData);
      setPoints(pointsData);
    } catch (err) {
      console.error('加载积分记录失败', err);
      setError('加载积分记录失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, [currentPage]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // 处理页码变化
  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  // 跳回会员页面
  const handleBack = () => {
    navigate('/membership');
  };

  if (loading && !history) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-5 h-5 text-zinc-400 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <div className="border border-red-200 bg-red-50 rounded-lg px-4 py-3 text-sm text-red-700 mb-4">
          {error}
        </div>
        <button
          onClick={handleBack}
          className="inline-flex items-center gap-1.5 text-xs text-zinc-500 hover:text-zinc-800 transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          返回会员中心
        </button>
      </div>
    );
  }

  return (
    <div className="fade-in">
      {/* 页头 */}
      <div className="flex items-end justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={handleBack}
            className="h-9 w-9 flex items-center justify-center border border-zinc-300 rounded-md text-zinc-500 hover:bg-zinc-50 hover:border-zinc-400 transition-colors shrink-0"
            aria-label="返回会员中心"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">积分记录</h1>
            <p className="mt-1 text-sm text-zinc-500">积分获取与使用明细</p>
          </div>
        </div>
        <div className="text-right shrink-0">
          <p className="text-xs text-zinc-500">当前积分</p>
          <p className="mt-1 font-mono text-2xl font-semibold text-primary-800 tabular-nums">
            {points.toLocaleString()}
          </p>
        </div>
      </div>

      {/* 积分记录列表 */}
      <div className="bg-white border border-zinc-200 rounded-lg shadow-sm overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="w-5 h-5 text-zinc-400 animate-spin" />
          </div>
        ) : !loading && history && history.content.length === 0 ? (
          <div className="px-5 py-8 text-center">
            <p className="text-xs text-zinc-400">暂无积分记录，去会员中心签到可获得积分</p>
            <button
              onClick={handleBack}
              className="mt-3 text-xs text-primary-700 hover:text-primary-800 hover:underline underline-offset-2 transition-colors"
            >
              返回会员中心
            </button>
          </div>
        ) : (
          <>
            <table className="w-full">
              <thead className="bg-zinc-50 border-b border-zinc-100">
                <tr>
                  <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">记录类型</th>
                  <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">积分变化</th>
                  <th className="text-left px-5 py-3 text-xs font-medium text-zinc-500">时间</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100">
                {history?.content.map((record) => (
                  <PointsRecordRow key={record.id} record={record} />
                ))}
              </tbody>
            </table>

            {/* 分页 */}
            {history && history.totalPages > 1 && (
              <Pagination
                currentPage={currentPage}
                totalPages={history.totalPages}
                totalElements={history.totalElements}
                onPageChange={handlePageChange}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}
