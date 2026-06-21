package com.game.community.model.elasticsearch;

import lombok.Data;

import java.io.Serializable;

@Data
public class SuggestDocument implements Serializable {

    private Long id;

    private String suggest;

    private String suggestNgram;
}
