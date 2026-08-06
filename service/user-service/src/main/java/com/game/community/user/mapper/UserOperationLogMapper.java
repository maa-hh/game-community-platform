package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.user.UserOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户操作日志 Mapper
 */
@Mapper
public interface UserOperationLogMapper extends BaseMapper<UserOperationLog> {
}
