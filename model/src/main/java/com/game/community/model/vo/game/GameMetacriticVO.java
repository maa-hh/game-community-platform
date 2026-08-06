package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;

@Data
public class GameMetacriticVO implements Serializable {

    private Integer score;

    private String url;
}
