import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Cpu,
  Plus,
  Edit2,
  Trash2,
  X,
  Save,
  Loader2,
  AlertCircle,
  RefreshCw,
  Zap,
  Check,
  ChevronDown,
  Eye,
} from 'lucide-react';
import { aiModelApi } from '../../api/aiModel';
import type {
  ActiveRoles,
  AiModelConfig,
  AiModelConfigRequest,
  AiModelConfigType,
  ProbeModelInfo,
} from '../../types/aiModel';
import ConfirmDialog from '../../components/ConfirmDialog';

/** 供应商徽标首字母（minimax→M，glm→智，custom→自） */
const providerBadge = (provider: string): string => {
  switch (provider) {
    case 'minimax':
      return 'M';
    case 'glm':
      return '智';
    case 'custom':
      return '自';
    default:
      return provider ? provider.charAt(0).toUpperCase() : '?';
  }
};

/** 角色中文标签 */
const roleLabel = (r: AiModelConfigType): string => (r === 'CHAT' ? '主模型' : '小模型');

interface FormData {
  provider: string;
  displayName: string;
  baseUrl: string;
  /** baseUrl 输入模式：true=完整 URL 原样入库；false=域名根（后端补 /v1） */
  useFullUrl: boolean;
  apiKey: string;
  modelName: string;
  temperature: number;
  /** 是否支持视觉（图片输入）：勾选后简历 PDF 解析可调用该模型识图 */
  supportsVision: boolean;
  /** 是否视觉优先（仅 supportsVision=true 时可选）：一律先视觉识别，失败回退文本 */
  visionPriority: boolean;
}

const emptyForm: FormData = {
  provider: 'minimax',
  displayName: '',
  baseUrl: '',
  useFullUrl: false,
  apiKey: '',
  modelName: '',
  temperature: 0.2,
  supportsVision: false,
  visionPriority: false,
};

/** 指派确认弹窗的目标（config + 角色） */
interface AssignTarget {
  config: AiModelConfig;
  role: AiModelConfigType;
}

/**
 * AI 模型配置页面（角色指派模型）
 * <p>凭证与角色解耦：创建凭证不再选类型；通过「启用为主模型 / 启用为小模型」把凭证指派到角色槽位。
 * 一条凭证可同时占两个槽（主和小都用同一模型）。小模型槽位为空 = 禁用，运行时退化使用主模型。
 * API Key 全场景固定显示 ******（后端查询不返回 key）。
 */
