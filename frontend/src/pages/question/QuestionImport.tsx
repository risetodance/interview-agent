import { useState, useRef, Fragment } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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

const labelCls = 'block text-xs font-medium text-zinc-600 mb-1.5';
const inputCls =
  'w-full h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors';
const textareaCls =
  'w-full px-3 py-2 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors resize-none';
const primaryBtnCls =
  'h-9 px-4 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2';
const secondaryBtnCls =
  'h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors';

/** 导入流程步骤（mono 数字 + 细线连接） */
const IMPORT_STEPS = ['选择来源', '预览确认', '执行导入'];

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

  // 难度标签样式（语义色：基础 emerald / 进阶 amber / 专家 red）
  const getDifficultyStyle = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'BASIC':
        return 'text-emerald-700 bg-emerald-50 border-emerald-200';
      case 'ADVANCED':
        return 'text-amber-700 bg-amber-50 border-amber-200';
      case 'EXPERT':
        return 'text-red-700 bg-red-50 border-red-200';
    }
  };

  // 难度选择按钮激活样式（与 chip 同语义色）
  const getDifficultyActiveStyle = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'BASIC':
        return 'border-emerald-500 bg-emerald-50 text-emerald-700';
      case 'ADVANCED':
        return 'border-amber-500 bg-amber-50 text-amber-700';
      case 'EXPERT':
        return 'border-red-500 bg-red-50 text-red-700';
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

  // 当前步骤索引（select=0 / preview=1 / importing·success·error=2）
  const stepIndex = step === 'select' ? 0 : step === 'preview' ? 1 : 2;

  // 导入方式选项
  const modeOptions: { value: ImportMode; icon: typeof FileSpreadsheet; title: string; desc: string }[] = [
    { value: 'excel', icon: FileSpreadsheet, title: 'Excel 导入', desc: '上传 .xlsx 文件批量导入' },
    { value: 'markdown', icon: FileText, title: 'Markdown 导入', desc: '粘贴 Markdown 格式内容' },
    { value: 'manual', icon: PenLine, title: '手动录入', desc: '表单方式逐题录入' },
  ];

  return (
    <div className="fade-in">
      {/* 页面头部 */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate(`/questions/bank/${bankId}`)}
          className="p-2 -ml-2 hover:bg-zinc-100 rounded-md transition-colors"
          aria-label="返回题库"
        >
          <ArrowLeft className="w-5 h-5 text-zinc-500" />
        </button>
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">导入题目</h1>
          <p className="mt-0.5 text-sm text-zinc-500">支持 Excel、Markdown 和手动录入</p>
        </div>
      </div>

      {/* 步骤指示：mono 数字 + 细线连接 */}
      <div className="flex items-center gap-3 mb-6">
        {IMPORT_STEPS.map((label, i) => (
          <Fragment key={label}>
            {i > 0 && (
              <span
                className={`flex-1 h-px ${i <= stepIndex ? 'bg-primary-500' : 'bg-zinc-200'}`}
              />
            )}
            <div className="flex items-center gap-2 shrink-0">
              <span
                className={`font-mono text-xs tabular-nums ${
                  i <= stepIndex ? 'text-primary-700' : 'text-zinc-300'
                }`}
              >
                {String(i + 1).padStart(2, '0')}
              </span>
              <span
                className={`text-sm ${
                  i === stepIndex
                    ? 'text-zinc-900 font-medium'
                    : i < stepIndex
                    ? 'text-zinc-500'
                    : 'text-zinc-400'
                }`}
              >
                {label}
              </span>
            </div>
          </Fragment>
        ))}
      </div>

      {/* 步骤：选择来源 */}
      {step === 'select' && (
        <div className="fade-in">
          {/* 题库选择 */}
          {!bankIdNum && (
            <div className="bg-white border border-zinc-200 rounded-lg shadow-sm mb-5">
              <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
                <h2 className="text-sm font-medium text-zinc-900">目标题库</h2>
                <span className="font-mono text-xs text-zinc-400">必选</span>
              </div>
              <div className="p-5">
                <QuestionBankSelect
                  selectedBankIds={selectedBankId ? [selectedBankId] : []}
                  onChange={(ids) => setSelectedBankId(ids[0] || 0)}
                  maxSelections={1}
                />
              </div>
            </div>
          )}

          {/* 导入方式选择 */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-5">
            {modeOptions.map((option) => {
              const Icon = option.icon;
              const active = mode === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setMode(option.value)}
                  className={`text-left border rounded-lg p-4 transition-colors ${
                    active
                      ? 'border-primary-600 bg-primary-50/50'
                      : 'border-zinc-300 bg-white hover:border-zinc-400'
                  }`}
                >
                  <Icon
                    className={`w-5 h-5 ${active ? 'text-primary-700' : 'text-zinc-400'}`}
                    strokeWidth={1.75}
                  />
                  <p className="mt-3 text-sm font-medium text-zinc-900">{option.title}</p>
                  <p className="mt-1 text-xs text-zinc-500">{option.desc}</p>
                </button>
              );
            })}
          </div>

          {/* Excel 文件选择 */}
          {mode === 'excel' && (
            <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
              <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
                <h2 className="text-sm font-medium text-zinc-900">选择 Excel 文件</h2>
                <button
                  onClick={handleDownloadTemplate}
                  disabled={downloading}
                  className="h-8 px-3 rounded-md border border-zinc-300 text-zinc-700 text-xs font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-1.5"
                >
                  {downloading ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <Download className="w-3.5 h-3.5" />
                  )}
                  下载模板
                </button>
              </div>

              <div className="p-5">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".xlsx,.xls"
                  onChange={handleFileSelect}
                  className="hidden"
                />
                <div
                  className={`border border-dashed rounded-lg px-6 py-10 text-center cursor-pointer transition-colors ${
                    file ? 'border-zinc-300 bg-zinc-50/50' : 'border-zinc-300 hover:border-primary-400'
                  }`}
                  onClick={() => fileInputRef.current?.click()}
                >
                  {file ? (
                    <div
                      className="flex items-center gap-3 border border-zinc-200 bg-white rounded-md px-4 py-3 max-w-lg mx-auto text-left"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <FileText className="w-4 h-4 text-zinc-400 shrink-0" />
                      <span className="flex-1 min-w-0 truncate font-mono text-sm text-zinc-800">
                        {file.name}
                      </span>
                      <CheckCircle className="w-4 h-4 text-emerald-600 shrink-0" />
                    </div>
                  ) : (
                    <div>
                      <Upload className="w-5 h-5 text-zinc-400 mx-auto" strokeWidth={1.75} />
                      <p className="mt-3 text-sm text-zinc-600">点击选择文件或拖拽到此处</p>
                      <p className="mt-1 text-xs text-zinc-400">支持 .xlsx / .xls 格式</p>
                    </div>
                  )}
                </div>
                {file && (
                  <div className="mt-4 flex justify-end gap-3 fade-in">
                    <button type="button" onClick={handleReselect} className={secondaryBtnCls}>
                      重新选择
                    </button>
                    <button type="button" onClick={handlePreviewExcel} className={primaryBtnCls}>
                      预览
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Markdown 内容输入 */}
          {mode === 'markdown' && (
            <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
              <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
                <h2 className="text-sm font-medium text-zinc-900">输入 Markdown 内容</h2>
                <span className="font-mono text-xs text-zinc-400">MARKDOWN</span>
              </div>
              <div className="p-5">
                <textarea
                  value={markdownContent}
                  onChange={(e) => setMarkdownContent(e.target.value)}
                  placeholder={`## 题目 1
Q: 你的优势是什么？

A: 我的优势是...

### 难度: 进阶
### 标签: 自我介绍,个人优势`}
                  rows={12}
                  className={`${textareaCls} font-mono text-xs leading-relaxed`}
                />
                <div className="mt-4 flex justify-end">
                  <button
                    type="button"
                    onClick={handlePreviewMarkdown}
                    disabled={!markdownContent.trim()}
                    className={primaryBtnCls}
                  >
                    预览
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* 手动录入 */}
          {mode === 'manual' && (
            <div className="space-y-5 fade-in">
              {/* 题目表单 */}
              <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
                <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
                  <h2 className="text-sm font-medium text-zinc-900">添加题目</h2>
                  <span className="font-mono text-xs text-zinc-400">逐题录入</span>
                </div>

                <div className="p-5 space-y-5">
                  {/* 题目内容 */}
                  <div>
                    <label className={labelCls}>
                      题目内容 <span className="text-red-500">*</span>
                    </label>
                    <textarea
                      value={currentQuestion.content}
                      onChange={(e) => setCurrentQuestion({ ...currentQuestion, content: e.target.value })}
                      placeholder="请输入面试题目内容…"
                      rows={3}
                      className={textareaCls}
                    />
                  </div>

                  {/* 答案 */}
                  <div>
                    <label className={labelCls}>参考答案</label>
                    <textarea
                      value={currentQuestion.answer}
                      onChange={(e) => setCurrentQuestion({ ...currentQuestion, answer: e.target.value })}
                      placeholder="请输入参考答案（选填）…"
                      rows={3}
                      className={textareaCls}
                    />
                  </div>

                  {/* 难度选择 */}
                  <div>
                    <label className={`${labelCls} mb-2`}>难度</label>
                    <div className="flex gap-2">
                      {(['BASIC', 'ADVANCED', 'EXPERT'] as QuestionDifficulty[]).map((diff) => (
                        <button
                          key={diff}
                          type="button"
                          onClick={() => setCurrentQuestion({ ...currentQuestion, difficulty: diff })}
                          className={`h-8 px-3 rounded-md border text-sm transition-colors ${
                            currentQuestion.difficulty === diff
                              ? getDifficultyActiveStyle(diff)
                              : 'border-zinc-300 text-zinc-600 hover:border-zinc-400 hover:bg-zinc-50'
                          }`}
                        >
                          {getDifficultyText(diff)}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* 标签 */}
                  <div>
                    <label className={labelCls}>标签</label>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        value={tagInput}
                        onChange={(e) => setTagInput(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddTag())}
                        placeholder="输入标签后按回车添加"
                        className={inputCls}
                      />
                      <button type="button" onClick={handleAddTag} className={`${secondaryBtnCls} shrink-0`}>
                        添加
                      </button>
                    </div>
                    {currentQuestion.tags && currentQuestion.tags.length > 0 && (
                      <div className="flex flex-wrap gap-1.5 mt-2.5">
                        {currentQuestion.tags.map((tag) => (
                          <span
                            key={tag}
                            className="inline-flex items-center gap-1 px-1.5 py-0.5 border border-zinc-200 bg-zinc-50 text-zinc-600 rounded text-xs"
                          >
                            {tag}
                            <button
                              type="button"
                              onClick={() => handleRemoveTag(tag)}
                              className="text-zinc-400 hover:text-zinc-700 transition-colors"
                              aria-label={`移除标签 ${tag}`}
                            >
                              <X className="w-3 h-3" />
                            </button>
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* 添加按钮 */}
                  <div className="flex justify-end pt-1">
                    <button
                      type="button"
                      onClick={handleAddQuestion}
                      disabled={!currentQuestion.content.trim()}
                      className={primaryBtnCls}
                    >
                      <Plus className="w-4 h-4" />
                      添加到列表
                    </button>
                  </div>
                </div>
              </div>

              {/* 已添加的题目列表 */}
              {manualQuestions.length > 0 && (
                <div className="bg-white border border-zinc-200 rounded-lg shadow-sm">
                  <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
                    <h2 className="text-sm font-medium text-zinc-900">已添加题目</h2>
                    <span className="font-mono text-xs text-zinc-400 tabular-nums">
                      {manualQuestions.length} 道
                    </span>
                  </div>

                  <div className="p-5">
                    <div className="space-y-3 max-h-[400px] overflow-y-auto">
                      {manualQuestions.map((q, index) => (
                        <div
                          key={index}
                          className="flex items-start justify-between gap-3 p-4 border border-zinc-200 bg-zinc-50/50 rounded-md"
                        >
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 mb-1.5">
                              <span className="font-mono text-xs text-zinc-400 tabular-nums">
                                {String(index + 1).padStart(2, '0')}
                              </span>
                              <span
                                className={`text-xs border rounded px-1.5 py-0.5 ${getDifficultyStyle(q.difficulty || 'ADVANCED')}`}
                              >
                                {getDifficultyText(q.difficulty || 'ADVANCED')}
                              </span>
                            </div>
                            <p className="text-sm text-zinc-800 line-clamp-2">{q.content}</p>
                            {q.tags && q.tags.length > 0 && (
                              <div className="flex flex-wrap gap-1 mt-2">
                                {q.tags.map((tag) => (
                                  <span
                                    key={tag}
                                    className="text-xs border border-zinc-200 bg-zinc-50 text-zinc-600 rounded px-1.5 py-0.5"
                                  >
                                    {tag}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                          <div className="flex items-center gap-0.5 shrink-0">
                            <button
                              type="button"
                              onClick={() => handleEditQuestion(index)}
                              className="p-1.5 text-zinc-400 hover:text-zinc-700 hover:bg-zinc-100 rounded-md transition-colors"
                              title="编辑"
                            >
                              <Edit3 className="w-4 h-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() => handleDeleteQuestion(index)}
                              className="p-1.5 text-zinc-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
                              title="删除"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                    <div className="mt-4 flex justify-end gap-3">
                      <button
                        type="button"
                        onClick={() => setManualQuestions([])}
                        className={secondaryBtnCls}
                      >
                        清空列表
                      </button>
                      <button
                        type="button"
                        onClick={handleSubmitManual}
                        disabled={submitting}
                        className={primaryBtnCls}
                      >
                        {submitting ? (
                          <>
                            <Loader2 className="w-4 h-4 animate-spin" />
                            导入中…
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
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* 步骤：预览 */}
      {step === 'preview' && (
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm fade-in">
          <div className="flex items-center justify-between h-[46px] px-5 border-b border-zinc-100 shrink-0">
            <h2 className="text-sm font-medium text-zinc-900">预览确认</h2>
            <span className="font-mono text-xs text-zinc-400 tabular-nums">
              {previewQuestions.length} 道
            </span>
          </div>

          <div className="p-5">
            <div className="space-y-3 max-h-[500px] overflow-y-auto">
              {previewQuestions.map((q, index) => (
                <div key={index} className="border border-zinc-200 rounded-md p-4">
                  <div className="flex items-center gap-2 mb-2">
                    <span className="font-mono text-xs text-zinc-400 tabular-nums">
                      {String(index + 1).padStart(2, '0')}
                    </span>
                    <span
                      className={`text-xs border rounded px-1.5 py-0.5 ${getDifficultyStyle(q.difficulty)}`}
                    >
                      {getDifficultyText(q.difficulty)}
                    </span>
                    {q.tags && q.tags.length > 0 && (
                      <span className="text-xs text-zinc-400 truncate">{q.tags.join(' · ')}</span>
                    )}
                  </div>
                  <p className="text-sm text-zinc-900 line-clamp-2">{q.content}</p>
                  {q.answer && (
                    <p className="mt-1.5 text-xs text-zinc-500 line-clamp-1">答案：{q.answer}</p>
                  )}
                </div>
              ))}
            </div>

            <div className="mt-5 flex items-center justify-end gap-3">
              <button type="button" onClick={handleReselect} className={secondaryBtnCls}>
                重新选择
              </button>
              <button type="button" onClick={handleImport} className={primaryBtnCls}>
                确认导入
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 步骤：导入中 */}
      {step === 'importing' && (
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm px-6 py-16 text-center fade-in">
          <Loader2 className="w-8 h-8 text-primary-600 mx-auto animate-spin" />
          <h3 className="mt-4 text-sm font-medium text-zinc-900">正在导入…</h3>
          <p className="mt-1 text-xs text-zinc-500">请稍候，完成后自动进入下一步</p>
        </div>
      )}

      {/* 步骤：导入成功 */}
      {step === 'success' && (
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm px-6 py-14 text-center fade-in">
          <div className="mx-auto w-11 h-11 rounded-md bg-emerald-50 border border-emerald-100 flex items-center justify-center">
            <CheckCircle className="w-5 h-5 text-emerald-600" strokeWidth={1.75} />
          </div>
          <h3 className="mt-4 text-sm font-medium text-zinc-900">导入成功</h3>
          <p className="mt-1.5 text-sm text-zinc-500">{importResult?.message}</p>
          <div className="mt-6 flex items-center justify-center gap-3">
            <button
              type="button"
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
              className={secondaryBtnCls}
            >
              继续导入
            </button>
            <button
              type="button"
              onClick={() => navigate(`/questions/bank/${selectedBankId || bankIdNum}`)}
              className={primaryBtnCls}
            >
              查看题库
            </button>
          </div>
        </div>
      )}

      {/* 步骤：导入失败 */}
      {step === 'error' && (
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm px-6 py-14 text-center fade-in">
          <div className="mx-auto w-11 h-11 rounded-md bg-red-50 border border-red-100 flex items-center justify-center">
            <AlertCircle className="w-5 h-5 text-red-600" strokeWidth={1.75} />
          </div>
          <h3 className="mt-4 text-sm font-medium text-zinc-900">导入失败</h3>
          <p className="mt-1.5 text-sm text-zinc-500">{importResult?.message}</p>
          <div className="mt-6 flex items-center justify-center gap-3">
            <button type="button" onClick={handleReselect} className={secondaryBtnCls}>
              重新选择
            </button>
            <button
              type="button"
              onClick={() => navigate(`/questions/bank/${selectedBankId || bankIdNum}`)}
              className={primaryBtnCls}
            >
              返回题库
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
