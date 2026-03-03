import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Crown,
  Star,
  FileText,
  MessageSquare,
  Sparkles,
  Calendar,
  Loader2,
  CreditCard,
  Gift,
  TrendingUp,
} from 'lucide-react';
import { membershipApi, MembershipDTO, MembershipType } from '../../api/membership';
import { pointsApi, SignInStatusResponse } from '../../api/points';
import SignInButton from '../../components/membership/SignInButton';

// 会员类型文本
function getMembershipText(type: MembershipType): string {
  return type === 'PREMIUM' ? 'VIP 会员' : '免费用户';
}

// 会员类型样式
function getMembershipStyle(type: MembershipType): {
  badge: string;
  text: string;
  bg: string;
} {
  if (type === 'PREMIUM') {
    return {
      badge: 'bg-gradient-to-r from-amber-400 to-orange-500',
      text: 'text-white',
      bg: 'from-amber-50 to-orange-50',
    };
  }
  return {
    badge: 'bg-slate-200',
    text: 'text-slate-600',
    bg: 'from-slate-50 to-slate-100',
  };
}

// 格式化日期
function formatDate(dateStr: string | undefined): string {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

// 额度卡片组件
function QuotaCard({
  icon: Icon,
  label,
  quota,
  color,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  quota: number;
  color: string;
}) {
  // Integer.MAX_VALUE = 2147483647，表示 VIP 无限额度
  const isUnlimited = quota === -1 || quota >= 2147483647;
  const displayQuota = isUnlimited ? '无限' : quota;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-white rounded-xl p-5 shadow-sm border border-slate-100"
    >
      <div className="flex items-center gap-3 mb-4">
        <div className={`p-2.5 rounded-lg ${color}`}>
          <Icon className="w-5 h-5 text-white" />
        </div>
        <span className="font-medium text-slate-700">{label}</span>
      </div>
      <div className="space-y-2">
        <div className="flex justify-between text-sm">
          <span className="text-slate-500">可用额度</span>
          <span className="font-medium text-slate-700">
            {displayQuota} 次
          </span>
        </div>
        {!isUnlimited && (
          <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
            <motion.div
              className="h-full"
              initial={{ width: 0 }}
              animate={{ width: '100%' }}
              transition={{ duration: 0.8, ease: 'easeOut' }}
              style={{ backgroundColor: color.includes('primary') ? '#6366f1' : color.includes('emerald') ? '#10b981' : '#f59e0b' }}
            />
          </div>
        )}
      </div>
    </motion.div>
  );
}

