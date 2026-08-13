package com.game.community.model.vo.search;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 对外展示的搜索历史，只暴露搜索记录所需字段。 */
@Data
public class SearchHistoryVO implements Serializable {

    private Long id;

    private String keyword;

    private LocalDateTime updateTime;
}
