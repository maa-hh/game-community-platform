package com.game.community.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.audit.mapper.ModerationTaskMapper;
import com.game.community.common.constant.audit.ModerationConstants;
import com.game.community.model.entity.audit.ModerationTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 审核任务状态持久化：每个状态跃迁独立提交，避免远程副作用导致任务状态回滚。 */
@Service
@RequiredArgsConstructor
public class ModerationTaskPersistenceService {

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final ModerationTaskMapper taskMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markActionStarted(Long taskId, Long handlerId, String claimToken,
                                     String requestId, int expectedVersion) {
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                .eq(ModerationTask::getId, taskId)
                .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                .eq(ModerationTask::getHandlerId, handlerId)
                .eq(ModerationTask::getClaimToken, claimToken)
                .eq(ModerationTask::getActionRequestId, "")
                .eq(ModerationTask::getVersion, expectedVersion)
                .set(ModerationTask::getActionRequestId, requestId)
                .setSql("version = version + 1")
                .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
        return updated > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int markCompleted(Long taskId, Long handlerId, String claimToken,
                             String requestId, String action, String remark) {
        return taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                .eq(ModerationTask::getId, taskId)
                .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                .eq(ModerationTask::getHandlerId, handlerId)
                .eq(ModerationTask::getClaimToken, claimToken)
                .eq(ModerationTask::getActionRequestId, requestId)
                .set(ModerationTask::getStatus, ModerationConstants.TaskStatus.COMPLETED)
                .set(ModerationTask::getHandleAction, action)
                .set(ModerationTask::getHandleRemark, remark)
                .set(ModerationTask::getHandleTime, LocalDateTime.now())
                .set(ModerationTask::getLeaseExpireTime, EPOCH)
                .set(ModerationTask::getLastError, "")
                .setSql("version = version + 1")
                .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releaseAction(Long taskId, Long handlerId, String claimToken,
                              String requestId, String error) {
        taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                .eq(ModerationTask::getId, taskId)
                .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                .eq(ModerationTask::getClaimToken, claimToken)
                .eq(ModerationTask::getActionRequestId, requestId)
                .eq(ModerationTask::getHandlerId, handlerId)
                .set(ModerationTask::getStatus, ModerationConstants.TaskStatus.PENDING)
                .set(ModerationTask::getHandlerId, null)
                .set(ModerationTask::getClaimTime, null)
                .set(ModerationTask::getLeaseExpireTime, EPOCH)
                .set(ModerationTask::getClaimToken, "")
                .set(ModerationTask::getActionRequestId, "")
                .set(ModerationTask::getLastError, error)
                .setSql("version = version + 1")
                .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
    }
}
