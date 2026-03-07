import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { Bell, Mail, MessageSquare, Wallet, Save, ChevronLeft } from 'lucide-react';
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
        <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <motion.div
      className="max-w-2xl mx-auto"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      {/* 页面头部 */}
      <div className="flex items-center gap-4 mb-6">
        <button
          onClick={handleBack}
          className="p-2 hover:bg-slate-100 rounded-xl transition-colors"
        >
          <ChevronLeft className="w-5 h-5 text-slate-600" />
        </button>
        <div>
          <motion.h1
            className="text-3xl font-bold text-slate-900 mb-2"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
          >
            通知设置
          </motion.h1>
          <motion.p
            className="text-slate-500"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.1 }}
          >
            管理您接收通知的方式
          </motion.p>
        </div>
      </div>

      {/* 消息提示 */}
      {message && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className={`mb-4 px-4 py-2 rounded-lg text-sm ${
            message.type === 'success' ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'
          }`}
        >
          {message.text}
        </motion.div>
      )}

      {/* 设置卡片 */}
      <motion.div
        className="bg-white rounded-2xl shadow-sm overflow-hidden"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="p-6 space-y-6">
          {/* 站内通知 */}
          <div className="flex items-center justify-between py-3 border-b border-slate-100">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 bg-primary-50 rounded-xl flex items-center justify-center">
                <Bell className="w-5 h-5 text-primary-500" />
              </div>
              <div>
                <h3 className="font-medium text-slate-900">站内通知</h3>
                <p className="text-sm text-slate-500">在平台内接收通知消息</p>
              </div>
            </div>
            <button
              onClick={() => handleToggle('inAppEnabled')}
              className={`relative w-12 h-6 rounded-full transition-colors ${
                settings.inAppEnabled ? 'bg-primary-500' : 'bg-slate-200'
              }`}
            >
              <span
                className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${
                  settings.inAppEnabled ? 'left-7' : 'left-1'
                }`}
              />
            </button>
          </div>

          {/* 邮件通知 */}
          <div className="flex items-center justify-between py-3 border-b border-slate-100">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 bg-blue-50 rounded-xl flex items-center justify-center">
                <Mail className="w-5 h-5 text-blue-500" />
              </div>
              <div>
                <h3 className="font-medium text-slate-900">邮件通知</h3>
                <p className="text-sm text-slate-500">通过邮箱接收重要通知</p>
              </div>
            </div>
            <button
              onClick={() => handleToggle('emailEnabled')}
              className={`relative w-12 h-6 rounded-full transition-colors ${
                settings.emailEnabled ? 'bg-primary-500' : 'bg-slate-200'
              }`}
            >
              <span
                className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${
                  settings.emailEnabled ? 'left-7' : 'left-1'
                }`}
              />
            </button>
          </div>

          {/* 短信通知 */}
          <div className="flex items-center justify-between py-3 border-b border-slate-100">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 bg-green-50 rounded-xl flex items-center justify-center">
                <MessageSquare className="w-5 h-5 text-green-500" />
              </div>
              <div>
                <h3 className="font-medium text-slate-900">短信通知</h3>
                <p className="text-sm text-slate-500">通过短信接收紧急通知</p>
              </div>
            </div>
            <button
              onClick={() => handleToggle('smsEnabled')}
              className={`relative w-12 h-6 rounded-full transition-colors ${
                settings.smsEnabled ? 'bg-primary-500' : 'bg-slate-200'
              }`}
            >
              <span
                className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${
                  settings.smsEnabled ? 'left-7' : 'left-1'
                }`}
              />
            </button>
          </div>

          {/* 微信通知 */}
          <div className="flex items-center justify-between py-3">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 bg-emerald-50 rounded-xl flex items-center justify-center">
                <Wallet className="w-5 h-5 text-emerald-500" />
              </div>
              <div>
                <h3 className="font-medium text-slate-900">微信通知</h3>
                <p className="text-sm text-slate-500">通过微信接收通知消息</p>
              </div>
            </div>
            <button
              onClick={() => handleToggle('wechatEnabled')}
              className={`relative w-12 h-6 rounded-full transition-colors ${
                settings.wechatEnabled ? 'bg-primary-500' : 'bg-slate-200'
              }`}
            >
              <span
                className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-transform ${
                  settings.wechatEnabled ? 'left-7' : 'left-1'
                }`}
              />
            </button>
          </div>
        </div>

        {/* 保存按钮 */}
        <div className="p-6 border-t border-slate-100">
          <button
            onClick={handleSave}
            disabled={saving}
            className="w-full flex items-center justify-center gap-2 px-4 py-3 bg-primary-500 hover:bg-primary-600 text-white font-medium rounded-xl transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {saving ? (
              <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
            ) : (
              <>
                <Save className="w-4 h-4" />
                保存设置
              </>
            )}
          </button>
        </div>
      </motion.div>
    </motion.div>
  );
}
