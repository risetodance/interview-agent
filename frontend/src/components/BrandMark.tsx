interface BrandMarkProps {
  className?: string;
}

/**
 * 品牌 mark：墨水蓝方块 + 白色对话气泡 + 问句圆点
 * 面试问答的几何抽象，与 favicon.svg 同款
 */
export default function BrandMark({ className = 'w-6 h-6' }: BrandMarkProps) {
  return (
    <svg viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg" className={className} aria-hidden="true">
      <rect width="32" height="32" rx="6" fill="#24394e" />
      <rect x="8" y="7" width="16" height="13" rx="3" fill="#ffffff" />
      <path d="M12 20 L12 25.5 L17.5 20 Z" fill="#ffffff" />
      <circle cx="16" cy="13.5" r="2.5" fill="#24394e" />
    </svg>
  );
}
