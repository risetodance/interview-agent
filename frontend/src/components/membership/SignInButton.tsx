import { useState, useEffect } from 'react';
import { Loader2 } from 'lucide-react';
import { pointsApi, SignInResponse } from '../../api/points';

export interface SignInButtonProps {
  /** 当前是否已签到 */
  signedIn?: boolean;
  /** 连续签到天数 */
  consecutiveDays?: number;
  /** 签到成功回调 */
  onSignInSuccess?: (data: SignInResponse) => void;
  /** 自定义样式 */
  className?: string;
}

/**
 * 签到按钮组件 - 简洁版
 *
 * 功能：
 * - 显示签到按钮或已签到状态
 * - 点击签到后调用签到API
 * - 显示连续签到天数
 * - 显示本次签到获得的积分数
 */
export default function SignInButton({
  signedIn = false,
  consecutiveDays = 0,
  onSignInSuccess,
  className = ''
}: SignInButtonProps) {
  const [loading, setLoading] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [signInResult, setSignInResult] = useState<SignInResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 使用内部状态来跟踪签到状态，确保立即更新UI
  const [signedInState, setSignedInState] = useState(signedIn);

  // 当 props 变化时更新内部状态
  useEffect(() => {
    setSignedInState(signedIn);
  }, [signedIn]);

  const handleSignIn = async () => {
    if (loading || signedInState) return;

    setLoading(true);
    setError(null);

    try {
      const result = await pointsApi.signIn();
      setSignInResult(result);
      setShowSuccess(true);

      // 立即更新内部签到状态
      setSignedInState(true);

      onSignInSuccess?.(result);

      // 3秒后隐藏成功提示
      setTimeout(() => {
        setShowSuccess(false);
      }, 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : '签到失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={`relative inline-flex items-center gap-3 ${className}`}>
      {/* 签到信息 */}
      <div className="flex flex-col">
        <span className={`text-sm font-medium ${signedInState ? 'text-zinc-500' : 'text-zinc-900'}`}>
          {signedInState ? '已签到' : '每日签到'}
        </span>
        <span className="text-xs text-zinc-400">
          {signedInState
            ? consecutiveDays > 0
              ? `连续 ${consecutiveDays} 天`
              : '今日已完成'
            : '签到得积分'}
        </span>
      </div>

      {/* 签到按钮 */}
      <button
        type="button"
        onClick={handleSignIn}
        disabled={loading || signedInState}
        className={
          signedInState
            ? 'h-9 px-4 rounded-md border border-zinc-200 bg-zinc-50 text-zinc-400 text-sm cursor-not-allowed'
            : 'h-9 px-4 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2'
        }
      >
        {loading && <Loader2 className="w-4 h-4 animate-spin" />}
        {loading ? '签到中…' : signedInState ? '已签到' : '签到'}
      </button>

      {/* 错误提示 */}
      {error && (
        <div className="absolute top-full left-0 mt-2 px-3 py-2 bg-red-50 border border-red-200 rounded-md text-red-700 text-xs whitespace-nowrap z-10 fade-in">
          {error}
        </div>
      )}

      {/* 签到成功提示 */}
      {showSuccess && signInResult && (
        <div className="absolute top-full right-0 mt-2 bg-white border border-zinc-200 rounded-md shadow-lg px-3 py-2.5 text-xs whitespace-nowrap z-10 fade-in">
          <span className="text-zinc-600">签到成功</span>
          <span className="mx-1.5 font-mono font-medium text-emerald-700">
            +{signInResult.pointsCanEarn}
          </span>
          <span className="text-zinc-400">连续 {signInResult.consecutiveDays} 天</span>
        </div>
      )}
    </div>
  );
}
