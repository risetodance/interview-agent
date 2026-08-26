import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff, AlertCircle, Loader2 } from 'lucide-react';
import { useUser } from '../../store/user';
import { getErrorMessage } from '../../api/request';
import { authApi } from '../../api/auth';
import { useCodeCountdown } from '../../hooks/useCodeCountdown';
import { isValidEmail } from '../../utils/validate';
import BrandMark from '../../components/BrandMark';
import AuthAside from '../../components/auth/AuthAside';

/**
 * 注册页面
 * 用户名 + 邮箱验证码 + 密码注册
 * 视觉：左墨水蓝叙事面板 + 右表单区，细边框控件语言（与登录页同构）
 */
export default function RegisterPage() {
  const navigate = useNavigate();
  const { register } = useUser();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [nickname, setNickname] = useState('');

  // 邮箱验证码 60s 倒计时 + 发码请求 in-flight 防抖
  const { codeText, counting, start: startCountdown } = useCodeCountdown();
  const [sending, setSending] = useState(false);

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 表单验证
  const validateForm = (): boolean => {
    // 用户名验证：3-50字符
    if (!username.trim()) {
      setError('请输入用户名');
      return false;
    }
    if (username.length < 3 || username.length > 50) {
      setError('用户名长度必须为 3-50 个字符');
      return false;
    }

    // 邮箱验证
    if (!email.trim()) {
      setError('请输入邮箱');
      return false;
    }
    if (!isValidEmail(email)) {
      setError('请输入有效的邮箱地址');
      return false;
    }

    // 邮箱验证码验证
    if (!code) {
      setError('请输入邮箱验证码');
      return false;
    }

    // 密码验证：6-100字符
    if (!password) {
      setError('请输入密码');
      return false;
    }
    if (password.length < 6 || password.length > 100) {
      setError('密码长度必须为 6-100 个字符');
      return false;
    }

    // 确认密码验证
    if (!confirmPassword) {
      setError('请输入确认密码');
      return false;
    }
    if (password !== confirmPassword) {
      setError('两次输入的密码不一致');
      return false;
    }

    return true;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      await register({
        username: username.trim(),
        email: email.trim(),
        password,
        code: code.trim() || undefined,
        nickname: nickname.trim() || undefined,
      });

      // 注册成功，跳转到首页
      navigate('/');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  /**
   * 获取邮箱验证码（注册场景）
   */
  const handleGetCode = async () => {
    if (counting || sending) return;
    setError('');
    if (!isValidEmail(email)) {
      setError('请输入正确的邮箱地址');
      return;
    }
    setSending(true);
    try {
      await authApi.sendEmailCode(email.trim(), 'REGISTER');
      startCountdown();
    } catch (err) {
      setError(getErrorMessage(err) || '验证码发送失败');
    } finally {
      setSending(false);
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
        tagline="AI Interview Workspace"
        title={<>创建账户，<br />开启面试训练</>}
        steps={[
          { title: '注册账户', desc: '邮箱验证，一分钟完成注册' },
          { title: '上传简历', desc: '上传即得结构化的简历分析' },
          { title: '模拟面试', desc: '多位 AI 面试官轮番提问' },
        ]}
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
            {/* 标题 */}
            <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">创建账户</h1>
            <p className="mt-1.5 text-sm text-zinc-500">注册后即可上传简历并开始模拟面试</p>

            {/* 错误提示 */}
            {error && (
              <div className="mt-5 flex items-center gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700 fade-in">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span className="break-all">{error}</span>
              </div>
            )}

            {/* 注册表单 */}
            <form onSubmit={handleSubmit} className="mt-6 space-y-5">
              {/* 用户名 */}
              <div>
                <label htmlFor="username" className={labelCls}>
                  用户名 <span className="text-red-500">*</span>
                </label>
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="请输入用户名（3-50个字符）"
                  className={inputCls}
                  disabled={loading}
                  autoComplete="username"
                />
              </div>

              {/* 邮箱 */}
              <div>
                <label htmlFor="email" className={labelCls}>
                  邮箱 <span className="text-red-500">*</span>
                </label>
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="请输入邮箱地址"
                  className={inputCls}
                  disabled={loading}
                  autoComplete="email"
                />
              </div>

              {/* 邮箱验证码 */}
              <div>
                <label htmlFor="emailCode" className={labelCls}>
                  验证码 <span className="text-red-500">*</span>
                </label>
                <div className="flex gap-2">
                  <input
                    id="emailCode"
                    type="text"
                    value={code}
                    onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    placeholder="请输入邮箱验证码"
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

              {/* 密码 */}
              <div>
                <label htmlFor="password" className={labelCls}>
                  密码 <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="请输入密码（6-100个字符）"
                    className={`${inputCls} pr-10`}
                    disabled={loading}
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-zinc-400 hover:text-zinc-700 transition-colors"
                    tabIndex={-1}
                    disabled={loading}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 确认密码 */}
              <div>
                <label htmlFor="confirmPassword" className={labelCls}>
                  确认密码 <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    id="confirmPassword"
                    type={showConfirmPassword ? 'text' : 'password'}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="请再次输入密码"
                    className={`${inputCls} pr-10`}
                    disabled={loading}
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-zinc-400 hover:text-zinc-700 transition-colors"
                    tabIndex={-1}
                    disabled={loading}
                  >
                    {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 昵称 */}
              <div>
                <label htmlFor="nickname" className={labelCls}>
                  昵称 <span className="font-normal text-zinc-400">（可选）</span>
                </label>
                <input
                  id="nickname"
                  type="text"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  placeholder="请输入昵称（可选）"
                  className={inputCls}
                  disabled={loading}
                />
              </div>

              {/* 注册按钮 */}
              <button type="submit" disabled={loading} className={primaryBtnCls}>
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    注册中...
                  </>
                ) : (
                  '注册'
                )}
              </button>
            </form>

            {/* 登录链接 */}
            <p className="mt-8 text-sm text-zinc-500">
              已有账户？{' '}
              <Link
                to="/login"
                className="text-primary-700 font-medium hover:text-primary-800 hover:underline underline-offset-2 transition-colors"
              >
                立即登录
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
