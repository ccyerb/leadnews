package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.SensitiveDto;
import com.heima.model.wemedia.pojos.WmSensitive;
import io.swagger.models.auth.In;

public interface WmSensitiveService extends IService<WmSensitive> {
    ResponseResult delete(Integer id);
    ResponseResult list(SensitiveDto dto);
    ResponseResult insert(WmSensitive wmSensitive);
    ResponseResult update(WmSensitive wmSensitive);

}
