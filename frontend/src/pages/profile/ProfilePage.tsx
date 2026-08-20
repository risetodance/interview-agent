import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../../store/user';
import { pointsApi } from '../../api/points';
import { membershipApi, MembershipType } from '../../api/membership';
import { authApi } from '../../api/auth';
import { getErrorMessage } from '../../api/request';
import { useCodeCountdown } from '../../hooks/useCodeCountdown';
import { isValidEmail } from '../../utils/validate';
import { User, Mail, Award, Crown, Lock, LogOut, Save, Eye, EyeOff, KeyRound } from 'lucide-react';

export default function ProfilePage() {
  const navigate = useNavigate();
  const { user, logout, updateProfile, changePassword, fetchUserProfile } = useUser();

  // 编辑资料表单状态
  const [nickname, setNickname] = useState('');
  const [avatar, setAvatar] = useState('');
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileMessage, setProfileMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // 修改密码表单状态
  const [pwdTab, setPwdTab] = useState<'old' | 'email'>('old');
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showOldPassword, setShowOldPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [passwordMessage, setPasswordMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // 邮箱验证码改密（验证码发往当前账号绑定邮箱）
  const [pwdCode, setPwdCode] = useState('');
  const { codeText: pwdCodeText, counting: pwdCounting, start: startPwdCountdown } = useCodeCountdown();
  // 改密发码 in-flight 防抖
  const [pwdSending, setPwdSending] = useState(false);

  // 绑定/换绑邮箱
  const [newEmail, setNewEmail] = useState('');
  const [bindCode, setBindCode] = useState('');
  const { codeText: bindCodeText, counting: bindCounting, start: startBindCountdown } = useCodeCountdown();
  // 绑定邮箱发码 in-flight 防抖
  const [bindSending, setBindSending] = useState(false);
  const [bindLoading, setBindLoading] = useState(false);
  const [bindMessage, setBindMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // 获取改密验证码（发往绑定邮箱）
  const handleGetPwdCode = async () => {
    if (pwdCounting || pwdSending) return;
    if (!user?.email) {
      setPasswordMessage({ type: 'error', text: '当前账号未绑定邮箱，请先在下方绑定邮箱' });
      return;
    }
    setPwdSending(true);
    try {
      await authApi.sendEmailCode(user.email, 'CHANGE_PASSWORD');
      startPwdCountdown();
      setPasswordMessage(null);
    } catch (err) {
      setPasswordMessage({ type: 'error', text: getErrorMessage(err) || '验证码发送失败' });
    } finally {
      setPwdSending(false);
    }
  };

  // 获取绑定邮箱验证码（发往新邮箱）
  const handleGetBindCode = async () => {
    if (bindCounting || bindSending) return;
    if (!isValidEmail(newEmail)) {
      setBindMessage({ type: 'error', text: '请输入正确的新邮箱地址' });
      return;
    }
    setBindSending(true);
    try {
      await authApi.sendEmailCode(newEmail, 'BIND_EMAIL');
      startBindCountdown();
      setBindMessage(null);
    } catch (err) {
      setBindMessage({ type: 'error', text: getErrorMessage(err) || '验证码发送失败' });
    } finally {
      setBindSending(false);
    }
  };

  // 登出确认
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  // 积分状态
  const [points, setPoints] = useState<number>(0);
  const [pointsLoading, setPointsLoading] = useState(false);

  // 会员等级状态
  const [membership, setMembership] = useState<MembershipType>('FREE');
  const [membershipLoading, setMembershipLoading] = useState(false);

  // 初始化表单数据
  useEffect(() => {
    if (user) {
      setNickname(user.nickname || '');
      setAvatar(user.avatar || '');
    }
  }, [user]);

  // 获取积分
  useEffect(() => {
    const fetchPoints = async () => {
      setPointsLoading(true);
      try {
        const data = await pointsApi.getPoints();
        setPoints(data);
      } catch (err) {
        console.error('获取积分失败', err);
      } finally {
        setPointsLoading(false);
      }
    };
    fetchPoints();
  }, []);

  // 获取会员等级
  useEffect(() => {
    const fetchMembership = async () => {
      setMembershipLoading(true);
      try {
        const data = await membershipApi.getMembership();
        setMembership(data.membership);
      } catch (err) {
        console.error('获取会员等级失败', err);
      } finally {
        setMembershipLoading(false);
      }
    };
    fetchMembership();
  }, []);

  // 处理资料更新
  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileMessage(null);
    setProfileLoading(true);

    try {
      await updateProfile({ nickname, avatar });
      setProfileMessage({ type: 'success', text: '资料更新成功' });
    } catch (error) {
      setProfileMessage({ type: 'error', text: error instanceof Error ? error.message : '资料更新失败' });
    } finally {
      setProfileLoading(false);
    }
  };

  // 处理密码修改（双方式：旧密码 / 邮箱验证码）
  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordMessage(null);

    // 验证新密码
    if (newPassword.length < 6) {
      setPasswordMessage({ type: 'error', text: '新密码长度至少为 6 位' });
      return;
    }

    // 验证确认密码
    if (newPassword !== confirmPassword) {
      setPasswordMessage({ type: 'error', text: '两次输入的新密码不一致' });
      return;
    }

    // 按方式校验凭证
    if (pwdTab === 'old' && !oldPassword) {
      setPasswordMessage({ type: 'error', text: '请输入旧密码' });
      return;
    }
    if (pwdTab === 'email' && !pwdCode) {
      setPasswordMessage({ type: 'error', text: '请输入邮箱验证码' });
      return;
    }

    setPasswordLoading(true);

    try {
      if (pwdTab === 'old') {
        await changePassword(oldPassword, newPassword);
      } else {
        await authApi.changePasswordByEmail(pwdCode, newPassword);
      }
      setPasswordMessage({ type: 'success', text: '密码修改成功' });
      // 清空密码表单
      setOldPassword('');
      setPwdCode('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (error) {
      setPasswordMessage({ type: 'error', text: getErrorMessage(error) || '密码修改失败' });
    } finally {
      setPasswordLoading(false);
    }
  };

  // 处理绑定/换绑邮箱
  const handleBindEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBindMessage(null);

    if (!isValidEmail(newEmail)) {
      setBindMessage({ type: 'error', text: '请输入正确的新邮箱地址' });
      return;
    }
    if (!bindCode) {
      setBindMessage({ type: 'error', text: '请输入验证码' });
      return;
    }

    setBindLoading(true);
    let bindSucceeded = false;
    try {
      await authApi.bindEmail(newEmail, bindCode);
      bindSucceeded = true;
      setBindMessage({ type: 'success', text: '邮箱绑定成功' });
      setNewEmail('');
      setBindCode('');
    } catch (error) {
      setBindMessage({ type: 'error', text: getErrorMessage(error) || '绑定失败' });
    } finally {
      setBindLoading(false);
    }
    // 刷新用户信息以显示新邮箱；刷新失败不覆盖成功提示、不误报失败，仅记录日志
    if (bindSucceeded) {
      fetchUserProfile().catch((e) => console.error('刷新用户信息失败', e));
    }
  };

  // 处理登出
  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // 获取会员状态显示
  const getMembershipDisplay = (membership: string) => {
    const membershipMap: Record<string, { label: string; className: string }> = {
      FREE: { label: '免费用户', className: 'bg-slate-100 text-slate-600' },
      VIP: { label: 'VIP 会员', className: 'bg-amber-100 text-amber-600' },
      PREMIUM: { label: '高级会员', className: 'bg-purple-100 text-purple-600' },
    };
    return membershipMap[membership] || { label: membership, className: 'bg-slate-100 text-slate-600' };
  };

  if (!user) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <User className="w-8 h-8 text-slate-400" />
          </div>
          <p className="text-slate-500">请先登录</p>
        </div>
      </div>
    );
  }

  const membershipInfo = getMembershipDisplay(membership);

  return (
    <motion.div
      className="max-w-4xl mx-auto"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      {/* 页面头部 */}
      <div className="mb-8">
        <motion.h1
          className="text-3xl font-bold text-slate-900 mb-2"
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          个人中心
        </motion.h1>
        <motion.p
          className="text-slate-500"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.1 }}
        >
          欢迎回来，{user.nickname || user.username}
        </motion.p>
      </div>

      {/* 用户信息展示卡片 */}
      <motion.div
        className="bg-white rounded-2xl shadow-sm p-6 mb-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="flex items-center gap-6">
          {/* 头像 */}
          <div className="relative">
            {user.avatar ? (
              <img
                src={user.avatar}
                alt={user.nickname || user.username}
                className="w-20 h-20 rounded-full object-cover border-4 border-primary-50"
              />
            ) : (
              <div className="w-20 h-20 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white text-2xl font-bold border-4 border-primary-50">
                {(user.nickname || user.username).charAt(0).toUpperCase()}
              </div>
            )}
            <div className={`absolute -bottom-1 -right-1 px-2 py-0.5 rounded-full text-xs font-medium ${membershipInfo.className}`}>
              {membership === 'FREE' ? <Crown className="w-3 h-3 inline mr-1" /> : null}
              {membershipInfo.label}
            </div>
          </div>

          {/* 用户信息 */}
          <div className="flex-1 grid grid-cols-2 gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-primary-50 rounded-xl flex items-center justify-center">
                <User className="w-5 h-5 text-primary-500" />
              </div>
              <div>
                <p className="text-xs text-slate-400">用户名</p>
                <p className="font-medium text-slate-800">{user.username}</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-primary-50 rounded-xl flex items-center justify-center">
                <Mail className="w-5 h-5 text-primary-500" />
              </div>
              <div>
                <p className="text-xs text-slate-400">邮箱</p>
                <p className="font-medium text-slate-800">{user.email}</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-amber-50 rounded-xl flex items-center justify-center">
                <Award className="w-5 h-5 text-amber-500" />
              </div>
              <div>
                <p className="text-xs text-slate-400">积分</p>
                <p className="font-medium text-slate-800">
                  {pointsLoading ? '加载中...' : points.toLocaleString()}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-purple-50 rounded-xl flex items-center justify-center">
                <Crown className="w-5 h-5 text-purple-500" />
              </div>
              <div>
                <p className="text-xs text-slate-400">会员等级</p>
                <p className="font-medium text-slate-800">{membershipLoading ? '加载中...' : membershipInfo.label}</p>
              </div>
            </div>
          </div>
        </div>
      </motion.div>

      {/* 两个卡片：个人资料和修改密码 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 个人资料卡片 */}
        <motion.div
          className="bg-white rounded-2xl shadow-sm p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 bg-primary-50 rounded-xl flex items-center justify-center">
              <User className="w-5 h-5 text-primary-500" />
            </div>
            <h2 className="text-lg font-semibold text-slate-800">编辑资料</h2>
          </div>

          <form onSubmit={handleProfileSubmit}>
            <div className="space-y-4">
              {/* 昵称 */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">昵称</label>
                <input
                  type="text"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500 transition-all"
                  placeholder="请输入昵称"
                />
              </div>

              {/* 头像 URL */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">头像 URL</label>
                <input
                  type="url"
                  value={avatar}
                  onChange={(e) => setAvatar(e.target.value)}
                  className="w-full px-4 py-2.5 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500 transition-all"
                  placeholder="https://example.com/avatar.jpg"
                />
              </div>

              {/* 消息提示 */}
              {profileMessage && (
                <div
                  className={`px-4 py-2 rounded-lg text-sm ${
                    profileMessage.type === 'success'
                      ? 'bg-emerald-50 text-emerald-600'
                      : 'bg-red-50 text-red-600'
                  }`}
                >
                  {profileMessage.text}
                </div>
              )}

              {/* 提交按钮 */}
              <button
                type="submit"
                disabled={profileLoading}
                className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-primary-500 hover:bg-primary-600 text-white font-medium rounded-xl transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {profileLoading ? (
                  <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                ) : (
                  <>
                    <Save className="w-4 h-4" />
                    保存修改
                  </>
                )}
              </button>
            </div>
          </form>
        </motion.div>

        {/* 修改密码卡片 */}
        <motion.div
          className="bg-white rounded-2xl shadow-sm p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
        >
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 bg-primary-50 rounded-xl flex items-center justify-center">
              <Lock className="w-5 h-5 text-primary-500" />
            </div>
            <h2 className="text-lg font-semibold text-slate-800">修改密码</h2>
          </div>

          {/* 验证方式切换 */}
          <div className="flex gap-2 mb-6 p-1 bg-slate-100 rounded-xl">
            <button
              type="button"
              onClick={() => { setPwdTab('old'); setPasswordMessage(null); }}
              className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-all ${
                pwdTab === 'old' ? 'bg-white text-primary-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              原密码验证
            </button>
            <button
              type="button"
              onClick={() => { setPwdTab('email'); setPasswordMessage(null); }}
              className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-all ${
                pwdTab === 'email' ? 'bg-white text-primary-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              邮箱验证码
            </button>
          </div>

          <form onSubmit={handlePasswordSubmit}>
            <div className="space-y-4">
              {/* 凭证：旧密码 或 邮箱验证码 */}
              {pwdTab === 'old' ? (
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">旧密码</label>
                  <div className="relative">
                    <input
                      type={showOldPassword ? 'text' : 'password'}
                      value={oldPassword}
                      onChange={(e) => setOldPassword(e.target.value)}
                      className="w-full px-4 py-2.5 pr-10 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500 transition-all"
                      placeholder="请输入旧密码"
                      required
                    />
                    <button
                      type="button"
                      onClick={() => setShowOldPassword(!showOldPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                    >
                      {showOldPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1.5">
                    验证码（发送至 {user.email || '未绑定邮箱'}）
                  </label>
                  <div className="flex gap-3">
                    <input
                      type="text"
                      value={pwdCode}
                      onChange={(e) => setPwdCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500 transition-all"
                      placeholder="邮箱验证码"
                      inputMode="numeric"
                    />
                    <button
                      type="button"
                      onClick={handleGetPwdCode}
                      disabled={pwdCounting || pwdSending || !user.email}
                      className="px-4 py-2.5 bg-primary-50 text-primary-600 rounded-xl text-sm font-semibold whitespace-nowrap transition-all hover:bg-primary-100 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {pwdCodeText}
                    </button>
                  </div>
                </div>
              )}

              {/* 新密码 */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">新密码</label>
                <div className="relative">
                  <input
                    type={showNewPassword ? 'text' : 'password'}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    className="w-full px-4 py-2.5 pr-10 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500 transition-all"
                    placeholder="请输入新密码（至少6位）"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPassword(!showNewPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                    >
                    {showNewPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 确认新密码 */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">确认新密码</label>
                <div className="relative">
                  <input
                    type={showConfirmPassword ? 'text' : 'password'}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className="w-full px-4 py-2.5 pr-10 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500 transition-all"
                    placeholder="请再次输入新密码"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                  >
                    {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 消息提示 */}
              {passwordMessage && (
                <div
                  className={`px-4 py-2 rounded-lg text-sm ${
                    passwordMessage.type === 'success'
                      ? 'bg-emerald-50 text-emerald-600'
                      : 'bg-red-50 text-red-600'
                  }`}
                >
                  {passwordMessage.text}
                </div>
              )}

              {/* 提交按钮 */}
              <button
                type="submit"
                disabled={passwordLoading}
                className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-primary-500 hover:bg-primary-600 text-white font-medium rounded-xl transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {passwordLoading ? (
                  <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                ) : (
                  <>
                    <Lock className="w-4 h-4" />
                    修改密码
                  </>
                )}
              </button>
            </div>
          </form>
        </motion.div>
      </div>

      {/* 绑定/换绑邮箱卡片 */}
      <motion.div
        className="bg-white rounded-2xl shadow-sm p-6 mt-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.35 }}
      >
        <div className="flex items-center gap-3 mb-6">
          <div className="w-10 h-10 bg-sky-50 rounded-xl flex items-center justify-center">
            <Mail className="w-5 h-5 text-sky-500" />
          </div>
          <h2 className="text-lg font-semibold text-slate-800">绑定邮箱</h2>
          <span className="ml-auto text-sm text-slate-400">
            当前：
            <span className="font-medium text-slate-600">{user.email || '未绑定'}</span>
          </span>
        </div>

        <form onSubmit={handleBindEmailSubmit}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* 新邮箱 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">新邮箱</label>
              <input
                type="email"
                value={newEmail}
                onChange={(e) => setNewEmail(e.target.value)}
                className="w-full px-4 py-2.5 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500 transition-all"
                placeholder="请输入要绑定的新邮箱"
              />
            </div>

            {/* 验证码 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">验证码</label>
              <div className="flex gap-3">
                <input
                  type="text"
                  value={bindCode}
                  onChange={(e) => setBindCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-500 transition-all"
                  placeholder="新邮箱收到的验证码"
                  inputMode="numeric"
                />
                <button
                  type="button"
                  onClick={handleGetBindCode}
                  disabled={bindCounting || bindSending || !newEmail}
                  className="px-4 py-2.5 bg-primary-50 text-primary-600 rounded-xl text-sm font-semibold whitespace-nowrap transition-all hover:bg-primary-100 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {bindCodeText}
                </button>
              </div>
            </div>

            {/* 消息提示 */}
            {bindMessage && (
              <div className="md:col-span-2">
                <div
                  className={`px-4 py-2 rounded-lg text-sm ${
                    bindMessage.type === 'success'
                      ? 'bg-emerald-50 text-emerald-600'
                      : 'bg-red-50 text-red-600'
                  }`}
                >
                  {bindMessage.text}
                </div>
              </div>
            )}

            {/* 提交按钮 */}
            <div className="md:col-span-2">
              <button
                type="submit"
                disabled={bindLoading}
                className="flex items-center justify-center gap-2 px-6 py-2.5 bg-primary-500 hover:bg-primary-600 text-white font-medium rounded-xl transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {bindLoading ? (
                  <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                ) : (
                  <>
                    <KeyRound className="w-4 h-4" />
                    {user.email ? '换绑邮箱' : '绑定邮箱'}
                  </>
                )}
              </button>
            </div>
          </div>
        </form>
      </motion.div>

      {/* 登出按钮 */}
      <motion.div
        className="mt-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.4 }}
      >
        <button
          onClick={() => setShowLogoutConfirm(true)}
          className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-red-50 hover:bg-red-100 text-red-600 font-medium rounded-xl transition-colors"
        >
          <LogOut className="w-4 h-4" />
          退出登录
        </button>
      </motion.div>

      {/* 登出确认对话框 */}
      {showLogoutConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <motion.div
            className="bg-white rounded-2xl p-6 max-w-sm w-full mx-4"
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
          >
            <div className="text-center">
              <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <LogOut className="w-8 h-8 text-red-500" />
              </div>
              <h3 className="text-lg font-semibold text-slate-800 mb-2">确认退出</h3>
              <p className="text-slate-500 mb-6">确定要退出当前账号吗？</p>
              <div className="flex gap-3">
                <button
                  onClick={() => setShowLogoutConfirm(false)}
                  className="flex-1 px-4 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium rounded-xl transition-colors"
                >
                  取消
                </button>
                <button
                  onClick={handleLogout}
                  className="flex-1 px-4 py-2.5 bg-red-500 hover:bg-red-600 text-white font-medium rounded-xl transition-colors"
                >
                  确认退出
                </button>
              </div>
            </div>
          </motion.div>
        </div>
      )}
    </motion.div>
  );
}
