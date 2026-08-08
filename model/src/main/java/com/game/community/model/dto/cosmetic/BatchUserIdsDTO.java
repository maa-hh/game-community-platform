package com.game.community.model.dto.cosmetic;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class BatchUserIdsDTO implements Serializable {

    @NotEmpty(message = "用户ID列表不能为空")
    /** 对外只接收 accountId，服务内再解析 t_user.id。 */
    private List<Long> accountIds;
}
