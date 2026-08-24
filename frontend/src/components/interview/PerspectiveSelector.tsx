import { useEffect } from 'react';
import type { InterviewerRole } from '../../types/interviewerRole';
import { CheckCircle } from 'lucide-react';

interface PerspectiveSelectorProps {
  roles: InterviewerRole[];
  selectedIds: number[];
  onChange: (ids: number[]) => void;
  maxSelect?: number;
  disabled?: boolean;
  // 会话级权重配置
  weights?: Record<number, number>;
  onWeightsChange?: (weights: Record<number, number>) => void;
  // 权重校验状态回调（总和是否为100%）
  onWeightValidationChange?: (isValid: boolean, totalWeight: number) => void;
}

const MAX_PERSPECTIVES = 3;

/**
 * 视角选择器组件 - 多选卡片，最多选择3个视角
 */
export default function PerspectiveSelector({
  roles,
  selectedIds,
  onChange,
  maxSelect = MAX_PERSPECTIVES,
  disabled = false,
  weights = {},
  onWeightsChange,
  onWeightValidationChange,
}: PerspectiveSelectorProps) {
  const enabledRoles = roles.filter((r) => r.status);

  // 计算总权重（浮点数容差 0.01）
  const totalWeight = selectedIds.reduce((sum, id) => {
    const role = enabledRoles.find((r) => r.id === id);
    return sum + (weights[id] ?? role?.weight ?? 0);
  }, 0);
  const isWeightValid = Math.abs(totalWeight - 1) < 0.01;

  // 通知父组件权重校验状态变化
  useEffect(() => {
    if (onWeightValidationChange && selectedIds.length > 0) {
      onWeightValidationChange(isWeightValid, totalWeight);
    }
  }, [isWeightValid, totalWeight, selectedIds.length, onWeightValidationChange]);

  const handleToggle = (roleId: number) => {
    if (disabled) return;

    const isSelected = selectedIds.includes(roleId);
    if (isSelected) {
      // 取消选择
      onChange(selectedIds.filter((id) => id !== roleId));
    } else {
      // 新增选择（不超过上限）
      if (selectedIds.length < maxSelect) {
        onChange([...selectedIds, roleId]);
      }
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <label className="block text-xs font-medium text-zinc-600">
          选择面试官视角
          <span className="text-zinc-400 font-normal ml-1">（最多选 {maxSelect} 个）</span>
        </label>
        <span className="font-mono text-xs text-zinc-400 tabular-nums">
          {selectedIds.length} / {maxSelect}
        </span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {enabledRoles.map((role) => {
          const isSelected = selectedIds.includes(role.id);
          const isDisabled = disabled || (!isSelected && selectedIds.length >= maxSelect);

          return (
            <div
              key={role.id}
              className={`
                relative p-4 rounded-lg border text-left transition-colors
                ${isSelected
                  ? 'border-primary-600 bg-primary-50'
                  : isDisabled
                    ? 'border-zinc-200 bg-zinc-50 opacity-60 cursor-not-allowed'
                    : 'border-zinc-200 bg-white hover:border-primary-400 cursor-pointer'
                }
              `}
              onClick={() => handleToggle(role.id)}
            >
              {/* 选中标记 */}
              {isSelected && (
                <div
                  className="absolute top-3 right-3"
                  onClick={(e) => e.stopPropagation()}
                >
                  <CheckCircle className="w-4 h-4 text-primary-600" />
                </div>
              )}

              {/* 角色名称（点击区域） */}
              <div className="flex items-center gap-2 mb-2 pr-6" onClick={(e) => e.stopPropagation()}>
                <div>
                  <div className="text-sm font-medium text-zinc-900">{role.roleName}</div>
                  {role.defaultTemplate && (
                    <span className="text-xs text-zinc-400">默认模板</span>
                  )}
                </div>
              </div>

              {/* 描述 */}
              {role.description && (
                <p className="text-xs text-zinc-500 mb-3 line-clamp-2">
                  {role.description}
                </p>
              )}

              {/* 权重（选中时可编辑，阻止点击冒泡） */}
              {isSelected && onWeightsChange ? (
                <div className="flex items-center gap-2 pointer-events-none" onClick={(e) => e.stopPropagation()}>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    value={weights[role.id] ?? role.weight}
                    onChange={(e) => {
                      e.stopPropagation();
                      const newWeight = parseFloat(e.target.value);
                      onWeightsChange({ ...weights, [role.id]: newWeight });
                    }}
                    onClick={(e) => e.stopPropagation()}
                    style={{ pointerEvents: 'auto' }}
                    className="flex-1 h-1.5 bg-zinc-200 rounded-full appearance-none cursor-pointer accent-primary-600"
                  />
                  <span className="font-mono text-xs font-medium text-primary-700 w-10 text-right tabular-nums">
                    {((weights[role.id] ?? role.weight) * 100).toFixed(0)}%
                  </span>
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  <div className="flex-1 h-1.5 bg-zinc-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-primary-400 rounded-full"
                      style={{ width: `${role.weight * 100}%` }}
                    />
                  </div>
                  <span className="font-mono text-xs text-zinc-500 w-10 text-right tabular-nums">
                    {(role.weight * 100).toFixed(0)}%
                  </span>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* 已选择提示 + 权重总和 */}
      {selectedIds.length > 0 && (
        <div className="mt-3 space-y-2">
          <div className="text-xs text-zinc-500">
            <span className="font-medium text-zinc-700">已选择：</span>
            {selectedIds
              .map((id) => {
                const role = enabledRoles.find((r) => r.id === id);
                const w = weights[id] ?? role?.weight ?? 0;
                return `${role?.roleName}(${(w * 100).toFixed(0)}%)`;
              })
              .filter(Boolean)
              .join('、')}
          </div>

          {/* 权重总和校验 */}
          {onWeightsChange && (
            <div className="flex items-center gap-2">
              <div className="flex-1 h-1.5 bg-zinc-100 rounded-full overflow-hidden">
                <div
                  className={`h-full rounded-full ${
                    totalWeight === 1 ? 'bg-emerald-500' : totalWeight > 1 ? 'bg-red-500' : 'bg-amber-500'
                  }`}
                  style={{ width: `${Math.min(totalWeight, 1) * 100}%` }}
                />
              </div>
              <span className={`font-mono text-xs font-medium w-16 text-right tabular-nums ${
                totalWeight === 1 ? 'text-emerald-700' : 'text-red-700'
              }`}>
                总计 {(totalWeight * 100).toFixed(0)}%
              </span>
              {totalWeight !== 1 && (
                <span className="text-xs text-amber-700">
                  {totalWeight < 1 ? '权重不足' : '权重超出'}
                </span>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
