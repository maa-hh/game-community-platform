import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

import type { PostSubTabKey } from '@/types/profile';
import { canGoBackInApp } from '@/utils/returnNavigation';

/** 返回上一页；无历史时回个人页指定 Tab */
export function useGoBack(fallbackTab: PostSubTabKey = 'draft') {
  const navigate = useNavigate();

  return useCallback(() => {
    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
    if (canGoBackInApp()) {
      navigate(-1);
      return;
    }
    navigate('/profile', { state: { postSubTab: fallbackTab } });
  }, [navigate, fallbackTab]);
}
