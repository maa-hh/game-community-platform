import { useCallback, useState } from 'react';

import type { ReportTargetType } from '@/components/ReportModal/types';

export function useReportModal() {
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState<{
    type: ReportTargetType;
    id: string;
    title?: string;
  } | null>(null);

  const openReport = useCallback(
    (type: ReportTargetType, id: string, title?: string) => {
      setTarget({ type, id, title });
      setOpen(true);
    },
    [],
  );

  const closeReport = useCallback(() => {
    setOpen(false);
  }, []);

  return {
    reportOpen: open,
    reportTarget: target,
    openReport,
    closeReport,
  };
}
