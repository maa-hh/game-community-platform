package com.game.community.model.entity.task;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务执行日志表
 */
@Data
@TableName("t_task_log")
public class TaskLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 任务类型
     */
    private Integer type;

    /**
     * 关联的业务ID
     */
    private Long businessId;

    /**
     * 执行状态: 0-成功, 1-失败
     */
    private Integer status;

    /**
     * 执行结果信息
     */
    private String resultMsg;

    /**
     * 执行耗时（毫秒）
     */
    private Long costTime;

    /**
     * 异常信息
     */
    private String exceptionMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
