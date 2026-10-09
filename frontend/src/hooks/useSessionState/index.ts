import { useCallback, useState, type SetStateAction } from 'react';

/**
 * `useState` kept in `sessionStorage` under `key`, so it survives leaving the
 * page and coming back (the search screens' criteria). Per browser tab; a new
 * tab starts from `initial`. Storage being unavailable only loses the memory.
 */
export function useSessionState<T>(
  key: string,
  initial: T,
): [T, (value: SetStateAction<T>) => void] {
  const [value, setValue] = useState<T>(() => {
    try {
      const raw = sessionStorage.getItem(key);
      return raw == null ? initial : (JSON.parse(raw) as T);
    } catch {
      return initial;
    }
  });

  const set = useCallback(
    (next: SetStateAction<T>) =>
      setValue((prev) => {
        const resolved = typeof next === 'function' ? (next as (p: T) => T)(prev) : next;
        try {
          sessionStorage.setItem(key, JSON.stringify(resolved));
        } catch {
          // Storage full or disabled — the page still works, it just won't remember.
        }
        return resolved;
      }),
    [key],
  );

  return [value, set];
}

/** A search screen's last run search: what to run again on coming back. */
export interface LastSearch<C> {
  criteria: C;
  page: number;
  size: number;
}
