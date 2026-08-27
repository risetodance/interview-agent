import { useCallback, useRef, type ReactNode } from 'react';
import BrandMark from '../BrandMark';

interface AuthStep {
  title: string;
  desc: string;
}

interface AuthAsideProps {
  /** mono 小标（如 AI Interview Workspace） */
  tagline: string;
  /** 两行标语（可含 <br/>） */
  title: ReactNode;
  /** 特性/步骤列表（3 条） */
  steps: AuthStep[];
  /** 底部 mono 信息 */
  footerLabel?: string;
}

/**
 * 登录/注册/找回密码 共用左侧叙事面板
 * 光标聚光灯效果：柔光球 + 圆内文字点亮（双层内容 + 径向 mask，CSS 变量驱动，无 React 重渲染）
 */
export default function AuthAside({
  tagline,
  title,
  steps,
  footerLabel = 'AI Assistant · Interview Workspace',
}: AuthAsideProps) {
  const asideRef = useRef<HTMLElement>(null);
  const rafRef = useRef(0);

  // 光标位置写入 CSS 变量（rAF 节流，供柔光球与 mask 亮层消费）
  const handleMove = useCallback((e: React.MouseEvent) => {
    const el = asideRef.current;
    if (!el) return;
    cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(() => {
      const rect = el.getBoundingClientRect();
      el.style.setProperty('--mx', `${e.clientX - rect.left}px`);
      el.style.setProperty('--my', `${e.clientY - rect.top}px`);
    });
  }, []);

  const handleEnter = useCallback(() => {
    asideRef.current?.style.setProperty('--spot', '1');
  }, []);

  const handleLeave = useCallback(() => {
    asideRef.current?.style.setProperty('--spot', '0');
  }, []);

  // 内容渲染两遍：暗态基础层 + 亮色层（结构完全一致保证对齐）
  const renderBody = (lit: boolean) => (
    <>
      {/* 品牌区 */}
      <div className="flex items-center gap-3">
        <BrandMark className="w-7 h-7" />
        <div>
          <span className="block text-sm font-semibold text-white tracking-tight">智能面试助手</span>
          <span className={`block text-xs ${lit ? 'text-white/60' : 'text-white/50'}`}>AI Assistant</span>
        </div>
      </div>

      {/* 主文案与特性 */}
      <div>
        <p className={`font-mono text-xs tracking-[0.2em] uppercase mb-5 ${lit ? 'text-primary-200' : 'text-primary-300/50'}`}>
          {tagline}
        </p>
        <h2 className={`text-3xl xl:text-4xl font-semibold leading-snug tracking-tight ${lit ? 'text-white' : 'text-white/90'}`}>
          {title}
        </h2>

        <div className="mt-12 divide-y divide-white/10 border-t border-white/10">
          {steps.map((step, i) => (
            <div key={step.title} className="py-4 flex items-start gap-4">
              <span className={`font-mono text-xs pt-1 ${lit ? 'text-primary-200' : 'text-primary-300/45'}`}>
                {String(i + 1).padStart(2, '0')}
              </span>
              <div>
                <p className={`text-sm font-medium ${lit ? 'text-white' : 'text-white/85'}`}>{step.title}</p>
                <p className={`text-sm mt-0.5 ${lit ? 'text-white/75' : 'text-white/45'}`}>{step.desc}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 底部信息 */}
      <p className={`font-mono text-xs ${lit ? 'text-white/50' : 'text-white/35'}`}>{footerLabel}</p>
    </>
  );

  const spotlightMask =
    'radial-gradient(circle 150px at var(--mx, 50%) var(--my, 50%), black 0%, black 35%, transparent 100%)';

  return (
    <aside
      ref={asideRef}
      onMouseMove={handleMove}
      onMouseEnter={handleEnter}
      onMouseLeave={handleLeave}
      className="hidden lg:flex w-[420px] xl:w-[480px] shrink-0 flex-col justify-between bg-primary-950 bg-grid p-12 relative overflow-hidden"
    >
      {/* 柔光球（网格与内容之间） */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'radial-gradient(circle 200px at var(--mx, 50%) var(--my, 50%), rgba(86,168,191,0.13), transparent 70%)',
          opacity: 'var(--spot, 0)',
          transition: 'opacity 0.35s ease',
        }}
      />

      {/* 底部光波纹（透明水光：发光波浪线形变起伏 + 渐隐填充 + 漂移光斑） */}
      <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-[220px] overflow-hidden">
        {/* 光斑（光阴）：缓慢漂移 + 呼吸 */}
        <div
          className="wave-glow-a absolute left-[12%] bottom-[-30px] w-80 h-40"
          style={{ background: 'radial-gradient(ellipse at center, rgba(196,233,240,0.10), transparent 70%)' }}
        />
        <div
          className="wave-glow-b absolute left-[55%] bottom-[-50px] w-96 h-48"
          style={{ background: 'radial-gradient(ellipse at center, rgba(138,199,214,0.08), transparent 70%)' }}
        />

        {/* 波浪线一（主）：渐隐描边 + 波形形变，随外层缓慢平移 */}
        <div className="wave-drift-a absolute bottom-0 left-0 w-[200%] h-full">
          <svg className="w-full h-full" viewBox="0 0 2880 220" preserveAspectRatio="none" aria-hidden="true">
            <defs>
              <linearGradient id="wave-fill-a" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="#56a8bf" stopOpacity="0.08" />
                <stop offset="1" stopColor="#56a8bf" stopOpacity="0" />
              </linearGradient>
            </defs>
            <path fill="url(#wave-fill-a)" stroke="rgba(196,233,240,0.32)" strokeWidth="1.5"
              d="M0,110 C240,70 480,70 720,110 C960,150 1200,150 1440,110 C1680,70 1920,70 2160,110 C2400,150 2640,150 2880,110 L2880,220 L0,220 Z"
            />
          </svg>
        </div>

        {/* 波浪线二（副）：反向平移、更淡 */}
        <div className="wave-drift-b absolute bottom-0 left-0 w-[200%] h-full">
          <svg className="w-full h-full" viewBox="0 0 2880 220" preserveAspectRatio="none" aria-hidden="true">
            <path fill="none" stroke="rgba(138,199,214,0.16)" strokeWidth="1"
              d="M0,140 C240,115 480,115 720,140 C960,165 1200,165 1440,140 C1680,115 1920,115 2160,140 C2400,165 2640,165 2880,140"
            />
          </svg>
        </div>

        {/* 波浪线三：小振幅、快、最亮 */}
        <div className="wave-drift-c absolute bottom-0 left-0 w-[200%] h-full">
          <svg className="w-full h-full" viewBox="0 0 2880 220" preserveAspectRatio="none" aria-hidden="true">
            <path fill="none" stroke="rgba(224,244,248,0.26)" strokeWidth="1.5"
              d="M0,160 C240,142 480,142 720,160 C960,178 1200,178 1440,160 C1680,142 1920,142 2160,160 C2400,178 2640,178 2880,160"
            />
          </svg>
        </div>
      </div>

      {/* 暗态基础层（正常文档流） */}
      <div className="relative flex flex-col justify-between h-full">{renderBody(false)}</div>

      {/* 亮色层：径向 mask 内的"点亮"内容（随光标移动） */}
      <div
        className="pointer-events-none absolute inset-0 p-12 flex flex-col justify-between"
        style={{
          maskImage: spotlightMask,
          WebkitMaskImage: spotlightMask,
          opacity: 'var(--spot, 0)',
          transition: 'opacity 0.35s ease',
        }}
      >
        {renderBody(true)}
      </div>
    </aside>
  );
}
