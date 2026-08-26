import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { KeyRound, AlertCircle, Loader2, ArrowLeft } from 'lucide-react';
import { getErrorMessage } from '../../api/request';
import { authApi } from '../../api/auth';
import { useCodeCountdown } from '../../hooks/useCodeCountdown';
import { isValidEmail } from '../../utils/validate';
import BrandMark from '../../components/BrandMark';
import AuthAside from '../../components/auth/AuthAside';

/**
 * 忘记密码页：邮箱 + 验证码 + 新密码
 * 视觉：左墨水蓝叙事面板 + 右表单区，细边框控件语言（与登录页同构）
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

  const labelCls = 'block text-xs font-medium text-zinc-600 mb-1.5';
  const inputCls =
    'w-full h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors disabled:bg-zinc-100 disabled:text-zinc-400';
  const primaryBtnCls =
    'w-full h-9 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2';

  return (
    <div className="min-h-screen flex bg-white">
      {/* 左侧叙事面板（大屏显示） */}
      <AuthAside
        tagline="Account Recovery"
        title={<>验证邮箱，<br />安全重置密码</>}
        steps={[
          { title: '邮箱验证', desc: '使用注册邮箱接收一次性验证码' },
          { title: '重置密码', desc: '新密码设置后立即生效' },
          { title: '重新登录', desc: '使用新密码登录，继续面试训练' },
        ]}
        footerLabel="AI Assistant · Account Recovery"
      />

      {/* 右侧表单区 */}
      <div className="flex-1 flex flex-col">
        {/* 移动端品牌行 */}
        <div className="lg:hidden flex items-center gap-2.5 px-6 pt-6">
          <BrandMark className="w-6 h-6" />
          <span className="text-sm font-semibold text-zinc-900">智能面试助手</span>
        </div>

        <div className="flex-1 flex items-center justify-center px-6 py-12">
          <div className="w-full max-w-sm fade-in">
            {/* 返回登录 */}
            <button
              type="button"
              onClick={() => navigate('/login', { state: { from } })}
              className="flex items-center gap-1.5 text-xs text-zinc-400 hover:text-zinc-700 transition-colors"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              返回登录
            </button>

            {/* 标题 */}
            <h1 className="mt-5 text-xl font-semibold text-zinc-900 tracking-tight">找回密码</h1>
            <p className="mt-1.5 text-sm text-zinc-500">通过注册邮箱的验证码重置密码</p>

            {/* 错误提示 */}
            {error && (
              <div className="mt-5 flex items-center gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700 fade-in">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span className="break-all">{error}</span>
              </div>
            )}

            {success ? (
              /* 重置成功 */
              <div className="mt-8 py-6 text-center fade-in">
                <div className="w-12 h-12 mx-auto mb-4 rounded-md bg-emerald-50 border border-emerald-200 flex items-center justify-center">
                  <KeyRound className="w-6 h-6 text-emerald-700" />
                </div>
                <p className="font-medium text-zinc-900 mb-1">密码重置成功</p>
                <p className="text-sm text-zinc-500 mb-6">请使用新密码登录</p>
                <Link
                  to="/login"
                  state={{ from }}
                  className="inline-flex items-center justify-center h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors"
                >
                  去登录
                </Link>
              </div>
            ) : (
              /* 重置表单 */
              <form onSubmit={handleReset} className="mt-6 space-y-5">
                <div>
                  <label htmlFor="fp-email" className={labelCls}>
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
                  <label htmlFor="fp-code" className={labelCls}>
                    验证码
                  </label>
                  <div className="flex gap-2">
                    <input
                      id="fp-code"
                      type="text"
                      value={code}
                      onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      placeholder="邮箱验证码"
                      className={`${inputCls} flex-1 font-mono tracking-widest`}
                      disabled={loading}
                      inputMode="numeric"
                    />
                    <button
                      type="button"
                      onClick={handleGetCode}
                      disabled={counting || sending || !email}
                      className="h-9 px-3.5 border border-zinc-300 rounded-md text-sm text-zinc-700 font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
                    >
                      {codeText}
                    </button>
                  </div>
                </div>

                <div>
                  <label htmlFor="fp-newpwd" className={labelCls}>
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
                  <label htmlFor="fp-confirm" className={labelCls}>
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

                <button type="submit" disabled={loading} className={primaryBtnCls}>
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      提交中...
                    </>
                  ) : (
                    '重置密码'
                  )}
                </button>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
