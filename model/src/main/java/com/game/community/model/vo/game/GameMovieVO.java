package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;

@Data
public class GameMovieVO implements Serializable {

    private String name;

    private String thumbnailUrl;

    private String mp4Url;

    private String webmUrl;
}
