package com.game.community.danmaku.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.danmaku.DanmakuMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DanmakuMessageMapper extends BaseMapper<DanmakuMessage> {

    @Select("SELECT * FROM t_danmaku_message "
            + "WHERE video_public_id = #{videoPublicId} "
            + "AND display_time_ms >= #{fromMs} AND display_time_ms < #{toMs} "
            + "AND status = 1 ORDER BY display_time_ms ASC, seq ASC LIMIT #{limit}")
    List<DanmakuMessage> selectHistory(@Param("videoPublicId") String videoPublicId,
                                       @Param("fromMs") Long fromMs,
                                       @Param("toMs") Long toMs,
                                       @Param("limit") Integer limit);
}
