import { useEffect, useState } from 'react';

/**
 * Counts down to an ISO deadline, returning the whole seconds remaining (never negative) and a
 * pre-formatted mm:ss label. Ticks once per second and cleans up its interval.
 */
export function useCountdown(deadlineIso: string | null): { secondsLeft: number; label: string; expired: boolean } {
  const compute = () => {
    if (!deadlineIso) return 0;
    const diffMs = new Date(deadlineIso).getTime() - Date.now();
    return Math.max(0, Math.floor(diffMs / 1000));
  };

  const [secondsLeft, setSecondsLeft] = useState<number>(compute);

  useEffect(() => {
    setSecondsLeft(compute());
    const interval = setInterval(() => setSecondsLeft(compute()), 1000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deadlineIso]);

  const minutes = Math.floor(secondsLeft / 60);
  const seconds = secondsLeft % 60;
  return {
    secondsLeft,
    label: `${minutes}:${seconds.toString().padStart(2, '0')}`,
    expired: deadlineIso !== null && secondsLeft === 0,
  };
}
