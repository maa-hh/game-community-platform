package com.game.community.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.gameaccount.GameAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameAccountMapper extends BaseMapper<GameAccount> {
}
