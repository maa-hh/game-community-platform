package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.user.AccountIdPool;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 账号ID号池 Mapper
 */
@Mapper
public interface AccountIdPoolMapper extends BaseMapper<AccountIdPool> {

    @Update("UPDATE t_account_id_pool SET status = 1, update_time = NOW() " +
            "WHERE account_id = #{accountId} AND status = 0")
    int casReserve(@Param("accountId") Long accountId);

    @Update("UPDATE t_account_id_pool SET user_id = #{userId}, update_time = NOW() " +
            "WHERE account_id = #{accountId} AND status = 1 AND user_id IS NULL")
    int bindUserId(@Param("accountId") Long accountId, @Param("userId") Long userId);

    /**
     * CAS抢占一个可用ID：将status从0改为1，同时绑定userId
     * 并发安全：WHERE status=0保证同一ID不会被两个请求同时抢占
     *
     * @param userId 占用该ID的用户ID
     * @return 影响行数，1=抢占成功，0=该ID已被抢占需重试
     */
    @Update("UPDATE t_account_id_pool SET status = 1, user_id = #{userId}, update_time = NOW() " +
            "WHERE account_id = #{accountId} AND status = 0")
    int casOccupy(@Param("accountId") Long accountId, @Param("userId") Long userId);

    /**
     * 批量扩容号池（INSERT IGNORE 防重复）。
     * 不要用超大递归 CTE 一次生成整档位数，会触发 cte_max_recursion_depth。
     */
    @Insert("<script>" +
            "INSERT IGNORE INTO t_account_id_pool (account_id, digit_count, status, create_time, update_time) VALUES " +
            "<foreach collection='accountIds' item='aid' separator=','>" +
            "(#{aid}, #{digitCount}, 0, NOW(), NOW())" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("accountIds") List<Long> accountIds, @Param("digitCount") int digitCount);
}
