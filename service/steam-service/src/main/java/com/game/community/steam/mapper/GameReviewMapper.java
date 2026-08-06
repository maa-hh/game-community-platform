package com.game.community.steam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.game.GameReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface GameReviewMapper extends BaseMapper<GameReview> {

    @Select("SELECT AVG(score) FROM t_game_review WHERE app_id = #{appId} AND status = 1")
    BigDecimal selectAvgScore(@Param("appId") Long appId);

    @Select("SELECT COUNT(*) FROM t_game_review WHERE app_id = #{appId} AND status = 1")
    Integer selectReviewCount(@Param("appId") Long appId);
}
