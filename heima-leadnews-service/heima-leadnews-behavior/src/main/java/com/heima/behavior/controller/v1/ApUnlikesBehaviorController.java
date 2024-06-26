package com.heima.behavior.controller.v1;

import com.heima.behavior.service.ApReadBehaviorService;
import com.heima.behavior.service.ApUnlikesBehaviorService;
import com.heima.model.behavior.dtos.ReadBehaviorDto;
import com.heima.model.behavior.dtos.UnLikesBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/read_behavior")
public class ApUnlikesBehaviorController {
    @Autowired
    private ApUnlikesBehaviorService apUnlikesBehaviorService;
    @PostMapping
    public ResponseResult unlike(@RequestBody UnLikesBehaviorDto dto) {
        return apUnlikesBehaviorService.unlike(dto);
    }

}