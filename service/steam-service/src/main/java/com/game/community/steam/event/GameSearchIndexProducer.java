package com.game.community.steam.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.GameSearchSyncMessage;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GamePriceVO;
import com.game.community.model.vo.game.GameListItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSearchIndexProducer {

    private final KafkaTemplate<String, GameSearchSyncMessage> kafkaTemplate;

    public void upsert(GameListItemVO game) {
        if (game == null || game.getAppId() == null) {
            return;
        }
        GameSearchSyncMessage message = new GameSearchSyncMessage(
                game.getAppId(), GameSearchSyncMessage.UPSERT, game, LocalDateTime.now());
        kafkaTemplate.send(KafkaTopicConstants.GAME_SEARCH_SYNC_TOPIC,
                        String.valueOf(game.getAppId()), message)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("游戏搜索索引事件发送失败: appId={}", game.getAppId(), error);
                    }
                });
    }

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
