package com.game.community.game.mongo;

import com.game.community.model.mongo.SkinDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SkinDetailRepository extends MongoRepository<SkinDetail, String> {

    Optional<SkinDetail> findBySkinCode(String skinCode);
}
