import { ArrowRight } from '@carbon/icons-react';
import { Button } from '@carbon/react';
import { useNavigate } from 'react-router-dom';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import PageLayout from '@/pages/PageLayout';

import type { FC } from 'react';

/**
 * In-shell 404 for authenticated users who hit an unknown path under a valid
 * area. (Unauthenticated / role-denied cases are handled by Landing /
 * Forbidden.)
 */
const NotFound: FC = () => {
  const navigate = useNavigate();
  return (
    <PageLayout title="Page not found" subtitle="The requested page couldn't be located">
      <EmptyState
        title="We couldn't find that page"
        body="The page you’re looking for doesn’t exist or may have moved."
        action={
          <Button size="md" renderIcon={ArrowRight} onClick={() => navigate('/home')}>
            Go to home
          </Button>
        }
      />
    </PageLayout>
  );
};

export default NotFound;
