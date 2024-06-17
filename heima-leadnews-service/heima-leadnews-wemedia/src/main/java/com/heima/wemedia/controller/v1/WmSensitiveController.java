package com.heima.wemedia.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.SensitiveDto;
import com.heima.model.wemedia.pojos.WmSensitive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sensitive")
public class WmSensitiveController {
    @DeleteMapping("/del/{id}")
    public ResponseResult delete(@PathVariable("id") Integer id) {
        return null;
    }
    @PostMapping("/list")
    public ResponseResult list(@RequestBody SensitiveDto dto) {
        return null;
    }
    @PostMapping("/save")
    public ResponseResult save(@RequestBody WmSensitive wmSensitive) {
        return null;
    }
    @PostMapping("/update")
    public ResponseResult update(@RequestBody WmSensitive wmSensitive) {
        return null;
    }
}
