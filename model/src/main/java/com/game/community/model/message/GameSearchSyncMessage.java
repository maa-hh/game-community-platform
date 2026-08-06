package com.game.community.model.message;

import com.game.community.model.vo.game.GameListItemVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Steam 游戏搜索索引的幂等同步事件。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameSearchSyncMessage implements Serializable {

    public static final String UPSERT = "UPSERT";

    public static final String DELETE = "DELETE";

    private Long appId;

    private String action;

    private GameListItemVO game;

    private LocalDateTime eventTime;
}
