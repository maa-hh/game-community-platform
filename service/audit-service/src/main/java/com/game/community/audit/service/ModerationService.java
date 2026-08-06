package com.game.community.audit.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.audit.HandleModerationTaskDTO;
import com.game.community.model.message.ModerationTaskMessage;
import com.game.community.model.message.ReportAuditMessage;
import com.game.community.model.vo.audit.ModerationTaskDetailVO;
import com.game.community.model.vo.audit.ModerationTaskClaimVO;
import com.game.community.model.vo.audit.ModerationTaskVO;

public interface ModerationService {

    void receiveTask(ModerationTaskMessage message);

    void receiveReport(ReportAuditMessage message);

    PageResult<ModerationTaskVO> pageTasks(Long page, Long size, Integer status, String taskType);

    ModerationTaskDetailVO getDetail(Long taskId, Long viewerId);

    ModerationTaskClaimVO claim(Long taskId, Long handlerId);

    void handle(Long taskId, Long handlerId, HandleModerationTaskDTO dto);

    int autoPassExpiredTasks();

    int recoverExpiredClaims();
}
