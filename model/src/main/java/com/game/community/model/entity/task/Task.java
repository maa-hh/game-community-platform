package com.game.community.model.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务表
 */
@Data
@TableName("t_task")
public class Task implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务类型: 1-文章定时发布, 2-文章定时下架, 3-其他
     */
    private Integer type;

    /**
     * 任务参数（JSON格式）
     */
    private String param;

    /**
     * 关联的业务ID（如文章ID）
     */
    private Long businessId;

    /**
     * 任务执行时间（立即执行为null，延迟执行为具体时间）
     */
    private LocalDateTime executeTime;

    /**
     * 任务状态: 0-待执行, 1-执行中, 2-执行成功, 3-执行失败, 4-已取消
     */
    private Integer status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 错误信息
     */
    private String errorMsg;

    private Integer queued;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

}
