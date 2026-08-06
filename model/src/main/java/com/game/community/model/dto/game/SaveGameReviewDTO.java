package com.game.community.model.dto.game;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class SaveGameReviewDTO implements Serializable {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低为1分")
    @Max(value = 10, message = "评分最高为10分")
    private Integer score;

    @Size(max = 2000, message = "短评不能超过2000字")
    private String content;
}
