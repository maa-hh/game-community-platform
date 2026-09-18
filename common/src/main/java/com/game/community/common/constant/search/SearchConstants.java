package com.game.community.common.constant.search;

public final class SearchConstants {

    /**
     * 文章索引版本化。analyzer 属于不可原地修改的 mapping，切换版本可以避免
     * 旧索引仍使用 standard analyzer，导致代码中的 IK 配置实际上没有生效。
     */
    public static final String ARTICLE_INDEX = "article_index_v2";
    public static final String SUGGEST_INDEX = "suggest_index";
    /** 游戏索引版本化，避免已有索引的 analyzer 被错误地原地修改。 */
    public static final String GAME_INDEX = "game_index_v2";

    public static final String SEARCH_MODE_LEXICAL = "lexical";
    public static final String SEARCH_MODE_SEMANTIC = "semantic";
    public static final String SEARCH_MODE_HYBRID = "hybrid";

    public static final int SEARCH_HISTORY_DEFAULT_MAX_RECORDS = 10;
    public static final int SEARCH_HISTORY_MAX_ALLOWED_RECORDS = 100;
    public static final int SEARCH_PAGE_DEFAULT_SIZE = 10;
    public static final int GAME_SEARCH_DEFAULT_SIZE = 20;
    public static final int SEARCH_PAGE_MAX_SIZE = 50;
    /** 默认匹配 ES index.max_result_window=10000（最大页码 200 * 50）。 */
    public static final int SEARCH_MAX_PAGE_NUMBER = 200;
    public static final int ARTICLE_REBUILD_PAGE_SIZE = 100;
    public static final int SUGGEST_ES_SYNC_PAGE_SIZE = 500;
    public static final int GAME_REBUILD_PAGE_SIZE = 200;
    public static final int GAME_TAG_MAX_IDS = 100;
    public static final int GAME_STATUS_ACTIVE = 1;

    public static final String SUGGEST_STATUS_ACTIVE = "ACTIVE";
    public static final String SUGGEST_STATUS_DISABLED = "DISABLED";
    public static final String SUGGEST_STATUS_EXPIRED = "EXPIRED";

    public static final String SUGGEST_SOURCE_ARTICLE = "ARTICLE";
    public static final String SUGGEST_SOURCE_UPLOAD = "UPLOAD";
    public static final String SUGGEST_SOURCE_AI = "AI";
    public static final String SUGGEST_SOURCE_GAME = "GAME";

    public static final int WEIGHT_UPLOAD = 3;
    public static final int WEIGHT_ARTICLE_TITLE = 2;
    public static final int WEIGHT_AI = 1;
    public static final int WEIGHT_GAME = 2;

    /** 新词观察期：入库后至少保留天数 */
    public static final int SUGGEST_OBSERVE_DAYS = 7;

    public static final int SUGGEST_AI_COLD_TTL_DAYS = 30;

    public static final int SUGGEST_MAX_RESULTS = 10;

    public static final int TERM_MIN_LEN = 2;
    public static final int TERM_MAX_LEN = 128;

    /** 文章语义向量维度（DashScope text-embedding-v4） */
    public static final int EMBEDDING_DIMS = 1024;

    /** 同步索引时用于 embedding 的最大字符数 */
    public static final int EMBED_TEXT_MAX_LEN = 2000;

    /** 混合检索候选池大小 */
    public static final int HYBRID_CANDIDATE_K = 50;

    /** 混合检索最大抓取条数（分页上限） */
    public static final int HYBRID_MAX_FETCH = 200;

    /** 混合检索 RRF 的 rank 常数，降低低排名候选的影响。 */
    public static final int HYBRID_RRF_K = 60;
    public static final float HYBRID_BM25_WEIGHT = 0.45F;
    public static final float HYBRID_VECTOR_WEIGHT = 0.55F;
    public static final float HYBRID_DUAL_HIT_BONUS = 0.08F;

    /** 启动重建使用 MySQL named lock，避免多实例重复全量重建。 */
    public static final String STARTUP_REBUILD_LOCK = "game-community:search:index-rebuild";
    public static final int STARTUP_REBUILD_LOCK_TIMEOUT_SECONDS = 0;
    public static final String SUGGEST_CLEANUP_LOCK = "game-community:search:suggest-cleanup";
    public static final String AI_SUGGEST_LOCK_PREFIX = "game-community:search:ai-suggest:";
    public static final String GAME_INDEX_LOCK_PREFIX = "game-community:search:game-index:";

    /** AI 扩词单次最多写入条数 */
    public static final int AI_SUGGEST_MAX_TERMS = 5;

    private SearchConstants() {
    }
}
