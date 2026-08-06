package com.game.community.model.vo.audit;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ModerationTaskDetailVO extends ModerationTaskVO implements Serializable {

    private String targetTitle;

    private String targetContent;

    private Object target;

    /** 工单创建时记录的目标状态快照 */
    private String targetStatusSnapshot;

    /** 工单创建时记录的目标更新时间 */
    private LocalDateTime targetUpdatedAt;

    /** 当前目标状态（可读） */
    private String currentTargetStatus;

    /** 当前目标更新时间 */
    private LocalDateTime currentTargetUpdatedAt;

    /** 目标内容相对工单是否已变更 */
    private Boolean targetContentChanged;

    /** 是否允许提交处理 */
    private Boolean reviewable;

    /** 不可处理时的说明 */
    private String reviewBlockReason;

    /** 举报类：当前可执行的处罚动作（不含 NO_VIOLATION） */
    private List<String> availableReportActions;

    /** 当前管理员认领后用于提交处理的租约令牌。 */
    private String claimToken;

    private LocalDateTime leaseExpireTime;
}
