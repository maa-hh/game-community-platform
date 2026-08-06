package com.game.community.content.controller;

import com.game.community.content.service.SharePageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外链分享 OG 落地页（供微信等爬虫抓取 meta）
 */
@RestController
@RequestMapping("/share/post")
@RequiredArgsConstructor
public class SharePageController {

    private final SharePageService sharePageService;

    @GetMapping(value = "/{articleId}", produces = MediaType.TEXT_HTML_VALUE)
    public String sharePost(@PathVariable("articleId") String publicId) {
        return sharePageService.renderSharePage(publicId);
    }
}
