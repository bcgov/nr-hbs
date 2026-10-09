import { InlineNotification, Button, Loading } from '@carbon/react';

import './AsyncBoundary.css';

import type { FC, ReactNode } from 'react';

interface AsyncBoundaryProps {
  loading: boolean;
  error?: string;
  onRetry?: () => void;
  /** Message shown while loading. */
  loadingText?: string;
  children: ReactNode;
}

/**
 * Standard loading/error wrapper for screens that read from the backend.
 * Shows a large spinner centred over the space the content will fill while
 * `loading`, an error notification (with an
 * optional Retry) when `error` is set, otherwise the children. Keeps the
 * loading/error treatment identical across every FTA screen.
 */
const AsyncBoundary: FC<AsyncBoundaryProps> = ({
  loading,
  error,
  onRetry,
  loadingText = 'Loading…',
  children,
}) => {
  if (loading) {
    return (
      <div className="async-boundary__loading">
        <Loading withOverlay={false} description={loadingText} />
        {/* Carbon's spinner carries the text for screen readers only. */}
        <p className="async-boundary__loading-text" aria-hidden="true">
          {loadingText}
        </p>
      </div>
    );
  }
  if (error) {
    return (
      <div>
        <InlineNotification
          kind="error"
          lowContrast
          title="Couldn’t load data"
          subtitle={error}
          hideCloseButton
        />
        {onRetry && (
          <Button kind="tertiary" size="sm" onClick={onRetry} style={{ marginTop: '1rem' }}>
            Retry
          </Button>
        )}
      </div>
    );
  }
  return <>{children}</>;
};

export default AsyncBoundary;
