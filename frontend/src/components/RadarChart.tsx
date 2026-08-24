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

/** 主题色（primary 色阶）与中性色（zinc 色阶），hex 供 recharts 使用 */
const CHART_COLORS = {
  primary: '#276f8d',
  primaryLight: '#3589a5',
  grid: '#e4e4e7',
  axisLabel: '#71717a',
  axisValue: '#a1a1aa',
  border: '#e4e4e7',
};

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
      <ResponsiveContainer
        width="100%"
        height="100%"
        initialDimension={{ width: 600, height }}
      >
        <RechartsRadarChart data={normalizedData}>
          <PolarGrid stroke={CHART_COLORS.grid} />
          <PolarAngleAxis
            dataKey="subject"
            tick={{ fill: CHART_COLORS.axisLabel, fontSize: 12, fontWeight: 500 }}
          />
          <PolarRadiusAxis
            angle={90}
            domain={[0, normalizedData.length > 0 ? normalizedData[0].fullMark : 40]}
            tick={{ fill: CHART_COLORS.axisValue, fontSize: 10 }}
            tickFormatter={(value) => value.toString()}
          />
          <Radar
            name="得分"
            dataKey="score"
            stroke={CHART_COLORS.primary}
            fill={CHART_COLORS.primaryLight}
            fillOpacity={0.4}
            strokeWidth={2}
          />
          <Tooltip
            contentStyle={{
              backgroundColor: '#fff',
              border: `1px solid ${CHART_COLORS.border}`,
              borderRadius: '6px',
              boxShadow: '0 1px 3px rgba(0,0,0,0.06)'
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
