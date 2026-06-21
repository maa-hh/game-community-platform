package com.game.community.model.dto.gameaccount;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class GameAccountBindDTO implements Serializable {

    @NotBlank(message = "游戏账号号不能为空")
    private String accountNo;
}
