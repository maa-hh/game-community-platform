package com.game.community.steam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.game.GameSearchOutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GameSearchOutboxMapper extends BaseMapper<GameSearchOutboxEvent> {

    @Select("SELECT * FROM t_game_search_outbox "
            + "WHERE status IN (0,3) AND (next_retry_time IS NULL OR next_retry_time <= NOW()) "
            + "ORDER BY id LIMIT #{limit}")
    List<GameSearchOutboxEvent> selectPending(@Param("limit") int limit);

    @Update("UPDATE t_game_search_outbox SET status = 1, lock_token = #{token}, lock_time = NOW(), "
            + "update_time = NOW() WHERE id = #{id} AND status IN (0,3)")
    int claim(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE t_game_search_outbox SET status = 2, lock_token = NULL, lock_time = NULL, "
            + "last_error = NULL, update_time = NOW() "
            + "WHERE id = #{id} AND status = 1 AND lock_token = #{token}")
    int markSent(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE t_game_search_outbox SET status = CASE WHEN retry_count + 1 >= 10 THEN 4 ELSE 3 END, "
            + "retry_count = retry_count + 1, "
            + "next_retry_time = DATE_ADD(NOW(), INTERVAL LEAST(300, POW(2, retry_count + 1)) SECOND), "
            + "last_error = LEFT(#{error}, 1000), lock_token = NULL, lock_time = NULL, update_time = NOW() "
            + "WHERE id = #{id} AND status = 1 AND lock_token = #{token}")
    int markFailed(@Param("id") Long id, @Param("token") String token, @Param("error") String error);

    @Update("UPDATE t_game_search_outbox SET status = 3, lock_token = NULL, lock_time = NULL, "
            + "next_retry_time = NOW(), update_time = NOW() WHERE status = 1 AND lock_time < #{staleBefore}")
    int releaseStale(@Param("staleBefore") LocalDateTime staleBefore);
}
