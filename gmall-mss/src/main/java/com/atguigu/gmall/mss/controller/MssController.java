package com.atguigu.gmall.mss.controller;


import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.utils.FormUtils;
import com.atguigu.gmall.common.utils.RandomUtils;
import com.atguigu.gmall.mss.service.MssService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/mss")
@Api(tags = "短信管理")
@Slf4j
public class MssController {

    @Autowired
    private MssService mssService;

    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("send/{mobile}")
    public ResponseVo getCode(@PathVariable("mobile") String mobile)  {
        //发送验证码
        mssService.send(mobile);
        return ResponseVo.ok("短信发送成功");
    }

}
