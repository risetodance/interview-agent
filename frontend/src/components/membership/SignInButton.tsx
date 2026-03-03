import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Check, Calendar, Loader2, Gift } from 'lucide-react';
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
      {/* 签到状态图标 */}
      <div className={`flex items-center justify-center w-10 h-10 rounded-full ${
        signedInState
          ? 'bg-emerald-100'
          : 'bg-amber-100'
      }`}>
        {loading ? (
          <Loader2 className="w-5 h-5 text-amber-600 animate-spin" />
        ) : signedInState ? (
          <Check className="w-5 h-5 text-emerald-600" />
        ) : (
          <Calendar className="w-5 h-5 text-amber-600" />
        )}
      </div>

      {/* 签到信息 */}
      <div className="flex flex-col">
        <span className={`font-medium ${signedInState ? 'text-emerald-600' : 'text-slate-700'}`}>
          {signedInState ? '已签到' : '每日签到'}
        </span>
        {!signedInState && !loading && (
          <span className="text-xs text-slate-500">签到得积分</span>
        )}
        {signedInState && consecutiveDays > 0 && (
          <span className="text-xs text-emerald-600">连续 {consecutiveDays} 天</span>
        )}
      </div>

      {/* 签到按钮 */}
      <motion.button
        onClick={handleSignIn}
        disabled={loading || signedInState}
        className={`px-4 py-2 rounded-lg text-sm font-medium transition-all
          ${signedInState
            ? 'bg-slate-100 text-slate-400 cursor-not-allowed'
            : loading
              ? 'bg-amber-100 text-amber-600 cursor-wait'
              : 'bg-gradient-to-r from-amber-500 to-orange-500 text-white hover:from-amber-600 hover:to-orange-600'
          }`}
        whileTap={!signedInState && !loading ? { scale: 0.95 } : {}}
      >
        {loading ? '签到中...' : signedInState ? '已签到' : '签到'}
      </motion.button>

      {/* 错误提示 */}
      <AnimatePresence>
        {error && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="absolute top-full left-0 mt-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm whitespace-nowrap z-10"
          >
            {error}
          </motion.div>
        )}
      </AnimatePresence>

      {/* 签到成功提示 */}
      <AnimatePresence>
        {showSuccess && signInResult && (
          <>
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.9 }}
              className="fixed inset-0 bg-black/20 z-40"
              onClick={() => setShowSuccess(false)}
            />
            <motion.div
              initial={{ opacity: 0, scale: 0.8, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.8, y: 20 }}
              className="absolute top-full left-1/2 -translate-x-1/2 mt-4 z-50"
            >
              <div className="bg-white rounded-2xl shadow-2xl p-5 text-center min-w-[180px]">
                <motion.div
                  className="w-12 h-12 mx-auto bg-gradient-to-br from-amber-400 to-orange-500 rounded-full flex items-center justify-center mb-3"
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  transition={{ type: 'spring', stiffness: 300, damping: 20 }}
                >
                  <Gift className="w-6 h-6 text-white" />
                </motion.div>
                <h4 className="text-lg font-bold text-slate-900 mb-1">
                  签到成功!
                </h4>
                <div className="text-2xl font-bold text-orange-500">
                  +{signInResult.pointsCanEarn}
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  连续签到 {signInResult.consecutiveDays} 天
                </p>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}
