import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useUser } from '../../store/user';
import { getErrorMessage } from '../../api/request';
import { Eye, EyeOff, Loader2 } from 'lucide-react';

export default function RegisterPage() {
  const navigate = useNavigate();
  const { register } = useUser();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [nickname, setNickname] = useState('');

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
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setError('请输入有效的邮箱地址');
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

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-sky-100 via-sky-50 to-white py-12 px-4 sm:px-6 lg:px-8 relative overflow-hidden">
      {/* 背景装饰 - 玻璃态圆形 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-20 left-20 w-72 h-72 bg-sky-200/40 rounded-full blur-3xl animate-pulse" />
        <div className="absolute bottom-20 right-20 w-96 h-96 bg-sky-300/30 rounded-full blur-3xl animate-pulse animation-delay-2000" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-sky-100/20 rounded-full blur-3xl" />
      </div>

      {/* 装饰性星星 - 动画效果 */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-32 right-32 w-5 h-5 bg-sky-400/60 animate-float-1" style={{clipPath: 'polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%)'}} />
        <div className="absolute top-48 right-48 w-4 h-4 bg-sky-300/80 animate-float-2" style={{clipPath: 'polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%)'}} />
        <div className="absolute bottom-40 left-32 w-6 h-6 bg-sky-400/50 animate-float-3" style={{clipPath: 'polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%)'}} />
        <div className="absolute bottom-60 left-48 w-4 h-4 bg-sky-300/70 animate-float-4" style={{clipPath: 'polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%)'}} />
        <div className="absolute top-40 left-1/4 w-3 h-3 bg-sky-400/40 animate-float-5" style={{clipPath: 'polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%)'}} />
        <div className="absolute bottom-32 right-1/3 w-5 h-5 bg-sky-400/50 animate-float-6" style={{clipPath: 'polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%)'}} />
      </div>
        <motion.div
        className="relative w-full max-w-md"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        {/* 标题 */}
        <div className="text-center">
          <h2 className="text-3xl font-bold text-slate-900">创建账户</h2>
          <p className="mt-2 text-sm text-slate-600">
            已有账户？{' '}
            <Link to="/login" className="font-medium text-primary-600 hover:text-primary-500">
              立即登录
            </Link>
          </p>
        </div>

        {/* 表单 */}
        <form className="mt-8 space-y-6 bg-white/80 backdrop-blur-sm py-8 px-6 shadow-lg rounded-xl border border-white/50" onSubmit={handleSubmit}>
          {/* 错误提示 */}
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-lg text-sm">
              {error}
            </div>
          )}

          <div className="space-y-5">
            {/* 用户名 */}
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-slate-700">
                用户名 <span className="text-red-500">*</span>
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名（3-50个字符）"
                className="mt-1 block w-full px-4 py-3 border border-slate-300 rounded-lg focus:ring-1 focus:ring-primary-500 focus:border-primary-500 outline-none transition-colors"
                disabled={loading}
              />
            </div>

            {/* 邮箱 */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-slate-700">
                邮箱 <span className="text-red-500">*</span>
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="请输入邮箱地址"
                className="mt-1 block w-full px-4 py-3 border border-slate-300 rounded-lg focus:ring-1 focus:ring-primary-500 focus:border-primary-500 outline-none transition-colors"
                disabled={loading}
              />
            </div>

            {/* 密码 */}
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                密码 <span className="text-red-500">*</span>
              </label>
              <div className="mt-1 relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入密码（6-100个字符）"
                  className="block w-full px-4 py-3 pr-12 border border-slate-300 rounded-lg focus:ring-1 focus:ring-primary-500 focus:border-primary-500 outline-none transition-colors"
                  disabled={loading}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                  disabled={loading}
                >
                  {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
            </div>

            {/* 确认密码 */}
            <div>
              <label htmlFor="confirmPassword" className="block text-sm font-medium text-slate-700">
                确认密码 <span className="text-red-500">*</span>
              </label>
              <div className="mt-1 relative">
                <input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="请再次输入密码"
                  className="block w-full px-4 py-3 pr-12 border border-slate-300 rounded-lg focus:ring-1 focus:ring-primary-500 focus:border-primary-500 outline-none transition-colors"
                  disabled={loading}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                  disabled={loading}
                >
                  {showConfirmPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
            </div>

            {/* 昵称 */}
            <div>
              <label htmlFor="nickname" className="block text-sm font-medium text-slate-700">
                昵称 <span className="text-slate-400">（可选）</span>
              </label>
              <input
                id="nickname"
                type="text"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="请输入昵称（可选）"
                className="mt-1 block w-full px-4 py-3 border border-slate-300 rounded-lg focus:ring-1 focus:ring-primary-500 focus:border-primary-500 outline-none transition-colors"
                disabled={loading}
              />
            </div>
          </div>

          {/* 注册按钮 */}
          <button
            type="submit"
            disabled={loading}
            className="w-full flex justify-center items-center py-3 px-4 border border-transparent rounded-lg shadow-sm text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors font-medium"
          >
            {loading ? (
              <>
                <Loader2 className="animate-spin -ml-1 mr-2 h-5 w-5" />
                注册中...
              </>
            ) : (
              '注册'
            )}
          </button>
        </form>
      </motion.div>
    </div>
  );
}
