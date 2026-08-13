package com.game.community.model.dto.social;

import lombok.Data;

import java.io.Serializable;

/**
 * 社交信息流查询参数。
 *
 * <p>游标时间由客户端携带，文章内部主键不作为公开分页参数。</p>
 */
@Data
public class FeedQueryDTO implements Serializable {

    private String before;

    private Long size = 20L;

    private Integer postType;

    private Boolean includeSelf = true;
}
