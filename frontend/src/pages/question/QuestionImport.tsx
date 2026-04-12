import { useState, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  ArrowLeft,
  Upload,
  FileSpreadsheet,
  FileText,
  Loader2,
  CheckCircle,
  AlertCircle,
  X,
  Download,
  Plus,
  Trash2,
  Edit3,
  Tag,
  BookOpen,
  PenLine,
} from 'lucide-react';
import {
  questionApi,
  QuestionDTO,
  QuestionDifficulty,
  CreateQuestionRequest,
} from '../../api/question';
import QuestionBankSelect from '../../components/question/QuestionBankSelect';

type ImportMode = 'excel' | 'markdown' | 'manual';
type ImportStep = 'select' | 'preview' | 'importing' | 'success' | 'error';

export default function QuestionImport() {
  const navigate = useNavigate();
  const { bankId } = useParams<{ bankId: string }>();
  const bankIdNum = bankId ? parseInt(bankId, 10) : 0;

  const [mode, setMode] = useState<ImportMode>('excel');
  const [step, setStep] = useState<ImportStep>('select');
  const [file, setFile] = useState<File | null>(null);
  const [markdownContent, setMarkdownContent] = useState('');
  const [previewQuestions, setPreviewQuestions] = useState<QuestionDTO[]>([]);
  const [importResult, setImportResult] = useState<{
    success: boolean;
    message: string;
  } | null>(null);
  const [downloading, setDownloading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 手动录入相关状态
  const [selectedBankId, setSelectedBankId] = useState<number>(bankIdNum || 0);
  const [manualQuestions, setManualQuestions] = useState<CreateQuestionRequest[]>([]);
  const [currentQuestion, setCurrentQuestion] = useState<CreateQuestionRequest>({
    questionBankId: bankIdNum || 0,
    content: '',
    answer: '',
    difficulty: 'ADVANCED',
    tags: [],
  });
  const [tagInput, setTagInput] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 处理文件选择
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      const validTypes = [
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-excel',
      ];
      if (!validTypes.includes(selectedFile.type)) {
        alert('请选择 Excel 文件 (.xlsx, .xls)');
        return;
      }
      setFile(selectedFile);
    }
  };

  // 下载 Excel 模板
  const handleDownloadTemplate = async () => {
    try {
      setDownloading(true);
      await questionApi.downloadTemplate();
    } catch (err) {
      console.error('下载模板失败', err);
      alert('下载模板失败，请重试');
    } finally {
      setDownloading(false);
    }
  };

  // 预览 Excel
  const handlePreviewExcel = async () => {
    if (!file) return;

    try {
      setStep('preview');
      const questions = await questionApi.previewExcel(file);
      setPreviewQuestions(questions);
    } catch (err) {
      console.error('预览失败', err);
      alert('预览失败，请检查文件格式');
      setStep('select');
    }
  };

  // 预览 Markdown
  const handlePreviewMarkdown = async () => {
    if (!markdownContent.trim()) {
      alert('请输入 Markdown 内容');
      return;
    }

    try {
      setStep('preview');
      const questions = await questionApi.previewMarkdown(markdownContent);
      setPreviewQuestions(questions);
    } catch (err) {
      console.error('预览失败', err);
      alert('预览失败，请检查 Markdown 格式');
      setStep('select');
    }
  };

  // 执行导入
  const handleImport = async () => {
    try {
      setStep('importing');

      let count: number;
      if (mode === 'excel' && file) {
        count = await questionApi.importFromExcel(file, selectedBankId || bankIdNum);
      } else if (mode === 'markdown') {
        count = await questionApi.importFromMarkdown(markdownContent, selectedBankId || bankIdNum);
      } else {
        throw new Error('无效的导入方式');
      }

      setImportResult({
        success: true,
        message: `成功导入 ${count} 道题目`,
      });
      setStep('success');
    } catch (err: any) {
      setImportResult({
        success: false,
        message: err.message || '导入失败',
      });
      setStep('error');
    }
  };

  // 重新选择
  const handleReselect = () => {
    setFile(null);
    setMarkdownContent('');
    setPreviewQuestions([]);
    setImportResult(null);
    setStep('select');
  };

  // 手动录入：添加标签
  const handleAddTag = () => {
    if (tagInput.trim() && currentQuestion.tags && !currentQuestion.tags.includes(tagInput.trim())) {
      setCurrentQuestion({
        ...currentQuestion,
        tags: [...currentQuestion.tags, tagInput.trim()],
      });
      setTagInput('');
    }
  };

  // 手动录入：移除标签
  const handleRemoveTag = (tag: string) => {
    if (currentQuestion.tags) {
      setCurrentQuestion({
        ...currentQuestion,
        tags: currentQuestion.tags.filter(t => t !== tag),
      });
    }
  };

  // 手动录入：添加题目到列表
  const handleAddQuestion = () => {
    if (!currentQuestion.content.trim()) {
      alert('请输入题目内容');
      return;
    }

    const questionToAdd = {
      ...currentQuestion,
      questionBankId: selectedBankId || bankIdNum,
      tags: currentQuestion.tags && currentQuestion.tags.length > 0 ? currentQuestion.tags : undefined,
    };

    setManualQuestions([...manualQuestions, questionToAdd]);
    setCurrentQuestion({
      questionBankId: selectedBankId || bankIdNum,
      content: '',
      answer: '',
      difficulty: 'ADVANCED',
      tags: [],
    });
  };

  // 手动录入：编辑题目
  const handleEditQuestion = (index: number) => {
    const question = manualQuestions[index];
    setCurrentQuestion({ ...question });
    setManualQuestions(manualQuestions.filter((_, i) => i !== index));
  };

  // 手动录入：删除题目
  const handleDeleteQuestion = (index: number) => {
    setManualQuestions(manualQuestions.filter((_, i) => i !== index));
  };

  // 手动录入：提交所有题目
  const handleSubmitManual = async () => {
    if (manualQuestions.length === 0) {
      alert('请至少添加一道题目');
      return;
    }

    if (!selectedBankId && !bankIdNum) {
      alert('请选择题库');
      return;
    }

    try {
      setSubmitting(true);
      const targetBankId = selectedBankId || bankIdNum;
      const count = await questionApi.batchCreateQuestions(targetBankId, manualQuestions);
      setImportResult({
        success: true,
        message: `成功导入 ${count} 道题目`,
      });
      setStep('success');
    } catch (err: any) {
      setImportResult({
        success: false,
        message: err.message || '导入失败',
      });
      setStep('error');
    } finally {
      setSubmitting(false);
    }
  };

  // 难度标签样式
  const getDifficultyStyle = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'BASIC':
        return 'bg-green-100 text-green-700';
      case 'ADVANCED':
        return 'bg-yellow-100 text-yellow-700';
      case 'EXPERT':
        return 'bg-red-100 text-red-700';
    }
  };

  // 难度文本
  const getDifficultyText = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'BASIC':
        return '基础';
      case 'ADVANCED':
        return '进阶';
      case 'EXPERT':
        return '专家';
    }
  };

  return (
    <div className="max-w-5xl mx-auto p-6">
      {/* 页面头部 */}
      <div className="flex items-center gap-4 mb-8">
        <button
          onClick={() => navigate(`/questions/bank/${bankId}`)}
          className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-slate-600" />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-slate-900">导入题目</h1>
          <p className="text-sm text-slate-500 mt-1">支持 Excel、Markdown 和手动录入</p>
        </div>
      </div>

      {/* 导入方式选择 */}
      {step === 'select' && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          {/* 题库选择 */}
          {!bankIdNum && (
            <div className="bg-primary-50 border border-primary-200 rounded-xl p-4 mb-6">
              <div className="flex items-center gap-2 mb-3">
                <BookOpen className="w-5 h-5 text-primary-600" />
                <span className="font-medium text-primary-800">选择目标题库</span>
              </div>
              <QuestionBankSelect
                selectedBankIds={selectedBankId ? [selectedBankId] : []}
                onChange={(ids) => setSelectedBankId(ids[0] || 0)}
                maxSelections={1}
              />
            </div>
          )}

          {/* 导入方式选择 */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            {/* Excel 导入 */}
            <div
              className={`bg-white rounded-xl border-2 p-5 cursor-pointer transition-all ${
                mode === 'excel'
                  ? 'border-primary-500 shadow-lg shadow-primary-100'
                  : 'border-slate-200 hover:border-slate-300'
              }`}
              onClick={() => setMode('excel')}
            >
              <div className="flex flex-col items-center text-center gap-3">
                <div className={`p-3 rounded-xl ${mode === 'excel' ? 'bg-primary-100' : 'bg-slate-100'}`}>
                  <FileSpreadsheet className={`w-8 h-8 ${mode === 'excel' ? 'text-primary-600' : 'text-slate-500'}`} />
                </div>
                <div>
                  <h3 className="font-medium text-slate-900">Excel 导入</h3>
                  <p className="text-sm text-slate-500 mt-1">上传 .xlsx 文件批量导入</p>
                </div>
              </div>
            </div>

            {/* Markdown 导入 */}
            <div
              className={`bg-white rounded-xl border-2 p-5 cursor-pointer transition-all ${
                mode === 'markdown'
                  ? 'border-primary-500 shadow-lg shadow-primary-100'
                  : 'border-slate-200 hover:border-slate-300'
              }`}
              onClick={() => setMode('markdown')}
            >
              <div className="flex flex-col items-center text-center gap-3">
                <div className={`p-3 rounded-xl ${mode === 'markdown' ? 'bg-primary-100' : 'bg-slate-100'}`}>
                  <FileText className={`w-8 h-8 ${mode === 'markdown' ? 'text-primary-600' : 'text-slate-500'}`} />
                </div>
                <div>
                  <h3 className="font-medium text-slate-900">Markdown 导入</h3>
                  <p className="text-sm text-slate-500 mt-1">粘贴 Markdown 格式内容</p>
                </div>
              </div>
            </div>

            {/* 手动录入 */}
            <div
              className={`bg-white rounded-xl border-2 p-5 cursor-pointer transition-all ${
                mode === 'manual'
                  ? 'border-primary-500 shadow-lg shadow-primary-100'
                  : 'border-slate-200 hover:border-slate-300'
              }`}
              onClick={() => setMode('manual')}
            >
              <div className="flex flex-col items-center text-center gap-3">
                <div className={`p-3 rounded-xl ${mode === 'manual' ? 'bg-primary-100' : 'bg-slate-100'}`}>
                  <PenLine className={`w-8 h-8 ${mode === 'manual' ? 'text-primary-600' : 'text-slate-500'}`} />
                </div>
                <div>
                  <h3 className="font-medium text-slate-900">手动录入</h3>
                  <p className="text-sm text-slate-500 mt-1">表单方式逐题录入</p>
                </div>
              </div>
            </div>
          </div>

          {/* Excel 文件选择 */}
          {mode === 'excel' && (
            <div className="bg-white rounded-xl border border-slate-200 p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-medium text-slate-900">选择 Excel 文件</h3>
                <button
                  onClick={handleDownloadTemplate}
                  disabled={downloading}
                  className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                >
                  {downloading ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Download className="w-4 h-4" />
                  )}
                  下载模板
                </button>
              </div>
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls"
                onChange={handleFileSelect}
                className="hidden"
              />
              <div
                className="border-2 border-dashed border-slate-300 rounded-xl p-8 text-center hover:border-primary-400 transition-colors cursor-pointer"
                onClick={() => fileInputRef.current?.click()}
              >
                {file ? (
                  <div className="flex items-center justify-center gap-2 text-green-600">
                    <CheckCircle className="w-5 h-5" />
                    <span className="font-medium">{file.name}</span>
                  </div>
                ) : (
                  <>
                    <Upload className="w-10 h-10 text-slate-400 mx-auto mb-2" />
                    <p className="text-slate-500">点击选择文件或拖拽到此处</p>
                    <p className="text-sm text-slate-400 mt-1">支持 .xlsx, .xls 格式</p>
                  </>
                )}
              </div>
              {file && (
                <div className="mt-4 flex justify-end gap-3">
                  <button
                    onClick={handleReselect}
                    className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                  >
                    重新选择
                  </button>
                  <button
                    onClick={handlePreviewExcel}
                    className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
                  >
                    预览
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Markdown 内容输入 */}
          {mode === 'markdown' && (
            <div className="bg-white rounded-xl border border-slate-200 p-6">
              <h3 className="font-medium text-slate-900 mb-4">输入 Markdown 内容</h3>
              <textarea
                value={markdownContent}
                onChange={(e) => setMarkdownContent(e.target.value)}
                placeholder={`## 题目 1
Q: 你的优势是什么？

A: 我的优势是...

### 难度: 进阶
### 标签: 自我介绍,个人优势`}
                rows={12}
                className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none font-mono text-sm"
              />
              <div className="mt-4 flex justify-end">
                <button
                  onClick={handlePreviewMarkdown}
                  disabled={!markdownContent.trim()}
                  className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                >
                  预览
                </button>
              </div>
            </div>
          )}

          {/* 手动录入 */}
          {mode === 'manual' && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="space-y-6"
            >
              {/* 题目表单 */}
              <div className="bg-white rounded-xl border border-slate-200 p-6">
                <h3 className="font-medium text-slate-900 mb-4 flex items-center gap-2">
                  <Plus className="w-5 h-5 text-primary-600" />
                  添加题目
                </h3>

                <div className="space-y-4">
                  {/* 题目内容 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">
                      题目内容 <span className="text-red-500">*</span>
                    </label>
                    <textarea
                      value={currentQuestion.content}
                      onChange={(e) => setCurrentQuestion({ ...currentQuestion, content: e.target.value })}
                      placeholder="请输入面试题目内容..."
                      rows={3}
                      className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                    />
                  </div>

                  {/* 答案 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">
                      参考答案
                    </label>
                    <textarea
                      value={currentQuestion.answer}
                      onChange={(e) => setCurrentQuestion({ ...currentQuestion, answer: e.target.value })}
                      placeholder="请输入参考答案（选填）..."
                      rows={3}
                      className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
                    />
                  </div>

                  {/* 难度选择 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-2">
                      难度
                    </label>
                    <div className="flex gap-3">
                      {(['BASIC', 'ADVANCED', 'EXPERT'] as QuestionDifficulty[]).map((diff) => (
                        <button
                          key={diff}
                          onClick={() => setCurrentQuestion({ ...currentQuestion, difficulty: diff })}
                          className={`px-4 py-2 rounded-lg font-medium transition-all ${
                            currentQuestion.difficulty === diff
                              ? diff === 'BASIC'
                                ? 'bg-green-100 text-green-700 border-2 border-green-500'
                                : diff === 'ADVANCED'
                                ? 'bg-yellow-100 text-yellow-700 border-2 border-yellow-500'
                                : 'bg-red-100 text-red-700 border-2 border-red-500'
                              : 'bg-slate-100 text-slate-600 border-2 border-transparent hover:border-slate-300'
                          }`}
                        >
                          {getDifficultyText(diff)}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 标签 */}
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">
                      标签
                    </label>
                    <div className="flex gap-2 mb-2">
                      <input
                        type="text"
                        value={tagInput}
                        onChange={(e) => setTagInput(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddTag())}
                        placeholder="输入标签后按回车添加"
                        className="flex-1 px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                      />
                      <button
                        onClick={handleAddTag}
                        className="px-4 py-2 bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 transition-colors"
                      >
                        添加
                      </button>
                    </div>
                    {currentQuestion.tags && currentQuestion.tags.length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {currentQuestion.tags.map((tag) => (
                          <span
                            key={tag}
                            className="inline-flex items-center gap-1 px-3 py-1 bg-primary-50 text-primary-700 rounded-full text-sm"
                          >
                            <Tag className="w-3 h-3" />
                            {tag}
                            <button
                              onClick={() => handleRemoveTag(tag)}
                              className="ml-1 hover:text-primary-900"
                            >
                              <X className="w-3 h-3" />
                            </button>
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* 添加按钮 */}
                  <div className="flex justify-end pt-2">
                    <button
                      onClick={handleAddQuestion}
                      disabled={!currentQuestion.content.trim()}
                      className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                    >
                      <Plus className="w-4 h-4" />
                      添加到列表
                    </button>
                  </div>
                </div>
              </div>

              {/* 已添加的题目列表 */}
              {manualQuestions.length > 0 && (
                <div className="bg-white rounded-xl border border-slate-200 p-6">
                  <h3 className="font-medium text-slate-900 mb-4">
                    已添加 {manualQuestions.length} 道题目
                  </h3>
                  <div className="space-y-3 max-h-[400px] overflow-y-auto">
                    {manualQuestions.map((q, index) => (
                      <div
                        key={index}
                        className="flex items-start justify-between p-4 bg-slate-50 rounded-lg border border-slate-100"
                      >
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-sm font-medium text-slate-500">#{index + 1}</span>
                            <span className={`px-2 py-0.5 rounded text-xs ${getDifficultyStyle(q.difficulty || 'ADVANCED')}`}>
                              {getDifficultyText(q.difficulty || 'ADVANCED')}
                            </span>
                          </div>
                          <p className="text-slate-900 line-clamp-2">{q.content}</p>
                          {q.tags && q.tags.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-2">
                              {q.tags.map((tag) => (
                                <span
                                  key={tag}
                                  className="px-2 py-0.5 bg-slate-200 text-slate-600 rounded text-xs"
                                >
                                  {tag}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                        <div className="flex items-center gap-1 ml-3">
                          <button
                            onClick={() => handleEditQuestion(index)}
                            className="p-2 text-slate-400 hover:text-primary-600 hover:bg-primary-50 rounded-lg transition-colors"
                          >
                            <Edit3 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDeleteQuestion(index)}
                            className="p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="mt-4 flex justify-end gap-3">
                    <button
                      onClick={() => setManualQuestions([])}
                      className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                    >
                      清空列表
                    </button>
                    <button
                      onClick={handleSubmitManual}
                      disabled={submitting}
                      className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50"
                    >
                      {submitting ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
                          导入中...
                        </>
                      ) : (
                        <>
                          <CheckCircle className="w-4 h-4" />
                          确认导入
                        </>
                      )}
                    </button>
                  </div>
                </div>
              )}
            </motion.div>
          )}
        </motion.div>
      )}

      {/* 步骤：预览 */}
      {step === 'preview' && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <div className="bg-white rounded-xl border border-slate-200 p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-medium text-slate-900">
                预览导入 ({previewQuestions.length} 道题目)
              </h3>
              <button
                onClick={handleReselect}
                className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3 max-h-[500px] overflow-y-auto">
              {previewQuestions.map((q, index) => (
                <div key={index} className="border border-slate-200 rounded-lg p-4">
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`px-2 py-0.5 rounded text-xs ${getDifficultyStyle(q.difficulty)}`}>
                      {getDifficultyText(q.difficulty)}
                    </span>
                    {q.tags && q.tags.length > 0 && (
                      <span className="text-xs text-slate-400">
                        {q.tags.join(', ')}
                      </span>
                    )}
                  </div>
                  <p className="text-slate-900 line-clamp-2">{q.content}</p>
                  {q.answer && (
                    <p className="text-sm text-slate-500 mt-1 line-clamp-1">
                      答案: {q.answer}
                    </p>
                  )}
                </div>
              ))}
            </div>

            <div className="mt-6 flex items-center justify-end gap-3">
              <button
                onClick={handleReselect}
                className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                重新选择
              </button>
              <button
                onClick={handleImport}
                className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
              >
                确认导入
              </button>
            </div>
          </div>
        </motion.div>
      )}

      {/* 步骤：导入中 */}
      {step === 'importing' && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-white rounded-xl border border-slate-200 p-12 text-center"
        >
          <Loader2 className="w-12 h-12 text-primary-500 mx-auto mb-4 animate-spin" />
          <h3 className="text-lg font-medium text-slate-900 mb-2">正在导入...</h3>
          <p className="text-slate-500">请稍候</p>
        </motion.div>
      )}

      {/* 步骤：导入成功 */}
      {step === 'success' && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-white rounded-xl border border-slate-200 p-12 text-center"
        >
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <CheckCircle className="w-8 h-8 text-green-600" />
          </div>
          <h3 className="text-lg font-medium text-slate-900 mb-2">导入成功</h3>
          <p className="text-slate-500 mb-6">{importResult?.message}</p>
          <div className="flex items-center justify-center gap-3">
            <button
              onClick={() => {
                setStep('select');
                setManualQuestions([]);
                setCurrentQuestion({
                  questionBankId: bankIdNum || 0,
                  content: '',
                  answer: '',
                  difficulty: 'ADVANCED',
                  tags: [],
                });
              }}
              className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
            >
              继续导入
            </button>
            <button
              onClick={() => navigate(`/questions/bank/${selectedBankId || bankIdNum}`)}
              className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
            >
              查看题库
            </button>
          </div>
        </motion.div>
      )}

      {/* 步骤：导入失败 */}
      {step === 'error' && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-white rounded-xl border border-slate-200 p-12 text-center"
        >
          <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <AlertCircle className="w-8 h-8 text-red-600" />
          </div>
          <h3 className="text-lg font-medium text-slate-900 mb-2">导入失败</h3>
          <p className="text-slate-500 mb-6">{importResult?.message}</p>
          <div className="flex items-center justify-center gap-3">
            <button
              onClick={handleReselect}
              className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
            >
              重新选择
            </button>
            <button
              onClick={() => navigate(`/questions/bank/${selectedBankId || bankIdNum}`)}
              className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
            >
              返回题库
            </button>
          </div>
        </motion.div>
      )}
    </div>
  );
}
