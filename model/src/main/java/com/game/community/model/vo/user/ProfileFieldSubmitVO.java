package com.game.community.model.vo.user;

import com.game.community.model.enums.user.AuditFieldType;
import lombok.Data;

/**
 * 字段审核提交结果（立即返回，异步审核）
 */
@Data
public class ProfileFieldSubmitVO {

    private Long taskId;

    private AuditFieldType field;

    /** 该字段当前审核状态：1审核中 */
    private Integer auditStatus;

    /** 仅对应 field 有值 */
    private String pendingValue;
}
