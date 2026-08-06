package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.user.UserAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户账户 Mapper
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
