package com.game.community.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.audit.event.NotificationEventProducer;
import com.game.community.audit.mapper.AuditReportTaskMapper;
import com.game.community.audit.service.AuditReportService;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.SocialFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.audit.HandleAuditReportDTO;
import com.game.community.model.entity.audit.AuditReportTask;
import com.game.community.model.message.ReportAuditMessage;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.audit.AuditReportDetailVO;
import com.game.community.model.vo.audit.AuditReportVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.ReplyVO;
import com.game.community.model.vo.user.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditReportServiceImpl implements AuditReportService {

    private final AuditReportTaskMapper taskMapper;

    private final ContentFeignClient contentFeignClient;

    private final SocialFeignClient socialFeignClient;

    private final UserFeignClient userFeignClient;

    private final NotificationEventProducer notificationEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveReport(ReportAuditMessage message) {
        AuditReportTask task = new AuditReportTask();
        task.setReportId(message.getReportId());
        task.setTargetType(message.getTargetType());
        task.setTargetId(message.getTargetId());
        task.setReporterId(message.getReporterId());
        task.setReportedUserId(message.getReportedUserId());
        task.setReason(message.getReason());
        task.setStatus(SocialConstants.ReportStatus.PENDING);
        task.setVersion(0);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ignored) {
            // Kafka 至少一次投递时，同一个 reportId 只保留一条审核工单。
        }
    }

    @Override
    public PageResult<AuditReportVO> pageReports(Long page, Long size, Integer status, Integer targetType) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<AuditReportTask> result = taskMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<AuditReportTask>()
                        .eq(status != null, AuditReportTask::getStatus, status)
                        .eq(targetType != null, AuditReportTask::getTargetType, targetType)
                        .orderByDesc(AuditReportTask::getCreateTime)
                        .orderByDesc(AuditReportTask::getId));
        List<AuditReportVO> records = enrichUsers(result.getRecords()).stream().map(this::toVO).toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public AuditReportDetailVO getDetail(Long taskId) {
        AuditReportTask task = requireTask(taskId);
        AuditReportDetailVO vo = toDetailVO(task);
        Map<Long, UserVO> users = userMap(List.of(task.getReporterId(), task.getReportedUserId()));
        UserVO reporter = users.get(task.getReporterId());
        UserVO reported = users.get(task.getReportedUserId());
        vo.setReporterName(reporter == null ? null : reporter.getUsername());
        vo.setReportedUserName(reported == null ? null : reported.getUsername());
        vo.setTargetAuthorName(reported == null ? null : reported.getUsername());
        fillTargetDetail(vo, task, reported);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long taskId, Long handlerId, HandleAuditReportDTO dto) {
        validateHandleStatus(dto.getStatus());
        AuditReportTask task = requireTask(taskId);
        int claimed = taskMapper.update(null, new LambdaUpdateWrapper<AuditReportTask>()
                .eq(AuditReportTask::getId, taskId)
                .eq(AuditReportTask::getStatus, SocialConstants.ReportStatus.PENDING)
                .set(AuditReportTask::getStatus, SocialConstants.ReportStatus.PROCESSING)
                .set(AuditReportTask::getHandlerId, handlerId)
                .set(AuditReportTask::getUpdateTime, LocalDateTime.now()));
        if (claimed == 0) {
            throw new BusinessException("该举报已被处理或正在处理");
        }
        try {
            if (Objects.equals(dto.getStatus(), SocialConstants.ReportStatus.ACCEPTED)) {
                applyAcceptedAction(task);
            }
            unwrap(socialFeignClient.markReportHandled(task.getReportId(), dto.getStatus(), handlerId, dto.getHandleRemark()),
                    "同步举报处理结果失败");
            int finished = taskMapper.update(null, new LambdaUpdateWrapper<AuditReportTask>()
                    .eq(AuditReportTask::getId, taskId)
                    .eq(AuditReportTask::getStatus, SocialConstants.ReportStatus.PROCESSING)
                    .set(AuditReportTask::getStatus, dto.getStatus())
                    .set(AuditReportTask::getHandlerId, handlerId)
                    .set(AuditReportTask::getHandleRemark, dto.getHandleRemark())
                    .set(AuditReportTask::getHandleTime, LocalDateTime.now())
                    .set(AuditReportTask::getUpdateTime, LocalDateTime.now()));
            if (finished == 0) {
                throw new BusinessException("举报处理状态已变化，请刷新后重试");
            }
            publishNotification(task, dto.getStatus(), dto.getHandleRemark());
        } catch (RuntimeException e) {
            taskMapper.update(null, new LambdaUpdateWrapper<AuditReportTask>()
                    .eq(AuditReportTask::getId, taskId)
                    .eq(AuditReportTask::getStatus, SocialConstants.ReportStatus.PROCESSING)
                    .set(AuditReportTask::getStatus, SocialConstants.ReportStatus.PENDING)
                    .set(AuditReportTask::getHandlerId, null)
                    .set(AuditReportTask::getUpdateTime, LocalDateTime.now()));
            throw e;
        }
    }

    private List<AuditReportTask> enrichUsers(List<AuditReportTask> tasks) {
        return tasks == null ? List.of() : tasks;
    }

    private AuditReportTask requireTask(Long taskId) {
        AuditReportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("举报审核工单不存在");
        }
        return task;
    }

    private void validateHandleStatus(Integer status) {
        if (status == null
                || status != SocialConstants.ReportStatus.ACCEPTED
                && status != SocialConstants.ReportStatus.REJECTED) {
            throw new BusinessException("处理结果不合法");
        }
    }

    private void applyAcceptedAction(AuditReportTask task) {
        if (task.getTargetType() == SocialConstants.ReportTargetType.ARTICLE) {
            unwrap(contentFeignClient.updateArticleStatus(task.getTargetId(), ContentConstants.ArticleStatus.OFFLINE),
                    "下架文章失败");
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.COMMENT) {
            unwrap(socialFeignClient.hideCommentByAudit(task.getTargetId()), "隐藏评论失败");
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.REPLY) {
            unwrap(socialFeignClient.hideReplyByAudit(task.getTargetId()), "隐藏回复失败");
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.USER) {
            unwrap(userFeignClient.updateUserStatus(task.getTargetId(), UserConstants.UserStatus.DISABLED),
                    "封禁用户失败");
            return;
        }
        throw new BusinessException("举报目标类型不合法");
    }

    private void publishNotification(AuditReportTask task, Integer status, String handleRemark) {
        RouteContext route = resolveRouteContext(task);
        String normalizedRemark = StringUtils.hasText(handleRemark) ? handleRemark : defaultHandleRemark(status);
        notificationEventProducer.publishReportResult(
                task.getReporterId(),
                route.routeType(),
                route.articleId(),
                route.commentId(),
                route.replyId(),
                route.targetUserId(),
                task.getReportId(),
                status != null && status == SocialConstants.ReportStatus.ACCEPTED ? "你提交的举报已成立" : "你提交的举报未通过",
                normalizedRemark
        );
        if (status != null
                && status == SocialConstants.ReportStatus.ACCEPTED
                && task.getReportedUserId() != null
                && !Objects.equals(task.getReportedUserId(), task.getReporterId())) {
            notificationEventProducer.publishPenaltyResult(
                    task.getReportedUserId(),
                    route.routeType(),
                    route.articleId(),
                    route.commentId(),
                    route.replyId(),
                    route.targetUserId(),
                    task.getReportId(),
                    task.getTargetType() != null && task.getTargetType() == SocialConstants.ReportTargetType.USER
                            ? "你的账号因举报已被封禁"
                            : "你发布的内容因举报已被处理",
                    normalizedRemark
            );
        }
    }

    private RouteContext resolveRouteContext(AuditReportTask task) {
        if (task.getTargetType() == null) {
            return new RouteContext(NotificationConstants.RouteType.NONE, null, null, null, null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.ARTICLE) {
            return new RouteContext(NotificationConstants.RouteType.ARTICLE, task.getTargetId(), null, null, null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.COMMENT) {
            CommentVO comment = unwrap(socialFeignClient.getCommentDetail(task.getTargetId()), "社交服务暂不可用");
            return new RouteContext(NotificationConstants.RouteType.COMMENT,
                    comment == null ? null : comment.getArticleId(),
                    task.getTargetId(),
                    null,
                    null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.REPLY) {
            ReplyVO reply = unwrap(socialFeignClient.getReplyDetail(task.getTargetId()), "社交服务暂不可用");
            return new RouteContext(NotificationConstants.RouteType.REPLY,
                    reply == null ? null : reply.getArticleId(),
                    reply == null ? null : reply.getCommentId(),
                    task.getTargetId(),
                    null);
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.USER) {
            return new RouteContext(NotificationConstants.RouteType.USER, null, null, null, task.getTargetId());
        }
        return new RouteContext(NotificationConstants.RouteType.NONE, null, null, null, null);
    }

    private String defaultHandleRemark(Integer status) {
        if (status != null && status == SocialConstants.ReportStatus.ACCEPTED) {
            return "举报内容已核实并完成处理";
        }
        return "管理员已查看本次举报，暂不支持处理";
    }

    private void fillTargetDetail(AuditReportDetailVO vo, AuditReportTask task, UserVO reported) {
        if (task.getTargetType() == SocialConstants.ReportTargetType.ARTICLE) {
            ArticleDetailVO detail = unwrap(contentFeignClient.getArticleDetail(task.getTargetId()), "文章服务暂不可用");
            vo.setTarget(detail);
            vo.setTargetTitle(detail == null ? null : detail.getTitle());
            if (detail != null && StringUtils.hasText(detail.getContent())) {
                vo.setTargetContent(detail.getContent());
            } else {
                vo.setTargetContent(detail == null ? null : detail.getSummary());
            }
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.COMMENT) {
            CommentVO comment = unwrap(socialFeignClient.getCommentDetail(task.getTargetId()), "社交服务暂不可用");
            vo.setTarget(comment);
            vo.setTargetTitle("评论 #" + task.getTargetId());
            vo.setTargetContent(comment == null ? null : comment.getContent());
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.REPLY) {
            ReplyVO reply = unwrap(socialFeignClient.getReplyDetail(task.getTargetId()), "社交服务暂不可用");
            vo.setTarget(reply);
            vo.setTargetTitle("回复 #" + task.getTargetId());
            vo.setTargetContent(reply == null ? null : reply.getContent());
            return;
        }
        if (task.getTargetType() == SocialConstants.ReportTargetType.USER) {
            vo.setTarget(reported);
            vo.setTargetTitle(reported == null ? "用户 #" + task.getTargetId() : reported.getUsername());
            vo.setTargetContent(reported == null ? null : reported.getSignature());
        }
    }

    private AuditReportVO toVO(AuditReportTask task) {
        AuditReportVO vo = new AuditReportVO();
        copyBase(task, vo);
        Map<Long, UserVO> users = userMap(List.of(task.getReporterId(), task.getReportedUserId()));
        UserVO reporter = users.get(task.getReporterId());
        UserVO reported = users.get(task.getReportedUserId());
        vo.setReporterName(reporter == null ? null : reporter.getUsername());
        vo.setReportedUserName(reported == null ? null : reported.getUsername());
        return vo;
    }

    private AuditReportDetailVO toDetailVO(AuditReportTask task) {
        AuditReportDetailVO vo = new AuditReportDetailVO();
        copyBase(task, vo);
        return vo;
    }

    private void copyBase(AuditReportTask task, AuditReportVO vo) {
        vo.setId(task.getId());
        vo.setReportId(task.getReportId());
        vo.setTargetType(task.getTargetType());
        vo.setTargetId(task.getTargetId());
        vo.setReporterId(task.getReporterId());
        vo.setReportedUserId(task.getReportedUserId());
        vo.setReason(task.getReason());
        vo.setStatus(task.getStatus());
        vo.setHandlerId(task.getHandlerId());
        vo.setHandleRemark(task.getHandleRemark());
        vo.setHandleTime(task.getHandleTime());
        vo.setCreateTime(task.getCreateTime());
    }

    private Map<Long, UserVO> userMap(List<Long> ids) {
        List<Long> distinctIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        List<UserVO> users = unwrap(userFeignClient.getUsersByIds(distinctIds), "用户服务暂不可用");
        if (users == null) {
            return Map.of();
        }
        return users.stream().collect(Collectors.toMap(UserVO::getId, item -> item, (a, b) -> a));
    }

    private <T> T unwrap(com.game.community.model.base.Result<T> result, String message) {
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BusinessException(result == null || !StringUtils.hasText(result.getMessage())
                    ? message
                    : result.getMessage());
        }
        return result.getData();
    }

    private record RouteContext(Integer routeType, Long articleId, Long commentId, Long replyId, Long targetUserId) {
    }
}
