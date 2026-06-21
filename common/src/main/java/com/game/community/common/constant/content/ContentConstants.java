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
}
