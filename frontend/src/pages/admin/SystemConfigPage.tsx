import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import {
  Settings,
  Save,
  Loader2,
  CheckCircle,
  AlertCircle,
} from 'lucide-react';
import { adminApi, SystemConfig } from '../../api/admin';

export default function SystemConfigPage() {
  const [configs, setConfigs] = useState<SystemConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 本地编辑状态
  const [editedConfigs, setEditedConfigs] = useState<Record<string, string>>({});

  const loadConfigs = useCallback(async () => {
    try {
      setLoading(true);
      const data = await adminApi.getSystemConfig();
      setConfigs(data);
      // 初始化编辑状态
      const initialValues: Record<string, string> = {};
      data.forEach((config) => {
        initialValues[config.key] = config.value;
      });
      setEditedConfigs(initialValues);
    } catch (err) {
      console.error('加载配置失败:', err);
      setError('加载配置失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConfigs();
  }, [loadConfigs]);

  // 处理配置值变更
  const handleConfigChange = (key: string, value: string) => {
    setEditedConfigs((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  // 保存配置
  const handleSave = async () => {
    try {
      setSaving(true);
      setError(null);
      await adminApi.updateSystemConfig(editedConfigs);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
      // 重新加载配置
      await loadConfigs();
    } catch (err) {
      console.error('保存配置失败:', err);
      setError('保存配置失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  // 获取配置类型
  const getConfigType = (key: string): string => {
    const config = configs.find((c) => c.key === key);
    return config?.type || 'text';
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      {/* 页面标题 */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
            <Settings className="w-7 h-7 text-primary-500" />
            系统配置
          </h1>
          <p className="text-slate-500 mt-1">配置系统参数和功能开关</p>
        </div>
        <div className="flex items-center gap-3">
          {saveSuccess && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="flex items-center gap-2 px-4 py-2 bg-green-50 text-green-700 rounded-lg"
            >
              <CheckCircle className="w-4 h-4" />
              <span className="text-sm font-medium">保存成功</span>
            </motion.div>
          )}
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {saving ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Save className="w-4 h-4" />
            )}
            <span>保存配置</span>
          </button>
        </div>
      </div>

      {/* 错误提示 */}
      {error && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex items-center gap-2 px-4 py-3 mb-6 bg-red-50 text-red-700 rounded-lg"
        >
          <AlertCircle className="w-5 h-5" />
          <span>{error}</span>
        </motion.div>
      )}

      {/* 配置列表 */}
      <div className="space-y-6">
        {configs.map((config, index) => (
          <motion.div
            key={config.key}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.05 }}
            className="bg-white rounded-xl p-6 shadow-sm border border-slate-100"
          >
            <div className="mb-4">
              <h3 className="text-lg font-semibold text-slate-800">{config.key}</h3>
              <p className="text-sm text-slate-500 mt-1">{config.description}</p>
            </div>

            {getConfigType(config.key) === 'boolean' ? (
              <div className="flex items-center gap-3">
                <button
                  onClick={() => handleConfigChange(config.key, 'true')}
                  className={`flex-1 py-3 px-4 rounded-lg border-2 transition-colors ${
                    editedConfigs[config.key] === 'true'
                      ? 'border-primary-500 bg-primary-50 text-primary-700'
                      : 'border-slate-200 text-slate-600 hover:border-slate-300'
                  }`}
                >
                  开启
                </button>
                <button
                  onClick={() => handleConfigChange(config.key, 'false')}
                  className={`flex-1 py-3 px-4 rounded-lg border-2 transition-colors ${
                    editedConfigs[config.key] === 'false'
                      ? 'border-red-500 bg-red-50 text-red-700'
                      : 'border-slate-200 text-slate-600 hover:border-slate-300'
                  }`}
                >
                  关闭
                </button>
              </div>
            ) : getConfigType(config.key) === 'number' ? (
              <input
                type="number"
                value={editedConfigs[config.key] || ''}
                onChange={(e) => handleConfigChange(config.key, e.target.value)}
                className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="请输入数值"
              />
            ) : getConfigType(config.key) === 'textarea' ? (
              <textarea
                value={editedConfigs[config.key] || ''}
                onChange={(e) => handleConfigChange(config.key, e.target.value)}
                rows={4}
                className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                placeholder="请输入内容"
              />
            ) : (
              <input
                type="text"
                value={editedConfigs[config.key] || ''}
                onChange={(e) => handleConfigChange(config.key, e.target.value)}
                className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="请输入内容"
              />
            )}
          </motion.div>
        ))}

        {configs.length === 0 && (
          <div className="bg-white rounded-xl p-12 shadow-sm border border-slate-100 text-center">
            <Settings className="w-16 h-16 text-slate-300 mx-auto mb-4" />
            <p className="text-slate-500">暂无系统配置</p>
          </div>
        )}
      </div>
    </div>
  );
}
