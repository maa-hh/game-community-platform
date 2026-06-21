package com.game.community.social.mongo;

import com.game.community.model.mongo.SocialCommentContent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SocialCommentContentRepository extends MongoRepository<SocialCommentContent, String> {

    Optional<SocialCommentContent> findByCommentId(Long commentId);

    List<SocialCommentContent> findByCommentIdIn(Collection<Long> commentIds);

    void deleteByCommentId(Long commentId);
}
