package com.game.community.game.mongo;

import com.game.community.model.mongo.CharacterDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CharacterDetailRepository extends MongoRepository<CharacterDetail, String> {

    Optional<CharacterDetail> findByCharacterCode(String characterCode);
}
