package com.game.community.user.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.UserSearchPageDTO;
import com.game.community.model.vo.user.UserCardVO;
import com.game.community.model.vo.user.UserPublicVO;
import com.game.community.user.service.UserQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户查询控制器（只读）
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserQueryController {

    private final UserQueryService userQueryService;

    @LoginCheck
    /** 执行 getUserById 对应的业务处理。 */
    @GetMapping("/{accountId}")
    public Result<UserPublicVO> getUserById(@PathVariable("accountId") Long accountId) {
        return userQueryService.getUserPublicByAccountId(accountId);
    }

    @LoginCheck
    /** 执行 getUserSimpleById 对应的业务处理。 */
    @GetMapping("/simple/{accountId}")
    public Result<UserCardVO> getUserSimpleById(@PathVariable("accountId") Long accountId) {
        return userQueryService.getUserCardByAccountId(accountId);
    }

    /** 批量用户名片（ids 为对外 accountId） */
    @GetMapping("/ids")
    public Result<List<UserCardVO>> getUsersByAccountIds(@RequestParam("ids") List<Long> ids) {
        return userQueryService.getUsersByAccountIds(ids);
    }

    @LoginCheck
    /** 执行 searchUsers 对应的业务处理。 */
    @GetMapping("/simple/search")
    public PageResult<UserCardVO> searchUsers(@Valid UserSearchPageDTO dto) {
        return userQueryService.searchUsers(dto);
    }
}
