import { afterEach, describe, expect, it, vi } from 'vitest';
import { tokenStorage } from './tokenStorage';

describe('tokenStorage', () => {
  afterEach(() => tokenStorage.clear());

  it('stores and returns the token pair', () => {
    tokenStorage.set('access-1', 'refresh-1');
    expect(tokenStorage.getAccess()).toBe('access-1');
    expect(tokenStorage.getRefresh()).toBe('refresh-1');
  });

  it('notifies subscribers on set and clear', () => {
    const listener = vi.fn();
    const unsubscribe = tokenStorage.subscribe(listener);

    tokenStorage.set('a', 'b');
    tokenStorage.clear();
    expect(listener).toHaveBeenCalledTimes(2);

    unsubscribe();
    tokenStorage.set('c', 'd');
    expect(listener).toHaveBeenCalledTimes(2); // no more after unsubscribe
  });
});
