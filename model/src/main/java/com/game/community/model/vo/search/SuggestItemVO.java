package com.game.community.model.vo.search;

import lombok.Data;

import java.io.Serializable;

@Data
public class SuggestItemVO implements Serializable {

    private Long id;

    private String term;

    private Integer weight;

    private String sourceType;
}
