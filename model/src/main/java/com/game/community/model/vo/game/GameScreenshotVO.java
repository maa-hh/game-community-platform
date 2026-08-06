package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;

@Data
public class GameScreenshotVO implements Serializable {

    private String thumbnailUrl;

    private String fullUrl;
}
