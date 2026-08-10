package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "t_game_catalog", autoResultMap = true)
public class GameCatalog implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long appId;

    private String steamName;

    /** Steam 简体中文名称。 */
    private String nameZh;

    /** Steam 英文名称。 */
    private String nameEn;

    private String displayName;

    private String headerImage;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> developers;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> publishers;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> genres;

    private String releaseDate;

    private String steamUrl;

    private String communityShort;

    private String communityAbout;

    private String coverOverride;

    private String descSource;

    private Integer discussCount;

    private Integer reviewCount;

    private BigDecimal avgScore;

    /** Steam 好评率 0–100 */
    private Integer steamReviewScore;

    /** Steam 评价总数 */
    private Integer steamReviewCount;

    private Boolean steamIsFree;

    private String priceCurrency;

    private Integer priceInitial;

    private Integer priceFinal;

    private Integer priceDiscount;

    /** Steam 促销截止 Unix 秒 */
    private Long priceDiscountEndAt;

    private String priceFormatted;

    private LocalDateTime steamSyncedAt;

    /** 静态字段最近成功同步时间。 */
    private LocalDateTime staticSyncedAt;

    /** Steam 评分和评价人数最近成功同步时间。 */
    private LocalDateTime metricsSyncedAt;

    /** 价格最近成功同步时间。 */
    private LocalDateTime priceSyncedAt;

    /** Mongo 富详情最近成功同步时间。 */
    private LocalDateTime richSyncedAt;

    private LocalDateTime lastRefreshAttemptAt;

    private LocalDateTime nextRefreshAt;

    private String refreshStatus;

    private Boolean detailReady;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
