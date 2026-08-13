package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.shop.ShopOrderPaidOutbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShopOrderPaidOutboxMapper extends BaseMapper<ShopOrderPaidOutbox> {

    @Insert("INSERT IGNORE INTO t_shop_order_paid_outbox(event_id, order_no, topic, message_key, payload, status, "
            + "retry_count, next_retry_time, lock_token, lock_time, last_error, create_time, update_time) "
            + "VALUES(#{eventId}, #{orderNo}, #{topic}, #{messageKey}, #{payload}, 0, 0, NOW(), '', "
            + "'1970-01-01 00:00:00', '', NOW(), NOW())")
    int insertIfAbsent(ShopOrderPaidOutbox outbox);

    @Select("SELECT * FROM t_shop_order_paid_outbox WHERE status IN (0, 3) "
            + "AND next_retry_time <= NOW() ORDER BY id LIMIT #{limit}")
    List<ShopOrderPaidOutbox> selectPending(@Param("limit") int limit);

    @Update("UPDATE t_shop_order_paid_outbox SET status = 1, lock_token = #{token}, lock_time = NOW(), "
            + "update_time = NOW() WHERE id = #{id} AND status IN (0, 3)")
    int claim(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE t_shop_order_paid_outbox SET status = 2, lock_token = '', "
            + "lock_time = '1970-01-01 00:00:00', last_error = '', update_time = NOW() "
            + "WHERE id = #{id} AND status = 1 AND lock_token = #{token}")
    int markSent(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE t_shop_order_paid_outbox SET status = CASE WHEN retry_count + 1 >= 20 THEN 4 ELSE 3 END, "
            + "retry_count = retry_count + 1, next_retry_time = DATE_ADD(NOW(), INTERVAL LEAST(600, "
            + "POW(2, retry_count + 1)) SECOND), last_error = LEFT(#{error}, 1000), lock_token = '', "
            + "lock_time = '1970-01-01 00:00:00', update_time = NOW() "
            + "WHERE id = #{id} AND status = 1 AND lock_token = #{token}")
    int markFailed(@Param("id") Long id, @Param("token") String token, @Param("error") String error);

    @Update("UPDATE t_shop_order_paid_outbox SET status = 3, lock_token = '', "
            + "lock_time = '1970-01-01 00:00:00', next_retry_time = NOW(), update_time = NOW() "
            + "WHERE status = 1 AND lock_time < #{staleBefore}")
    int releaseStale(@Param("staleBefore") LocalDateTime staleBefore);
}
