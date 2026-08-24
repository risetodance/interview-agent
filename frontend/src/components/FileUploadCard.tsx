import { ChangeEvent, DragEvent, useCallback, useState } from 'react';
import { Upload, FileText, X, AlertCircle, Loader2 } from 'lucide-react';

export interface FileUploadCardProps {
  /** 标题 */
  title: string;
  /** 副标题 */
  subtitle: string;
  /** 接受的文件类型 */
  accept: string;
  /** 支持的格式说明 */
  formatHint: string;
  /** 最大文件大小说明 */
  maxSizeHint: string;
  /** 是否正在上传 */
  uploading?: boolean;
  /** 上传按钮文字 */
  uploadButtonText?: string;
  /** 选择按钮文字 */
  selectButtonText?: string;
  /** 是否显示名称输入框 */
  showNameInput?: boolean;
  /** 名称输入框占位符 */
  namePlaceholder?: string;
  /** 名称输入框标签 */
  nameLabel?: string;
  /** 是否显示分类输入框 */
  showCategoryInput?: boolean;
  /** 分类输入框占位符 */
  categoryPlaceholder?: string;
  /** 分类输入框标签 */
  categoryLabel?: string;
  /** 错误信息 */
  error?: string;
  /** 文件选择回调 */
  onFileSelect?: (file: File) => void;
  /** 上传回调 */
  onUpload: (file: File, name?: string, category?: string) => void;
  /** 返回回调 */
  onBack?: () => void;
}

/**
 * 上传卡片：扁平虚线拖放区 + 文件条
 * 视觉语言：细边框、无阴影、无渐变；知识库上传页与本页共用
 */
export default function FileUploadCard({
  title,
  subtitle,
  accept,
  formatHint,
  maxSizeHint,
  uploading = false,
  uploadButtonText = '开始上传',
  selectButtonText = '选择文件',
  showNameInput = false,
  namePlaceholder = '留空则使用文件名',
  nameLabel = '名称（可选）',
  showCategoryInput = false,
  categoryPlaceholder = '输入分类名称',
  categoryLabel = '分类（可选）',
  error,
  onFileSelect,
  onUpload,
  onBack,
}: FileUploadCardProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [name, setName] = useState('');
  const [category, setCategory] = useState('');

  const handleDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
  }, []);

  const handleDrop = useCallback((e: DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const files = e.dataTransfer.files;
    if (files.length > 0) {
      setSelectedFile(files[0]);
      onFileSelect?.(files[0]);
    }
  }, [onFileSelect]);

  const handleFileChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      setSelectedFile(files[0]);
      onFileSelect?.(files[0]);
    }
  }, [onFileSelect]);

  const handleUpload = () => {
    if (!selectedFile) return;
    onUpload(selectedFile, name.trim() || undefined, category.trim() || undefined);
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const inputCls =
    'w-full h-9 px-3 text-sm border border-zinc-300 rounded-md bg-white text-zinc-900 placeholder:text-zinc-400 focus:outline-none focus:border-primary-600 focus:ring-1 focus:ring-primary-600 transition-colors disabled:bg-zinc-100';

  return (
    <div className="w-full max-w-2xl mx-auto fade-in">
      {/* 页头 */}
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-zinc-900 tracking-tight">{title}</h1>
        <p className="mt-1 text-sm text-zinc-500">{subtitle}</p>
      </div>

      {/* 拖放区 */}
      <div
        className={`border border-dashed rounded-lg p-10 text-center cursor-pointer transition-colors ${
          dragOver
            ? 'border-primary-600 bg-primary-50/40'
            : 'border-zinc-300 bg-white hover:border-primary-400'
        }`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => !uploading && document.getElementById('file-upload-input')?.click()}
      >
        <input
          type="file"
          id="file-upload-input"
          className="hidden"
          accept={accept}
          onChange={handleFileChange}
          disabled={uploading}
        />

        {selectedFile ? (
          <div
            className="flex items-center gap-3 border border-zinc-200 bg-zinc-50 rounded-md px-4 py-3 text-left"
            onClick={(e) => e.stopPropagation()}
          >
            <FileText className="w-4 h-4 text-zinc-400 shrink-0" />
            <span className="flex-1 min-w-0 truncate font-mono text-sm text-zinc-800">
              {selectedFile.name}
            </span>
            <span className="font-mono text-xs text-zinc-400 shrink-0">
              {formatFileSize(selectedFile.size)}
            </span>
            <button
              type="button"
              className="p-1 text-zinc-400 hover:text-zinc-700 transition-colors shrink-0"
              onClick={(e) => {
                e.stopPropagation();
                setSelectedFile(null);
              }}
              aria-label="移除文件"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <div>
            <Upload className="mx-auto w-7 h-7 text-zinc-400" strokeWidth={1.5} />
            <p className="mt-3 text-sm text-zinc-600">
              拖拽文件到此处，或
              <button
                type="button"
                className="text-primary-700 hover:text-primary-800 underline underline-offset-2 ml-1"
                onClick={(e) => {
                  e.stopPropagation();
                  document.getElementById('file-upload-input')?.click();
                }}
              >
                {selectButtonText}
              </button>
            </p>
            <p className="mt-1.5 font-mono text-xs text-zinc-400">
              {formatHint} · {maxSizeHint}
            </p>
          </div>
        )}
      </div>

      {/* 名称 / 分类输入（知识库上传用） */}
      {showNameInput && selectedFile && (
        <div className="mt-4 space-y-4 fade-in">
          <div>
            <label htmlFor="upload-name" className="block text-xs font-medium text-zinc-600 mb-1.5">
              {nameLabel}
            </label>
            <input
              id="upload-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={namePlaceholder}
              className={inputCls}
              disabled={uploading}
            />
          </div>
          {showCategoryInput && (
            <div>
              <label htmlFor="upload-category" className="block text-xs font-medium text-zinc-600 mb-1.5">
                {categoryLabel}
              </label>
              <input
                id="upload-category"
                type="text"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                placeholder={categoryPlaceholder}
                className={inputCls}
                disabled={uploading}
              />
            </div>
          )}
        </div>
      )}

      {/* 错误提示 */}
      {error && (
        <div className="mt-4 flex items-center gap-2 border border-red-200 bg-red-50 rounded-md px-3 py-2.5 text-sm text-red-700 fade-in">
          <AlertCircle className="w-4 h-4 shrink-0" />
          {error}
        </div>
      )}

      {/* 操作按钮 */}
      <div className="mt-6 flex gap-3">
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            className="h-9 px-4 rounded-md border border-zinc-300 text-sm text-zinc-700 font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors"
          >
            返回
          </button>
        )}
        {selectedFile && (
          <button
            type="button"
            onClick={handleUpload}
            disabled={uploading}
            className="h-9 px-5 rounded-md bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 active:bg-primary-800 transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2"
          >
            {uploading && <Loader2 className="w-4 h-4 animate-spin" />}
            {uploading ? '处理中…' : uploadButtonText}
          </button>
        )}
      </div>
    </div>
  );
}
