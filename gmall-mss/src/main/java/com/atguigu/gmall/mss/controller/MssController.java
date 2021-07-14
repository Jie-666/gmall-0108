package com.atguigu.gmall.mss.controller;

import com.aliyuncs.exceptions.ClientException;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.utils.FormUtils;
import com.atguigu.gmall.common.utils.RandomUtils;
import com.atguigu.gmall.mss.service.MssService;
import com.baomidou.mybatisplus.extension.api.R;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

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
    public ResponseVo getCode(@PathVariable String mobile) throws Exception {

        //校验手机号是否合法
        if(StringUtils.isEmpty(mobile) || !FormUtils.isMobile(mobile)){
            return ResponseVo.ok("手机号码不合法");
        }

        //生成验证码
        String checkCode = RandomUtils.getFourBitRandom();
        //发送验证码
        mssService.send(mobile, checkCode);
        //将验证码存入redis缓存
        String key = "checkCode::" + mobile;
        redisTemplate.opsForValue().set(key, checkCode, 5, TimeUnit.MINUTES);

        return ResponseVo.ok("短信发送成功");
    }

}
