package com.game.community.content.service;

/**
 * 图文帖正文纯文字 + 封面多图的历史数据迁移。
 */
public interface ArticleContentMigrationService {

    /**
     * 迁移 Mongo 正文与 MySQL 封面：去掉正文插图标记，图片并入封面列表。
     *
     * @return 实际更新的文章数
     */
    int migrateLegacyImageTextContent();
}
