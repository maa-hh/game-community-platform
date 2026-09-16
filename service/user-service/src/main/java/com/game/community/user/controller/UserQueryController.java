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

    /** 按对外 accountId 查询用户公开资料。 */
    @LoginCheck
    @GetMapping("/{accountId}")
    public Result<UserPublicVO> getUserById(@PathVariable("accountId") Long accountId) {
        return userQueryService.getUserPublicByAccountId(accountId);
    }

    /** 按对外 accountId 查询用户卡片资料。 */
    @LoginCheck
    @GetMapping("/simple/{accountId}")
    public Result<UserCardVO> getUserSimpleById(@PathVariable("accountId") Long accountId) {
        return userQueryService.getUserCardByAccountId(accountId);
    }

    /** 批量用户名片（ids 为对外 accountId） */
    @GetMapping("/ids")
    public Result<List<UserCardVO>> getUsersByAccountIds(@RequestParam("ids") List<Long> ids) {
        return userQueryService.getUsersByAccountIds(ids);
    }

    /** 按 accountId 精确匹配或按昵称前缀分页搜索用户。 */
    @LoginCheck
    @GetMapping("/simple/search")
    public PageResult<UserCardVO> searchUsers(@Valid UserSearchPageDTO dto) {
        return userQueryService.searchUsers(dto);
    }
}
