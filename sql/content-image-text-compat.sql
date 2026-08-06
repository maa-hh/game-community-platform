-- 图文帖数据模型兼容：文章类型归并为图文（封面多图 + 正文纯文字）
-- 可重复执行。Mongo 正文迁移请调用 content-service：
-- POST /feign/content/articles/migrate-image-text-content

UPDATE t_article
SET post_type = 1,
    update_time = NOW()
WHERE post_type = 2
  AND (deleted IS NULL OR deleted = 0);
