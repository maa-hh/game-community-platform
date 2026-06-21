package com.game.community.game.service;

import com.game.community.model.vo.gameaccount.SignInResultVO;
import com.game.community.model.vo.gameaccount.SignInStatusVO;

public interface GameSignInService {

    SignInResultVO signIn(Long userId);

    SignInStatusVO getStatus(Long userId);
}
