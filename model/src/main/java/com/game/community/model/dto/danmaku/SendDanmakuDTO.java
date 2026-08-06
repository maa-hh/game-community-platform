package com.game.community.model.dto.danmaku;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** WebSocket 弹幕发送命令。 */
@Data
public class SendDanmakuDTO implements Serializable {

    @NotBlank(message = "客户端消息 ID 不能为空")
    @Size(max = 64, message = "客户端消息 ID 不能超过64字")
    private String clientMessageId;

    @NotNull(message = "视频时间不能为空")
    private Long videoTimeMs;

    @NotBlank(message = "弹幕内容不能为空")
    @Size(max = 200, message = "弹幕不能超过200字")
    private String content;
}
