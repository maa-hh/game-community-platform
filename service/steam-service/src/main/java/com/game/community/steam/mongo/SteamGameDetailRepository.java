package com.game.community.steam.mongo;

import com.game.community.model.mongo.SteamGameDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SteamGameDetailRepository extends MongoRepository<SteamGameDetail, String> {

    Optional<SteamGameDetail> findByAppId(Long appId);
}
