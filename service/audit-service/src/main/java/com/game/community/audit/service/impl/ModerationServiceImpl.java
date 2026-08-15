package com.game.community.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.audit.event.NotificationEventProducer;
import com.game.community.audit.mapper.ModerationTaskMapper;
import com.game.community.audit.service.ModerationService;
import com.game.community.audit.util.ModerationSummaryUtils;
import com.game.community.common.constant.audit.ModerationConstants;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.DanmakuFeignClient;
import com.game.community.feign.SocialFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.audit.HandleModerationTaskDTO;
import com.game.community.model.entity.audit.ModerationTask;
import com.game.community.model.message.ModerationTaskMessage;
import com.game.community.model.message.ReportAuditMessage;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.audit.ModerationTaskDetailVO;
import com.game.community.model.vo.audit.ModerationTaskClaimVO;
import com.game.community.model.vo.audit.ModerationTaskVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.ReplyVO;
import com.game.community.model.vo.danmaku.DanmakuVO;
import com.game.community.model.vo.user.UserAuditTaskBriefVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private static final Set<String> REPORT_ACTIONS = Set.of(
            ModerationConstants.HandleAction.NO_VIOLATION,
            ModerationConstants.HandleAction.OFFLINE_ARTICLE,
            ModerationConstants.HandleAction.HIDE_COMMENT,
            ModerationConstants.HandleAction.HIDE_REPLY,
            ModerationConstants.HandleAction.HIDE_DANMAKU,
            ModerationConstants.HandleAction.BAN_USER
    );

    private static final Set<String> TASK_TYPES = Set.of(
            ModerationConstants.TaskType.REPORT,
            ModerationConstants.TaskType.ARTICLE_AUDIT,
            ModerationConstants.TaskType.PROFILE_AUDIT
    );

    private final ModerationTaskMapper taskMapper;
    private final ContentFeignClient contentFeignClient;
    private final DanmakuFeignClient danmakuFeignClient;
    private final SocialFeignClient socialFeignClient;
    private final UserFeignClient userFeignClient;
    private final NotificationEventProducer notificationEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveTask(ModerationTaskMessage message) {
        if (message == null || !StringUtils.hasText(message.getTaskType()) || message.getSourceId() == null) {
            return;
        }
        ModerationTask latest = taskMapper.selectOne(new LambdaQueryWrapper<ModerationTask>()
                .eq(ModerationTask::getTaskType, message.getTaskType())
                .eq(ModerationTask::getSourceId, message.getSourceId())
                .orderByDesc(ModerationTask::getId)
                .last("LIMIT 1"));
        if (latest != null && isDuplicateTaskEvent(latest, message)) {
            return;
        }
        ModerationTask task = new ModerationTask();
        task.setPublicId(UUID.randomUUID().toString());
        task.setTaskType(message.getTaskType());
        task.setSourceId(message.getSourceId());
        task.setTargetType(message.getTargetType());
        task.setTargetId(message.getTargetId());
        task.setSubjectUserId(message.getSubjectUserId());
        task.setReporterId(message.getReporterId());
        task.setReason(message.getReason());
        task.setSummary(StringUtils.hasText(message.getSummary())
                ? message.getSummary()
                : ModerationSummaryUtils.firstLines(message.getReason()));
        task.setExtraPayload(message.getExtraPayload());
        task.setTargetStatusSnapshot(message.getTargetStatusSnapshot());
        task.setTargetUpdatedAt(message.getTargetUpdatedAt());
        task.setStatus(ModerationConstants.TaskStatus.PENDING);
        task.setVersion(0);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ignored) {
            // 幂等
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveReport(ReportAuditMessage message) {
        if (message == null || message.getReportId() == null) {
            return;
        }
        ModerationTaskMessage taskMessage = new ModerationTaskMessage();
        taskMessage.setTaskType(ModerationConstants.TaskType.REPORT);
        taskMessage.setSourceId(message.getReportId());
        taskMessage.setTargetType(message.getTargetType());
        taskMessage.setTargetId(message.getTargetId());
        taskMessage.setSubjectUserId(message.getReportedUserId());
        taskMessage.setReporterId(message.getReporterId());
        taskMessage.setReason(message.getReason());
        // Kafka 消费只负责快速落库；目标正文在管理员打开详情时按需读取，避免远程调用阻塞消费线程。
        taskMessage.setSummary(ModerationSummaryUtils.firstLines(message.getReason()));
        taskMessage.setEventTime(message.getEventTime());
        receiveTask(taskMessage);
    }

    @Override
    public PageResult<ModerationTaskVO> pageTasks(Long page, Long size, Integer status, String taskType) {
        if (StringUtils.hasText(taskType) && !TASK_TYPES.contains(taskType)) {
            throw new BusinessException("审核工单类型不合法");
        }
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<ModerationTask> result = taskMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<ModerationTask>()
                        .eq(status != null, ModerationTask::getStatus, status)
                        .eq(StringUtils.hasText(taskType), ModerationTask::getTaskType, taskType)
                        .orderByDesc(ModerationTask::getCreateTime)
                        .orderByDesc(ModerationTask::getId));
        Map<Long, UserCardInternalVO> users = userMap(result.getRecords().stream()
                .flatMap(task -> java.util.stream.Stream.of(
                        task.getSubjectUserId(), task.getReporterId(), task.getHandlerId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<ModerationTaskVO> records = result.getRecords().stream()
                .map(task -> toVO(task, users))
                .toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public ModerationTaskDetailVO getDetail(String taskKey, Long viewerId) {
        ModerationTask task = requireTask(taskKey);
        Map<Long, UserCardInternalVO> users = userMap(Stream.of(task.getSubjectUserId(), task.getReporterId(), task.getHandlerId())
                .filter(Objects::nonNull).toList());
        ModerationTaskDetailVO vo = toDetailVO(task, users);
        fillTargetDetail(vo, task, users.get(task.getSubjectUserId()));
        enrichReviewContext(vo, task);
        if (Objects.equals(task.getStatus(), ModerationConstants.TaskStatus.PROCESSING)
                && task.getHandlerId() != null
                && !Objects.equals(task.getHandlerId(), viewerId)) {
            vo.setHandleRemark("该工单正由其他管理员处理");
        } else if (Objects.equals(task.getHandlerId(), viewerId)
                && StringUtils.hasText(task.getClaimToken())) {
            vo.setClaimToken(task.getClaimToken());
            vo.setLeaseExpireTime(task.getLeaseExpireTime());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModerationTaskClaimVO claim(String taskKey, Long handlerId) {
        if (handlerId == null || handlerId <= 0) {
            throw new BusinessException("处理人身份无效");
        }
        ModerationTask task = requireTask(taskKey);
        if (Objects.equals(task.getStatus(), ModerationConstants.TaskStatus.COMPLETED)) {
            throw new BusinessException("该工单已处理完成");
        }
        LocalDateTime now = LocalDateTime.now();
        if (Objects.equals(task.getStatus(), ModerationConstants.TaskStatus.PROCESSING)) {
            if (Objects.equals(task.getHandlerId(), handlerId)
                    && task.getLeaseExpireTime() != null
                    && task.getLeaseExpireTime().isAfter(now)
                    && StringUtils.hasText(task.getClaimToken())) {
                return claimVO(task);
            }
            if (task.getLeaseExpireTime() == null || task.getLeaseExpireTime().isAfter(now)) {
                throw new BusinessException("该工单正由其他管理员处理");
            }
        }
        ModerationTaskClaimVO result = claimInternal(task, handlerId, now);
        if (result == null) {
            throw new BusinessException("认领失败，请刷新后重试");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(String taskKey, Long handlerId, HandleModerationTaskDTO dto) {
        if (dto == null) {
            throw new BusinessException("处理参数不能为空");
        }
        validateAction(dto.getHandleAction());
        ModerationTask task = requireTask(taskKey);
        if (Objects.equals(task.getStatus(), ModerationConstants.TaskStatus.COMPLETED)) {
            if (Objects.equals(task.getActionRequestId(), dto.getRequestId())) {
                return;
            }
            throw new BusinessException("该工单已处理完成");
        }
        if (!Objects.equals(task.getStatus(), ModerationConstants.TaskStatus.PROCESSING)
                || !Objects.equals(task.getHandlerId(), handlerId)
                || !Objects.equals(task.getClaimToken(), dto.getClaimToken())
                || task.getLeaseExpireTime() == null
                || !task.getLeaseExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("认领已失效，请重新认领工单");
        }
        validateBeforeHandle(task, dto.getHandleAction());
        int expectedVersion = task.getVersion() == null ? 0 : task.getVersion();
        int started = taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                .eq(ModerationTask::getId, task.getId())
                .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                .eq(ModerationTask::getHandlerId, handlerId)
                .eq(ModerationTask::getClaimToken, dto.getClaimToken())
                .eq(ModerationTask::getActionRequestId, "")
                .eq(ModerationTask::getVersion, expectedVersion)
                .set(ModerationTask::getActionRequestId, dto.getRequestId())
                .setSql("version = version + 1")
                .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
        if (started == 0) {
            ModerationTask latest = requireTaskById(task.getId());
            if (Objects.equals(latest.getStatus(), ModerationConstants.TaskStatus.COMPLETED)
                    && Objects.equals(latest.getActionRequestId(), dto.getRequestId())) {
                return;
            }
            throw new BusinessException("该工单正在处理，请勿重复提交");
        }
        try {
            applyAction(task, dto.getHandleAction(), dto.getHandleRemark(), handlerId);
            int finished = taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                    .eq(ModerationTask::getId, task.getId())
                    .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                    .eq(ModerationTask::getHandlerId, handlerId)
                    .eq(ModerationTask::getClaimToken, dto.getClaimToken())
                    .eq(ModerationTask::getActionRequestId, dto.getRequestId())
                    .set(ModerationTask::getStatus, ModerationConstants.TaskStatus.COMPLETED)
                    .set(ModerationTask::getHandleAction, dto.getHandleAction())
                    .set(ModerationTask::getHandleRemark, dto.getHandleRemark())
                    .set(ModerationTask::getHandleTime, LocalDateTime.now())
                    .set(ModerationTask::getLeaseExpireTime, LocalDateTime.of(1970, 1, 1, 0, 0))
                    .set(ModerationTask::getLastError, "")
                    .setSql("version = version + 1")
                    .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
            if (finished == 0) {
                throw new BusinessException("处理失败，请刷新后重试");
            }
            publishNotifications(task, dto.getHandleAction(), dto.getHandleRemark());
        } catch (RuntimeException e) {
            taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                    .eq(ModerationTask::getId, task.getId())
                    .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                    .eq(ModerationTask::getClaimToken, dto.getClaimToken())
                    .eq(ModerationTask::getActionRequestId, dto.getRequestId())
                    .eq(ModerationTask::getHandlerId, handlerId)
                    .set(ModerationTask::getStatus, ModerationConstants.TaskStatus.PENDING)
                    .set(ModerationTask::getHandlerId, null)
                    .set(ModerationTask::getClaimTime, null)
                    .set(ModerationTask::getLeaseExpireTime, LocalDateTime.of(1970, 1, 1, 0, 0))
                    .set(ModerationTask::getActionRequestId, "")
                    .set(ModerationTask::getLastError, abbreviate(e.getMessage()))
                    .setSql("version = version + 1")
                    .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
            throw e;
        }
    }

    @Override
    public int autoPassExpiredTasks() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(ModerationConstants.AUTO_PASS_DAYS);
        List<ModerationTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ModerationTask>()
                .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PENDING)
                .lt(ModerationTask::getCreateTime, deadline)
                .last("LIMIT 200"));
        int count = 0;
        for (ModerationTask task : tasks) {
            String action = resolveAutoPassAction(task.getTaskType());
            HandleModerationTaskDTO dto = new HandleModerationTaskDTO();
            dto.setHandleAction(action);
            dto.setHandleRemark("超过3天未处理，系统自动通过");
            ModerationTaskClaimVO claim = null;
            try {
                claim = claimInternal(task, ModerationConstants.SYSTEM_HANDLER_ID, LocalDateTime.now());
                if (claim == null) {
                    continue;
                }
                ModerationTask processing = requireTaskById(task.getId());
                if (!isReviewable(processing)) {
                    releaseClaim(processing, claim.getClaimToken(), "目标已变化，等待人工确认");
                    continue;
                }
                applyAction(processing, action, dto.getHandleRemark(), 0L);
                taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                        .eq(ModerationTask::getId, task.getId())
                        .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                        .eq(ModerationTask::getClaimToken, claim.getClaimToken())
                        .eq(ModerationTask::getActionRequestId, "")
                        .set(ModerationTask::getStatus, ModerationConstants.TaskStatus.COMPLETED)
                        .set(ModerationTask::getHandleAction, action)
                        .set(ModerationTask::getHandleRemark, dto.getHandleRemark())
                        .set(ModerationTask::getHandleTime, LocalDateTime.now())
                        .set(ModerationTask::getLeaseExpireTime, LocalDateTime.of(1970, 1, 1, 0, 0))
                        .set(ModerationTask::getLastError, "")
                        .setSql("version = version + 1")
                        .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
                publishNotifications(processing, action, dto.getHandleRemark());
                count++;
            } catch (RuntimeException ignored) {
                if (claim != null) {
                    releaseClaim(task, claim.getClaimToken(), abbreviate(ignored.getMessage()));
                }
            }
        }
        return count;
    }

    @Override
    public int recoverExpiredClaims() {
        return taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                .lt(ModerationTask::getLeaseExpireTime, LocalDateTime.now())
                .set(ModerationTask::getStatus, ModerationConstants.TaskStatus.PENDING)
                .set(ModerationTask::getHandlerId, null)
                .set(ModerationTask::getClaimTime, null)
                .set(ModerationTask::getLeaseExpireTime, LocalDateTime.of(1970, 1, 1, 0, 0))
                .set(ModerationTask::getClaimToken, "")
                .set(ModerationTask::getActionRequestId, "")
                .set(ModerationTask::getLastError, "认领租约已过期，任务重新入队")
                .setSql("version = version + 1")
                .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
    }

    private String resolveAutoPassAction(String taskType) {
        if (ModerationConstants.TaskType.ARTICLE_AUDIT.equals(taskType)) {
            return ModerationConstants.HandleAction.AUDIT_APPROVE;
        }
        if (ModerationConstants.TaskType.PROFILE_AUDIT.equals(taskType)) {
            return ModerationConstants.HandleAction.PROFILE_APPROVE;
        }
        return ModerationConstants.HandleAction.NO_VIOLATION;
    }

    private boolean isDuplicateTaskEvent(ModerationTask latest, ModerationTaskMessage message) {
        if (ModerationConstants.TaskType.REPORT.equals(message.getTaskType())) {
            return true;
        }
        return Objects.equals(latest.getTargetUpdatedAt(), message.getTargetUpdatedAt());
    }

    private ModerationTaskClaimVO claimInternal(ModerationTask task, Long handlerId, LocalDateTime now) {
        if (task == null || task.getId() == null) {
            return null;
        }
        int version = task.getVersion() == null ? 0 : task.getVersion();
        String token = UUID.randomUUID().toString();
        LocalDateTime leaseExpireTime = now.plusMinutes(ModerationConstants.CLAIM_LEASE_MINUTES);
        LambdaUpdateWrapper<ModerationTask> wrapper = new LambdaUpdateWrapper<ModerationTask>()
                .eq(ModerationTask::getId, task.getId())
                .eq(ModerationTask::getVersion, version);
        if (Objects.equals(task.getStatus(), ModerationConstants.TaskStatus.PENDING)) {
            wrapper.eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PENDING);
        } else {
            wrapper.eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                    .le(ModerationTask::getLeaseExpireTime, now);
        }
        int claimed = taskMapper.update(null, wrapper
                .set(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                .set(ModerationTask::getHandlerId, handlerId)
                .set(ModerationTask::getClaimTime, now)
                .set(ModerationTask::getLeaseExpireTime, leaseExpireTime)
                .set(ModerationTask::getClaimToken, token)
                .set(ModerationTask::getActionRequestId, "")
                .set(ModerationTask::getLastError, "")
                .setSql("version = version + 1, attempt_count = attempt_count + 1")
                .set(ModerationTask::getUpdateTime, now));
        if (claimed == 0) {
            return null;
        }
        ModerationTaskClaimVO result = new ModerationTaskClaimVO();
        result.setTaskKey(task.getPublicId());
        result.setClaimToken(token);
        result.setHandlerAccountId(accountIdOf(handlerId));
        result.setVersion(version + 1);
        result.setLeaseExpireTime(leaseExpireTime);
        return result;
    }

    private ModerationTaskClaimVO claimVO(ModerationTask task) {
        ModerationTaskClaimVO result = new ModerationTaskClaimVO();
        result.setTaskKey(task.getPublicId());
        result.setClaimToken(task.getClaimToken());
        result.setHandlerAccountId(accountIdOf(task.getHandlerId()));
        result.setVersion(task.getVersion());
        result.setLeaseExpireTime(task.getLeaseExpireTime());
        return result;
    }

    private void releaseClaim(ModerationTask task, String claimToken, String error) {
        if (task == null || task.getId() == null || !StringUtils.hasText(claimToken)) {
            return;
        }
        taskMapper.update(null, new LambdaUpdateWrapper<ModerationTask>()
                .eq(ModerationTask::getId, task.getId())
                .eq(ModerationTask::getStatus, ModerationConstants.TaskStatus.PROCESSING)
                .eq(ModerationTask::getClaimToken, claimToken)
                .set(ModerationTask::getStatus, ModerationConstants.TaskStatus.PENDING)
                .set(ModerationTask::getHandlerId, null)
                .set(ModerationTask::getClaimTime, null)
                .set(ModerationTask::getLeaseExpireTime, LocalDateTime.of(1970, 1, 1, 0, 0))
                .set(ModerationTask::getClaimToken, "")
                .set(ModerationTask::getActionRequestId, "")
                .set(ModerationTask::getLastError, abbreviate(error))
                .setSql("version = version + 1")
                .set(ModerationTask::getUpdateTime, LocalDateTime.now()));
    }

    private String abbreviate(String value) {
        if (!StringUtils.hasText(value)) {
            return "处理失败，请稍后重试";
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private void validateAction(String action) {
        if (!StringUtils.hasText(action)) {
            throw new BusinessException("处理结果不合法");
        }
    }

    private void applyAction(ModerationTask task, String action, String remark, Long handlerId) {
        if (ModerationConstants.TaskType.REPORT.equals(task.getTaskType())) {
            applyReportAction(task, action, remark, handlerId);
            return;
        }
        if (ModerationConstants.TaskType.ARTICLE_AUDIT.equals(task.getTaskType())) {
            applyArticleAuditAction(task, action, remark);
            return;
        }
        if (ModerationConstants.TaskType.PROFILE_AUDIT.equals(task.getTaskType())) {
            applyProfileAuditAction(task, action, remark);
            return;
        }
        throw new BusinessException("工单类型不支持");
    }

    private void applyReportAction(ModerationTask task, String action, String remark, Long handlerId) {
        if (!REPORT_ACTIONS.contains(action)) {
            throw new BusinessException("举报处理结果不合法");
        }
        if (!ModerationConstants.HandleAction.NO_VIOLATION.equals(action)
                && !isReportActionAvailable(task, action)) {
            throw new BusinessException("举报目标当前无法执行该处理措施，请选择「通过（不处理）」或刷新详情后重试");
        }
        Integer reportStatus = ModerationConstants.HandleAction.NO_VIOLATION.equals(action)
                ? SocialConstants.ReportStatus.REJECTED
                : SocialConstants.ReportStatus.ACCEPTED;
        if (!ModerationConstants.HandleAction.NO_VIOLATION.equals(action)) {
            if (ModerationConstants.HandleAction.OFFLINE_ARTICLE.equals(action)) {
                unwrap(contentFeignClient.updateArticleStatus(task.getTargetId(), ContentConstants.ArticleStatus.OFFLINE),
                        "下架文章失败");
            } else if (ModerationConstants.HandleAction.HIDE_COMMENT.equals(action)) {
                unwrap(socialFeignClient.hideCommentByAudit(task.getTargetId()), "隐藏评论失败");
            } else if (ModerationConstants.HandleAction.HIDE_REPLY.equals(action)) {
                unwrap(socialFeignClient.hideReplyByAudit(task.getTargetId()), "隐藏回复失败");
            } else if (ModerationConstants.HandleAction.HIDE_DANMAKU.equals(action)) {
                unwrap(danmakuFeignClient.hideMessage(task.getTargetId()), "隐藏弹幕失败");
            } else if (ModerationConstants.HandleAction.BAN_USER.equals(action)) {
                Long userId = task.getTargetType() != null
                        && task.getTargetType() == SocialConstants.ReportTargetType.USER
                        ? task.getTargetId()
                        : task.getSubjectUserId();
                UserCardInternalVO targetUser = tryFetchUser(userId);
                if (targetUser == null || targetUser.getAccountId() == null) {
                    throw new BusinessException("封禁目标用户不存在");
                }
                unwrap(userFeignClient.banUser(targetUser.getAccountId(),
                                StringUtils.hasText(remark) ? remark : "举报成立封禁", null),
                        "封禁用户失败");
            }
        }
        unwrap(socialFeignClient.markReportHandled(
                        task.getSourceId(),
                        reportStatus,
                        handlerId,
                        remark),
                "同步举报状态失败");
    }

    private void applyArticleAuditAction(ModerationTask task, String action, String remark) {
        if (ModerationConstants.HandleAction.AUDIT_APPROVE.equals(action)) {
            unwrap(contentFeignClient.approveArticleManualAudit(task.getTargetId()), "文章审核通过失败");
            return;
        }
        if (ModerationConstants.HandleAction.AUDIT_REJECT.equals(action)) {
            unwrap(contentFeignClient.rejectArticleManualAudit(task.getTargetId(), remark), "文章审核驳回失败");
            return;
        }
        throw new BusinessException("帖子审核处理结果不合法");
    }

    private void applyProfileAuditAction(ModerationTask task, String action, String remark) {
        if (ModerationConstants.HandleAction.PROFILE_APPROVE.equals(action)) {
            unwrap(userFeignClient.approveProfileManualAudit(task.getSourceId()), "资料审核通过失败");
            return;
        }
        if (ModerationConstants.HandleAction.PROFILE_REJECT.equals(action)) {
            unwrap(userFeignClient.rejectProfileManualAudit(task.getSourceId(), remark), "资料审核驳回失败");
            return;
        }
        throw new BusinessException("资料审核处理结果不合法");
    }

    private void publishNotifications(ModerationTask task, String action, String remark) {
        String normalizedRemark = StringUtils.hasText(remark) ? remark : "管理员已完成处理";
        if (ModerationConstants.TaskType.REPORT.equals(task.getTaskType())) {
            publishReportNotifications(task, action, normalizedRemark);
            return;
        }
        if (ModerationConstants.TaskType.ARTICLE_AUDIT.equals(task.getTaskType())) {
            publishArticleAuditNotifications(task, action, normalizedRemark);
        }
    }

    private void publishReportNotifications(ModerationTask task, String action, String remark) {
        RouteContext route = resolveRouteContext(task);
        boolean upheld = !ModerationConstants.HandleAction.NO_VIOLATION.equals(action);
        if (task.getReporterId() != null) {
            notificationEventProducer.publishReportResult(
                    task.getReporterId(),
                    route.routeType(),
                    route.articleId(),
                    route.commentId(),
                    route.replyId(),
                    route.danmakuId(),
                    route.videoPublicId(),
                    route.targetUserId(),
                    task.getSourceId(),
                    upheld ? "你提交的举报已成立" : "你提交的举报未通过",
                    remark);
        }
        if (upheld
                && task.getSubjectUserId() != null
                && !Objects.equals(task.getSubjectUserId(), task.getReporterId())) {
            notificationEventProducer.publishPenaltyResult(
                    task.getSubjectUserId(),
                    route.routeType(),
                    route.articleId(),
                    route.commentId(),
                    route.replyId(),
                    route.danmakuId(),
                    route.videoPublicId(),
                    route.targetUserId(),
                    task.getSourceId(),
                    ModerationConstants.HandleAction.BAN_USER.equals(action)
                            ? "你的账号因举报已被封禁"
                            : "你发布的内容因举报已被处理",
                    remark);
        }
    }

    private void publishArticleAuditNotifications(ModerationTask task, String action, String remark) {
        if (task.getSubjectUserId() == null) {
            return;
        }
        RouteContext route = new RouteContext(NotificationConstants.RouteType.ARTICLE, task.getTargetId(), null, null,
                null, null, null);
        if (ModerationConstants.HandleAction.AUDIT_APPROVE.equals(action)) {
            notificationEventProducer.publishArticleAuditPassed(
                    task.getSubjectUserId(),
                    route.articleId(),
                    "你的帖子已通过人工审核并发布",
                    remark);
            return;
        }
        // 驳回由 content-service 的 ARTICLE_AUDIT_REJECTED 事件统一发送，避免重复通知。
    }

    private void fillTargetDetail(ModerationTaskDetailVO vo, ModerationTask task, UserCardInternalVO subjectUser) {
        if (task.getTargetType() == null || task.getTargetId() == null) {
            return;
        }
        boolean safeFetch = ModerationConstants.TaskType.REPORT.equals(task.getTaskType());
        if (task.getTargetType() == SocialConstants.ReportTargetType.ARTICLE
                || ModerationConstants.TaskType.ARTICLE_AUDIT.equals(task.getTaskType())) {
            ArticleDetailVO detail = safeFetch
                    ? tryFetchArticle(task.getTargetId())
                    : unwrap(contentFeignClient.getArticleDetail(task.getTargetId()), "文章服务暂不可用");
            vo.setTargetPublicId(detail == null ? null : detail.getPublicId());
            vo.setTargetTitle(detail == null ? null : detail.getTitle());
            vo.setTargetContent(detail == null ? null
                    : (StringUtils.hasText(detail.getContent()) ? detail.getContent() : detail.getSummary()));
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.COMMENT) {
            CommentVO comment = safeFetch
                    ? tryFetchComment(task.getTargetId())
                    : unwrap(socialFeignClient.getCommentDetail(task.getTargetId()), "社交服务暂不可用");
            vo.setTargetPublicId(comment == null || comment.getArticleId() == null
                    ? null : articlePublicId(comment.getArticleId()));
            vo.setTargetTitle("评论");
            vo.setTargetContent(comment == null ? null : comment.getContent());
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.REPLY) {
            ReplyVO reply = safeFetch
                    ? tryFetchReply(task.getTargetId())
                    : unwrap(socialFeignClient.getReplyDetail(task.getTargetId()), "社交服务暂不可用");
            vo.setTargetPublicId(reply == null || reply.getArticleId() == null
                    ? null : articlePublicId(reply.getArticleId()));
            vo.setTargetTitle("回复");
            vo.setTargetContent(reply == null ? null : reply.getContent());
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.DANMAKU) {
            var danmaku = safeFetch
                    ? tryFetchDanmaku(task.getTargetId())
                    : unwrap(danmakuFeignClient.getMessage(task.getTargetId()), "弹幕服务暂不可用");
            vo.setTargetPublicId(danmaku == null ? null : danmaku.getVideoPublicId());
            vo.setTargetTitle("弹幕");
            vo.setTargetContent(danmaku == null ? null : danmaku.getContent());
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.USER
                || ModerationConstants.TaskType.PROFILE_AUDIT.equals(task.getTaskType())) {
            vo.setTargetAccountId(subjectUser == null ? null : subjectUser.getAccountId());
            vo.setTargetTitle(subjectUser == null ? "用户" : subjectUser.getUsername());
            vo.setTargetContent(subjectUser == null ? null : subjectUser.getSignature());
        }
    }

    private String articlePublicId(Long articleId) {
        ArticleDetailVO article = tryFetchArticle(articleId);
        return article == null ? null : article.getPublicId();
    }

    private void enrichReviewContext(ModerationTaskDetailVO vo, ModerationTask task) {
        vo.setTargetStatusSnapshot(task.getTargetStatusSnapshot());
        vo.setTargetUpdatedAt(task.getTargetUpdatedAt());
        if (ModerationConstants.TaskType.ARTICLE_AUDIT.equals(task.getTaskType())) {
            enrichArticleAuditContext(vo, task);
            return;
        }
        if (ModerationConstants.TaskType.PROFILE_AUDIT.equals(task.getTaskType())) {
            enrichProfileAuditContext(vo, task);
            return;
        }
        if (ModerationConstants.TaskType.REPORT.equals(task.getTaskType())) {
            enrichReportContext(vo, task);
        }
    }

    private void enrichArticleAuditContext(ModerationTaskDetailVO vo, ModerationTask task) {
        ArticleDetailVO article = tryFetchArticle(task.getTargetId());
        if (article == null) {
            vo.setCurrentTargetStatus("帖子不存在");
            vo.setTargetContentChanged(Boolean.FALSE);
            vo.setReviewable(Boolean.FALSE);
            vo.setReviewBlockReason("帖子不存在或已删除，无法审核");
            return;
        }
        vo.setCurrentTargetStatus(articleStatusLabel(article.getStatus()));
        vo.setCurrentTargetUpdatedAt(article.getUpdateTime());
        boolean statusOk = Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PENDING);
        boolean timeOk = isSameInstant(task.getTargetUpdatedAt(), article.getUpdateTime());
        vo.setTargetContentChanged(task.getTargetUpdatedAt() != null && !timeOk);
        vo.setReviewable(statusOk && timeOk);
        if (!statusOk) {
            vo.setReviewBlockReason("帖子已不在审核中（当前：" + articleStatusLabel(article.getStatus()) + "）");
        } else if (!timeOk) {
            vo.setReviewBlockReason("用户已修改帖子，工单快照时间与当前更新时间不一致，请刷新后确认");
        }
    }

    private void enrichProfileAuditContext(ModerationTaskDetailVO vo, ModerationTask task) {
        UserAuditTaskBriefVO auditTask = tryFetchAuditTask(task.getSourceId());
        if (auditTask == null) {
            vo.setCurrentTargetStatus("资料审核任务不存在");
            vo.setTargetContentChanged(Boolean.FALSE);
            vo.setReviewable(Boolean.FALSE);
            vo.setReviewBlockReason("资料审核任务不存在，无法处理");
            return;
        }
        vo.setCurrentTargetStatus(auditTaskStatusLabel(auditTask.getStatus()));
        vo.setCurrentTargetUpdatedAt(auditTask.getUpdateTime());
        boolean statusOk = "HUMAN_REVIEW".equals(auditTask.getStatus());
        boolean timeOk = isSameInstant(task.getTargetUpdatedAt(), auditTask.getUpdateTime());
        vo.setTargetContentChanged(task.getTargetUpdatedAt() != null && !timeOk);
        vo.setReviewable(statusOk && timeOk);
        if (!statusOk) {
            vo.setReviewBlockReason("资料已不在人工审核中（当前：" + auditTaskStatusLabel(auditTask.getStatus()) + "）");
        } else if (!timeOk) {
            vo.setReviewBlockReason("用户已修改资料，工单快照时间与当前更新时间不一致，请刷新后确认");
        }
    }

    private void enrichReportContext(ModerationTaskDetailVO vo, ModerationTask task) {
        List<String> available = new ArrayList<>();
        if (isReportActionAvailable(task, ModerationConstants.HandleAction.OFFLINE_ARTICLE)) {
            available.add(ModerationConstants.HandleAction.OFFLINE_ARTICLE);
        }
        if (isReportActionAvailable(task, ModerationConstants.HandleAction.HIDE_COMMENT)) {
            available.add(ModerationConstants.HandleAction.HIDE_COMMENT);
        }
        if (isReportActionAvailable(task, ModerationConstants.HandleAction.HIDE_REPLY)) {
            available.add(ModerationConstants.HandleAction.HIDE_REPLY);
        }
        if (isReportActionAvailable(task, ModerationConstants.HandleAction.HIDE_DANMAKU)) {
            available.add(ModerationConstants.HandleAction.HIDE_DANMAKU);
        }
        if (isReportActionAvailable(task, ModerationConstants.HandleAction.BAN_USER)) {
            available.add(ModerationConstants.HandleAction.BAN_USER);
        }
        vo.setAvailableReportActions(available);
        vo.setReviewable(Boolean.TRUE);
        if (available.isEmpty()) {
            vo.setReviewBlockReason("举报目标已不存在或无法处罚，仅可「通过（不处理）」关闭工单");
        } else {
            vo.setCurrentTargetStatus("可查看详情");
        }
    }

    private void validateBeforeHandle(ModerationTask task, String action) {
        if (ModerationConstants.TaskType.REPORT.equals(task.getTaskType())) {
            if (ModerationConstants.HandleAction.NO_VIOLATION.equals(action)) {
                return;
            }
            if (!isReportActionAvailable(task, action)) {
                throw new BusinessException("举报目标当前无法执行该处理措施，请选择「通过（不处理）」");
            }
            return;
        }
        if (!isReviewable(task)) {
            ModerationTaskDetailVO probe = new ModerationTaskDetailVO();
            enrichReviewContext(probe, task);
            throw new BusinessException(StringUtils.hasText(probe.getReviewBlockReason())
                    ? probe.getReviewBlockReason()
                    : "当前工单不可处理，请刷新详情");
        }
    }

    private boolean isReviewable(ModerationTask task) {
        if (ModerationConstants.TaskType.ARTICLE_AUDIT.equals(task.getTaskType())) {
            ArticleDetailVO article = tryFetchArticle(task.getTargetId());
            if (article == null) {
                return false;
            }
            return Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PENDING)
                    && isSameInstant(task.getTargetUpdatedAt(), article.getUpdateTime());
        }
        if (ModerationConstants.TaskType.PROFILE_AUDIT.equals(task.getTaskType())) {
            UserAuditTaskBriefVO auditTask = tryFetchAuditTask(task.getSourceId());
            if (auditTask == null) {
                return false;
            }
            return "HUMAN_REVIEW".equals(auditTask.getStatus())
                    && isSameInstant(task.getTargetUpdatedAt(), auditTask.getUpdateTime());
        }
        return true;
    }

    private boolean isReportActionAvailable(ModerationTask task, String action) {
        if (task.getTargetType() == null || task.getTargetId() == null) {
            return false;
        }
        if (ModerationConstants.HandleAction.OFFLINE_ARTICLE.equals(action)) {
            if (task.getTargetType() != SocialConstants.ReportTargetType.ARTICLE) {
                return false;
            }
            ArticleDetailVO article = tryFetchArticle(task.getTargetId());
            return article != null && Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED);
        }
        if (ModerationConstants.HandleAction.HIDE_COMMENT.equals(action)) {
            return task.getTargetType() == SocialConstants.ReportTargetType.COMMENT
                    && tryFetchComment(task.getTargetId()) != null;
        }
        if (ModerationConstants.HandleAction.HIDE_REPLY.equals(action)) {
            return task.getTargetType() == SocialConstants.ReportTargetType.REPLY
                    && tryFetchReply(task.getTargetId()) != null;
        }
        if (ModerationConstants.HandleAction.HIDE_DANMAKU.equals(action)) {
            return task.getTargetType() == SocialConstants.ReportTargetType.DANMAKU
                    && tryFetchDanmaku(task.getTargetId()) != null;
        }
        if (ModerationConstants.HandleAction.BAN_USER.equals(action)) {
            Long userId = task.getTargetType() == SocialConstants.ReportTargetType.USER
                    ? task.getTargetId()
                    : task.getSubjectUserId();
            return userId != null && tryFetchUser(userId) != null;
        }
        return false;
    }

    private ArticleDetailVO tryFetchArticle(Long articleId) {
        return tryUnwrap(contentFeignClient.getArticleDetail(articleId));
    }

    private CommentVO tryFetchComment(Long commentId) {
        return tryUnwrap(socialFeignClient.getCommentDetail(commentId));
    }

    private ReplyVO tryFetchReply(Long replyId) {
        return tryUnwrap(socialFeignClient.getReplyDetail(replyId));
    }

    private com.game.community.model.vo.danmaku.DanmakuVO tryFetchDanmaku(Long messageId) {
        return tryUnwrap(danmakuFeignClient.getMessage(messageId));
    }

    private UserCardInternalVO tryFetchUser(Long userId) {
        List<UserCardInternalVO> users = tryUnwrap(userFeignClient.getUsersByUserIds(List.of(userId)));
        if (users == null || users.isEmpty()) {
            return null;
        }
        return users.get(0);
    }

    private UserAuditTaskBriefVO tryFetchAuditTask(Long taskId) {
        return tryUnwrap(userFeignClient.getAuditTaskBrief(taskId));
    }

    private <T> T tryUnwrap(Result<T> result) {
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            return null;
        }
        return result.getData();
    }

    private boolean isSameInstant(LocalDateTime expected, LocalDateTime actual) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return expected.truncatedTo(ChronoUnit.SECONDS).equals(actual.truncatedTo(ChronoUnit.SECONDS));
    }

    private String articleStatusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        if (Objects.equals(status, ContentConstants.ArticleStatus.DRAFT)) {
            return "草稿";
        }
        if (Objects.equals(status, ContentConstants.ArticleStatus.PUBLISHED)) {
            return "已发布";
        }
        if (Objects.equals(status, ContentConstants.ArticleStatus.PENDING)) {
            return "待审核";
        }
        if (Objects.equals(status, ContentConstants.ArticleStatus.OFFLINE)) {
            return "已下架";
        }
        if (Objects.equals(status, ContentConstants.ArticleStatus.REJECTED)) {
            return "审核驳回";
        }
        return "状态" + status;
    }

    private String auditTaskStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "未知";
        }
        return switch (status) {
            case "PENDING" -> "待处理";
            case "PROCESSING" -> "审核中";
            case "PASSED" -> "已通过";
            case "REJECTED" -> "已拒绝";
            case "HUMAN_REVIEW" -> "人工复核中";
            case "FAILED" -> "失败";
            default -> status;
        };
    }

    private ModerationTask requireTask(String taskKey) {
        if (!StringUtils.hasText(taskKey) || taskKey.length() > 64) {
            throw new BusinessException("审核工单标识不合法");
        }
        ModerationTask task = taskMapper.selectOne(new LambdaQueryWrapper<ModerationTask>()
                .eq(ModerationTask::getPublicId, taskKey.trim())
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException("审核工单不存在");
        }
        return task;
    }

    private ModerationTask requireTaskById(Long taskId) {
        ModerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("审核工单不存在");
        }
        return task;
    }

    private ModerationTaskVO toVO(ModerationTask task, Map<Long, UserCardInternalVO> users) {
        ModerationTaskVO vo = new ModerationTaskVO();
        copyBase(task, vo);
        UserCardInternalVO subject = users.get(task.getSubjectUserId());
        UserCardInternalVO reporter = users.get(task.getReporterId());
        UserCardInternalVO handler = users.get(task.getHandlerId());
        vo.setSubjectUserName(subject == null ? null : subject.getUsername());
        vo.setReporterName(reporter == null ? null : reporter.getUsername());
        vo.setHandlerName(handler == null ? null : handler.getUsername());
        vo.setSubjectAccountId(subject == null ? null : subject.getAccountId());
        vo.setReporterAccountId(reporter == null ? null : reporter.getAccountId());
        vo.setHandlerAccountId(handler == null ? null : handler.getAccountId());
        return vo;
    }

    private ModerationTaskDetailVO toDetailVO(ModerationTask task, Map<Long, UserCardInternalVO> users) {
        ModerationTaskDetailVO vo = new ModerationTaskDetailVO();
        copyBase(task, vo);
        UserCardInternalVO subject = users.get(task.getSubjectUserId());
        UserCardInternalVO reporter = users.get(task.getReporterId());
        UserCardInternalVO handler = users.get(task.getHandlerId());
        vo.setSubjectUserName(subject == null ? null : subject.getUsername());
        vo.setReporterName(reporter == null ? null : reporter.getUsername());
        vo.setHandlerName(handler == null ? null : handler.getUsername());
        vo.setSubjectAccountId(subject == null ? null : subject.getAccountId());
        vo.setReporterAccountId(reporter == null ? null : reporter.getAccountId());
        vo.setHandlerAccountId(handler == null ? null : handler.getAccountId());
        return vo;
    }

    private void copyBase(ModerationTask task, ModerationTaskVO vo) {
        vo.setTaskKey(task.getPublicId());
        vo.setTaskType(task.getTaskType());
        vo.setTargetType(task.getTargetType());
        vo.setReason(task.getReason());
        vo.setSummary(task.getSummary());
        vo.setExtraPayload(task.getExtraPayload());
        vo.setStatus(task.getStatus());
        vo.setHandleAction(task.getHandleAction());
        vo.setHandleRemark(task.getHandleRemark());
        vo.setClaimTime(task.getClaimTime());
        vo.setHandleTime(task.getHandleTime());
        vo.setCreateTime(task.getCreateTime());
    }

    private Long accountIdOf(Long userId) {
        if (userId == null) {
            return null;
        }
        UserCardInternalVO user = tryFetchUser(userId);
        return user == null ? null : user.getAccountId();
    }

    private Map<Long, UserCardInternalVO> userMap(List<Long> ids) {
        List<Long> distinctIds = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        List<UserCardInternalVO> users = unwrap(userFeignClient.getUsersByUserIds(distinctIds), "用户服务暂不可用");
        if (users == null) {
            return Map.of();
        }
        return users.stream().collect(Collectors.toMap(UserCardInternalVO::getUserId, item -> item, (a, b) -> a));
    }

    private RouteContext resolveRouteContext(ModerationTask task) {
        if (task.getTargetType() == null) {
            return new RouteContext(NotificationConstants.RouteType.NONE, null, null, null, null, null, null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.ARTICLE) {
            return new RouteContext(NotificationConstants.RouteType.ARTICLE, task.getTargetId(), null, null, null, null, null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.COMMENT) {
            CommentVO comment = unwrap(socialFeignClient.getCommentDetail(task.getTargetId()), "社交服务暂不可用");
            return new RouteContext(NotificationConstants.RouteType.COMMENT,
                    comment == null ? null : comment.getArticleId(),
                    task.getTargetId(),
                    null,
                    null,
                    null,
                    null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.REPLY) {
            ReplyVO reply = unwrap(socialFeignClient.getReplyDetail(task.getTargetId()), "社交服务暂不可用");
            return new RouteContext(NotificationConstants.RouteType.REPLY,
                    reply == null ? null : reply.getArticleId(),
                    reply == null ? null : reply.getCommentId(),
                    task.getTargetId(),
                    null,
                    null,
                    null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.USER) {
            return new RouteContext(NotificationConstants.RouteType.USER, null, null, null, task.getTargetId(), null, null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.DANMAKU) {
            DanmakuVO danmaku = unwrap(danmakuFeignClient.getMessage(task.getTargetId()), "弹幕服务暂不可用");
            ArticleListVO article = danmaku == null ? null
                    : unwrap(contentFeignClient.getArticleByPublicId(danmaku.getVideoPublicId()), "文章服务暂不可用");
            return new RouteContext(NotificationConstants.RouteType.DANMAKU,
                    article == null ? null : article.getId(), null, null, null,
                    task.getTargetId(), danmaku == null ? null : danmaku.getVideoPublicId());
        }
        return new RouteContext(NotificationConstants.RouteType.NONE, null, null, null, null, null, null);
    }

    private <T> T unwrap(Result<T> result, String message) {
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BusinessException(result == null || !StringUtils.hasText(result.getMessage())
                    ? message
                    : result.getMessage());
        }
        return result.getData();
    }

    private record RouteContext(Integer routeType, Long articleId, Long commentId, Long replyId,
                                Long targetUserId, Long danmakuId, String videoPublicId) {
    }
}
