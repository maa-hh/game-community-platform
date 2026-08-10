package com.game.community.model.dto.steam;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * Steam 游戏库分页同步参数。
 *
 * <p>首次请求不传 syncId，后续请求携带上一次返回的 syncId，并按 page 从 0 递增。</p>
 */
@Data
public class SteamLibrarySyncQuery implements Serializable {

    /** 分页同步会话 ID，首次请求为空。 */
    private String syncId;

    /** 从 0 开始的同步页码。 */
    @Min(0)
    private Integer page = 0;
}
