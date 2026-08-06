package com.game.community.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.social.CreateReportDTO;
import com.game.community.model.dto.social.HandleReportDTO;
import com.game.community.model.entity.social.SocialComment;
import com.game.community.model.entity.social.SocialReport;
import com.game.community.model.entity.social.SocialReply;
import com.game.community.model.vo.social.ReportVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.feign.DanmakuFeignClient;
import com.game.community.social.client.SocialRemoteClient;
import com.game.community.social.event.NotificationEventProducer;
import com.game.community.social.event.ReportAuditProducer;
import com.game.community.social.mapper.SocialCommentMapper;
import com.game.community.social.mapper.SocialReportMapper;
import com.game.community.social.mapper.SocialReplyMapper;
import com.game.community.social.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final SocialReportMapper reportMapper;

    private final SocialCommentMapper commentMapper;

    private final SocialReplyMapper replyMapper;

    private final SocialRemoteClient remoteClient;

    private final DanmakuFeignClient danmakuFeignClient;

    private final ReportAuditProducer reportAuditProducer;

    private final NotificationEventProducer notificationEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReport(Long userId, CreateReportDTO dto) {
        validateTargetType(dto.getTargetType());
        Long targetId = resolveInternalTargetId(dto.getTargetType(), dto.getTargetId());
        dto.setInternalTargetId(targetId);
        Long reportedUserId = resolveReportedUserId(userId, dto.getTargetType(), targetId);
        if (reportedUserId != null && reportedUserId.equals(userId)) {
            throw new BusinessException("不能举报自己发布的内容");
        }
        SocialReport report = new SocialReport();
        report.setTargetType(dto.getTargetType());
        report.setTargetId(targetId);
        report.setReporterId(userId);
        report.setReportedUserId(reportedUserId);
        report.setReason(dto.getReason().trim());
        report.setStatus(SocialConstants.ReportStatus.PENDING);
        report.setVersion(0);
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("你已经举报过该目标，请等待审核结果");
        }
        reportAuditProducer.publish(report.getId(), report.getTargetType(), report.getTargetId(),
                report.getReporterId(), report.getReportedUserId(), report.getReason());
        Long articleId = null;
        Long commentId = null;
        Long replyId = null;
        Long targetUserId = null;
        if (report.getTargetType() != null) {
            if (report.getTargetType() == SocialConstants.ReportTargetType.ARTICLE) {
                articleId = report.getTargetId();
            } else if (report.getTargetType() == SocialConstants.ReportTargetType.COMMENT) {
                commentId = report.getTargetId();
                SocialComment comment = commentMapper.selectById(report.getTargetId());
                articleId = comment == null ? null : comment.getArticleId();
            } else if (report.getTargetType() == SocialConstants.ReportTargetType.REPLY) {
                replyId = report.getTargetId();
                SocialReply reply = replyMapper.selectById(report.getTargetId());
                if (reply != null) {
                    articleId = reply.getArticleId();
                    commentId = reply.getCommentId();
                }
            } else if (report.getTargetType() == SocialConstants.ReportTargetType.USER) {
                targetUserId = report.getReportedUserId();
            }
        }
        notificationEventProducer.publishReportSubmitted(
                userId,
                report.getTargetType() == null ? null : report.getTargetType().longValue(),
                articleId,
                commentId,
                replyId,
                targetUserId,
                report.getReason());
        return report.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long handlerId, Long reportId, HandleReportDTO dto) {
        markReportHandled(handlerId, reportId, dto.getStatus(), dto.getHandleRemark());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReportHandled(Long handlerId, Long reportId, Integer status, String handleRemark) {
        validateHandleStatus(status);
        int updated = reportMapper.update(null, new LambdaUpdateWrapper<SocialReport>()
                .eq(SocialReport::getId, reportId)
                .in(SocialReport::getStatus, SocialConstants.ReportStatus.PENDING, SocialConstants.ReportStatus.PROCESSING)
                .set(SocialReport::getStatus, status)
                .set(SocialReport::getHandlerId, handlerId)
                .set(SocialReport::getHandleRemark, handleRemark)
                .set(SocialReport::getHandleTime, LocalDateTime.now())
                .set(SocialReport::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("举报不存在或已处理");
        }
    }

    @Override
    public PageResult<ReportVO> pageReports(Long page, Long size, Integer status, Integer targetType) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<SocialReport> result = reportMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialReport>()
                        .eq(status != null, SocialReport::getStatus, status)
                        .eq(targetType != null, SocialReport::getTargetType, targetType)
                        .orderByDesc(SocialReport::getCreateTime));
        List<ReportVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    private void validateTargetType(Integer targetType) {
        if (targetType == null
                || targetType != SocialConstants.ReportTargetType.ARTICLE
                && targetType != SocialConstants.ReportTargetType.COMMENT
                && targetType != SocialConstants.ReportTargetType.REPLY
                && targetType != SocialConstants.ReportTargetType.USER
                && targetType != SocialConstants.ReportTargetType.DANMAKU) {
            throw new BusinessException("举报目标类型不合法");
        }
    }

    private void validateHandleStatus(Integer status) {
        if (status == null
                || status != SocialConstants.ReportStatus.ACCEPTED
                && status != SocialConstants.ReportStatus.REJECTED) {
            throw new BusinessException("处理状态不合法");
        }
    }

    private Long resolveReportedUserId(Long reporterId, Integer targetType, Long targetId) {
        if (targetId == null) {
            throw new BusinessException("举报目标不存在");
        }
        if (targetType == SocialConstants.ReportTargetType.ARTICLE) {
            var article = remoteClient.getArticle(targetId);
            if (article == null) {
                throw new BusinessException("文章不存在");
            }
            return remoteClient.articleAuthorUserId(article);
        }
        if (targetType == SocialConstants.ReportTargetType.COMMENT) {
            SocialComment comment = commentMapper.selectById(targetId);
            if (comment == null || comment.getStatus() == null
                    || comment.getStatus() != SocialConstants.CommentStatus.NORMAL) {
                throw new BusinessException("评论不存在");
            }
            return comment.getUserId();
        }
        if (targetType == SocialConstants.ReportTargetType.REPLY) {
            SocialReply reply = replyMapper.selectById(targetId);
            if (reply == null || reply.getStatus() == null
                    || reply.getStatus() != SocialConstants.ReplyStatus.NORMAL) {
                throw new BusinessException("回复不存在");
            }
            return reply.getUserId();
        }
        if (targetType == SocialConstants.ReportTargetType.USER) {
            UserCardInternalVO targetUser = remoteClient.listUsersByIds(List.of(targetId)).stream().findFirst().orElse(null);
            if (targetUser == null) {
                throw new BusinessException("用户不存在");
            }
            return targetId;
        }
        if (targetType == SocialConstants.ReportTargetType.DANMAKU) {
            var result = danmakuFeignClient.getMessage(targetId);
            if (result == null || result.getData() == null) {
                throw new BusinessException("弹幕不存在");
            }
            return result.getData().getUserId();
        }
        throw new BusinessException("举报目标类型不合法");
    }

    private Long resolveInternalTargetId(Integer targetType, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new BusinessException("举报目标不存在");
        }
        String normalized = targetId.trim();
        if (targetType == SocialConstants.ReportTargetType.ARTICLE) {
            var article = remoteClient.getArticleByPublicId(normalized);
            if (article == null || article.getId() == null) {
                throw new BusinessException("文章不存在");
            }
            return article.getId();
        }
        if (targetType == SocialConstants.ReportTargetType.USER) {
            Long accountId = parseTargetId(normalized);
            UserCardInternalVO targetUser = remoteClient.getUserByAccountId(accountId);
            if (targetUser == null || targetUser.getUserId() == null) {
                throw new BusinessException("用户不存在");
            }
            return targetUser.getUserId();
        }
        return parseTargetId(normalized);
    }

    private Long parseTargetId(String targetId) {
        try {
            return Long.valueOf(targetId);
        } catch (NumberFormatException e) {
            throw new BusinessException("举报目标不存在");
        }
    }

    private ReportVO toVO(SocialReport report) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setTargetType(report.getTargetType());
        vo.setTargetId(report.getTargetId());
        vo.setReporterId(report.getReporterId());
        vo.setReportedUserId(report.getReportedUserId());
        vo.setReason(report.getReason());
        vo.setStatus(report.getStatus());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandleRemark(report.getHandleRemark());
        vo.setHandleTime(report.getHandleTime());
        vo.setCreateTime(report.getCreateTime());
        return vo;
    }
}
