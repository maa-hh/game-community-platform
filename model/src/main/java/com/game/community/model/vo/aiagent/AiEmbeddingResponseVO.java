package com.game.community.model.vo.aiagent;

import lombok.Data;

import java.util.List;

/** 内部 AI embedding 响应。 */
@Data
public class AiEmbeddingResponseVO {

    private List<Float> vector;

    private String provider;

    private String model;
}
