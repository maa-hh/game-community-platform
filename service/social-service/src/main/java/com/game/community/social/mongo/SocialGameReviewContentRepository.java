package com.game.community.social.mongo;

import com.game.community.model.mongo.SocialGameReviewContent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SocialGameReviewContentRepository extends MongoRepository<SocialGameReviewContent, String> {

    Optional<SocialGameReviewContent> findByReviewId(String reviewId);

    List<SocialGameReviewContent> findByReviewIdIn(Collection<String> reviewIds);
}
