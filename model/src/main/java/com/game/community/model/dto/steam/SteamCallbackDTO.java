package com.game.community.model.dto.steam;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Steam OpenID 回调参数。
 *
 * <p>Steam OpenID 参数名称由协议动态定义，使用 Map 保存原始参数；state
 * 单独建模，避免在业务 Service 中继续传递裸 Map 和独立字符串。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SteamCallbackDTO implements Serializable {

    /** 社区发起授权时生成的一次性状态值。 */
    private String state;

    /** Steam OpenID 原始回调参数。 */
    private Map<String, String> openIdParams = new LinkedHashMap<>();
}
