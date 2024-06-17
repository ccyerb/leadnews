package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.ChannelDto;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.service.WmChannelService;
import com.heima.wemedia.service.WmNewsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@Transactional
@Slf4j
public class WmChannelServiceImpl extends ServiceImpl<WmChannelMapper, WmChannel> implements WmChannelService {


    @Override
    public ResponseResult findAll() {
        return ResponseResult.okResult(list());
    }

    @Override
    public ResponseResult findByNameAndPage(ChannelDto dto) {
        //1.检查参数
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //检查分页
        dto.checkParam();

        //2.模糊查询+分页
        IPage page = new Page(dto.getPage(), dto.getSize());

        //频道名称模糊查询
        LambdaQueryWrapper<WmChannel> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(dto.getName())) {
            lambdaQueryWrapper.like(WmChannel::getName, dto.getName());
        }

        page = page(page, lambdaQueryWrapper);

        //3.结果返回
        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        responseResult.setData(page.getRecords());
        return responseResult;
    }

    @Override
    public ResponseResult insert(WmChannel adChannel) {
        //1.检查参数
        if (adChannel == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        WmChannel channel = getOne(Wrappers.<WmChannel>lambdaQuery().eq(WmChannel::getName, adChannel.getName()));
        if (channel != null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_EXIST, "频道已存在");
        }

        //2.保存
        adChannel.setCreatedTime(new Date());
        save(adChannel);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Autowired
    private  WmNewsService wmNewsService;
    @Override
    public ResponseResult update(WmChannel adChannel) {
        //1.检查参数
        if (adChannel == null || adChannel.getId() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //判断是否被引用
        int count = wmNewsService.count(Wrappers.<WmNews>lambdaQuery()
                                    .eq(WmNews::getChannelId, adChannel.getId())
                                    .eq(WmNews::getStatus, WmNews.Status.PUBLISHED.getCode()));
        if(count > 0){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"频道被引用不能修改或禁用");
        }

        //2.修改
        updateById(adChannel);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult delete(Integer id) {
        //1.检查参数
        if(id == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.查询频道
        WmChannel wmChannel = getById(id);
        if(wmChannel == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }

        //3.频道是否有效
        if(wmChannel.getStatus()){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"频道有效，不能删除");
        }

        //判断是否被引用
        int count = wmNewsService.count(Wrappers.<WmNews>lambdaQuery().eq(WmNews::getChannelId, wmChannel.getId())
                .eq(WmNews::getStatus, WmNews.Status.PUBLISHED.getCode()));
        if(count > 0){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"频道被引用不能删除");
        }

        //4.删除
        removeById(id);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}