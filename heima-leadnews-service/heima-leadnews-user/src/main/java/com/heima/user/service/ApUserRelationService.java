package com.heima.user.service;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.dtos.UserRelationDto;

public interface ApUserRelationService {
    public ResponseResult follow(UserRelationDto dto);
}
