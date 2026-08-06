package com.game.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.UserSearchPageDTO;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAccount;
import com.game.community.model.vo.user.UserAccountVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.user.UserCardVO;
import com.game.community.model.vo.user.UserPublicVO;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.UserAccountService;
import com.game.community.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户查询服务实现（只读）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final UserMapper userMapper;
    private final UserAccountService userAccountService;

    @Override
    public Result<UserPublicVO> getUserPublicByAccountId(Long accountId) {
        User user = getByAccountId(accountId);
        return Result.success("查询成功", convertToPublicVO(user));
    }

    @Override
    public Result<UserCardVO> getUserCardByAccountId(Long accountId) {
        return Result.success("查询成功", convertToCardVO(getByAccountId(accountId)));
    }

    @Override
    public Result<List<UserCardVO>> getUsersByAccountIds(List<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Result.success("查询成功", List.of());
        }
        if (accountIds.size() > 100) {
            throw new BusinessException("批量查询不能超过100个");
        }
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getAccountId, accountIds));
        List<UserCardVO> list = users.stream().map(this::convertToCardVO).toList();
        return Result.success("查询成功", list);
    }

    @Override
    public Result<List<UserCardInternalVO>> getUsersByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Result.success("查询成功", List.of());
        }
        if (userIds.size() > 100) {
            throw new BusinessException("批量查询不能超过100个");
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        List<UserCardInternalVO> list = users.stream().map(this::convertToInternalCardVO).toList();
        return Result.success("查询成功", list);
    }

    @Override
    public Result<UserCardInternalVO> getUserInternalByAccountId(Long accountId) {
        return Result.success("查询成功", convertToInternalCardVO(getByAccountId(accountId)));
    }

    @Override
    public Result<List<UserCardInternalVO>> getUsersInternalByAccountIds(List<Long> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Result.success("查询成功", List.of());
        }
        if (accountIds.size() > 100) {
            throw new BusinessException("批量查询不能超过100个");
        }
        List<UserCardInternalVO> list = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getAccountId, accountIds))
                .stream()
                .map(this::convertToInternalCardVO)
                .toList();
        return Result.success("查询成功", list);
    }

    @Override
    public PageResult<UserCardVO> searchUsers(UserSearchPageDTO dto) {
        int current = dto == null || dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int pageSize = dto == null || dto.getSize() == null || dto.getSize() < 1
                ? 10
                : Math.min(dto.getSize(), 20);
        String key = dto == null ? "" : dto.resolveKeyword();
        key = key == null ? "" : key.trim();
        if (!StringUtils.hasText(key)) {
            return PageResult.of(List.of(), (long) current, (long) pageSize, 0L);
        }

        if (key.matches("^\\d+$")) {
            try {
                Long accountId = Long.parseLong(key);
                User byAccount = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getAccountId, accountId));
                if (byAccount != null) {
                    return PageResult.of(
                            List.of(convertToCardVO(byAccount)),
                            (long) current,
                            (long) pageSize,
                            1L);
                }
            } catch (NumberFormatException ignored) {
                // fall through to username prefix
            }
        }

        Page<User> userPage = userMapper.selectPage(new Page<>(current, pageSize), new LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, key)
                .orderByDesc(User::getCreateTime));
        List<UserCardVO> records = userPage.getRecords().stream()
                .map(this::convertToCardVO)
                .toList();
        return PageResult.of(records, (long) current, (long) pageSize, userPage.getTotal());
    }

    @Override
    public Result<UserAccountVO> getUserAccountVO(Long userId) {
        UserAccount account;
        try {
            account = userAccountService.refreshStatus(userId);
        } catch (BusinessException e) {
            return Result.success("查询成功", null);
        }
        UserAccountVO vo = new UserAccountVO();
        vo.setUserId(account.getUserId());
        vo.setStatus(account.getStatus().getCode());
        vo.setType(account.getType().getCode());
        vo.setBanUntil(account.getBanUntil());
        vo.setBanReason(account.getBanReason());
        return Result.success("查询成功", vo);
    }

    private User getByAccountId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException("用户不存在");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccountId, accountId));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private UserPublicVO convertToPublicVO(User user) {
        UserPublicVO vo = new UserPublicVO();
        vo.setAccountId(user.getAccountId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        vo.setSteamAccount(user.getSteamAccount());
        vo.setFollowCount(0);
        vo.setFansCount(0);
        return vo;
    }

    private UserCardVO convertToCardVO(User user) {
        UserCardVO vo = new UserCardVO();
        vo.setAccountId(user.getAccountId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        return vo;
    }

    private UserCardInternalVO convertToInternalCardVO(User user) {
        UserCardInternalVO vo = new UserCardInternalVO();
        vo.setUserId(user.getId());
        vo.setAccountId(user.getAccountId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        return vo;
    }
}
