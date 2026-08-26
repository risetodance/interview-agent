import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Crown,
  Star,
  FileText,
  MessageSquare,
  Sparkles,
  Loader2,
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

// 额度条目组件
function QuotaItem({
  icon: Icon,
  label,
  quota,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  quota: number;
}) {
  // Integer.MAX_VALUE = 2147483647，表示 VIP 无限额度
  const isUnlimited = quota === -1 || quota >= 2147483647;

  return (
    <div className="px-5 py-4">
      <div className="flex items-center gap-2">
        <Icon className="w-4 h-4 text-zinc-400" />
        <span className="text-xs text-zinc-500">{label}</span>
      </div>
      <p className="mt-2 flex items-baseline gap-1">
        <span className="font-mono text-2xl font-semibold text-primary-800 tabular-nums">
          {isUnlimited ? '无限' : quota}
        </span>
        {!isUnlimited && <span className="text-xs text-zinc-400">次</span>}
      </p>
    </div>
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
        <Loader2 className="w-5 h-5 text-zinc-400 animate-spin" />
      </div>
    );
  }

  if (error || !membership) {
    return (
      <div className="border border-red-200 bg-red-50 rounded-lg px-4 py-3 text-sm text-red-700">
        {error || '加载失败，请稍后重试'}
      </div>
    );
  }

  const isFree = membership.membership === 'FREE';

  // VIP 会员特权（免费用户引导升级）
  const vipPrivileges = [
    { icon: Crown, title: '无限额度', desc: '简历分析、模拟面试、AI 调用次数无限制' },
    { icon: Gift, title: '积分赠送', desc: '升级即送 1000 积分' },
    { icon: Star, title: '专属客服', desc: '享受优先客服支持' },
    { icon: TrendingUp, title: '功能优先', desc: '新功能优先体验' },
  ];

  return (
    <div className="fade-in">
      {/* 页头 */}
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">会员中心</h1>
        <p className="mt-1 text-sm text-zinc-500">查看会员状态、积分与额度使用情况</p>
      </div>

      {/* 会员状态（当前档位突出）+ 积分 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 mb-5">
        {/* 当前档位卡片 */}
        <div className="lg:col-span-2 rounded-lg border border-primary-600 bg-primary-50/50 p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-medium text-zinc-900">{getMembershipText(membership.membership)}</h2>
              <span className="text-xs border rounded px-1.5 py-0.5 text-primary-700 bg-primary-50 border-primary-200">
                当前档位
              </span>
            </div>
            {membership.vipExpiryDate ? (
              <p className="mt-2 font-mono text-xs text-zinc-500">
                VIP 到期时间：{formatDate(membership.vipExpiryDate)}
              </p>
            ) : (
              <p className="mt-2 text-xs text-zinc-500">基础额度版，可升级 VIP 解锁无限额度</p>
            )}
          </div>

          {/* 升级按钮 - 仅免费用户显示 */}
          {isFree && (
            <button
              onClick={handleUpgrade}
              disabled={upgrading}
              className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-2 shrink-0"
            >
              {upgrading && <Loader2 className="w-4 h-4 animate-spin" />}
              {upgrading ? '升级中…' : '立即升级 VIP'}
            </button>
          )}
        </div>

        {/* 积分卡片 */}
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm p-5 flex flex-col justify-between gap-4">
          <div>
            <p className="text-xs text-zinc-500">当前积分</p>
            <p className="mt-1.5 font-mono text-2xl font-semibold text-primary-800 tabular-nums">
              {points.toLocaleString()}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
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
              className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
            >
              查看积分记录
            </button>
          </div>
        </div>
      </div>

      {/* 额度使用情况 */}
      <div className="bg-white border border-zinc-200 rounded-lg shadow-sm mb-5">
        <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
          <h2 className="text-sm font-medium text-zinc-900">额度使用</h2>
          <span className="font-mono text-xs text-zinc-400">
            {isFree ? 'FREE' : 'PREMIUM'}
          </span>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-3 divide-y sm:divide-y-0 sm:divide-x divide-zinc-100">
          <QuotaItem icon={FileText} label="简历分析额度" quota={membership.resumeQuota} />
          <QuotaItem icon={MessageSquare} label="模拟面试额度" quota={membership.interviewQuota} />
          <QuotaItem icon={Sparkles} label="AI 调用额度" quota={membership.aiCallQuota} />
        </div>
      </div>

      {/* 会员特权说明 - 仅免费用户显示 */}
      {isFree && (
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">VIP 会员特权</h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 md:divide-x divide-zinc-100">
            <div className="divide-y divide-zinc-100">
              {vipPrivileges.slice(0, 2).map((p) => (
                <div key={p.title} className="px-5 py-4 flex items-start gap-3">
                  <p.icon className="w-4 h-4 text-zinc-400 mt-0.5 shrink-0" />
                  <div>
                    <p className="text-sm font-medium text-zinc-800">{p.title}</p>
                    <p className="mt-0.5 text-xs text-zinc-500">{p.desc}</p>
                  </div>
                </div>
              ))}
            </div>
            <div className="divide-y divide-zinc-100">
              {vipPrivileges.slice(2, 4).map((p) => (
                <div key={p.title} className="px-5 py-4 flex items-start gap-3">
                  <p.icon className="w-4 h-4 text-zinc-400 mt-0.5 shrink-0" />
                  <div>
                    <p className="text-sm font-medium text-zinc-800">{p.title}</p>
                    <p className="mt-0.5 text-xs text-zinc-500">{p.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
