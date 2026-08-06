package com.game.community.model.dto.game;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class BatchAppIdsDTO implements Serializable {

    @NotEmpty(message = "游戏 ID 列表不能为空")
    @Size(max = 100, message = "单次最多查询 100 个游戏")
    private List<Long> appIds;
}
