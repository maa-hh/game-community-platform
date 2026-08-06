package com.game.community.user.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.UserSearchPageDTO;
import com.game.community.model.vo.user.UserAccountVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.user.UserCardVO;
import com.game.community.model.vo.user.UserPublicVO;

import java.util.List;

/**
 * 用户查询服务：只读操作
 */
public interface UserQueryService {

    Result<UserPublicVO> getUserPublicByAccountId(Long accountId);

    Result<UserCardVO> getUserCardByAccountId(Long accountId);

    /** 对外批量查询（ids 为 accountId） */
    Result<List<UserCardVO>> getUsersByAccountIds(List<Long> accountIds);

    /** Feign 内部批量查询（ids 为内部 userId） */
    Result<List<UserCardInternalVO>> getUsersByUserIds(List<Long> userIds);

    /** Feign：按 accountId 查内部名片（含 userId） */
    Result<UserCardInternalVO> getUserInternalByAccountId(Long accountId);

    /** 服务内批量查询 accountId 对应的内部名片（含 userId） */
    Result<List<UserCardInternalVO>> getUsersInternalByAccountIds(List<Long> accountIds);

    /**
     * 用户搜索：纯数字优先 accountId 精确匹配，未命中再按 username 前缀；
     * 非数字仅 username 前缀。单页最多 20。
     */
    PageResult<UserCardVO> searchUsers(UserSearchPageDTO dto);

    /** 查询用户账户状态（供 Feign 跨服务调用） */
    Result<UserAccountVO> getUserAccountVO(Long userId);
}
