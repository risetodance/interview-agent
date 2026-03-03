import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft,
  Gift,
  Calendar,
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

// 积分记录类型样式
function getPointsTypeStyle(type: PointsRecordType): {
  bg: string;
  text: string;
  icon: React.ComponentType<{ className?: string }>;
} {
  switch (type) {
    case 'SIGN_IN':
      return {
        bg: 'bg-green-100',
        text: 'text-green-600',
        icon: Calendar,
      };
    case 'COMPLETE_INTERVIEW':
      return {
        bg: 'bg-blue-100',
        text: 'text-blue-600',
        icon: Gift,
      };
    case 'SHARE_KB':
      return {
        bg: 'bg-purple-100',
        text: 'text-purple-600',
        icon: Gift,
      };
    case 'EXCHANGE':
      return {
        bg: 'bg-orange-100',
        text: 'text-orange-600',
        icon: Gift,
      };
    default:
      return {
        bg: 'bg-slate-100',
        text: 'text-slate-600',
        icon: Gift,
      };
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
function PointsRecordRow({
  record,
  index,
}: {
  record: PointsRecordDTO;
  index: number;
}) {
  const typeStyle = getPointsTypeStyle(record.type);
  const isPositive = record.points > 0;

  return (
    <motion.tr
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.05 }}
      className="border-b border-slate-50 hover:bg-slate-50 transition-colors"
    >
      <td className="px-6 py-4">
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-lg ${typeStyle.bg}`}>
            <typeStyle.icon className={`w-4 h-4 ${typeStyle.text}`} />
          </div>
          <div>
            <p className="font-medium text-slate-800">{getPointsTypeText(record.type)}</p>
            <p className="text-sm text-slate-500">{record.description}</p>
          </div>
        </div>
      </td>
      <td className="px-6 py-4">
        <span className={`font-semibold ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
          {isPositive ? '+' : ''}{record.points}
        </span>
      </td>
      <td className="px-6 py-4 text-sm text-slate-500">
        {formatDateTime(record.createdAt)}
      </td>
    </motion.tr>
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
    <div className="flex items-center justify-between px-6 py-4 border-t border-slate-100">
      <div className="text-sm text-slate-500">
        共 <span className="font-medium text-slate-700">{totalElements}</span> 条记录，
        第 <span className="font-medium text-slate-700">{currentPage + 1}</span> /{' '}
        <span className="font-medium text-slate-700">{totalPages}</span> 页
      </div>
      <div className="flex items-center gap-1">
        {/* 上一页 */}
        <button
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 0}
          className="p-2 rounded-lg text-slate-500 hover:bg-slate-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>

        {/* 页码 */}
        {getPageNumbers().map((page, index) =>
          typeof page === 'number' ? (
            <button
              key={index}
              onClick={() => onPageChange(page)}
              className={`min-w-[36px] h-9 px-2 rounded-lg text-sm font-medium transition-colors ${
                currentPage === page
                  ? 'bg-primary-500 text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {page + 1}
            </button>
          ) : (
            <span key={index} className="px-2 text-slate-400">
              {page}
            </span>
          )
        )}

        {/* 下一页 */}
        <button
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage === totalPages - 1}
          className="p-2 rounded-lg text-slate-500 hover:bg-slate-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronRight className="w-5 h-5" />
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
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-5xl mx-auto">
        <div className="bg-red-50 text-red-600 p-4 rounded-lg mb-4">
          {error}
        </div>
        <button
          onClick={handleBack}
          className="flex items-center gap-2 text-primary-600 hover:text-primary-700"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>返回会员中心</span>
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto">
      {/* 页面头部 */}
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-4">
          <button
            onClick={handleBack}
            className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-slate-600" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
              <Gift className="w-7 h-7 text-primary-500" />
              积分记录
            </h1>
            <p className="text-slate-500 mt-1">查看您的积分获取与使用记录</p>
          </div>
        </div>
        <div className="flex items-center gap-3 px-4 py-2 bg-primary-50 rounded-xl">
          <Gift className="w-5 h-5 text-primary-600" />
          <span className="text-sm text-primary-600">当前积分:</span>
          <span className="text-xl font-bold text-primary-700">
            {points.toLocaleString()}
          </span>
        </div>
      </div>

      {/* 积分记录列表 */}
      <div className="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
        {loading && (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="w-6 h-6 text-primary-500 animate-spin" />
          </div>
        )}

        {!loading && history && history.content.length === 0 ? (
          <div className="text-center py-16">
            <Gift className="w-16 h-16 text-slate-300 mx-auto mb-4" />
            <p className="text-slate-500">暂无积分记录</p>
            <button
              onClick={handleBack}
              className="mt-4 text-primary-500 hover:text-primary-600"
            >
              返回会员中心
            </button>
          </div>
        ) : (
          <>
            <table className="w-full">
              <thead className="bg-slate-50 border-b border-slate-100">
                <tr>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">
                    记录类型
                  </th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">
                    积分变化
                  </th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600">
                    时间
                  </th>
                </tr>
              </thead>
              <tbody>
                {history?.content.map((record, index) => (
                  <PointsRecordRow
                    key={record.id}
                    record={record}
                    index={index}
                  />
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
