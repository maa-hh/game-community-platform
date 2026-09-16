package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.user.AccountIdPool;
import com.game.community.common.constant.user.UserConstants;
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

    /** CAS 预占一个可用 accountId。 */
    @Update("UPDATE t_account_id_pool SET status = " + UserConstants.ACCOUNT_POOL_RESERVED
            + ", update_time = NOW() " +
            "WHERE account_id = #{accountId} AND status = " + UserConstants.ACCOUNT_POOL_AVAILABLE)
    /** 原子预占指定 accountId，返回实际更新行数。 */
    int casReserve(@Param("accountId") Long accountId);

    /** 将预占的 accountId 绑定到新用户。 */
    @Update("UPDATE t_account_id_pool SET user_id = #{userId}, update_time = NOW() " +
            "WHERE account_id = #{accountId} AND status = " + UserConstants.ACCOUNT_POOL_RESERVED
            + " AND user_id IS NULL")
    /** 将已预占的 accountId 绑定到内部 userId，返回实际更新行数。 */
    int bindUserId(@Param("accountId") Long accountId, @Param("userId") Long userId);

    /**
     * 批量扩容号池（INSERT IGNORE 防重复）。
     * 不要用超大递归 CTE 一次生成整档位数，会触发 cte_max_recursion_depth。
     */
    @Insert("<script>" +
            "INSERT IGNORE INTO t_account_id_pool (account_id, digit_count, status, create_time, update_time) VALUES " +
            "<foreach collection='accountIds' item='aid' separator=','>" +
            "(#{aid}, #{digitCount}, " + UserConstants.ACCOUNT_POOL_AVAILABLE + ", NOW(), NOW())" +
            "</foreach>" +
            "</script>")
    /** 批量扩充指定位数的可用 accountId 号池。 */
    int insertBatch(@Param("accountIds") List<Long> accountIds, @Param("digitCount") int digitCount);
}
