package com.game.community.model.dto.user;

import com.game.community.model.base.PageDto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户搜索分页：keyword 纯数字按 accountId 精确，否则 username 前缀
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserSearchPageDTO extends PageDto {

    @NotBlank(message = "请输入搜索关键字")
    private String keyword;

    public String resolveKeyword() {
        return keyword == null ? "" : keyword.trim();
    }
}
