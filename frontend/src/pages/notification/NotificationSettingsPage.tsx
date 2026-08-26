import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, Loader2 } from 'lucide-react';
import { notificationApi, NotificationSettings, NotificationSettingsUpdateRequest } from '../../api/notification';
import { getErrorMessage } from '../../api/request';

export default function NotificationSettingsPage() {
  const navigate = useNavigate();

  // 表单状态
  const [settings, setSettings] = useState<NotificationSettings>({
    inAppEnabled: true,
    emailEnabled: false,
    smsEnabled: false,
    wechatEnabled: false,
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // 加载设置
  useEffect(() => {
    const loadSettings = async () => {
      try {
        const data = await notificationApi.getSettings();
        setSettings(data);
      } catch (error) {
        console.error('获取通知设置失败', error);
      } finally {
        setLoading(false);
      }
    };
    loadSettings();
  }, []);

  // 切换开关
  const handleToggle = (key: keyof NotificationSettings) => {
    setSettings(prev => ({
      ...prev,
      [key]: !prev[key],
    }));
  };

  // 保存设置
  const handleSave = async () => {
    setSaving(true);
    setMessage(null);

    try {
      const updateData: NotificationSettingsUpdateRequest = {
        inAppEnabled: settings.inAppEnabled,
        emailEnabled: settings.emailEnabled,
        smsEnabled: settings.smsEnabled,
        wechatEnabled: settings.wechatEnabled,
      };
      await notificationApi.updateSettings(updateData);
      setMessage({ type: 'success', text: '设置保存成功' });
    } catch (error) {
      setMessage({ type: 'error', text: getErrorMessage(error) });
    } finally {
      setSaving(false);
    }
  };

  // 返回通知列表
  const handleBack = () => {
    navigate('/notifications');
  };

  // 清除消息
  useEffect(() => {
    if (message) {
      const timer = setTimeout(() => setMessage(null), 3000);
      return () => clearTimeout(timer);
    }
  }, [message]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-5 h-5 text-zinc-400 animate-spin" />
      </div>
    );
  }

  // 通知渠道配置项
  const channels: { key: keyof NotificationSettings; title: string; desc: string }[] = [
    { key: 'inAppEnabled', title: '站内通知', desc: '在平台内接收通知消息' },
    { key: 'emailEnabled', title: '邮件通知', desc: '通过邮箱接收重要通知' },
    { key: 'smsEnabled', title: '短信通知', desc: '通过短信接收紧急通知' },
    { key: 'wechatEnabled', title: '微信通知', desc: '通过微信接收通知消息' },
  ];

  return (
    <div className="fade-in">
      {/* 页头 */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={handleBack}
          className="h-9 w-9 flex items-center justify-center border border-zinc-300 rounded-md text-zinc-500 hover:bg-zinc-50 hover:border-zinc-400 transition-colors shrink-0"
          aria-label="返回通知中心"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">通知设置</h1>
          <p className="mt-1 text-sm text-zinc-500">管理您接收通知的方式</p>
        </div>
      </div>

      {/* 消息提示 */}
      {message && (
        <div
          className={`mb-4 border rounded-md px-3 py-2.5 text-sm fade-in ${
            message.type === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
              : 'border-red-200 bg-red-50 text-red-700'
          }`}
        >
          {message.text}
        </div>
      )}

      {/* 设置卡片 */}
      <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
        <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
          <h2 className="text-sm font-medium text-zinc-900">通知渠道</h2>
          <span className="font-mono text-xs text-zinc-400">{channels.length} 项</span>
        </div>

        <div className="divide-y divide-zinc-100">
          {channels.map((channel) => (
            <div key={channel.key} className="flex items-center justify-between px-5 py-4">
              <div>
                <h3 className="text-sm font-medium text-zinc-900">{channel.title}</h3>
                <p className="mt-0.5 text-xs text-zinc-500">{channel.desc}</p>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={settings[channel.key]}
                onClick={() => handleToggle(channel.key)}
                className={`relative w-9 h-5 rounded-full transition-colors shrink-0 ${
                  settings[channel.key] ? 'bg-primary-600' : 'bg-zinc-200'
                }`}
              >
                <span
                  className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform ${
                    settings[channel.key] ? 'translate-x-4' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>
          ))}
        </div>

        {/* 保存按钮 */}
        <div className="border-t border-zinc-100 px-5 py-4">
          <button
            onClick={handleSave}
            disabled={saving}
            className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-2"
          >
            {saving && <Loader2 className="w-4 h-4 animate-spin" />}
            {saving ? '保存中…' : '保存设置'}
          </button>
        </div>
      </div>
    </div>
  );
}
