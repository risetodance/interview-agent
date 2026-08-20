import { useState, useEffect, useCallback } from 'react';

/**
 * 邮箱验证码 60s 倒计时（发码成功后调 start()）
 * 返回 codeText（按钮文案）、counting（倒计时中）、start（启动）
 */
export function useCodeCountdown(seconds = 60) {
  const [codeText, setCodeText] = useState('获取验证码');
  const [counting, setCounting] = useState(false);

  useEffect(() => {
    if (!counting) return;
    let left = seconds;
    setCodeText(`${left}s`);
    const timer = setInterval(() => {
      left -= 1;
      setCodeText(left > 0 ? `${left}s` : '获取验证码');
      if (left <= 0) {
        setCounting(false);
        clearInterval(timer);
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [counting, seconds]);

  const start = useCallback(() => setCounting(true), []);
  return { codeText, counting, start };
}
