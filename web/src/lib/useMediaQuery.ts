import { useSyncExternalStore } from 'react';

/** Whether a media query matches now, updating as it changes. Without matchMedia, as in jsdom, it assumes a match. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (typeof matchMedia !== 'function') {
        return () => {};
      }
      const list = matchMedia(query);
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    () => typeof matchMedia !== 'function' || matchMedia(query).matches,
  );
}
