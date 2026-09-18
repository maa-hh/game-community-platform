package com.game.community.model.vo.aiagent;

import lombok.Data;

import java.util.List;

/** 内部 AI 搜索词扩展响应。 */
@Data
public class AiSearchTermsResponseVO {

    private List<String> terms;

    private String provider;

    private String model;
}
