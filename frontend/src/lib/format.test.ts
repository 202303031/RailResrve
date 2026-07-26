import { describe, expect, it } from 'vitest';
import { classNames, formatCurrency, formatDuration } from './format';

describe('format helpers', () => {
  it('formats currency in INR', () => {
    expect(formatCurrency(450)).toContain('450');
    expect(formatCurrency(1350.5)).toContain('₹');
  });

  it('formats a duration as hours and padded minutes', () => {
    expect(formatDuration(125)).toBe('2h 05m');
    expect(formatDuration(60)).toBe('1h 00m');
  });

  it('joins truthy class names only', () => {
    expect(classNames('a', false, undefined, 'b', null)).toBe('a b');
  });
});
