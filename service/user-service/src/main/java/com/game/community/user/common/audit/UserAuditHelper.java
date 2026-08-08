package com.game.community.user.common.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.entity.user.UserProfileAudit;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.AuditMode;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.model.enums.user.FieldAuditStatus;
import com.game.community.model.enums.user.UserStrings;
import com.game.community.model.payload.user.FieldAuditPayload;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.mapper.UserProfileAuditMapper;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.audit.AuditModeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户字段审核：审核状态表 CAS、任务创建、pending 解析
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AuditModeProperties.class)
public class UserAuditHelper {

    private final UserMapper userMapper;
    private final UserProfileAuditMapper profileAuditMapper;
    private final UserAuditTaskMapper userAuditTaskMapper;
    private final MinIOUtils minIOUtils;
    private final ObjectMapper objectMapper;
    private final AuditModeProperties auditModeProperties;

    /** 执行 getOrCreate 对应的业务处理。 */
    public UserProfileAudit getOrCreate(Long userId) {
        // 首次并发访问依靠 user_id 主键竞争，冲突后重新读取已存在的审核行。
        UserProfileAudit audit = profileAuditMapper.selectById(userId);
        if (audit != null) {
            return audit;
        }
        try {
            initProfileAudit(userId);
        } catch (DuplicateKeyException ignored) {
            // 并发首次访问由唯一主键裁决，直接读取已创建行。
        }
        return profileAuditMapper.selectById(userId);
    }

    /** 执行 initProfileAudit 对应的业务处理。 */
    public void initProfileAudit(Long userId) {
        UserProfileAudit audit = new UserProfileAudit();
        audit.setUserId(userId);
        audit.setUsernameAuditStatus(FieldAuditStatus.NONE);
        audit.setSignatureAuditStatus(FieldAuditStatus.NONE);
        audit.setAvatarAuditStatus(FieldAuditStatus.NONE);
        audit.setVersion(UserConstants.INITIAL_VERSION);
        audit.setCreateTime(LocalDateTime.now());
        audit.setUpdateTime(LocalDateTime.now());
        profileAuditMapper.insert(audit);
    }

