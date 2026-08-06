package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;

@Data
public class GameTagVO implements Serializable {

    private Long appId;

    private String name;

    private String headerImage;
}
