CREATE TABLE IF NOT EXISTS t_search_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '搜索历史ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    keyword VARCHAR(128) NOT NULL COMMENT '搜索关键词',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次搜索时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近搜索时间',
    UNIQUE KEY uk_search_history_user_keyword (user_id, keyword),
    KEY idx_search_history_user_time (user_id, update_time, id)
) COMMENT='用户搜索历史表';

/*
Elasticsearch index: article_index
- id: long
- userId: long
- username/avatar: keyword
- title/summary/content: text, ik_max_word + ik_smart
- categoryId/categoryName
- status/publishedTime/createTime/updateTime

Elasticsearch index: suggest_index
- suggest/suggestNgram: text, ik_max_word + ik_smart
*/
