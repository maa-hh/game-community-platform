package com.game.community.model.vo.article;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 作者侧：上传进度 + 审核状态
 */
@Data
public class ArticleProgressVO implements Serializable {

    private Long articleId;
    private Integer status;
    private String auditMessage;
    private Integer postType;

    /** 任务状态：0待执行 1执行中 2完成 3失败 4已取消；无任务为 null */
    private Integer taskStatus;

    /** 最近一条发布任务的错误信息（失败时） */
    private String taskErrorMessage;

    /** 最近审核阶段：1本地文本 2 AI文本 3 AI图片 */
    private Integer auditStage;

    private String auditStageText;

    /** 上传进度 0-100；无进行中上传时可为 null 或 100 */
    private Integer uploadPercent;

    private String uploadStatus;

    private List<String> activeUploadIds;
}
