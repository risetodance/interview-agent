import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { KeyRound, AlertCircle, Loader2, ArrowLeft } from 'lucide-react';
import { getErrorMessage } from '../../api/request';
import { authApi } from '../../api/auth';
import { useCodeCountdown } from '../../hooks/useCodeCountdown';
import { isValidEmail } from '../../utils/validate';

/**
 * 忘记密码页：邮箱 + 验证码 + 新密码
 */
export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const location = useLocation();

  // 透传来源页，重置成功回登录后继续原跳转意图
  const from = (location.state as { from?: string })?.from || '/upload';

  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const { codeText, counting, start: startCountdown } = useCodeCountdown();
  // 发码请求 in-flight 防抖（倒计时外补充：pending 期间禁止重复发码）
  const [sending, setSending] = useState(false);

  const handleGetCode = async () => {
    if (counting || sending) return;
    setError('');
    if (!isValidEmail(email)) {
      setError('请输入正确的邮箱地址');
      return;
    }
    setSending(true);
    try {
      // RESET_PASSWORD 场景后端校验邮箱已注册
      await authApi.sendEmailCode(email, 'RESET_PASSWORD');
      startCountdown();
    } catch (err) {
      setError(getErrorMessage(err) || '验证码发送失败');
    } finally {
      setSending(false);
    }
  };

  const handleReset = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!isValidEmail(email)) {
      setError('请输入正确的邮箱地址');
      return;
    }
    if (!code) {
      setError('请输入验证码');
      return;
    }
    if (newPassword.length < 6) {
      setError('新密码至少 6 位');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }

    setLoading(true);
    try {
      await authApi.resetPassword(email, code, newPassword);
      setSuccess(true);
    } catch (err) {
      setError(getErrorMessage(err) || '重置失败');
    } finally {
      setLoading(false);
    }
  };

  const inputCls =
    'w-full px-4 py-3 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all';

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-sky-100 via-sky-50 to-white px-4 py-12 relative overflow-hidden">
      {/* 背景装饰 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-20 left-20 w-72 h-72 bg-sky-200/40 rounded-full blur-3xl animate-pulse" />
        <div className="absolute bottom-20 right-20 w-96 h-96 bg-sky-300/30 rounded-full blur-3xl animate-pulse animation-delay-2000" />
      </div>

      <motion.div
        className="relative w-full max-w-md"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <div className="bg-white/80 backdrop-blur-sm rounded-3xl shadow-xl p-8 border border-white/50">
          {/* 返回登录 */}
          <button
            type="button"
            onClick={() => navigate('/login', { state: { from } })}
            className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-600 transition-colors mb-6"
          >
            <ArrowLeft className="w-4 h-4" />
            返回登录
          </button>

          <div className="text-center mb-8">
            <div className="w-14 h-14 mx-auto mb-4 rounded-2xl bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center shadow-lg shadow-primary-500/30">
              <KeyRound className="w-7 h-7 text-white" />
            </div>
            <h1 className="text-3xl font-bold text-slate-900 mb-2">找回密码</h1>
            <p className="text-slate-500">通过注册邮箱的验证码重置密码</p>
          </div>

          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-600 text-sm flex items-center gap-2"
              >
                <AlertCircle className="w-5 h-5 flex-shrink-0" />
                {error}
              </motion.div>
            )}
          </AnimatePresence>

          {success ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="text-center py-6"
            >
              <div className="w-14 h-14 mx-auto mb-4 rounded-full bg-green-100 flex items-center justify-center">
                <KeyRound className="w-7 h-7 text-green-600" />
              </div>
              <p className="text-slate-700 font-semibold mb-2">密码重置成功</p>
              <p className="text-slate-500 text-sm mb-6">请使用新密码登录</p>
              <Link
                to="/login"
                state={{ from }}
                className="inline-block px-8 py-3 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 transition-all hover:shadow-xl"
              >
                去登录
              </Link>
            </motion.div>
          ) : (
            <form onSubmit={handleReset} className="space-y-5">
              <div>
                <label htmlFor="fp-email" className="block text-sm font-semibold text-slate-700 mb-2">
                  注册邮箱
                </label>
                <input
                  id="fp-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="请输入注册时的邮箱地址"
                  className={inputCls}
                  disabled={loading}
                  autoComplete="email"
                />
              </div>

              <div>
                <label htmlFor="fp-code" className="block text-sm font-semibold text-slate-700 mb-2">
                  验证码
                </label>
                <div className="flex gap-3">
                  <input
                    id="fp-code"
                    type="text"
                    value={code}
                    onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    placeholder="邮箱验证码"
                    className={`${inputCls} flex-1`}
                    disabled={loading}
                    inputMode="numeric"
                  />
                  <button
                    type="button"
                    onClick={handleGetCode}
                    disabled={counting || sending || !email}
                    className="px-4 py-3 bg-primary-50 text-primary-600 rounded-xl text-sm font-semibold whitespace-nowrap transition-all hover:bg-primary-100 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {codeText}
                  </button>
                </div>
              </div>

              <div>
                <label htmlFor="fp-newpwd" className="block text-sm font-semibold text-slate-700 mb-2">
                  新密码
                </label>
                <input
                  id="fp-newpwd"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="至少 6 位"
                  className={inputCls}
                  disabled={loading}
                  autoComplete="new-password"
                />
              </div>

              <div>
                <label htmlFor="fp-confirm" className="block text-sm font-semibold text-slate-700 mb-2">
                  确认新密码
                </label>
                <input
                  id="fp-confirm"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="再次输入新密码"
                  className={inputCls}
                  disabled={loading}
                  autoComplete="new-password"
                />
              </div>

              <motion.button
                type="submit"
                disabled={loading}
                className="w-full py-3.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:shadow-xl hover:shadow-primary-500/40 transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                whileHover={{ scale: loading ? 1 : 1.02 }}
                whileTap={{ scale: loading ? 1 : 0.98 }}
              >
                {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <KeyRound className="w-5 h-5" />}
                {loading ? '提交中...' : '重置密码'}
              </motion.button>
            </form>
          )}
        </div>
      </motion.div>
    </div>
  );
}