export default function MembershipPage() {
  const navigate = useNavigate();
  const [membership, setMembership] = useState<MembershipDTO | null>(null);
  const [points, setPoints] = useState<number>(0);
  const [signInStatus, setSignInStatus] = useState<SignInStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [upgrading, setUpgrading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 加载会员信息和积分
  const loadData = useCallback(async () => {
    try {
      const [membershipData, pointsData, signInData] = await Promise.all([
        membershipApi.getMembership(),
        pointsApi.getPoints(),
        pointsApi.getSignInStatus(),
      ]);
      setMembership(membershipData);
      setPoints(pointsData);
      setSignInStatus(signInData);
    } catch (err) {
      console.error('加载会员信息失败', err);
      setError('加载会员信息失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // 升级为 VIP
  const handleUpgrade = async () => {
    try {
      setUpgrading(true);
      const updated = await membershipApi.upgradeToPremium();
      setMembership(updated);
    } catch (err) {
      console.error('升级失败', err);
      alert('升级失败，请稍后重试');
    } finally {
      setUpgrading(false);
    }
  };

  // 跳转到积分记录页面
  const handleViewPointsHistory = () => {
    navigate('/membership/points-history');
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  if (error || !membership) {
    return (
      <div className="max-w-4xl mx-auto">
        <div className="bg-red-50 text-red-600 p-4 rounded-lg">
          {error || '加载失败，请稍后重试'}
        </div>
      </div>
    );
  }

  const membershipStyle = getMembershipStyle(membership.membership);

  return (
    <div className="max-w-5xl mx-auto">
      {/* 页面标题 */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
          <Crown className="w-7 h-7 text-amber-500" />
          会员中心
        </h1>
        <p className="text-slate-500 mt-1">查看会员状态和额度使用情况</p>
      </div>

      {/* 会员状态卡片 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className={`bg-gradient-to-br ${membershipStyle.bg} rounded-2xl p-8 mb-8 border border-slate-200`}
      >
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-4">
            <div className={`p-4 rounded-2xl ${membershipStyle.badge}`}>
              {membership.membership === 'PREMIUM' ? (
                <Crown className="w-8 h-8 text-white" />
              ) : (
                <Star className="w-8 h-8 text-slate-500" />
              )}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-2xl font-bold text-slate-800">
                  {getMembershipText(membership.membership)}
                </h2>
                {membership.membership === 'PREMIUM' && (
                  <span className="px-2 py-0.5 bg-amber-100 text-amber-700 text-xs font-medium rounded-full">
                    当前
                  </span>
                )}
              </div>
              {membership.vipExpiryDate && (
                <div className="flex items-center gap-2 mt-2 text-slate-500">
                  <Calendar className="w-4 h-4" />
                  <span className="text-sm">
                    VIP 到期时间: {formatDate(membership.vipExpiryDate)}
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* 升级按钮 - 仅免费用户显示 */}
          {membership.membership === 'FREE' && (
            <button
              onClick={handleUpgrade}
              disabled={upgrading}
              className="flex items-center gap-2 px-6 py-3 bg-gradient-to-r from-amber-400 to-orange-500 text-white font-medium rounded-xl hover:from-amber-500 hover:to-orange-600 transition-all shadow-lg shadow-amber-500/30 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {upgrading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  <span>升级中...</span>
                </>
              ) : (
                <>
                  <Crown className="w-5 h-5" />
                  <span>立即升级 VIP</span>
                </>
              )}
            </button>
          )}
        </div>

        {/* 积分余额展示 */}
        <div className="mt-8 pt-6 border-t border-slate-200/50">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2.5 bg-primary-100 rounded-xl">
                <Gift className="w-6 h-6 text-primary-600" />
              </div>
              <div>
                <p className="text-sm text-slate-500">当前积分</p>
                <p className="text-3xl font-bold text-slate-800">{points.toLocaleString()}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <SignInButton
                signedIn={signInStatus?.signedIn ?? false}
                consecutiveDays={signInStatus?.consecutiveDays ?? 0}
                onSignInSuccess={() => {
                  // 签到成功后刷新数据
                  loadData();
                }}
              />
              <button
                onClick={handleViewPointsHistory}
                className="flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 text-slate-600 rounded-lg hover:bg-slate-50 transition-colors"
              >
                <TrendingUp className="w-4 h-4" />
                <span>查看积分记录</span>
              </button>
            </div>
          </div>
        </div>
      </motion.div>

      {/* 额度使用情况 */}
      <div className="mb-6">
        <h3 className="text-lg font-semibold text-slate-800 mb-4 flex items-center gap-2">
          <CreditCard className="w-5 h-5 text-slate-500" />
          额度使用情况
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <QuotaCard
            icon={FileText}
            label="简历分析额度"
            quota={membership.resumeQuota}
            color="bg-primary-500"
          />
          <QuotaCard
            icon={MessageSquare}
            label="模拟面试额度"
            quota={membership.interviewQuota}
            color="bg-emerald-500"
          />
          <QuotaCard
            icon={Sparkles}
            label="AI 调用额度"
            quota={membership.aiCallQuota}
            color="bg-amber-500"
          />
        </div>
      </div>

      {/* 会员特权说明 - 仅免费用户显示 */}
      {membership.membership === 'FREE' && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="bg-white rounded-xl p-6 border border-slate-100"
        >
          <h3 className="text-lg font-semibold text-slate-800 mb-4">VIP 会员特权</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="flex items-start gap-3 p-4 bg-amber-50 rounded-lg">
              <Crown className="w-5 h-5 text-amber-500 mt-0.5" />
              <div>
                <p className="font-medium text-slate-800">无限额度</p>
                <p className="text-sm text-slate-500">简历分析、模拟面试、AI 调用次数无限制</p>
              </div>
            </div>
            <div className="flex items-start gap-3 p-4 bg-amber-50 rounded-lg">
              <Gift className="w-5 h-5 text-amber-500 mt-0.5" />
              <div>
                <p className="font-medium text-slate-800">积分赠送</p>
                <p className="text-sm text-slate-500">升级即送 1000 积分</p>
              </div>
            </div>
            <div className="flex items-start gap-3 p-4 bg-amber-50 rounded-lg">
              <Star className="w-5 h-5 text-amber-500 mt-0.5" />
              <div>
                <p className="font-medium text-slate-800">专属客服</p>
                <p className="text-sm text-slate-500">享受优先客服支持</p>
              </div>
            </div>
            <div className="flex items-start gap-3 p-4 bg-amber-50 rounded-lg">
              <TrendingUp className="w-5 h-5 text-amber-500 mt-0.5" />
              <div>
                <p className="font-medium text-slate-800">功能优先</p>
                <p className="text-sm text-slate-500">新功能优先体验</p>
              </div>
            </div>
          </div>
        </motion.div>
      )}
    </div>
  );
}
