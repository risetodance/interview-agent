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
} from 'lucide-react';
import {
  questionApi,
  QuestionDTO,
  QuestionDifficulty,
} from '../../api/question';

type ImportMode = 'excel' | 'markdown';
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
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 处理文件选择
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      // 验证文件类型
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
        count = await questionApi.importFromExcel(file, bankIdNum);
      } else if (mode === 'markdown') {
        count = await questionApi.importFromMarkdown(markdownContent, bankIdNum);
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

  // 难度标签样式
  const getDifficultyStyle = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'EASY':
        return 'bg-green-100 text-green-700';
      case 'MEDIUM':
        return 'bg-yellow-100 text-yellow-700';
      case 'HARD':
        return 'bg-red-100 text-red-700';
    }
  };

  // 难度文本
  const getDifficultyText = (difficulty: QuestionDifficulty) => {
    switch (difficulty) {
      case 'EASY':
        return '简单';
      case 'MEDIUM':
        return '中等';
      case 'HARD':
        return '困难';
    }
  };

  return (
    <div className="max-w-4xl mx-auto p-6">
      {/* 页面头部 */}
      <div className="flex items-center gap-4 mb-6">
        <button
          onClick={() => navigate(`/questions/bank/${bankId}`)}
          className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-slate-600" />
        </button>
        <h1 className="text-2xl font-bold text-slate-900">导入题目</h1>
      </div>

      {/* 步骤：选择导入方式 */}
      {step === 'select' && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          {/* 导入方式选择 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
            {/* Excel 导入 */}
            <div
              className={`bg-white rounded-xl border-2 p-6 cursor-pointer transition-all ${
                mode === 'excel'
                  ? 'border-primary-500 shadow-md'
                  : 'border-slate-200 hover:border-slate-300'
              }`}
              onClick={() => setMode('excel')}
            >
              <div className="flex items-center gap-4">
                <div className={`p-3 rounded-lg ${mode === 'excel' ? 'bg-primary-100' : 'bg-slate-100'}`}>
                  <FileSpreadsheet className={`w-8 h-8 ${mode === 'excel' ? 'text-primary-600' : 'text-slate-500'}`} />
                </div>
                <div>
                  <h3 className="font-medium text-slate-900">Excel 导入</h3>
                  <p className="text-sm text-slate-500">上传 .xlsx 或 .xls 文件</p>
                </div>
              </div>
            </div>

            {/* Markdown 导入 */}
            <div
              className={`bg-white rounded-xl border-2 p-6 cursor-pointer transition-all ${
                mode === 'markdown'
                  ? 'border-primary-500 shadow-md'
                  : 'border-slate-200 hover:border-slate-300'
              }`}
              onClick={() => setMode('markdown')}
            >
              <div className="flex items-center gap-4">
                <div className={`p-3 rounded-lg ${mode === 'markdown' ? 'bg-primary-100' : 'bg-slate-100'}`}>
                  <FileText className={`w-8 h-8 ${mode === 'markdown' ? 'text-primary-600' : 'text-slate-500'}`} />
                </div>
                <div>
                  <h3 className="font-medium text-slate-900">Markdown 导入</h3>
                  <p className="text-sm text-slate-500">粘贴 Markdown 格式内容</p>
                </div>
              </div>
            </div>
          </div>

          {/* Excel 文件选择 */}
          {mode === 'excel' && (
            <div className="bg-white rounded-xl border border-slate-200 p-6">
              <h3 className="font-medium text-slate-900 mb-4">选择 Excel 文件</h3>
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls"
                onChange={handleFileSelect}
                className="hidden"
              />
              <div
                className="border-2 border-dashed border-slate-300 rounded-lg p-8 text-center hover:border-primary-400 transition-colors cursor-pointer"
                onClick={() => fileInputRef.current?.click()}
              >
                {file ? (
                  <div className="flex items-center justify-center gap-2 text-green-600">
                    <CheckCircle className="w-5 h-5" />
                    <span>{file.name}</span>
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
                <div className="mt-4 flex justify-end">
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
Q: 你的优势是什么？\n\nA: 我的优势是...\n\n### 难度: 中等\n### 标签: 自我介绍,个人优势`}
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
          <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-slate-900 mb-2">导入成功</h3>
          <p className="text-slate-500 mb-6">{importResult?.message}</p>
          <div className="flex items-center justify-center gap-3">
            <button
              onClick={handleReselect}
              className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
            >
              继续导入
            </button>
            <button
              onClick={() => navigate(`/questions/bank/${bankId}`)}
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
          <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
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
              onClick={() => navigate(`/questions/bank/${bankId}`)}
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
