package com.game.community.model.entity.audit;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_moderation_task")
public class ModerationTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外工单标识；数据库自增主键只用于服务内部关联。 */
    private String publicId;

    private String taskType;

    private Long sourceId;

    private Integer targetType;

    private Long targetId;

    private Long subjectUserId;

    private Long reporterId;

    private String reason;

    private String summary;

    private String extraPayload;

    private String targetStatusSnapshot;

    private LocalDateTime targetUpdatedAt;

    private Integer status;

    private String handleAction;

    private Long handlerId;

    private String handleRemark;

    private LocalDateTime claimTime;

    private LocalDateTime handleTime;

    /** 当前认领租约令牌，仅持有令牌的管理员可以提交处理。 */
    private String claimToken;

    /** 认领租约到期时间，服务实例宕机后允许任务被回收。 */
    private LocalDateTime leaseExpireTime;

    /** 当前执行请求令牌，防止同一管理员重复点击造成远程动作并发执行。 */
    private String actionRequestId;

    /** 最近一次处理错误，不影响任务重新入队。 */
    private String lastError;

    private Integer attemptCount;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
