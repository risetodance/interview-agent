import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  UserCog,
  Plus,
  Edit2,
  Trash2,
  X,
  Save,
  Loader2,
  Star,
  StarOff,
  GripVertical,
  AlertCircle,
} from 'lucide-react';
import { interviewerRoleApi } from '../../api/interviewerRole';
import type { InterviewerRole, CreateRoleRequest } from '../../types/interviewerRole';
import ConfirmDialog from '../../components/ConfirmDialog';

/**
 * 面试官角色管理页面
 */
export default function InterviewerRoleManagementPage() {
  const [roles, setRoles] = useState<InterviewerRole[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 创建/编辑表单状态
  const [showForm, setShowForm] = useState(false);
  const [editingRole, setEditingRole] = useState<InterviewerRole | null>(null);
  const [formData, setFormData] = useState<CreateRoleRequest>({
    roleName: '',
    roleCode: '',
    description: '',
    scoringPrompt: '',
    questionPrompt: '',
    weight: 0.3,
    icon: '',
    sortOrder: 0,
    status: true,
    defaultTemplate: false,
  });
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // 删除确认
  const [deleteTarget, setDeleteTarget] = useState<InterviewerRole | null>(null);
  const [deleting, setDeleting] = useState(false);

  // 权重调整
  const [updatingWeight, setUpdatingWeight] = useState<number | null>(null);

  // 加载角色列表
  const loadRoles = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await interviewerRoleApi.getRoles();
      // 按 sortOrder 排序
      data.sort((a, b) => a.sortOrder - b.sortOrder);
      setRoles(data);
    } catch (err) {
      console.error('加载角色列表失败:', err);
      setError('加载角色列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRoles();
  }, [loadRoles]);

  // 打开创建表单
  const handleOpenCreate = () => {
    setEditingRole(null);
    setFormData({
      roleName: '',
      roleCode: '',
      description: '',
      scoringPrompt: '',
      questionPrompt: '',
      weight: 0.3,
      icon: '',
      sortOrder: roles.length,
      status: true,
      defaultTemplate: false,
    });
    setFormError(null);
    setShowForm(true);
  };

  // 打开编辑表单
  const handleOpenEdit = (role: InterviewerRole) => {
    setEditingRole(role);
    setFormData({
      roleName: role.roleName,
      roleCode: role.roleCode,
      description: role.description || '',
      scoringPrompt: role.scoringPrompt,
      questionPrompt: role.questionPrompt || '',
      weight: role.weight,
      icon: role.icon || '',
      sortOrder: role.sortOrder,
      status: role.status,
      defaultTemplate: role.defaultTemplate,
    });
    setFormError(null);
    setShowForm(true);
  };

  // 关闭表单
  const handleCloseForm = () => {
    setShowForm(false);
    setEditingRole(null);
    setFormError(null);
  };

  // 保存角色
  const handleSave = async () => {
    // 验证
    if (!formData.roleName.trim()) {
      setFormError('请输入角色名称');
      return;
    }
    if (!formData.roleCode.trim()) {
      setFormError('请输入角色编码');
      return;
    }
    if (!formData.scoringPrompt.trim()) {
      setFormError('请输入评分 Prompt');
      return;
    }
    if (!formData.questionPrompt.trim()) {
      setFormError('请输入出题 Prompt');
      return;
    }
    if (formData.weight !== undefined && (formData.weight < 0 || formData.weight > 1)) {
      setFormError('权重需在 0-1 之间');
      return;
    }

    try {
      setSaving(true);
      setFormError(null);
      if (editingRole) {
        await interviewerRoleApi.updateRole(editingRole.id, formData);
      } else {
        await interviewerRoleApi.createRole(formData);
      }
      await loadRoles();
      handleCloseForm();
    } catch (err) {
      console.error('保存角色失败:', err);
      setFormError('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  // 删除角色
  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      setDeleting(true);
      await interviewerRoleApi.deleteRole(deleteTarget.id);
      await loadRoles();
      setDeleteTarget(null);
    } catch (err) {
      console.error('删除角色失败:', err);
    } finally {
      setDeleting(false);
    }
  };

  // 切换启用/禁用
  const handleToggleStatus = async (role: InterviewerRole) => {
    try {
      await interviewerRoleApi.updateRole(role.id, { status: !role.status });
      await loadRoles();
    } catch (err) {
      console.error('更新状态失败:', err);
    }
  };

  // 快速更新权重
  const handleWeightChange = async (role: InterviewerRole, newWeight: number) => {
    if (newWeight < 0 || newWeight > 1) return;
    setUpdatingWeight(role.id);
    try {
      await interviewerRoleApi.updateWeight(role.id, { weight: newWeight });
      await loadRoles();
    } catch (err) {
      console.error('更新权重失败:', err);
    } finally {
      setUpdatingWeight(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  return (
    <div>
      {/* 页面标题 */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center">
              <UserCog className="w-5 h-5 text-white" />
            </div>
            面试官角色管理
          </h1>
          <p className="text-slate-500 mt-1">配置面试官角色的出题 Prompt、评分 Prompt 和默认权重</p>
        </div>
        <motion.button
          onClick={handleOpenCreate}
          className="px-5 py-2.5 bg-primary-500 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30 hover:bg-primary-600 transition-colors flex items-center gap-2"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          <Plus className="w-5 h-5" />
          添加角色
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

      {/* 角色列表 */}
      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600 w-10">
                  <GripVertical className="w-4 h-4" />
                </th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">角色信息</th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">编码</th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">权重</th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">状态</th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">默认模板</th>
                <th className="px-6 py-4 text-left text-sm font-semibold text-slate-600">操作</th>
              </tr>
            </thead>
            <tbody>
              {roles.map((role) => (
                <tr
                  key={role.id}
                  className="border-b border-slate-100 hover:bg-slate-50 transition-colors"
                >
                  <td className="px-6 py-4 text-slate-400 cursor-move">
                    <GripVertical className="w-4 h-4" />
                  </td>
                  <td className="px-6 py-4">
                    <div>
                      <div className="font-medium text-slate-900 flex items-center gap-2">
                        {role.roleName}
                        {role.defaultTemplate && (
                          <span className="px-2 py-0.5 bg-amber-100 text-amber-700 text-xs rounded-full">
                            默认
                          </span>
                        )}
                      </div>
                      {role.description && (
                        <div className="text-sm text-slate-500 mt-0.5 line-clamp-1">
                          {role.description}
                        </div>
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <code className="text-sm bg-slate-100 px-2 py-1 rounded text-slate-600">
                      {role.roleCode}
                    </code>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.05"
                        value={role.weight}
                        onChange={(e) =>
                          handleWeightChange(role, parseFloat(e.target.value))
                        }
                        disabled={updatingWeight === role.id}
                        className="w-24 h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-primary-500 disabled:opacity-50"
                      />
                      <span className="text-sm font-medium text-slate-700 w-12 text-right">
                        {(role.weight * 100).toFixed(0)}%
                      </span>
                      {updatingWeight === role.id && (
                        <Loader2 className="w-4 h-4 text-primary-500 animate-spin" />
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <button
                      onClick={() => handleToggleStatus(role)}
                      className={`px-3 py-1 rounded-full text-sm font-medium transition-colors ${
                        role.status
                          ? 'bg-green-100 text-green-700 hover:bg-green-200'
                          : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                      }`}
                    >
                      {role.status ? '已启用' : '已禁用'}
                    </button>
                  </td>
                  <td className="px-6 py-4">
                    {role.defaultTemplate ? (
                      <Star className="w-5 h-5 text-amber-500" />
                    ) : (
                      <StarOff className="w-5 h-5 text-slate-300" />
                    )}
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-2">
                      <motion.button
                        onClick={() => handleOpenEdit(role)}
                        className="p-2 text-slate-500 hover:text-primary-500 hover:bg-primary-50 rounded-lg transition-colors"
                        whileHover={{ scale: 1.1 }}
                        whileTap={{ scale: 0.9 }}
                      >
                        <Edit2 className="w-4 h-4" />
                      </motion.button>
                      <motion.button
                        onClick={() => setDeleteTarget(role)}
                        className="p-2 text-slate-500 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                        whileHover={{ scale: 1.1 }}
                        whileTap={{ scale: 0.9 }}
                      >
                        <Trash2 className="w-4 h-4" />
                      </motion.button>
                    </div>
                  </td>
                </tr>
              ))}
              {roles.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-slate-500">
                    暂无角色，点击"添加角色"创建第一个面试官角色
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 创建/编辑表单弹窗 */}
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
              {/* 弹窗头部 */}
              <div className="flex items-center justify-between p-6 border-b border-slate-200">
                <h2 className="text-xl font-bold text-slate-900">
                  {editingRole ? '编辑角色' : '添加角色'}
                </h2>
                <button
                  onClick={handleCloseForm}
                  className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* 表单内容 */}
              <div className="p-6 space-y-5">
                {/* 角色名称 */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    角色名称 <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.roleName}
                    onChange={(e) =>
                      setFormData({ ...formData, roleName: e.target.value })
                    }
                    placeholder="如：技术面试官、HR面试官"
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  />
                </div>

                {/* 角色编码 */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    角色编码 <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.roleCode}
                    onChange={(e) =>
                      setFormData({ ...formData, roleCode: e.target.value.toUpperCase() })
                    }
                    placeholder="如：TECH_INTERVIEWER"
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 font-mono uppercase focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  />
                  <p className="text-xs text-slate-500 mt-1">建议使用大写下划线格式，如 TECH_INTERVIEWER</p>
                </div>

                {/* 角色描述 */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    角色描述
                  </label>
                  <textarea
                    value={formData.description}
                    onChange={(e) =>
                      setFormData({ ...formData, description: e.target.value })
                    }
                    placeholder="描述该角色的职责和关注点"
                    rows={2}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                  />
                </div>

                {/* 出题 Prompt */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    出题 Prompt <span className="text-red-500">*</span>
                  </label>
                  <textarea
                    value={formData.questionPrompt}
                    onChange={(e) =>
                      setFormData({ ...formData, questionPrompt: e.target.value })
                    }
                    placeholder="输入该角色的出题 Prompt，AI 以此角色身份生成面试问题..."
                    rows={6}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                  />
                  <p className="text-xs text-slate-500 mt-1">
                    Prompt 应包含：角色定义、以什么身份出题、问题风格、输出格式等
                  </p>
                </div>

                {/* 评分 Prompt */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    评分 Prompt <span className="text-red-500">*</span>
                  </label>
                  <textarea
                    value={formData.scoringPrompt}
                    onChange={(e) =>
                      setFormData({ ...formData, scoringPrompt: e.target.value })
                    }
                    placeholder="输入该角色的评分 Prompt，指导 AI 如何从该视角评分..."
                    rows={6}
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                  />
                  <p className="text-xs text-slate-500 mt-1">
                    Prompt 应包含：角色定义、关注维度、评分标准
                  </p>
                </div>

                {/* 权重滑块 */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    权重：{(formData.weight ?? 0) * 100}%
                  </label>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.05"
                    value={formData.weight ?? 0}
                    onChange={(e) =>
                      setFormData({ ...formData, weight: parseFloat(e.target.value) })
                    }
                    className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-primary-500"
                  />
                  <div className="flex justify-between text-xs text-slate-500 mt-1">
                    <span>0%</span>
                    <span>50%</span>
                    <span>100%</span>
                  </div>
                </div>

                {/* 图标 */}
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-2">
                    图标
                  </label>
                  <input
                    type="text"
                    value={formData.icon}
                    onChange={(e) =>
                      setFormData({ ...formData, icon: e.target.value })
                    }
                    placeholder="可选，如 emoji 或图标代码"
                    className="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  />
                </div>

                {/* 状态和默认模板 */}
                <div className="flex items-center gap-6">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={formData.status}
                      onChange={(e) =>
                        setFormData({ ...formData, status: e.target.checked })
                      }
                      className="w-4 h-4 rounded border-slate-300 text-primary-500 focus:ring-primary-500"
                    />
                    <span className="text-sm font-medium text-slate-700">启用该角色</span>
                  </label>
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={formData.defaultTemplate}
                      onChange={(e) =>
                        setFormData({ ...formData, defaultTemplate: e.target.checked })
                      }
                      className="w-4 h-4 rounded border-slate-300 text-primary-500 focus:ring-primary-500"
                    />
                    <span className="text-sm font-medium text-slate-700">默认模板</span>
                  </label>
                </div>

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

              {/* 弹窗底部 */}
              <div className="flex items-center justify-end gap-3 p-6 border-t border-slate-200">
                <motion.button
                  onClick={handleCloseForm}
                  className="px-5 py-2.5 border border-slate-200 rounded-xl text-slate-600 font-medium hover:bg-slate-50 transition-colors"
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
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 删除确认 */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="删除角色"
        message={`确定要删除角色"${deleteTarget?.roleName}"吗？删除后不可恢复。`}
        confirmText="删除"
        cancelText="取消"
        confirmVariant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
