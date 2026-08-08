package com.game.community.model.entity.danmaku;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_danmaku_message")
public class DanmakuMessage implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String eventId;

    private String clientMessageId;

    private String videoPublicId;

    private Long videoTimeMs;

    private Long displayTimeMs;

    private Long seq;

    private Long userId;

    /** 对外返回使用的账号 ID，内部 userId 只用于本表关联。 */
    private Long accountId;

    private String usernameSnapshot;

    private String avatarSnapshot;

    private String content;

    /** 1-实时可见，2-举报/审核隐藏。 */
    private Integer status;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
