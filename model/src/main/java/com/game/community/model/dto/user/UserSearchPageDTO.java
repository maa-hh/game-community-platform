package com.game.community.model.dto.user;

import com.game.community.model.base.PageDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户搜索分页请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserSearchPageDTO extends PageDto {

    private String username;
}
