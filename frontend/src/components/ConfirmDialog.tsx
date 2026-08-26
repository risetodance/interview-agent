import { Loader2 } from 'lucide-react';

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string | React.ReactNode;
  confirmText?: string;
  cancelText?: string;
  confirmVariant?: 'danger' | 'primary' | 'warning';
  onConfirm: () => void;
  onCancel: () => void;
  loading?: boolean;
  customContent?: React.ReactNode;
  hideButtons?: boolean;
}

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmText = '确定',
  cancelText = '取消',
  confirmVariant = 'primary',
  onConfirm,
  onCancel,
  loading = false,
  customContent,
  hideButtons = false
}: ConfirmDialogProps) {
  if (!open) return null;

  const variantStyles: Record<'danger' | 'primary' | 'warning', string> = {
    danger: 'bg-red-600 hover:bg-red-700',
    primary: 'bg-primary-600 hover:bg-primary-700 active:bg-primary-800',
    warning: 'bg-amber-600 hover:bg-amber-700'
  };

  return (
    <>
      {/* 背景遮罩 */}
      <div onClick={onCancel} className="fixed inset-0 bg-zinc-950/40 z-50 fade-in" />

      {/* 对话框 */}
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="bg-white border border-zinc-200 rounded-lg shadow-sm max-w-md w-full p-5 fade-in">
          {/* 标题 */}
          <h3 className="text-sm font-medium text-zinc-900 mb-3">
            {title}
          </h3>

          {/* 内容 */}
          <div className="text-sm text-zinc-500 mb-6">
            {typeof message === 'string' ? (
              message && <p className="whitespace-pre-line">{message}</p>
            ) : (
              message
            )}
            {customContent}
          </div>

          {/* 按钮 */}
          {!hideButtons && (
            <div className="flex gap-3 justify-end">
              <button
                onClick={onCancel}
                disabled={loading}
                className="h-9 px-4 rounded-md border border-zinc-300 text-zinc-700 text-sm font-medium hover:bg-zinc-50 hover:border-zinc-400 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {cancelText}
              </button>
              <button
                onClick={onConfirm}
                disabled={loading}
                className={`h-9 px-4 rounded-md text-white text-sm font-medium transition-colors disabled:opacity-60 disabled:cursor-not-allowed flex items-center gap-2 ${variantStyles[confirmVariant]}`}
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    处理中…
                  </>
                ) : (
                  confirmText
                )}
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
