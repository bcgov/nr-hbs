import { useState } from 'react';

export interface LazyTabs {
  /** The tab showing — pass to Carbon `<Tabs selectedIndex>`. */
  selected: number;
  /** Pass to Carbon `<Tabs onChange>`. */
  onChange: (state: { selectedIndex: number }) => void;
  /** Whether a tab has been opened yet, i.e. whether its panel should be mounted. */
  isOpened: (index: number) => boolean;
}

interface State {
  key: string;
  selected: number;
  opened: ReadonlySet<number>;
}

/**
 * Controlled state for a detail page's Carbon Tabs that mounts a panel only
 * once its tab is first opened, then keeps it — so a page doesn't load every
 * tab up front, and going back to a tab doesn't load it again.
 *
 * Call it in the page, above its loading boundary: a save that reloads the
 * record unmounts the tabs, and state held here brings the user back to the
 * same tab. A different `recordKey` (another record) starts on the first tab.
 */
export function useLazyTabs(recordKey: string): LazyTabs {
  const [state, setState] = useState<State>(() => fresh(recordKey));
  const current = state.key === recordKey ? state : fresh(recordKey);

  return {
    selected: current.selected,
    onChange: ({ selectedIndex }) =>
      setState({
        key: recordKey,
        selected: selectedIndex,
        opened: new Set(current.opened).add(selectedIndex),
      }),
    isOpened: (index) => current.opened.has(index),
  };
}

function fresh(key: string): State {
  return { key, selected: 0, opened: new Set([0]) };
}
