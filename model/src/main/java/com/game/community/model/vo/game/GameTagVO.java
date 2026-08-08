package com.game.community.model.vo.game;

import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import lombok.Data;

import java.io.Serializable;

@Data
public class GameTagVO implements Serializable {

    @JsonView(ApiJsonViews.Public.class)
    private Long appId;

    @JsonView(ApiJsonViews.Public.class)
    private String name;

    @JsonView(ApiJsonViews.Public.class)
    private String headerImage;
}
