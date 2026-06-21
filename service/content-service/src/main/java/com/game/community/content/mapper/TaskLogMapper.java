package com.game.community.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.task.TaskLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务日志 Mapper
 */
@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLog> {
}
