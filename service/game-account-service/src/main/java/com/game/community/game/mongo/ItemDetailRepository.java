package com.game.community.game.mongo;

import com.game.community.model.mongo.ItemDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ItemDetailRepository extends MongoRepository<ItemDetail, String> {

    Optional<ItemDetail> findByItemCode(String itemCode);
}
