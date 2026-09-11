package com.game.community.model.enums.aiagent;

/** AI 审核内容类型。 */
public enum ModerationContentType {
    TEXT,
    IMAGE,
    /** 一次提交文章正文及其全部图片，Agent 内部执行一次多模态审核。 */
    ARTICLE
}
