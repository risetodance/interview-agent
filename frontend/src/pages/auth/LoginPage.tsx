import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { Eye, EyeOff, LogIn, AlertCircle, Loader2, Mail, ArrowLeft } from 'lucide-react';
import { useUser } from '../../store/user';
import { getErrorMessage } from '../../api/request';
import { authApi } from '../../api/auth';
import { useCodeCountdown } from '../../hooks/useCodeCountdown';
import { isValidEmail } from '../../utils/validate';

type LoginMode = 'account' | 'email';

/**
 * 登录页面
 * 双方式：账号密码 / 邮箱验证码（两步式：未注册邮箱验证后进入第二步设置账号信息）
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, loginWithToken } = useUser();

  const [mode, setMode] = useState<LoginMode>('account');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 账号密码表单
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  // 邮箱验证码表单（第一步）
  const [email, setEmail] = useState('');
  const [emailCode, setEmailCode] = useState('');
  const { codeText, counting, start: startCountdown } = useCodeCountdown();
  // 发码请求 in-flight 防抖（倒计时外补充：pending 期间禁止重复发码）
  const [sending, setSending] = useState(false);
  // 获取验证码成功后锁定邮箱输入，直到流程重置（切回账号 tab / 返回第一步）
  const [emailLocked, setEmailLocked] = useState(false);

  // 两步式第二步（邮箱已验证未注册 → 设置用户名密码）
  const [registerStep, setRegisterStep] = useState(false);
  const [regUsername, setRegUsername] = useState('');
  const [regPassword, setRegPassword] = useState('');

  // 从 location state 获取跳转路径，默认为首页
  const from = (location.state as { from?: string })?.from || '/upload';

  /**
   * 账号密码登录
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!username.trim()) {
      setError('请输入用户名');
      return;
    }
    if (!password) {
      setError('请输入密码');
      return;
    }

    setLoading(true);
    try {
      await login(username, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(getErrorMessage(err) || '登录失败，请检查用户名和密码');
    } finally {
      setLoading(false);
    }
  };

  /**
   * 获取邮箱验证码（登录场景）
   */
  const handleGetCode = async () => {
    if (counting || sending) return;
    setError('');
    if (!isValidEmail(email)) {
      setError('请输入正确的邮箱地址');
      return;
    }
    // 进入即锁定邮箱：防止发码请求 pending 期间修改邮箱，导致锁定值与收码邮箱错位
    setEmailLocked(true);
    setSending(true);
    try {
      await authApi.sendEmailCode(email, 'LOGIN');
      startCountdown();
    } catch (err) {
      // 发送失败解锁，允许修改邮箱后重试
      setEmailLocked(false);
      setError(getErrorMessage(err) || '验证码发送失败');
    } finally {
      setSending(false);
    }
  };

  /**
   * 邮箱验证码登录（第一步）→ 已注册直接登录；未注册进入第二步
   */
  const handleEmailLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!isValidEmail(email)) {
      setError('请输入正确的邮箱地址');
      return;
    }
    if (!emailCode) {
      setError('请输入验证码');
      return;
    }

    setLoading(true);
    try {
      const result = await authApi.emailLogin(email, emailCode);
      if (result.needsRegister) {
        // 验证码已由后端还原（5 分钟内有效），进入第二步
        setRegisterStep(true);
        setRegUsername(email.split('@')[0]);
        return;
      }
      if (!result.login?.token) {
        setError('登录异常，请重试');
        return;
      }
      await loginWithToken(result.login.token);
      navigate(from, { replace: true });
    } catch (err) {
      setError(getErrorMessage(err) || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  /**
   * 邮箱验证码注册（两步式第二步）：设置用户名密码，完成即登录
   */
  const handleEmailRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (regUsername.trim().length < 3 || regUsername.trim().length > 50) {
      setError('用户名长度应为 3-50 个字符');
      return;
    }
    if (regPassword.length < 6) {
      setError('密码至少 6 位');
      return;
    }

    setLoading(true);
    try {
      const result = await authApi.emailRegister({
        email,
        code: emailCode,
        username: regUsername.trim(),
        password: regPassword,
      });
      await loginWithToken(result.token);
      navigate(from, { replace: true });
    } catch (err) {
      const msg = getErrorMessage(err) || '注册失败';
      setError(msg);
      // 验证码失效/错误时回到第一步重新获取：
      // 优先按后端业务码判断（12002 验证码过期 / 12003 验证码错误 / 12004 验证码失败次数过多）
      const bizCode = (err as { code?: number })?.code;
      const isEmailCodeError =
        bizCode === 12002 || bizCode === 12003 || bizCode === 12004 ||
        // 拿不到业务码时回退文案匹配（防御后端非 Result 壳响应）
        (bizCode === undefined && msg.includes('验证码'));
      if (isEmailCodeError) {
        setRegisterStep(false);
        setEmailCode('');
      }
    } finally {
      setLoading(false);
    }
  };

  const inputCls =
    'w-full px-4 py-3 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all';

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-sky-100 via-sky-50 to-white px-4 py-12 relative overflow-hidden">
      {/* 背景装饰 - 玻璃态圆形 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-20 left-20 w-72 h-72 bg-sky-200/40 rounded-full blur-3xl animate-pulse" />
        <div className="absolute bottom-20 right-20 w-96 h-96 bg-sky-300/30 rounded-full blur-3xl animate-pulse animation-delay-2000" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-sky-100/20 rounded-full blur-3xl" />
      </div>

      {/* 装饰性圆点 - 动画效果 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-32 right-32 w-3 h-3 bg-sky-400/60 rounded-full animate-float-1" />
        <div className="absolute top-48 right-48 w-2 h-2 bg-sky-300/80 rounded-full animate-float-2" />
        <div className="absolute bottom-40 left-32 w-4 h-4 bg-sky-400/50 rounded-full animate-float-3" />
        <div className="absolute bottom-60 left-48 w-2 h-2 bg-sky-300/70 rounded-full animate-float-4" />
        <div className="absolute top-40 left-1/4 w-2 h-2 bg-sky-400/40 rounded-full animate-float-5" />
        <div className="absolute bottom-32 right-1/3 w-3 h-3 bg-sky-400/50 rounded-full animate-float-6" />
      </div>

      <motion.div
        className="relative w-full max-w-md"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        {/* 卡片 */}
        <div className="bg-white/80 backdrop-blur-sm rounded-3xl shadow-xl p-8 border border-white/50">
          {/* 标题 */}
          <motion.div
            className="text-center mb-8"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
          >
            <h1 className="text-3xl font-bold text-slate-900 mb-2">欢迎回来</h1>
            <p className="text-slate-500">登录您的账号开始 AI 模拟面试</p>
          </motion.div>

          {/* 错误提示 */}
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

          {/* 登录方式 tabs */}
          <div className="flex gap-2 mb-6 p-1 bg-slate-100 rounded-xl">
            <button
              type="button"
              disabled={loading}
              onClick={() => { setMode('account'); setRegisterStep(false); setEmailLocked(false); setEmailCode(''); setError(''); }}
              className={`flex-1 py-2.5 rounded-lg text-sm font-semibold transition-all ${
                mode === 'account' ? 'bg-white text-primary-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              账号密码登录
            </button>
            <button
              type="button"
              disabled={loading}
              onClick={() => { setMode('email'); setError(''); }}
              className={`flex-1 py-2.5 rounded-lg text-sm font-semibold transition-all ${
                mode === 'email' ? 'bg-white text-primary-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              邮箱验证码
            </button>
          </div>

          <AnimatePresence mode="wait">
            {/* 账号密码登录表单 */}
            {mode === 'account' && (
              <motion.form
                key="account"
                onSubmit={handleSubmit}
                className="space-y-5"
                initial={{ opacity: 0, x: -12 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 12 }}
                transition={{ duration: 0.2 }}
              >
                <div>
                  <label htmlFor="username" className="block text-sm font-semibold text-slate-700 mb-2">
                    用户名
                  </label>
                  <input
                    id="username"
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="请输入用户名"
                    className={inputCls}
                    disabled={loading}
                    autoComplete="username"
                  />
                </div>

                <div>
                  <label htmlFor="password" className="block text-sm font-semibold text-slate-700 mb-2">
                    密码
                  </label>
                  <div className="relative">
                    <input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="请输入密码"
                      className={`${inputCls} pr-12`}
                      disabled={loading}
                      autoComplete="current-password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 text-slate-400 hover:text-slate-600 transition-colors"
                      tabIndex={-1}
                    >
                      {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                    </button>
                  </div>
                </div>

                {/* 忘记密码 */}
                <div className="flex justify-end">
                  <Link
                    to="/forgot-password"
                    state={{ from }}
                    className="text-sm text-primary-600 font-medium hover:text-primary-700 transition-colors"
                  >
                    忘记密码？
                  </Link>
                </div>

                <motion.button
                  type="submit"
                  disabled={loading}
                  className="w-full py-3.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:shadow-xl hover:shadow-primary-500/40 transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                  whileHover={{ scale: loading ? 1 : 1.02 }}
                  whileTap={{ scale: loading ? 1 : 0.98 }}
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-5 h-5 animate-spin" />
                      登录中...
                    </>
                  ) : (
                    <>
                      <LogIn className="w-5 h-5" />
                      登录
                    </>
                  )}
                </motion.button>
              </motion.form>
            )}

            {/* 邮箱验证码登录表单 */}
            {mode === 'email' && !registerStep && (
              <motion.form
                key="email-step1"
                onSubmit={handleEmailLogin}
                className="space-y-5"
                initial={{ opacity: 0, x: 12 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -12 }}
                transition={{ duration: 0.2 }}
              >
                <div>
                  <label htmlFor="email" className="block text-sm font-semibold text-slate-700 mb-2">
                    邮箱
                  </label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="请输入邮箱地址"
                    className={inputCls}
                    disabled={loading || emailLocked}
                    autoComplete="email"
                  />
                </div>

                <div>
                  <label htmlFor="emailCode" className="block text-sm font-semibold text-slate-700 mb-2">
                    验证码
                  </label>
                  <div className="flex gap-3">
                    <input
                      id="emailCode"
                      type="text"
                      value={emailCode}
                      onChange={(e) => setEmailCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
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

                <motion.button
                  type="submit"
                  disabled={loading}
                  className="w-full py-3.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-semibold shadow-lg shadow-primary-500/30 hover:shadow-xl hover:shadow-primary-500/40 transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                  whileHover={{ scale: loading ? 1 : 1.02 }}
                  whileTap={{ scale: loading ? 1 : 0.98 }}
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-5 h-5 animate-spin" />
                      登录中...
                    </>
                  ) : (
                    <>
                      <Mail className="w-5 h-5" />
                      登录 / 注册
                    </>
                  )}
                </motion.button>

                <p className="text-center text-xs text-slate-400">
                  未注册的邮箱验证通过后将引导设置账号信息
                </p>
              </motion.form>
            )}

            {/* 邮箱两步式第二步：设置账号信息 */}
            {mode === 'email' && registerStep && (
              <motion.form
                key="email-step2"
                onSubmit={handleEmailRegister}
                className="space-y-5"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                transition={{ duration: 0.2 }}
              >
                <div className="p-3 bg-sky-50 border border-sky-100 rounded-xl text-sm text-sky-700 flex items-center gap-2">
                  <Mail className="w-4 h-4 flex-shrink-0" />
                  {email}（已验证）
                </div>

                <div>
                  <label htmlFor="regUsername" className="block text-sm font-semibold text-slate-700 mb-2">
                    用户名
                  </label>
                  <input
                    id="regUsername"
                    type="text"
                    value={regUsername}
                    onChange={(e) => setRegUsername(e.target.value)}
                    placeholder="3-50 个字符"
                    className={inputCls}
                    disabled={loading}
                    autoComplete="username"
                  />
                </div>

                <div>
                  <label htmlFor="regPassword" className="block text-sm font-semibold text-slate-700 mb-2">
                    设置密码
                  </label>
                  <input
                    id="regPassword"
                    type="password"
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    placeholder="至少 6 位"
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
                  {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <LogIn className="w-5 h-5" />}
                  {loading ? '提交中...' : '完成注册并登录'}
                </motion.button>

                <button
                  type="button"
                  onClick={() => { setRegisterStep(false); setEmailCode(''); setEmailLocked(false); setError(''); }}
                  className="w-full text-sm text-slate-400 hover:text-slate-600 transition-colors flex items-center justify-center gap-1"
                >
                  <ArrowLeft className="w-4 h-4" />
                  返回重新获取验证码
                </button>
              </motion.form>
            )}
          </AnimatePresence>

          {/* 注册链接 */}
          <motion.div
            className="mt-8 text-center"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.4 }}
          >
            <p className="text-slate-500">
              还没有账号？{' '}
              <Link
                to="/register"
                state={{ from }}
                className="text-primary-600 font-semibold hover:text-primary-700 transition-colors"
              >
                立即注册
              </Link>
            </p>
          </motion.div>
        </div>
      </motion.div>
    </div>
  );
}
