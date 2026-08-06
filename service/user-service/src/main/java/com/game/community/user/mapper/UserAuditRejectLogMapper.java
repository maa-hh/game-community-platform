package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.user.UserAuditRejectLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审核队列拒绝日志
 */
@Mapper
public interface UserAuditRejectLogMapper extends BaseMapper<UserAuditRejectLog> {
}
