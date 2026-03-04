import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { TrendingUp, Loader2, AlertCircle } from 'lucide-react';
import { interviewApi } from '../../api/interview';
import type { ScoreTrend, ScoreStatistics } from '../../types/interview';

interface ScoreTrendChartProps {
  className?: string;
}

/**
 * 评分趋势图表组件
 * 展示用户的面试评分趋势和统计信息
 */
export default function ScoreTrendChart({ className = '' }: ScoreTrendChartProps) {
  const [trend, setTrend] = useState<ScoreTrend | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadScoreTrend();
  }, []);

  const loadScoreTrend = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await interviewApi.getScoreTrend();
      setTrend(data);
    } catch (err) {
      console.error('加载评分趋势失败', err);
      setError('加载评分趋势失败');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className={`bg-white rounded-2xl p-6 flex items-center justify-center ${className}`}>
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className={`bg-white rounded-2xl p-6 flex items-center justify-center ${className}`}>
        <div className="text-center">
          <AlertCircle className="w-8 h-8 text-red-500 mx-auto mb-2" />
          <p className="text-slate-500">{error}</p>
        </div>
      </div>
    );
  }

  if (!trend || trend.dailyScores.length === 0) {
    return (
      <div className={`bg-white rounded-2xl p-6 text-center ${className}`}>
        <TrendingUp className="w-12 h-12 text-slate-300 mx-auto mb-4" />
        <h3 className="text-lg font-semibold text-slate-700 mb-2">暂无评分数据</h3>
        <p className="text-slate-500">完成面试后即可查看评分趋势</p>
      </div>
    );
  }

  // 准备图表数据
  const chartData = trend.dailyScores.map((daily) => ({
    date: daily.date.split('T')[0], // 格式化日期
    score: Math.round(daily.averageScore),
    count: daily.interviewCount,
  }));

  return (
    <div className={`space-y-6 ${className}`}>
      {/* 统计卡片 */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatCard
          label="平均分"
          value={trend.statistics.averageScore.toFixed(1)}
          color="primary"
        />
        <StatCard
          label="最高分"
          value={trend.statistics.highestScore.toString()}
          color="green"
        />
        <StatCard
          label="最低分"
          value={trend.statistics.lowestScore.toString()}
          color="red"
        />
        <StatCard
          label="面试次数"
          value={trend.statistics.totalInterviews.toString()}
          color="blue"
        />
      </div>

      {/* 趋势图表 */}
      <motion.div
        className="bg-white rounded-2xl p-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-primary-500" />
            <span className="font-semibold text-slate-800">评分趋势</span>
          </div>
          <span className="text-sm text-slate-500">
            共 {trend.statistics.totalInterviews} 场练习
          </span>
        </div>

        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis
                dataKey="date"
                axisLine={false}
                tickLine={false}
                tick={{ fill: '#94a3b8', fontSize: 12 }}
              />
              <YAxis
                domain={[0, 100]}
                axisLine={false}
                tickLine={false}
                tick={{ fill: '#94a3b8', fontSize: 12 }}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#fff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '12px',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                }}
                formatter={(value: number, name: string) => [
                  `${value} 分`,
                  name === 'score' ? '得分' : name,
                ]}
                labelFormatter={(label) => `日期: ${label}`}
              />
              <Line
                type="monotone"
                dataKey="score"
                stroke="#6366f1"
                strokeWidth={3}
                dot={{ fill: '#6366f1', strokeWidth: 2, r: 5 }}
                activeDot={{ r: 8, fill: '#6366f1' }}
                name="score"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </motion.div>
    </div>
  );
}

// 统计卡片组件
interface StatCardProps {
  label: string;
  value: string;
  color: 'primary' | 'green' | 'red' | 'blue';
}

function StatCard({ label, value, color }: StatCardProps) {
  const colorClasses = {
    primary: 'bg-primary-50 text-primary-700',
    green: 'bg-green-50 text-green-700',
    red: 'bg-red-50 text-red-700',
    blue: 'bg-blue-50 text-blue-700',
  };

  return (
    <div className={`rounded-xl p-4 ${colorClasses[color]}`}>
      <div className="text-sm opacity-70 mb-1">{label}</div>
      <div className="text-2xl font-bold">{value}</div>
    </div>
  );
}
