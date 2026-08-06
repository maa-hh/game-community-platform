package com.game.community.model.dto.search;

import lombok.Data;

import java.io.Serializable;

@Data
public class SuggestTriggerDTO implements Serializable {

    private Long termId;

    private String term;
}
