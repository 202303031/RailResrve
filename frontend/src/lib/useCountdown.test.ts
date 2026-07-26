import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useCountdown } from './useCountdown';

describe('useCountdown', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('counts down to a deadline and formats mm:ss', () => {
    const deadline = new Date(Date.now() + 90_000).toISOString(); // 90s out
    const { result } = renderHook(() => useCountdown(deadline));

    expect(result.current.label).toBe('1:30');
    expect(result.current.expired).toBe(false);

    act(() => { vi.advanceTimersByTime(31_000); });
    expect(result.current.label).toBe('0:59');
  });

  it('reports expired at the deadline and never goes negative', () => {
    const deadline = new Date(Date.now() + 2_000).toISOString();
    const { result } = renderHook(() => useCountdown(deadline));

    act(() => { vi.advanceTimersByTime(5_000); });
    expect(result.current.secondsLeft).toBe(0);
    expect(result.current.expired).toBe(true);
  });
});
