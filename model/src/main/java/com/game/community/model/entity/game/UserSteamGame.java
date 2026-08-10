package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user_steam_game")
public class UserSteamGame implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long appId;

    /** 兼容旧数据的展示名称，优先使用中文名，其次使用英文名。 */
    private String name;

    /** Steam 返回的简体中文名称。 */
    private String nameZh;

    /** Steam 返回的英文名称。 */
    private String nameEn;

    private String iconUrl;

    /** 无需加载详情即可展示的 Steam 商店头图。 */
    private String coverUrl;

    private Integer playtimeForever;

    private Integer playtimeTwoWeeks;

    private Integer achievementUnlocked;

    private Integer achievementTotal;

    private LocalDateTime lastPlayedAt;

    private LocalDateTime syncedAt;

    /** 最近一次成功更新该游戏的同步会话 ID。 */
    private String syncId;

    /** 当前是否仍在 Steam 游戏库中，完整同步结束时统一更新移出库的游戏。 */
    private Integer isOwned;

    /** 玩家成就状态最近一次成功同步时间。 */
    private LocalDateTime achievementSyncedAt;

    private LocalDateTime achievementLastAttemptAt;

    private LocalDateTime achievementNextRefreshAt;

    private String achievementRefreshStatus;

    private String achievementSyncId;

    private Integer achievementFailCount;
}
