import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../../store/user';
import { pointsApi } from '../../api/points';
import { membershipApi, MembershipType } from '../../api/membership';
import { authApi } from '../../api/auth';
import { getErrorMessage } from '../../api/request';
import { useCodeCountdown } from '../../hooks/useCodeCountdown';
import { isValidEmail } from '../../utils/validate';
import { Eye, EyeOff, Loader2 } from 'lucide-react';

// 控件样式（统一设计体系）
const labelCls = 'block text-xs font-medium text-zinc-600 mb-1.5';
const inputCls =
  'w-full h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors disabled:bg-zinc-100';
const primaryBtnCls =
  'h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-2';
const secondaryBtnCls =
  'h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-60 disabled:cursor-not-allowed';

// 表单消息提示（成功/失败语义色）
function messageCls(type: 'success' | 'error'): string {
  return `border rounded-md px-3 py-2.5 text-sm fade-in ${
    type === 'success'
      ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
      : 'border-red-200 bg-red-50 text-red-700'
  }`;
}

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

  // 获取会员状态显示（克制语义色 chip）
  const getMembershipDisplay = (membership: string) => {
    const membershipMap: Record<string, { label: string; chipCls: string }> = {
      FREE: { label: '免费用户', chipCls: 'text-zinc-500 bg-zinc-50 border-zinc-200' },
      VIP: { label: 'VIP 会员', chipCls: 'text-amber-700 bg-amber-50 border-amber-200' },
      PREMIUM: { label: '高级会员', chipCls: 'text-primary-700 bg-primary-50 border-primary-200' },
    };
    return membershipMap[membership] || { label: membership, chipCls: 'text-zinc-500 bg-zinc-50 border-zinc-200' };
  };

  if (!user) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <p className="px-5 py-8 text-xs text-zinc-400 text-center">请先登录后查看个人中心</p>
      </div>
    );
  }

  const membershipInfo = getMembershipDisplay(membership);

  return (
    <div className="fade-in">
      {/* 页头 */}
      <div className="flex items-end justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">个人中心</h1>
          <p className="mt-1 text-sm text-zinc-500">欢迎回来，{user.nickname || user.username}</p>
        </div>
        <span className="font-mono text-xs text-zinc-400">ID {user.id ?? '—'}</span>
      </div>

      {/* 账号信息卡片 */}
      <div className="bg-white border border-zinc-200 rounded-lg shadow-sm p-5 mb-5">
        <div className="flex items-center gap-5">
          {/* 头像：有头像展示图片，否则首字母圆 */}
          {user.avatar ? (
            <img
              src={user.avatar}
              alt={user.nickname || user.username}
              className="w-16 h-16 rounded-full object-cover border border-zinc-200 shrink-0"
            />
          ) : (
            <div className="w-16 h-16 rounded-full bg-primary-800 text-white text-xl font-medium flex items-center justify-center uppercase shrink-0">
              {(user.nickname || user.username).charAt(0)}
            </div>
          )}

          {/* 基础信息 */}
          <div className="flex-1 grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="min-w-0">
              <p className="text-xs text-zinc-400">用户名</p>
              <p className="mt-1 text-sm font-medium text-zinc-800 truncate">{user.username}</p>
            </div>
            <div className="min-w-0">
              <p className="text-xs text-zinc-400">邮箱</p>
              <p className="mt-1 text-sm font-medium text-zinc-800 truncate">{user.email || '—'}</p>
            </div>
            <div>
              <p className="text-xs text-zinc-400">积分</p>
              <p className="mt-1 font-mono text-sm font-medium text-primary-800 tabular-nums">
                {pointsLoading ? '—' : points.toLocaleString()}
              </p>
            </div>
            <div>
              <p className="text-xs text-zinc-400">会员等级</p>
              <span
                className={`mt-1.5 inline-block text-xs border rounded px-1.5 py-0.5 ${
                  membershipLoading ? 'text-zinc-500 bg-zinc-50 border-zinc-200' : membershipInfo.chipCls
                }`}
              >
                {membershipLoading ? '—' : membershipInfo.label}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* 两个卡片：个人资料和修改密码 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* 个人资料卡片 */}
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm flex flex-col">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">编辑资料</h2>
          </div>

          <form onSubmit={handleProfileSubmit} className="p-5">
            <div className="space-y-4">
              {/* 昵称 */}
              <div>
                <label htmlFor="nickname" className={labelCls}>
                  昵称
                </label>
                <input
                  id="nickname"
                  type="text"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  className={inputCls}
                  placeholder="请输入昵称"
                />
              </div>

              {/* 头像 URL */}
              <div>
                <label htmlFor="avatar" className={labelCls}>
                  头像 URL
                </label>
                <input
                  id="avatar"
                  type="url"
                  value={avatar}
                  onChange={(e) => setAvatar(e.target.value)}
                  className={inputCls}
                  placeholder="https://example.com/avatar.jpg"
                />
              </div>

              {/* 消息提示 */}
              {profileMessage && <div className={messageCls(profileMessage.type)}>{profileMessage.text}</div>}

              {/* 提交按钮 */}
              <div>
                <button type="submit" disabled={profileLoading} className={primaryBtnCls}>
                  {profileLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                  {profileLoading ? '保存中…' : '保存修改'}
                </button>
              </div>
            </div>
          </form>
        </div>

        {/* 修改密码卡片 */}
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm flex flex-col">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">修改密码</h2>
          </div>

          {/* 验证方式切换：底线式 tabs */}
          <div className="px-5 pt-4">
            <div className="flex border-b border-zinc-200">
              <button
                type="button"
                onClick={() => { setPwdTab('old'); setPasswordMessage(null); }}
                className={`pb-2.5 pr-6 text-sm border-b-2 -mb-px transition-colors ${
                  pwdTab === 'old'
                    ? 'border-primary-600 text-zinc-900 font-medium'
                    : 'border-transparent text-zinc-500 hover:text-zinc-800'
                }`}
              >
                原密码验证
              </button>
              <button
                type="button"
                onClick={() => { setPwdTab('email'); setPasswordMessage(null); }}
                className={`pb-2.5 pr-6 text-sm border-b-2 -mb-px transition-colors ${
                  pwdTab === 'email'
                    ? 'border-primary-600 text-zinc-900 font-medium'
                    : 'border-transparent text-zinc-500 hover:text-zinc-800'
                }`}
              >
                邮箱验证码
              </button>
            </div>
          </div>

          <form onSubmit={handlePasswordSubmit} className="p-5 pt-4">
            <div className="space-y-4">
              {/* 凭证：旧密码 或 邮箱验证码 */}
              {pwdTab === 'old' ? (
                <div>
                  <label htmlFor="oldPassword" className={labelCls}>
                    旧密码
                  </label>
                  <div className="relative">
                    <input
                      id="oldPassword"
                      type={showOldPassword ? 'text' : 'password'}
                      value={oldPassword}
                      onChange={(e) => setOldPassword(e.target.value)}
                      className={`${inputCls} pr-10`}
                      placeholder="请输入旧密码"
                      required
                    />
                    <button
                      type="button"
                      onClick={() => setShowOldPassword(!showOldPassword)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-zinc-400 hover:text-zinc-700 transition-colors"
                      tabIndex={-1}
                    >
                      {showOldPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  <label htmlFor="pwdCode" className={labelCls}>
                    验证码（发送至 {user.email || '未绑定邮箱'}）
                  </label>
                  <div className="flex gap-2">
                    <input
                      id="pwdCode"
                      type="text"
                      value={pwdCode}
                      onChange={(e) => setPwdCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                      className={`${inputCls} flex-1 font-mono tracking-widest`}
                      placeholder="6 位数字验证码"
                      inputMode="numeric"
                    />
                    <button
                      type="button"
                      onClick={handleGetPwdCode}
                      disabled={pwdCounting || pwdSending || !user.email}
                      className="h-9 px-3.5 border border-zinc-300 rounded-md text-sm text-zinc-700 font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
                    >
                      {pwdCodeText}
                    </button>
                  </div>
                </div>
              )}

              {/* 新密码 */}
              <div>
                <label htmlFor="newPassword" className={labelCls}>
                  新密码
                </label>
                <div className="relative">
                  <input
                    id="newPassword"
                    type={showNewPassword ? 'text' : 'password'}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    className={`${inputCls} pr-10`}
                    placeholder="请输入新密码（至少6位）"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPassword(!showNewPassword)}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-zinc-400 hover:text-zinc-700 transition-colors"
                    tabIndex={-1}
                  >
                    {showNewPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 确认新密码 */}
              <div>
                <label htmlFor="confirmPassword" className={labelCls}>
                  确认新密码
                </label>
                <div className="relative">
                  <input
                    id="confirmPassword"
                    type={showConfirmPassword ? 'text' : 'password'}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className={`${inputCls} pr-10`}
                    placeholder="请再次输入新密码"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-zinc-400 hover:text-zinc-700 transition-colors"
                    tabIndex={-1}
                  >
                    {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 消息提示 */}
              {passwordMessage && <div className={messageCls(passwordMessage.type)}>{passwordMessage.text}</div>}

              {/* 提交按钮 */}
              <div>
                <button type="submit" disabled={passwordLoading} className={primaryBtnCls}>
                  {passwordLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                  {passwordLoading ? '提交中…' : '修改密码'}
                </button>
              </div>
            </div>
          </form>
        </div>
      </div>

      {/* 绑定/换绑邮箱卡片 */}
      <div className="bg-white border border-zinc-200 rounded-lg shadow-sm mt-5">
        <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
          <h2 className="text-sm font-medium text-zinc-900">绑定邮箱</h2>
          <span className="font-mono text-xs text-zinc-400 truncate">当前：{user.email || '未绑定'}</span>
        </div>

        <form onSubmit={handleBindEmailSubmit} className="p-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* 新邮箱 */}
            <div>
              <label htmlFor="newEmail" className={labelCls}>
                新邮箱
              </label>
              <input
                id="newEmail"
                type="email"
                value={newEmail}
                onChange={(e) => setNewEmail(e.target.value)}
                className={inputCls}
                placeholder="请输入要绑定的新邮箱"
              />
            </div>

            {/* 验证码 */}
            <div>
              <label htmlFor="bindCode" className={labelCls}>
                验证码
              </label>
              <div className="flex gap-2">
                <input
                  id="bindCode"
                  type="text"
                  value={bindCode}
                  onChange={(e) => setBindCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  className={`${inputCls} flex-1 font-mono tracking-widest`}
                  placeholder="新邮箱收到的验证码"
                  inputMode="numeric"
                />
                <button
                  type="button"
                  onClick={handleGetBindCode}
                  disabled={bindCounting || bindSending || !newEmail}
                  className="h-9 px-3.5 border border-zinc-300 rounded-md text-sm text-zinc-700 font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
                >
                  {bindCodeText}
                </button>
              </div>
            </div>

            {/* 消息提示 */}
            {bindMessage && (
              <div className="md:col-span-2">
                <div className={messageCls(bindMessage.type)}>{bindMessage.text}</div>
              </div>
            )}

            {/* 提交按钮 */}
            <div className="md:col-span-2">
              <button type="submit" disabled={bindLoading} className={primaryBtnCls}>
                {bindLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                {bindLoading ? '提交中…' : user.email ? '换绑邮箱' : '绑定邮箱'}
              </button>
            </div>
          </div>
        </form>
      </div>

      {/* 退出登录 */}
      <div className="mt-5">
        <button
          onClick={() => setShowLogoutConfirm(true)}
          className="h-9 px-4 rounded-md bg-red-600 text-white text-sm font-medium hover:bg-red-700 transition-colors"
        >
          退出登录
        </button>
      </div>

      {/* 登出确认对话框 */}
      {showLogoutConfirm && (
        <>
          <div className="fixed inset-0 bg-zinc-950/40 z-50 fade-in" onClick={() => setShowLogoutConfirm(false)} />
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div className="bg-white border border-zinc-200 rounded-lg shadow-sm max-w-sm w-full p-5 fade-in">
              <h3 className="text-sm font-medium text-zinc-900 mb-3">确认退出</h3>
              <p className="text-sm text-zinc-500 mb-6">确定要退出当前账号吗？</p>
              <div className="flex gap-3 justify-end">
                <button onClick={() => setShowLogoutConfirm(false)} className={secondaryBtnCls}>
                  取消
                </button>
                <button
                  onClick={handleLogout}
                  className="h-9 px-4 rounded-md bg-red-600 text-white text-sm font-medium hover:bg-red-700 transition-colors"
                >
                  确认退出
                </button>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
