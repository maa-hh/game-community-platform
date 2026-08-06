package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 发送验证码响应
 */
@Data
public class SendCodeVO {

    /** 验证码有效秒数 */
    private Integer expireIn;
}
