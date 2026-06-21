package com.game.community.common.constant.aiagent;

public final class AiAgentConstants {

    public static final String CHAT_MEMORY_KEY_PREFIX = "ai:chat:memory:";
    public static final long CHAT_MEMORY_TTL_HOURS = 72L;
    public static final int CHAT_MEMORY_MAX_ROUNDS = 10;
    public static final int CHAT_MEMORY_MAX_MESSAGES = CHAT_MEMORY_MAX_ROUNDS * 2;

    public static final String KNOWLEDGE_INDEX = "ai_knowledge_segment";
    public static final int SEGMENT_TARGET_LENGTH = 420;
    public static final int SEGMENT_OVERLAP = 60;
    public static final int RETRIEVAL_TOP_K = 6;
    public static final int RETRIEVAL_CANDIDATE_K = 12;

    public static final float BM25_WEIGHT = 0.45f;
    public static final float VECTOR_WEIGHT = 0.55f;
    public static final float DUAL_HIT_BONUS = 0.08f;

    public static final int SOURCE_TYPE_TEXT = 1;
    public static final int SOURCE_TYPE_FILE = 2;

    public static final int DOCUMENT_STATUS_ACTIVE = 1;
    public static final int DOCUMENT_STATUS_DISABLED = 0;

    public static final int INDEX_STATUS_PENDING = 0;
    public static final int INDEX_STATUS_READY = 1;
    public static final int INDEX_STATUS_INDEXING = 2;
    public static final int INDEX_STATUS_FAILED = 3;

    private AiAgentConstants() {
    }
}
