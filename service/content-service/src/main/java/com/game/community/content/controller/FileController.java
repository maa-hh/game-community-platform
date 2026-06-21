package com.game.community.content.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.Result;
import com.game.community.utils.MinIOUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private static final int MAX_ARTICLE_IMAGE_COUNT = 10;

    private static final long MAX_ARTICLE_IMAGE_SIZE = 2L * 1024 * 1024;

    private final MinIOUtils minIOUtils;

    /**
     * 批量上传文件，返回URL列表
     */
    @LoginCheck
    @PostMapping("/upload")
    public Result<List<String>> upload(@RequestParam("files") List<MultipartFile> files) {
        validateArticleImages(files);
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            String url = minIOUtils.uploadPublicFile(file, file.getOriginalFilename(), "content");
            urls.add(url);
        }
        return Result.success(urls);
    }

    private void validateArticleImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (files.size() > MAX_ARTICLE_IMAGE_COUNT) {
            throw new BusinessException("文章图片最多支持10张");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("图片不能为空");
            }
            if (file.getSize() > MAX_ARTICLE_IMAGE_SIZE) {
                throw new BusinessException("单张图片大小不能超过2MB");
            }
            String contentType = file.getContentType();
            if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
                throw new BusinessException("仅支持上传图片文件");
            }
        }
    }
}
