import { useEffect } from 'react';

import { useAppDispatch, useAppSelector } from '@/store';
import { fetchCurrentUserAction } from '@/store/modules/auth';
import { isFieldBusy } from '@/service/types';

const PROFILE_AUDIT_POLL_INTERVAL_MS = 5000;

/**
 * SSE 之外的审核状态兜底同步。
 * /user/me 会在查询时被动刷新审核任务状态，因此只在存在审核任务时轮询，
 * 所有字段结束审核后自动停止，避免给正常登录用户制造常驻请求。
 */
export function useProfileAuditPoll() {
  const dispatch = useAppDispatch();
  const accessToken = useAppSelector((state) => state.auth.accessToken);
  const usernameAuditStatus = useAppSelector(
    (state) => state.auth.user?.usernameAuditStatus,
  );
  const signatureAuditStatus = useAppSelector(
    (state) => state.auth.user?.signatureAuditStatus,
  );
  const avatarAuditStatus = useAppSelector(
    (state) => state.auth.user?.avatarAuditStatus,
  );

  const hasPendingAudit =
    isFieldBusy(usernameAuditStatus) ||
    isFieldBusy(signatureAuditStatus) ||
    isFieldBusy(avatarAuditStatus);

  useEffect(() => {
    if (!accessToken || !hasPendingAudit) return;

    let cancelled = false;
    let requestInFlight = false;

    const poll = () => {
      if (cancelled || requestInFlight) return;
      requestInFlight = true;
      void dispatch(fetchCurrentUserAction()).finally(() => {
        requestInFlight = false;
      });
    };

    poll();
    const timer = window.setInterval(poll, PROFILE_AUDIT_POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [accessToken, dispatch, hasPendingAudit]);
}
