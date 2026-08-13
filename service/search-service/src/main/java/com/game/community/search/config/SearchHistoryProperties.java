package com.game.community.search.config;

import com.game.community.common.constant.search.SearchConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 搜索历史容量配置，避免把用户体验限制写死在业务实现中。 */
@Data
@Component
@ConfigurationProperties(prefix = "search.history")
public class SearchHistoryProperties {

    /** 每个用户保留的最近搜索条数，默认十条。 */
    private int maxRecords = SearchConstants.SEARCH_HISTORY_DEFAULT_MAX_RECORDS;

    /** 返回经过边界保护后的历史条数。 */
    public int normalizedMaxRecords() {
        return Math.min(Math.max(maxRecords, 1), SearchConstants.SEARCH_HISTORY_MAX_ALLOWED_RECORDS);
    }
}