export default function AiModelConfigPage() {
  const [configs, setConfigs] = useState<AiModelConfig[]>([]);
  const [activeRoles, setActiveRoles] = useState<ActiveRoles>({ CHAT: null, SMALL_CHAT: null });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 表单
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<AiModelConfig | null>(null);
  const [formData, setFormData] = useState<FormData>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // combobox 拉取
  const [probeModels, setProbeModels] = useState<string[]>([]);
  const [probing, setProbing] = useState(false);
  const [probeMsg, setProbeMsg] = useState<{ text: string; ok: boolean } | null>(null);
  // 模型下拉显隐：与 probeModels 解耦——拉取成功只缓存模型 + 显示 icon，由用户点 icon 主动展开
  const [probeDropdownOpen, setProbeDropdownOpen] = useState(false);

  // 拉取模型下拉框：点击外部仅关闭下拉（不清空 probeModels，icon 保留可反复点开）
  const probeDropdownRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!probeDropdownOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (probeDropdownRef.current && !probeDropdownRef.current.contains(e.target as Node)) {
        setProbeDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [probeDropdownOpen]);

  // Modal 内测试连接
  const [testing, setTesting] = useState(false);
  const [testMsg, setTestMsg] = useState<{ text: string; ok: boolean } | null>(null);

  // 列表行测试
  const [rowTestingId, setRowTestingId] = useState<number | null>(null);

  // 指派确认弹窗
  const [assignTarget, setAssignTarget] = useState<AssignTarget | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [disabling, setDisabling] = useState(false);

  // 删除确认弹窗
  const [deleteTarget, setDeleteTarget] = useState<AiModelConfig | null>(null);
  const [deleting, setDeleting] = useState(false);

  // 全局 toast
  const [toast, setToast] = useState<{ text: string; ok: boolean } | null>(null);

  const showToast = useCallback((text: string, ok = true) => {
    setToast({ text, ok });
    setTimeout(() => setToast(null), 3000);
  }, []);

  const loadConfigs = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await aiModelApi.list();
      setConfigs(data.configs);
      setActiveRoles(data.activeRoles);
    } catch (err) {
      console.error('加载 AI 模型配置失败:', err);
      setError('加载 AI 模型配置失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConfigs();
  }, [loadConfigs]);

  // ========== 派生数据 ==========

  /** 某 config 当前被哪些角色引用 */
  const rolesOf = useCallback(
    (config: AiModelConfig): AiModelConfigType[] => {
      const roles: AiModelConfigType[] = [];
      if (activeRoles.CHAT === config.id) roles.push('CHAT');
      if (activeRoles.SMALL_CHAT === config.id) roles.push('SMALL_CHAT');
      return roles;
    },
    [activeRoles],
  );

  // ========== 表单 ==========

  const handleOpenCreate = () => {
    setEditing(null);
    setFormData({ ...emptyForm });
    setFormError(null);
    setProbeModels([]);
    setProbeDropdownOpen(false);
    setProbeMsg(null);
    setTestMsg(null);
    setShowForm(true);
  };

  const handleOpenEdit = (config: AiModelConfig) => {
    setEditing(config);
    setFormData({
      provider: config.provider,
      displayName: config.displayName,
      baseUrl: config.baseUrl,
      useFullUrl: false, // 入库都是完整 URL，无法反推用户当初输入模式，统一默认 false（改 baseUrl 时重新选择）
      apiKey: '', // 编辑态不回填 key（后端不返回），留空=不修改
      modelName: config.modelName,
      temperature: config.temperature,
      supportsVision: config.supportsVision ?? false,
      visionPriority: config.visionPriority ?? false,
    });
    setFormError(null);
    setProbeModels([]);
    setProbeDropdownOpen(false);
    setProbeMsg(null);
    setTestMsg(null);
    setShowForm(true);
  };

  const handleCloseForm = () => {
    setShowForm(false);
    setEditing(null);
    setFormError(null);
    setProbeModels([]);
    setProbeDropdownOpen(false);
    setProbeMsg(null);
    setTestMsg(null);
  };

  // combobox 拉取模型列表
  const handleProbe = async () => {
    // 判断是否用表单值验证：
    //   新建态 → 用表单值（baseUrl + apiKey 都必须填）
    //   编辑态改了 baseUrl 或填了新 apiKey → 用表单值（含新 URL/key）
    //   编辑态都没改 → 用已存 id
    // 关键：编辑态改了 baseUrl 但没填新 key → 必须提示填 key
    //       （后端不返回旧 key，前端拿不到旧 key 配新 URL 验证）
    const baseUrlChanged = !!editing && formData.baseUrl.trim() !== editing.baseUrl;
    const hasNewKey = formData.apiKey.trim() !== '';
    const useFormValues = !editing || baseUrlChanged || hasNewKey;
    if (useFormValues) {
      if (!formData.baseUrl.trim()) {
        setProbeMsg({ text: '请先填写 Base URL', ok: false });
        return;
      }
      if (!hasNewKey) {
        const hint = editing
          ? '改了 Base URL 需要填写新的 API Key 才能验证新地址'
          : '请先填写 Base URL 和 API Key 再拉取';
        setProbeMsg({ text: hint, ok: false });
        return;
      }
    }
    setProbing(true);
    setProbeMsg(null);
    setProbeModels([]);
    try {
      const res = useFormValues
        ? await aiModelApi.probe({
            baseUrl: formData.baseUrl.trim(),
            apiKey: formData.apiKey.trim(),
          })
        : await aiModelApi.probe({ id: editing!.id });
      if (res.ok) {
        const ids = res.models.map((m: ProbeModelInfo) => m.id).filter(Boolean);
        setProbeModels(ids);
        setProbeDropdownOpen(false); // 拉取成功不自动弹下拉，仅显示 icon + toast
        setProbeMsg(null);
        showToast(`拉取到 ${ids.length} 个模型`);
      } else {
        setProbeMsg({ text: res.message || '拉取失败', ok: false });
      }
    } catch (e) {
      setProbeMsg({ text: e instanceof Error ? e.message : '拉取失败', ok: false });
    } finally {
      setProbing(false);
    }
  };

  // Modal 内测试连接
  const handleTestConnection = async () => {
    // 与拉取同理：新建态或编辑态改了 baseUrl/apiKey → 用表单值测试；编辑态都没改 → 用已存 id。
    const baseUrlChanged = !!editing && formData.baseUrl.trim() !== editing.baseUrl;
    const hasNewKey = formData.apiKey.trim() !== '';
    const useFormValues = !editing || baseUrlChanged || hasNewKey;
    if (useFormValues) {
      if (!formData.baseUrl.trim() || !formData.modelName.trim()) {
        setTestMsg({ text: '请先填写 Base URL 和 模型名', ok: false });
        setTesting(false);
        return;
      }
      if (!hasNewKey) {
        const hint = editing
          ? '改了 Base URL 需要填写新的 API Key 才能验证新地址'
          : '请先填写 Base URL / API Key / 模型名';
        setTestMsg({ text: hint, ok: false });
        setTesting(false);
        return;
      }
    }
    setTesting(true);
    setTestMsg(null);
    try {
      let res;
      res = useFormValues
        ? await aiModelApi.test({
            baseUrl: formData.baseUrl.trim(),
            apiKey: formData.apiKey.trim(),
            modelName: formData.modelName.trim(),
          })
        : await aiModelApi.test({ id: editing!.id });
      setTestMsg({
        text: res.ok ? `连接成功（${res.latencyMs ?? '-'}ms）` : res.message || '连接失败',
        ok: res.ok,
      });
    } catch (e) {
      setTestMsg({ text: e instanceof Error ? e.message : '连接失败', ok: false });
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async () => {
    if (!formData.provider.trim()) {
      setFormError('请选择供应商');
      return;
    }
    if (!formData.displayName.trim()) {
      setFormError('请输入显示名称');
      return;
    }
    if (!formData.baseUrl.trim()) {
      setFormError('请输入 Base URL');
      return;
    }
    if (!formData.modelName.trim()) {
      setFormError('请输入模型名');
      return;
    }
    if (!editing && !formData.apiKey.trim()) {
      setFormError('新建配置必须填写 API Key');
      return;
    }

    try {
      setSaving(true);
      setFormError(null);
      const payload: AiModelConfigRequest = {
        provider: formData.provider,
        displayName: formData.displayName.trim(),
        baseUrl: formData.baseUrl.trim(),
        useFullUrl: formData.useFullUrl,
        apiKey: formData.apiKey, // 编辑态空字符串 → 后端不修改
        modelName: formData.modelName.trim(),
        temperature: formData.temperature,
        supportsVision: formData.supportsVision,
        visionPriority: formData.supportsVision && formData.visionPriority, // 不支持视觉时强制 false
      };
      if (editing) {
        await aiModelApi.update(editing.id, payload);
        showToast('配置已更新，新请求将使用新模型');
      } else {
        await aiModelApi.create(payload);
        showToast('配置已创建，请在列表中指派角色启用');
      }
      await loadConfigs();
      handleCloseForm();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  // 列表行测试
  const handleRowTest = async (config: AiModelConfig) => {
    setRowTestingId(config.id);
    try {
      const res = await aiModelApi.test({ id: config.id });
      showToast(
        res.ok ? `「${config.modelName}」连接成功（${res.latencyMs ?? '-'}ms）` : res.message || '连接失败',
        res.ok,
      );
      await loadConfigs();
    } catch (e) {
      showToast(e instanceof Error ? e.message : '连接失败', false);
    } finally {
      setRowTestingId(null);
    }
  };

  // 指派角色确认
  const confirmAssign = async () => {
    if (!assignTarget) return;
    try {
      setAssigning(true);
      await aiModelApi.assignRole(assignTarget.role, assignTarget.config.id);
      showToast(`已启用为${roleLabel(assignTarget.role)}，新请求立即生效`);
      await loadConfigs();
      setAssignTarget(null);
    } catch (e) {
      showToast(e instanceof Error ? e.message : '切换失败', false);
    } finally {
      setAssigning(false);
    }
  };

  // 禁用小模型（直接执行，退化主模型是安全降级）
  const handleDisableSmall = async () => {
    try {
      setDisabling(true);
      await aiModelApi.disableRole('SMALL_CHAT');
      showToast('已禁用小模型，将退化使用主模型');
      await loadConfigs();
    } catch (e) {
      showToast(e instanceof Error ? e.message : '禁用失败', false);
    } finally {
      setDisabling(false);
    }
  };

  // 删除确认
  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try {
      setDeleting(true);
      await aiModelApi.remove(deleteTarget.id);
      showToast('配置已删除');
      await loadConfigs();
      setDeleteTarget(null);
    } catch (e) {
      showToast(e instanceof Error ? e.message : '删除失败', false);
    } finally {
      setDeleting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  // ========== 渲染辅助 ==========

  /** 「当前启用」双卡：结构完全一致，根据槽位是否有指派显示运行中徽章 */
  const renderActiveCard = (label: string, role: AiModelConfigType) => {
    const configId = role === 'CHAT' ? activeRoles.CHAT : activeRoles.SMALL_CHAT;
    const config = configId ? configs.find((c) => c.id === configId) : undefined;
    const assigned = !!configId;
    return (
      <div className="flex-1 bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
        <div className="flex items-center justify-between mb-4">
          <span className="text-sm font-medium text-slate-500">{label}</span>
          {assigned && (
            <span className="flex items-center gap-1 text-xs text-green-600 bg-green-50 px-2 py-1 rounded-full">
              <span className="w-1.5 h-1.5 bg-green-500 rounded-full" />
              运行中
            </span>
          )}
        </div>
        {config ? (
          <div>
            <div className="flex items-center gap-3 mb-3">
              <div className="w-10 h-10 bg-slate-100 rounded-lg flex items-center justify-center font-mono font-bold text-slate-600">
                {providerBadge(config.provider)}
              </div>
              <div className="min-w-0">
                <div className="font-semibold text-slate-900 truncate">{config.displayName}</div>
                <div className="text-sm text-slate-500 font-mono truncate">{config.modelName}</div>
              </div>
            </div>
            <div className="text-xs text-slate-400 font-mono truncate mb-2">{config.baseUrl}</div>
            <div className="flex items-center gap-3 text-xs text-slate-500">
              <span>temp {config.temperature}</span>
              {role === 'SMALL_CHAT' && (
                <button
                  onClick={handleDisableSmall}
                  disabled={disabling}
                  className="text-red-500 hover:underline disabled:opacity-50"
                >
                  {disabling ? '禁用中...' : '禁用（退化主模型）'}
                </button>
              )}
            </div>
          </div>
        ) : (
          <div>
            {role === 'CHAT' ? (
              <>
                <div className="text-slate-400 text-sm mb-3">未指派主模型</div>
                <button
                  onClick={handleOpenCreate}
                  className="w-full py-8 border-2 border-dashed border-slate-200 rounded-xl text-slate-400 hover:border-primary-300 hover:text-primary-500 transition-colors text-sm"
                >
                  未配置，点击新建凭证
                </button>
              </>
            ) : (
              <div className="py-6 text-center">
                <div className="text-slate-400 text-sm mb-1">已禁用</div>
                <div className="text-xs text-slate-400">小模型将退化使用主模型</div>
              </div>
            )}
          </div>
        )}
      </div>
    );
  };

  return (
    <div>
      {/* toast */}
      <AnimatePresence>
        {toast && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className={`fixed top-6 left-1/2 -translate-x-1/2 z-[60] px-5 py-3 rounded-xl shadow-lg flex items-center gap-2 text-sm font-medium ${
              toast.ok ? 'bg-green-500 text-white' : 'bg-red-500 text-white'
            }`}
          >
            {toast.ok ? <Check className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
            {toast.text}
          </motion.div>
        )}
      </AnimatePresence>

      {/* 标题区 */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center">
              <Cpu className="w-5 h-5 text-white" />
            </div>
            AI 模型配置
          </h1>
          <p className="text-slate-500 mt-1">管理面试大模型凭证，指派主/小模型角色，改完热生效</p>
        </div>
        <motion.button
          onClick={handleOpenCreate}
          className="px-5 py-2.5 bg-primary-500 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30 hover:bg-primary-600 transition-colors flex items-center gap-2"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          <Plus className="w-5 h-5" />
          新建凭证
        </motion.button>
      </div>

      {/* 错误提示 */}
      {error && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-600 flex items-center gap-2"
        >
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          {error}
        </motion.div>
      )}

      {/* 当前启用（双卡结构一致） */}
      <div className="mb-8">
        <h2 className="text-sm font-semibold text-slate-500 mb-3">当前启用</h2>
        <div className="flex gap-6">
          {renderActiveCard('主对话模型', 'CHAT')}
          {renderActiveCard('小模型 · Reranker', 'SMALL_CHAT')}
        </div>
      </div>

      {/* 全部配置 */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold text-slate-500">全部凭证</h2>
          <span className="text-xs text-slate-400">
            点击「启用为主模型 / 启用为小模型」把凭证指派到角色槽位，一条凭证可同时占两个槽
          </span>
        </div>

        <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200">
                  <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">配置</th>
                  <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">模型</th>
                  <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">角色指派</th>
                  <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">API Key</th>
                  <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">最近测试</th>
                  <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">操作</th>
                </tr>
              </thead>
              <tbody>
                {configs.map((config, index) => {
                  const isChat = activeRoles.CHAT === config.id;
                  const isSmall = activeRoles.SMALL_CHAT === config.id;
                  const roles = rolesOf(config);
                  return (
                    <motion.tr
                      key={config.id}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: index * 0.05 }}
                      className="border-b border-slate-100 hover:bg-slate-50 transition-colors"
                    >
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 bg-slate-100 rounded-lg flex items-center justify-center font-mono font-bold text-slate-600 flex-shrink-0">
                            {providerBadge(config.provider)}
                          </div>
                          <div className="min-w-0">
                            <div className="font-medium text-slate-900">{config.displayName}</div>
                            <div className="text-xs text-slate-400 font-mono truncate max-w-[200px]">
                              {config.baseUrl}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <code className="text-sm bg-slate-100 px-2 py-1 rounded text-slate-600">
                            {config.modelName}
                          </code>
                          {config.supportsVision && (
                            <span
                              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${
                                config.visionPriority
                                  ? 'bg-amber-100 text-amber-700'
                                  : 'bg-emerald-100 text-emerald-700'
                              }`}
                              title={
                                config.visionPriority
                                  ? '支持视觉识别，且已设为视觉优先（简历 PDF 一律先识图）'
                                  : '支持视觉识别（简历文本解析失败/过少时兜底识图）'
                              }
                            >
                              <Eye className="w-3 h-3" />
                              {config.visionPriority ? '视觉·优先' : '视觉'}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1 flex-wrap">
                          {roles.length === 0 && <span className="text-xs text-slate-400">未指派</span>}
                          {roles.map((r) => (
                            <span
                              key={r}
                              className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                                r === 'CHAT' ? 'bg-sky-100 text-sky-700' : 'bg-purple-100 text-purple-700'
                              }`}
                            >
                              {roleLabel(r)}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <code className="text-sm text-slate-400 font-mono">******</code>
                      </td>
                      <td className="px-6 py-4">
                        {config.lastTestAt ? (
                          <span
                            className={`inline-flex items-center gap-1 text-xs ${
                              config.lastTestOk ? 'text-green-600' : 'text-red-500'
                            }`}
                          >
                            {config.lastTestOk ? (
                              <Check className="w-3.5 h-3.5" />
                            ) : (
                              <AlertCircle className="w-3.5 h-3.5" />
                            )}
                            {new Date(config.lastTestAt).toLocaleDateString()}
                          </span>
                        ) : (
                          <span className="text-xs text-slate-400">未测试</span>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <button
                            onClick={() => setAssignTarget({ config, role: 'CHAT' })}
                            disabled={isChat}
                            className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-colors ${
                              isChat
                                ? 'bg-sky-100 text-sky-600 cursor-default'
                                : 'bg-slate-100 text-slate-600 hover:bg-sky-50 hover:text-sky-600'
                            }`}
                          >
                            {isChat ? '✓ 主模型' : '启用为主模型'}
                          </button>
                          <button
                            onClick={() => setAssignTarget({ config, role: 'SMALL_CHAT' })}
                            disabled={isSmall}
                            className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-colors ${
                              isSmall
                                ? 'bg-purple-100 text-purple-600 cursor-default'
                                : 'bg-slate-100 text-slate-600 hover:bg-purple-50 hover:text-purple-600'
                            }`}
                          >
                            {isSmall ? '✓ 小模型' : '启用为小模型'}
                          </button>
                          {isSmall && (
                            <button
                              onClick={handleDisableSmall}
                              disabled={disabling}
                              className="px-2.5 py-1 rounded-lg text-xs font-medium text-red-500 hover:bg-red-50 disabled:opacity-50"
                            >
                              禁用
                            </button>
                          )}
                          <motion.button
                            onClick={() => handleRowTest(config)}
                            disabled={rowTestingId === config.id}
                            title="测试连接"
                            className="p-2 text-slate-400 hover:text-primary-500 hover:bg-primary-50 rounded-lg transition-colors disabled:opacity-50"
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.9 }}
                          >
                            {rowTestingId === config.id ? (
                              <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                              <Zap className="w-4 h-4" />
                            )}
                          </motion.button>
                          <motion.button
                            onClick={() => handleOpenEdit(config)}
                            title="编辑"
                            className="p-2 text-slate-400 hover:text-primary-500 hover:bg-primary-50 rounded-lg transition-colors"
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.9 }}
                          >
                            <Edit2 className="w-4 h-4" />
                          </motion.button>
                          <motion.button
                            onClick={() => setDeleteTarget(config)}
                            title="删除"
                            className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.9 }}
                          >
                            <Trash2 className="w-4 h-4" />
                          </motion.button>
                        </div>
                      </td>
                    </motion.tr>
                  );
                })}
                {configs.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-6 py-12 text-center text-slate-500">
                      暂无凭证，点击「新建凭证」添加第一个模型
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* 新建/编辑 Modal */}
      <AnimatePresence>
        {showForm && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
            onClick={(e) => {
              if (e.target === e.currentTarget) handleCloseForm();
            }}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto"
            >
              {/* 头部 */}
              <div className="flex items-center justify-between p-6 border-b border-slate-200">
                <h2 className="text-xl font-bold text-slate-900">
                  {editing ? '编辑凭证' : '新建凭证'}
                </h2>
                <button
                  onClick={handleCloseForm}
                  className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* 表单 */}
              <div className="p-6 space-y-5">
                <p className="text-xs text-slate-400">
                  凭证不绑角色，保存后在列表中通过「启用为主模型 / 启用为小模型」指派，热生效。
                </p>

                {/* 供应商 */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    供应商 <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={formData.provider}
                    onChange={(e) => setFormData({ ...formData, provider: e.target.value })}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 bg-white focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                    <option value="minimax">MiniMax</option>
                    <option value="glm">智谱 GLM</option>
                    <option value="custom">自定义</option>
                  </select>
                </div>

                {/* 显示名称 */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    显示名称 <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.displayName}
                    onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                    placeholder="如：主对话模型"
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  />
                </div>

                {/* Base URL（带「完整 URL」输入模式开关） */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="block text-sm font-semibold text-slate-700">
                      Base URL <span className="text-red-500">*</span>
                    </label>
                    <label className="flex items-center gap-2 text-xs text-slate-500 select-none">
                      <span>完整 URL</span>
                      <button
                        type="button"
                        role="switch"
                        aria-checked={formData.useFullUrl}
                        onClick={() => setFormData({ ...formData, useFullUrl: !formData.useFullUrl })}
                        title="开启=填完整 URL（如含 /v4 的智谱地址）原样入库；关闭=填域名根，后端补 /v1"
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                          formData.useFullUrl ? 'bg-primary-500' : 'bg-slate-300'
                        }`}
                      >
                        <span
                          className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                            formData.useFullUrl ? 'translate-x-4' : 'translate-x-0.5'
                          }`}
                        />
                      </button>
                    </label>
                  </div>
                  <input
                    type="text"
                    value={formData.baseUrl}
                    onChange={(e) => setFormData({ ...formData, baseUrl: e.target.value })}
                    placeholder={
                      formData.useFullUrl
                        ? 'https://open.bigmodel.cn/api/paas/v4'
                        : 'https://api.minimaxi.com/'
                    }
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  />
                  <p className="text-xs text-slate-400 mt-1">
                    {formData.useFullUrl
                      ? '完整 URL 模式：原样入库（含版本前缀，如智谱 /v4）'
                      : '默认模式：填域名根，保存时自动补 /v1（已含 /v1 /v4 则不补）'}
                  </p>
                </div>

                {/* API Key */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    API Key {!editing && <span className="text-red-500">*</span>}
                  </label>
                  <input
                    type="password"
                    value={formData.apiKey}
                    onChange={(e) => setFormData({ ...formData, apiKey: e.target.value })}
                    placeholder={editing ? '留空则不修改' : '输入 API Key（明文存储）'}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  />
                  <p className="text-xs text-slate-400 mt-1">
                    {editing ? '已存储的密钥不回显；填写新值则覆盖，留空保持不变' : '将明文存储于数据库，查询接口不返回'}
                  </p>
                </div>

                {/* 模型名 combobox */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    模型名 <span className="text-red-500">*</span>
                  </label>
                  <div className="relative" ref={probeDropdownRef}>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        value={formData.modelName}
                        onChange={(e) => {
                          setFormData({ ...formData, modelName: e.target.value });
                          setProbeModels([]); // 手改视为缓存过期，icon 隐藏
                          setProbeDropdownOpen(false);
                        }}
                        placeholder="如 MiniMax-M2.7，可手输或点右侧拉取"
                        className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                      />
                      {/* 拉取成功后显示的下拉触发 icon：用已缓存的 probeModels 反复展开，不重新调后端 */}
                      {probeModels.length > 0 && (
                        <button
                          type="button"
                          onClick={() => setProbeDropdownOpen((o) => !o)}
                          title={probeDropdownOpen ? '收起模型列表' : '展开模型列表'}
                          className="px-3 border border-slate-200 rounded-xl text-slate-500 hover:text-primary-500 hover:bg-primary-50 transition-colors flex items-center justify-center"
                        >
                          <ChevronDown
                            className={`w-4 h-4 transition-transform ${probeDropdownOpen ? 'rotate-180' : ''}`}
                          />
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={handleProbe}
                        disabled={probing}
                        title="拉取可用模型列表"
                        className="px-3 border border-slate-200 rounded-xl text-slate-500 hover:text-primary-500 hover:bg-primary-50 transition-colors disabled:opacity-50 flex items-center justify-center"
                      >
                        {probing ? (
                          <Loader2 className="w-4 h-4 animate-spin" />
                        ) : (
                          <RefreshCw className="w-4 h-4" />
                        )}
                      </button>
                    </div>
                    {probeMsg && (
                      <p className={`text-xs mt-1 ${probeMsg.ok ? 'text-green-600' : 'text-red-500'}`}>
                        {probeMsg.text}
                      </p>
                    )}
                    {probeDropdownOpen && probeModels.length > 0 && (
                      <div className="absolute z-20 mt-1 w-full bg-white border border-slate-200 rounded-xl shadow-lg max-h-48 overflow-y-auto">
                        {probeModels.map((m) => (
                          <button
                            key={m}
                            type="button"
                            onClick={() => {
                              setFormData({ ...formData, modelName: m });
                              setProbeDropdownOpen(false); // 选中只关下拉，不清缓存，icon 保留
                            }}
                            className="block w-full text-left px-4 py-2 hover:bg-primary-50 text-sm font-mono text-slate-700 transition-colors"
                          >
                            {m}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </div>

                {/* Temperature */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    Temperature：{formData.temperature.toFixed(1)}
                  </label>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.1"
                    value={formData.temperature}
                    onChange={(e) =>
                      setFormData({ ...formData, temperature: parseFloat(e.target.value) })
                    }
                    className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-primary-500"
                  />
                  <div className="flex justify-between text-xs text-slate-400 mt-1">
                    <span>0.0</span>
                    <span>0.5</span>
                    <span>1.0</span>
                  </div>
                </div>

                {/* 能力配置：支持视觉 + 视觉优先 */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="block text-sm font-semibold text-slate-700">支持视觉</label>
                    <label className="flex items-center gap-2 text-xs text-slate-500 select-none">
                      <span>图片输入</span>
                      <button
                        type="button"
                        role="switch"
                        aria-checked={formData.supportsVision}
                        onClick={() =>
                          setFormData((prev) => ({
                            ...prev,
                            supportsVision: !prev.supportsVision,
                            visionPriority: false, // 关闭能力时联动重置视觉优先
                          }))
                        }
                        title="勾选后简历 PDF 解析可调用该模型识图（扫描件/复杂排版兜底）；请确保模型真实支持图片输入"
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                          formData.supportsVision ? 'bg-primary-500' : 'bg-slate-300'
                        }`}
                      >
                        <span
                          className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                            formData.supportsVision ? 'translate-x-4' : 'translate-x-0.5'
                          }`}
                        />
                      </button>
                    </label>
                  </div>
                  <p className="text-xs text-slate-400">
                    勾选后简历 PDF 解析可调用该模型识图（解决扫描件、复杂排版）；请确保模型真实支持图片输入（如
                    MiniMax-M3 / GLM-VL 系列，MiniMax-M2.x 不支持）
                  </p>
                </div>

                {/* 视觉优先：仅支持视觉时显示 */}
                {formData.supportsVision && (
                  <div className="rounded-xl bg-slate-50 border border-slate-100 p-4">
                    <div className="flex items-center justify-between mb-1">
                      <label className="block text-sm font-semibold text-slate-700">视觉优先</label>
                      <label className="flex items-center gap-2 text-xs text-slate-500 select-none">
                        <span>简历解析策略</span>
                        <button
                          type="button"
                          role="switch"
                          aria-checked={formData.visionPriority}
                          onClick={() =>
                            setFormData({ ...formData, visionPriority: !formData.visionPriority })
                          }
                          title="开=简历 PDF 一律先视觉识别（失败回退文本）；关=文本解析失败/过少时才兜底视觉"
                          className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                            formData.visionPriority ? 'bg-primary-500' : 'bg-slate-300'
                          }`}
                        >
                          <span
                            className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                              formData.visionPriority ? 'translate-x-4' : 'translate-x-0.5'
                            }`}
                          />
                        </button>
                      </label>
                    </div>
                    <p className="text-xs text-slate-400">
                      开：简历 PDF 一律先视觉识别，扫描件与乱序排版都治，但每份简历都消耗视觉
                      token；关：仅文本解析失败/过少时才走视觉兜底
                    </p>
                  </div>
                )}

                {/* 测试连接结果 */}
                {testMsg && (
                  <motion.div
                    initial={{ opacity: 0, y: -10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className={`p-3 rounded-xl text-sm flex items-center gap-2 ${
                      testMsg.ok
                        ? 'bg-green-50 border border-green-200 text-green-600'
                        : 'bg-red-50 border border-red-200 text-red-600'
                    }`}
                  >
                    {testMsg.ok ? <Check className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
                    {testMsg.text}
                  </motion.div>
                )}

                {/* 表单错误 */}
                {formError && (
                  <motion.div
                    initial={{ opacity: 0, y: -10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="p-3 bg-red-50 border border-red-200 rounded-xl text-red-600 text-sm flex items-center gap-2"
                  >
                    <AlertCircle className="w-4 h-4 flex-shrink-0" />
                    {formError}
                  </motion.div>
                )}
              </div>

              {/* 底部 */}
              <div className="flex items-center justify-between gap-3 p-6 border-t border-slate-200">
                <motion.button
                  onClick={handleTestConnection}
                  disabled={testing}
                  className="px-4 py-2.5 border border-primary-200 text-primary-600 rounded-xl font-medium hover:bg-primary-50 transition-colors disabled:opacity-60 flex items-center gap-2"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  {testing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Zap className="w-4 h-4" />}
                  测试连接
                </motion.button>
                <div className="flex items-center gap-3">
                  <motion.button
                    onClick={handleCloseForm}
                    className="px-5 py-2.5 border border-slate-200 text-slate-600 font-medium hover:bg-slate-50 transition-colors"
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    取消
                  </motion.button>
                  <motion.button
                    onClick={handleSave}
                    disabled={saving}
                    className="px-5 py-2.5 bg-primary-500 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30 hover:bg-primary-600 transition-colors disabled:opacity-60 flex items-center gap-2"
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    {saving ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        保存中...
                      </>
                    ) : (
                      <>
                        <Save className="w-4 h-4" />
                        保存
                      </>
                    )}
                  </motion.button>
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 指派角色确认 */}
      <ConfirmDialog
        open={!!assignTarget}
        title={`启用为${assignTarget ? roleLabel(assignTarget.role) : ''}`}
        message={
          assignTarget
            ? `将「${assignTarget.config.displayName}」启用为${roleLabel(assignTarget.role)}？切换后立即热生效，新请求将使用该模型。`
            : ''
        }
        confirmText="确认启用"
        cancelText="取消"
        confirmVariant="primary"
        loading={assigning}
        onConfirm={confirmAssign}
        onCancel={() => setAssignTarget(null)}
      />

      {/* 删除确认 */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="删除凭证"
        message={
          deleteTarget
            ? `确定要删除「${deleteTarget.displayName}」吗？若该凭证正被某角色引用，将无法删除（需先取消指派）。删除后不可恢复。`
            : ''
        }
        confirmText="删除"
        cancelText="取消"
        confirmVariant="danger"
        loading={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
