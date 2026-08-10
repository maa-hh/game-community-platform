package com.game.community.social.mongo;

import com.game.community.model.mongo.SocialGameReviewReplyContent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SocialGameReviewReplyContentRepository
        extends MongoRepository<SocialGameReviewReplyContent, String> {

    Optional<SocialGameReviewReplyContent> findByReplyId(String replyId);

    List<SocialGameReviewReplyContent> findByReplyIdIn(Collection<String> replyIds);
}
