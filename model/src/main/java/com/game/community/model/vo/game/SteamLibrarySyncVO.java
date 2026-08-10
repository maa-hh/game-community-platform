package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;

/** Steam 游戏库分批同步结果。 */
@Data
public class SteamLibrarySyncVO implements Serializable {

    /** 后续分页请求需要携带的会话 ID。 */
    private String syncId;

    /** 本次最后处理完成的页码，从 0 开始。 */
    private Integer page;

    /** 下一次请求应提交的页码。 */
    private Integer nextPage;

    /** 每批固定处理数量。 */
    private Integer pageSize;

    /** Steam 返回的总游戏数量。 */
    private Integer total;

    /** 本次请求连续处理的批次数量。 */
    private Integer batchCount;

    /** 当前同步会话已经处理的游戏数量。 */
    private Integer processedCount;

    /** Steam 游戏库是否公开可同步。 */
    private Boolean libraryPublic;

    /** 是否已经完成全部分页。 */
    private Boolean completed;

    /** 是否还有下一批数据。 */
    private Boolean hasMore;
}
