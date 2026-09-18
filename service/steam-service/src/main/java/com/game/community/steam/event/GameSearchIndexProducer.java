package com.game.community.steam.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.model.message.GameSearchSyncMessage;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GamePriceVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.entity.game.GameSearchOutboxEvent;
import com.game.community.steam.mapper.GameSearchOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSearchIndexProducer {

    private final GameSearchOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    /** 发布游戏卡片索引更新事件，由搜索服务异步写入 ES。 */
    @Transactional(rollbackFor = Exception.class)
    public void upsert(GameListItemVO game) {
        if (game == null || game.getAppId() == null) {
            return;
        }
        GameSearchSyncMessage message = new GameSearchSyncMessage(
                game.getAppId(), GameSearchSyncMessage.UPSERT, game, LocalDateTime.now());
        GameSearchOutboxEvent event = new GameSearchOutboxEvent();
        event.setEventKey("game-search:" + game.getAppId() + ":" + UUID.randomUUID());
        event.setAppId(game.getAppId());
        event.setAction(GameSearchSyncMessage.UPSERT);
        event.setPayload(toJson(message));
        event.setStatus(0);
        event.setRetryCount(0);
        event.setCreateTime(LocalDateTime.now());
        event.setUpdateTime(event.getCreateTime());
        outboxMapper.insert(event);
    }

    private String toJson(GameSearchSyncMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("游戏搜索索引事件序列化失败", e);
        }
    }

    /** 将目录实体转换为索引消息并发布，供详情或评分更新复用。 */
    public void upsertCatalog(GameCatalog catalog) {
        if (catalog == null) {
            return;
        }
        GameListItemVO item = new GameListItemVO();
        item.setAppId(catalog.getAppId());
        item.setName(org.springframework.util.StringUtils.hasText(catalog.getDisplayName())
                ? catalog.getDisplayName() : catalog.getSteamName());
        item.setNameZh(catalog.getNameZh());
        item.setNameEn(catalog.getNameEn());
        item.setAliases(java.util.stream.Stream.of(catalog.getNameZh(), catalog.getNameEn())
                .filter(org.springframework.util.StringUtils::hasText)
                .distinct()
                .toList());
        item.setCoverUrl(org.springframework.util.StringUtils.hasText(catalog.getCoverOverride())
                ? catalog.getCoverOverride() : catalog.getHeaderImage());
        item.setGenres(catalog.getGenres());
        item.setDeveloper(catalog.getDevelopers() == null || catalog.getDevelopers().isEmpty()
                ? null : catalog.getDevelopers().get(0));
        item.setPublisher(catalog.getPublishers() == null || catalog.getPublishers().isEmpty()
                ? null : catalog.getPublishers().get(0));
        item.setReleaseDate(catalog.getReleaseDate());
        item.setSteamReviewScore(catalog.getSteamReviewScore());
        item.setSteamReviewCount(catalog.getSteamReviewCount());
        item.setAvgScore(catalog.getAvgScore());
        item.setReviewCount(catalog.getReviewCount());
        item.setDiscussCount(catalog.getDiscussCount());
        if (catalog.getPriceFinal() != null || catalog.getPriceInitial() != null
                || catalog.getSteamIsFree() != null) {
            GamePriceVO price = new GamePriceVO();
            price.setFree(catalog.getSteamIsFree());
            price.setCurrency(catalog.getPriceCurrency());
            price.setInitial(catalog.getPriceInitial());
            price.setFinalPrice(catalog.getPriceFinal());
            price.setDiscountPercent(catalog.getPriceDiscount());
            price.setDiscountEndAt(catalog.getPriceDiscountEndAt());
            price.setFormatted(catalog.getPriceFormatted());
            item.setPrice(price);
        }
        upsert(item);
    }
}
