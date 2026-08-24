import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { Eye, EyeOff, AlertCircle, Loader2, Mail, ArrowLeft } from 'lucide-react';
import { useUser } from '../../store/user';
import { getErrorMessage } from '../../api/request';
import { authApi } from '../../api/auth';
import { useCodeCountdown } from '../../hooks/useCodeCountdown';
import { isValidEmail } from '../../utils/validate';
import BrandMark from '../../components/BrandMark';

type LoginMode = 'account' | 'email';

/**
 * 登录页面
 * 双方式：账号密码 / 邮箱验证码（两步式：未注册邮箱验证后进入第二步设置账号信息）
 * 视觉：左墨水蓝叙事面板 + 右表单区，细边框控件语言
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

  const labelCls = 'block text-xs font-medium text-zinc-600 mb-1.5';
  const inputCls =
    'w-full h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors disabled:bg-zinc-100 disabled:text-zinc-400';
  const primaryBtnCls =
    'w-full h-9 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2';

  return (
    <div className="min-h-screen flex bg-white">
      {/* 左侧叙事面板（大屏显示） */}
      <aside className="hidden lg:flex w-[420px] xl:w-[480px] shrink-0 flex-col justify-between bg-primary-950 bg-grid p-12 relative">
        {/* 品牌区 */}
        <div className="flex items-center gap-3">
          <BrandMark className="w-7 h-7" />
          <div>
            <span className="block text-sm font-semibold text-white tracking-tight">智能面试助手</span>
            <span className="block text-xs text-white/50">AI Assistant</span>
          </div>
        </div>

        {/* 主文案与特性 */}
        <div>
          <p className="font-mono text-xs tracking-[0.2em] text-primary-300/70 uppercase mb-5">
            AI Interview Workspace
          </p>
          <h2 className="text-3xl xl:text-4xl font-semibold text-white leading-snug tracking-tight">
            和 AI 面试官，
            <br />
            练一场真面试
          </h2>

          <div className="mt-12 divide-y divide-white/10 border-t border-white/10">
            <div className="py-4 flex items-start gap-4">
              <span className="font-mono text-xs text-primary-300/70 pt-1">01</span>
              <div>
                <p className="text-sm font-medium text-white">简历解析</p>
                <p className="text-sm text-white/50 mt-0.5">上传即得结构化的简历分析</p>
              </div>
            </div>
            <div className="py-4 flex items-start gap-4">
              <span className="font-mono text-xs text-primary-300/70 pt-1">02</span>
              <div>
                <p className="text-sm font-medium text-white">多视角模拟面试</p>
                <p className="text-sm text-white/50 mt-0.5">多位 AI 面试官轮番提问</p>
              </div>
            </div>
            <div className="py-4 flex items-start gap-4">
              <span className="font-mono text-xs text-primary-300/70 pt-1">03</span>
              <div>
                <p className="text-sm font-medium text-white">面试报告</p>
                <p className="text-sm text-white/50 mt-0.5">多维度评分与改进建议</p>
              </div>
            </div>
          </div>
        </div>

        {/* 底部信息 */}
        <p className="font-mono text-xs text-white/35">AI Assistant · Interview Workspace</p>
      </aside>

      {/* 右侧表单区 */}
      <div className="flex-1 flex flex-col">
        {/* 移动端品牌行 */}
        <div className="lg:hidden flex items-center gap-2.5 px-6 pt-6">
          <BrandMark className="w-6 h-6" />
          <span className="text-sm font-semibold text-zinc-900">智能面试助手</span>
        </div>

        <div className="flex-1 flex items-center justify-center px-6 py-12">
          <div className="w-full max-w-sm fade-in">
            {/* 标题 */}
            <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">登录</h1>
            <p className="mt-1.5 text-sm text-zinc-500">登录以继续使用模拟面试</p>

            {/* 错误提示 */}
            {error && (
              <div className="mt-5 flex items-center gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700 fade-in">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span className="break-all">{error}</span>
              </div>
            )}

            {/* 登录方式 tabs：底线式 */}
            <div className="mt-6 flex border-b border-zinc-200">
              <button
                type="button"
                disabled={loading}
                onClick={() => { setMode('account'); setRegisterStep(false); setEmailLocked(false); setEmailCode(''); setError(''); }}
                className={`pb-2.5 pr-6 text-sm border-b-2 -mb-px transition-colors ${
                  mode === 'account'
                    ? 'border-primary-600 text-zinc-900 font-medium'
                    : 'border-transparent text-zinc-500 hover:text-zinc-800'
                }`}
              >
                账号密码
              </button>
              <button
                type="button"
                disabled={loading}
                onClick={() => { setMode('email'); setError(''); }}
                className={`pb-2.5 pr-6 text-sm border-b-2 -mb-px transition-colors ${
                  mode === 'email' && !registerStep
                    ? 'border-primary-600 text-zinc-900 font-medium'
                    : 'border-transparent text-zinc-500 hover:text-zinc-800'
                }`}
              >
                邮箱验证码
              </button>
            </div>

            {/* 账号密码登录表单 */}
            {mode === 'account' && (
              <form key="account" onSubmit={handleSubmit} className="mt-6 space-y-5 fade-in">
                <div>
                  <label htmlFor="username" className={labelCls}>
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
                  <label htmlFor="password" className={labelCls}>
                    密码
                  </label>
                  <div className="relative">
                    <input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="请输入密码"
                      className={`${inputCls} pr-10`}
                      disabled={loading}
                      autoComplete="current-password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-zinc-400 hover:text-zinc-700 transition-colors"
                      tabIndex={-1}
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="flex justify-end">
                  <Link
                    to="/forgot-password"
                    state={{ from }}
                    className="text-xs text-primary-700 hover:text-primary-800 hover:underline underline-offset-2 transition-colors"
                  >
                    忘记密码？
                  </Link>
                </div>

                <button type="submit" disabled={loading} className={primaryBtnCls}>
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      登录中…
                    </>
                  ) : (
                    '登录'
                  )}
                </button>
              </form>
            )}

            {/* 邮箱验证码登录表单（第一步） */}
            {mode === 'email' && !registerStep && (
              <form key="email-step1" onSubmit={handleEmailLogin} className="mt-6 space-y-5 fade-in">
                <div>
                  <label htmlFor="email" className={labelCls}>
                    邮箱
                  </label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="name@example.com"
                    className={inputCls}
                    disabled={loading || emailLocked}
                    autoComplete="email"
                  />
                </div>

                <div>
                  <label htmlFor="emailCode" className={labelCls}>
                    验证码
                  </label>
                  <div className="flex gap-2">
                    <input
                      id="emailCode"
                      type="text"
                      value={emailCode}
                      onChange={(e) => setEmailCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      placeholder="6 位数字验证码"
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

                <button type="submit" disabled={loading} className={primaryBtnCls}>
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      登录中…
                    </>
                  ) : (
                    '登录 / 注册'
                  )}
                </button>

                <p className="text-xs text-zinc-400">
                  未注册的邮箱验证通过后将引导设置账号信息
                </p>
              </form>
            )}

            {/* 邮箱两步式第二步：设置账号信息 */}
            {mode === 'email' && registerStep && (
              <form key="email-step2" onSubmit={handleEmailRegister} className="mt-6 space-y-5 fade-in">
                <div className="flex items-center gap-2 border border-zinc-200 bg-zinc-50 rounded-md px-3 py-2.5 text-sm text-zinc-700">
                  <Mail className="w-4 h-4 shrink-0 text-zinc-400" />
                  <span className="font-mono text-xs truncate">{email}</span>
                  <span className="ml-auto text-xs text-primary-700 shrink-0">已验证</span>
                </div>

                <div>
                  <label htmlFor="regUsername" className={labelCls}>
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
                  <label htmlFor="regPassword" className={labelCls}>
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

                <button type="submit" disabled={loading} className={primaryBtnCls}>
                  {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                  {loading ? '提交中…' : '完成注册并登录'}
                </button>

                <button
                  type="button"
                  onClick={() => { setRegisterStep(false); setEmailCode(''); setEmailLocked(false); setError(''); }}
                  className="w-full flex items-center justify-center gap-1.5 text-xs text-zinc-400 hover:text-zinc-700 transition-colors"
                >
                  <ArrowLeft className="w-3.5 h-3.5" />
                  返回重新获取验证码
                </button>
              </form>
            )}

            {/* 注册链接 */}
            <p className="mt-8 text-sm text-zinc-500">
              还没有账号？{' '}
              <Link
                to="/register"
                state={{ from }}
                className="text-primary-700 font-medium hover:text-primary-800 hover:underline underline-offset-2 transition-colors"
              >
                立即注册
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