    /** 执行 acquireUsernameAudit 对应的业务处理。 */
    public boolean acquireUsernameAudit(Long userId, String pendingUsername) {
        // 只有 NONE 状态才能 CAS 为 AUDITING，避免重复提交覆盖 pending 值。
        ensureProfileAuditRow(userId);
        return profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getUsernameAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getUsernameAuditStatus, FieldAuditStatus.AUDITING)
                .set(UserProfileAudit::getPendingUsername, pendingUsername)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now())) > 0;
    }

    /** 执行 acquireSignatureAudit 对应的业务处理。 */
    public boolean acquireSignatureAudit(Long userId, String pendingSignature) {
        // 签名字段独立 CAS，占用失败表示已有审核任务正在处理。
        ensureProfileAuditRow(userId);
        return profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getSignatureAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getSignatureAuditStatus, FieldAuditStatus.AUDITING)
                .set(UserProfileAudit::getPendingSignature, pendingSignature)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now())) > 0;
    }

    /** 执行 acquireAvatarAudit 对应的业务处理。 */
    public boolean acquireAvatarAudit(Long userId, String pendingObjectName) {
        // 头像对象名先写入 pending，审核通过前不写入正式头像字段。
        ensureProfileAuditRow(userId);
        return profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getAvatarAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getAvatarAuditStatus, FieldAuditStatus.AUDITING)
                .set(UserProfileAudit::getPendingAvatar, pendingObjectName)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now())) > 0;
    }

    /** 执行 clearUsernameAudit 对应的业务处理。 */
    public void clearUsernameAudit(Long userId, String pendingUsername) {
        profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getPendingUsername, pendingUsername)
                .set(UserProfileAudit::getUsernameAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getPendingUsername, UserStrings.EMPTY)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
    }

    /** 执行 clearSignatureAudit 对应的业务处理。 */
    public void clearSignatureAudit(Long userId, String pendingSignature) {
        profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getPendingSignature, pendingSignature)
                .set(UserProfileAudit::getSignatureAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getPendingSignature, UserStrings.EMPTY)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
    }

    /** 执行 clearAvatarAudit 对应的业务处理。 */
    public void clearAvatarAudit(Long userId, String pendingAvatar) {
        profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getPendingAvatar, pendingAvatar)
                .set(UserProfileAudit::getAvatarAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getPendingAvatar, UserStrings.EMPTY)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
    }

    /** 执行 markUsernameHumanReview 对应的业务处理。 */
    public void markUsernameHumanReview(Long userId) {
        profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getUsernameAuditStatus, FieldAuditStatus.AUDITING)
                .set(UserProfileAudit::getUsernameAuditStatus, FieldAuditStatus.HUMAN_REVIEW)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
    }

    /** 执行 markSignatureHumanReview 对应的业务处理。 */
    public void markSignatureHumanReview(Long userId) {
        profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getSignatureAuditStatus, FieldAuditStatus.AUDITING)
                .set(UserProfileAudit::getSignatureAuditStatus, FieldAuditStatus.HUMAN_REVIEW)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
    }

    /** 执行 markAvatarHumanReview 对应的业务处理。 */
    public void markAvatarHumanReview(Long userId) {
        profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getAvatarAuditStatus, FieldAuditStatus.AUDITING)
                .set(UserProfileAudit::getAvatarAuditStatus, FieldAuditStatus.HUMAN_REVIEW)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
    }

    /** 执行 applyUsernamePassed 对应的业务处理。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyUsernamePassed(Long userId, Integer expectedUserVersion, String username) {
        if (expectedUserVersion == null) {
            return false;
        }
        int userRows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, expectedUserVersion)
                .set(User::getUsername, username)
                .set(User::getVersion, expectedUserVersion + 1)
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (userRows == 0) {
            return false;
        }
        int auditRows = profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getUsernameAuditStatus, FieldAuditStatus.AUDITING)
                .eq(UserProfileAudit::getPendingUsername, username)
                .set(UserProfileAudit::getPendingUsername, UserStrings.EMPTY)
                .set(UserProfileAudit::getUsernameAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
        if (auditRows == 0) {
            throw new BusinessException("审核状态已变化");
        }
        return true;
    }

    /** 执行 applySignaturePassed 对应的业务处理。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean applySignaturePassed(Long userId, Integer expectedUserVersion, String signature) {
        if (expectedUserVersion == null) {
            return false;
        }
        int userRows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, expectedUserVersion)
                .set(User::getSignature, signature)
                .set(User::getVersion, expectedUserVersion + 1)
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (userRows == 0) {
            return false;
        }
        int auditRows = profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getSignatureAuditStatus, FieldAuditStatus.AUDITING)
                .eq(UserProfileAudit::getPendingSignature, signature)
                .set(UserProfileAudit::getPendingSignature, UserStrings.EMPTY)
                .set(UserProfileAudit::getSignatureAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
        if (auditRows == 0) {
            throw new BusinessException("审核状态已变化");
        }
        return true;
    }

    /** 执行 applyAvatarPassed 对应的业务处理。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyAvatarPassed(Long userId, Integer expectedUserVersion,
                                     String pendingAvatar, String publicAvatarUrl) {
        if (expectedUserVersion == null) {
            return false;
        }
        int userRows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getVersion, expectedUserVersion)
                .set(User::getAvatar, publicAvatarUrl)
                .set(User::getVersion, expectedUserVersion + 1)
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (userRows == 0) {
            return false;
        }
        int auditRows = profileAuditMapper.update(null, new LambdaUpdateWrapper<UserProfileAudit>()
                .eq(UserProfileAudit::getUserId, userId)
                .eq(UserProfileAudit::getAvatarAuditStatus, FieldAuditStatus.AUDITING)
                .eq(UserProfileAudit::getPendingAvatar, pendingAvatar)
                .set(UserProfileAudit::getPendingAvatar, UserStrings.EMPTY)
                .set(UserProfileAudit::getAvatarAuditStatus, FieldAuditStatus.NONE)
                .set(UserProfileAudit::getUpdateTime, LocalDateTime.now()));
        if (auditRows == 0) {
            throw new BusinessException("审核状态已变化");
        }
        return true;
    }

    /** 执行 createFieldAuditTask 对应的业务处理。 */
    public Long createFieldAuditTask(Long userId, AuditFieldType taskType, String pendingContent,
                                     FieldAuditPayload payload) {
        // payload 保存完整审核上下文，异步线程只按 taskId 读取数据库即可执行。
        UserAuditTask task = new UserAuditTask();
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setStatus(AuditTaskStatus.PENDING);
        task.setPendingContent(pendingContent);
        task.setAuditMode(auditModeProperties.isLlmMode() ? AuditMode.LLM : AuditMode.MOCK);
        try {
            task.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new BusinessException("审核任务数据异常");
        }
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        task.setDeleted(UserConstants.NOT_DELETED);
        userAuditTaskMapper.insert(task);
        return task.getId();
    }

    /** 执行 resolvePendingAvatarUrl 对应的业务处理。 */
    public String resolvePendingAvatarUrl(UserProfileAudit audit) {
        if (audit == null || !StringUtils.hasText(audit.getPendingAvatar())) {
            return null;
        }
        try {
            return minIOUtils.generatePrivateAvatarUrl(audit.getPendingAvatar());
        } catch (Exception e) {
            log.warn("生成待审头像预览失败: userId={}", audit.getUserId(), e);
            return null;
        }
    }

    /** 执行 resolveLatestFieldAuditError 对应的业务处理。 */
    public String resolveLatestFieldAuditError(Long userId, AuditFieldType taskType) {
        UserAuditTask latest = userAuditTaskMapper.selectOne(new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getUserId, userId)
                .eq(UserAuditTask::getTaskType, taskType)
                .orderByDesc(UserAuditTask::getCreateTime)
                .last("limit 1"));
        if (latest == null) {
            return null;
        }
        if (latest.getStatus() != AuditTaskStatus.REJECTED
                && latest.getStatus() != AuditTaskStatus.FAILED) {
            return null;
        }
        if (!StringUtils.hasText(latest.getErrorMessage())) {
            return taskType.label() + "审核未通过";
        }
        return latest.getErrorMessage();
    }

    /** 执行 hasInFlightTask 对应的业务处理。 */
    public boolean hasInFlightTask(Long userId, AuditFieldType taskType) {
        Long count = userAuditTaskMapper.selectCount(new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getUserId, userId)
                .eq(UserAuditTask::getTaskType, taskType)
                .in(UserAuditTask::getStatus, UserConstants.IN_FLIGHT_AUDIT_STATUSES));
        return count != null && count > 0;
    }

    /** 执行 readPayload 对应的业务处理。 */
    public FieldAuditPayload readPayload(UserAuditTask task) {
        // 解析失败返回 null，由执行器统一回滚字段占用并结束任务。
        if (task == null || !StringUtils.hasText(task.getPayload())) {
            return null;
        }
        try {
            return objectMapper.readValue(task.getPayload(), FieldAuditPayload.class);
        } catch (Exception e) {
            log.warn("解析审核负载失败: taskId={}", task.getId(), e);
            return null;
        }
    }

    /** 执行 ensureProfileAuditRow 对应的业务处理。 */
    private void ensureProfileAuditRow(Long userId) {
        if (profileAuditMapper.selectById(userId) == null) {
            try {
                initProfileAudit(userId);
            } catch (DuplicateKeyException ignored) {
                // 并发首次访问时由主键唯一约束裁决，调用方继续执行 CAS 更新。
            }
        }
    }
}
