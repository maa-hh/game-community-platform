package com.game.community.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.search.SearchHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {

    @Insert("""
            INSERT INTO t_search_history (user_id, keyword, create_time, update_time)
            VALUES (#{userId}, #{keyword}, #{now}, #{now})
            ON DUPLICATE KEY UPDATE update_time = #{now}
            """)
    int upsert(@Param("userId") Long userId,
               @Param("keyword") String keyword,
               @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM t_search_history
            WHERE user_id = #{userId}
              AND id NOT IN (
                SELECT id FROM (
                    SELECT id
                    FROM t_search_history
                    WHERE user_id = #{userId}
                    ORDER BY update_time DESC, id DESC
                    LIMIT #{keepCount}
                ) AS keep_rows
              )
            """)
    int deleteExcess(@Param("userId") Long userId, @Param("keepCount") int keepCount);
}
