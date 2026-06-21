package com.game.community.game.service;

import com.game.community.model.dto.gameaccount.GameAccountBindDTO;
import com.game.community.model.vo.gameaccount.GameAccountBindVO;
import com.game.community.model.vo.gameaccount.GameAccountProfileVO;

public interface GameAccountBindingService {

    GameAccountBindVO bind(Long userId, GameAccountBindDTO dto);

    GameAccountBindVO rebind(Long userId, GameAccountBindDTO dto);

    void unbind(Long userId);

    GameAccountProfileVO current(Long userId);
}
