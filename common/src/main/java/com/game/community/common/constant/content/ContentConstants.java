package com.game.community.common.constant.content;

/**
 * 内容模块通用常量
 */
public class ContentConstants {

    // ==================== Redis Key 前缀 ====================

    /**
     * 用户Feed信箱Key前缀
     */
    public static final String FEED_KEY_PREFIX = "user:feed:";

    /**
     * 用户关注列表Key前缀
     */
    public static final String FOLLOW_KEY_PREFIX = "user:follow:";

    /**
     * 立即执行任务队列Key
     */
    public static final String TASK_QUEUE_KEY = "task:queue:immediate";

    /**
     * 延迟任务ZSet Key
     */
    public static final String TASK_ZSET_KEY = "task:queue:delay";

    public static final String TASK_ZSET_LOCK_KEY = "task:queue:delay:lock";

    // ==================== Feed 相关常量 ====================

    /**
     * Feed信箱容量
     */
    public static final int FEED_CAPACITY = 100;

    /**
     * 粉丝批量获取大小
     */
    public static final int FAN_BATCH_SIZE = 100;

    /**
     * 推送批量大小
     */
    public static final int PUSH_BATCH_SIZE = 200;

    /**
     * 任务队列每次批量获取大小
     */
    public static final int TASK_POP_BATCH_SIZE = 20;

    /**
     * 任务执行线程池大小
     */
    public static final int TASK_THREAD_POOL_SIZE = 10;

    /**
     * 标记 queued=1 但长时间未更新的任务，视为 Redis 队列丢失后补偿回灌
     */
    public static final int TASK_ORPHAN_QUEUED_STALE_SECONDS = 30;

    /**
     * RUNNING 超时后重置为 PENDING，避免进程崩溃导致任务永久卡死
     */
    public static final int TASK_RUNNING_STALE_MINUTES = 5;

    public static final int TASK_LEASE_MINUTES = 10;

    // ==================== 文章状态 ====================

    /**
     * 文章状态
     */
    public static final class ArticleStatus {
        /**
         * 草稿
         */
        public static final int DRAFT = 0;

        /**
         * 已发布
         */
        public static final int PUBLISHED = 1;

        /**
         * 待审核
         */
        public static final int PENDING = 2;

        /**
         * 已下架
         */
        public static final int OFFLINE = 3;

        /**
         * 审核驳回
         */
        public static final int REJECTED = 4;
    }

    /**
     * 文章审核状态
     */
    public static final class AuditStatus {
        /**
         * 待审核
         */
        public static final int PENDING = 0;

        /**
         * 审核通过
         */
        public static final int PASS = 1;

        /**
         * 审核中/待人工审核
         */
        public static final int REVIEW = 2;

        /**
         * 违规/驳回
         */
        public static final int BLOCK = 3;
    }

    /**
     * 审核阶段
     */
    public static final class AuditStage {
        public static final int LOCAL_TEXT = 1;

        public static final int AI_TEXT = 2;

        public static final int AI_IMAGE = 3;
    }

    /**
     * 审核类型
     */
    public static final class AuditType {
        /**
         * DFA 敏感词审核
         */
        public static final int DFA = 1;

        /**
         * 阿里云文本审核
         */
        public static final int ALIYUN_TEXT = 2;

        /**
         * 阿里云图片审核
         */
        public static final int ALIYUN_IMAGE = 3;

        /**
         * OCR + DFA 审核
         */
        public static final int OCR_DFA = 4;

        /**
         * OCR + 阿里云审核
         */
        public static final int OCR_ALIYUN = 5;
    }

    /**
     * 任务状态
     */
    public static final class TaskStatus {
        /**
         * 待执行
         */
        public static final int PENDING = 0;

        /**
         * 执行中
         */
        public static final int RUNNING = 1;

        /**
         * 已完成
         */
        public static final int COMPLETED = 2;

        /**
         * 执行失败
         */
        public static final int FAILED = 3;

        /**
         * 已取消
         */
        public static final int CANCELLED = 4;
    }

    /**
     * 任务类型
     */
    public static final class TaskType {
        public static final int ARTICLE_PUBLISH = 1;
    }

    /**
     * 发帖模式：图文 / 文章 / 视频 / 转发
     */
    public static final class PostType {
        /** 图文：封面可选，正文纯文字 */
        public static final int IMAGE_TEXT = 1;
        /** 文章：无封面，正文可插图 */
        public static final int ARTICLE = 2;
        /** 视频：主视频 + 介绍，封面可选 */
        public static final int VIDEO = 3;
        /** 转发：引用原帖 + 个人评论（附言） */
        public static final int REPOST = 4;
    }

    /**
     * 转发帖默认文案
     */
    public static final class Repost {
        public static final String DEFAULT_COMMENT = "转发了这条动态";

        private Repost() {
        }
    }

    /**
     * 媒体限制
     */
    public static final class MediaLimit {
        public static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
        public static final int IMAGE_MAX_COUNT = 20;
        public static final long VIDEO_MAX_BYTES = 500L * 1024 * 1024;
        public static final long CHUNK_SIZE_BYTES = 1L * 1024 * 1024;
        public static final int PRESIGNED_EXPIRE_SECONDS = 900;
    }

    /**
     * 分片上传 Redis
     */
    public static final class UploadRedis {
        public static final String SESSION_PREFIX = "content:upload:session:";
        /** 当前用户按文件 MD5 定位可恢复的上传会话。 */
        public static final String MD5_INDEX_PREFIX = "content:upload:md5:";
        /** 文章关联的 uploadId 集合 */
        public static final String ARTICLE_UPLOADS_PREFIX = "content:article:uploads:";
        public static final long SESSION_TTL_SECONDS = 24 * 3600L;
    }
}
