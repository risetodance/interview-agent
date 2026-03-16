import {useMemo} from 'react';
import {
    PolarAngleAxis,
    PolarGrid,
    PolarRadiusAxis,
    Radar,
    RadarChart as RechartsRadarChart,
    ResponsiveContainer,
    Tooltip
} from 'recharts';
import {normalizeScore} from '../utils/score';

interface RadarChartProps {
  data: Array<{
    subject: string;
    score: number;
    fullMark: number;
  }>;
  height?: number;
  className?: string;
}

/**
 * 雷达图组件（自动归一化到统一比例）
 */
export default function RadarChart({ data, height = 320, className = '' }: RadarChartProps) {
  // 归一化数据：将所有维度归一化到最大满分
  const normalizedData = useMemo(() => {
    if (!data || data.length === 0) return [];
    
    const maxFullMark = Math.max(...data.map(item => item.fullMark));
    
    // 计算所有归一化后的分数，找出最大值（可能超过maxFullMark）
    const normalizedScores = data.map(item => 
      normalizeScore(item.score, item.fullMark, maxFullMark)
    );
    const maxNormalizedScore = Math.max(...normalizedScores, maxFullMark);
    
    // 使用实际的最大值作为domain，但至少是maxFullMark
    const chartMax = Math.max(maxFullMark, maxNormalizedScore);
    
    return data.map(item => ({
      subject: item.subject,
      score: normalizeScore(item.score, item.fullMark, maxFullMark),
      fullMark: chartMax,
      originalScore: item.score,
      originalFullMark: item.fullMark
    }));
  }, [data]);

  return (
    <div className={className} style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <RechartsRadarChart data={normalizedData}>
          <PolarGrid stroke="#e2e8f0" />
          <PolarAngleAxis
            dataKey="subject"
            tick={{ fill: '#64748b', fontSize: 12, fontWeight: 500 }}
          />
          <PolarRadiusAxis
            angle={90}
            domain={[0, normalizedData.length > 0 ? normalizedData[0].fullMark : 40]}
            tick={{ fill: '#94a3b8', fontSize: 10 }}
            tickFormatter={(value) => value.toString()}
          />
          <Radar
            name="得分"
            dataKey="score"
            stroke="#6366f1"
            fill="#6366f1"
            fillOpacity={0.6}
            strokeWidth={2}
          />
          <Tooltip
            contentStyle={{
              backgroundColor: '#fff',
              border: '1px solid #e2e8f0',
              borderRadius: '12px',
              boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
            }}
            formatter={(_value: number | undefined, _name: string | undefined, props: any) => {
              const originalScore = props?.payload?.originalScore ?? 0;
              const originalFullMark = props?.payload?.originalFullMark ?? 40;
              const percentage = originalFullMark > 0 
                ? Math.round((originalScore / originalFullMark) * 100) 
                : 0;
              return [`${originalScore}/${originalFullMark} (${percentage}%)`, '得分'];
            }}
          />
        </RechartsRadarChart>
      </ResponsiveContainer>
    </div>
  );
}

