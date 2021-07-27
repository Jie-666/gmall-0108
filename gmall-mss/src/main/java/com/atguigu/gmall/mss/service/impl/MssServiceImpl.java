package com.atguigu.gmall.mss.service.impl;


import com.atguigu.gmall.common.utils.FormUtils;
import com.atguigu.gmall.common.utils.RandomUtils;
import com.atguigu.gmall.mss.service.MssService;
import com.atguigu.gmall.mss.utils.HttpUtils;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MssServiceImpl implements MssService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void send(String mobile) {
        //1、检查手机号码的格式：正则表达式
        boolean b = FormUtils.isMobile(mobile);
        if(!b){
            //手机号码格式错误
            throw new RuntimeException("手机号码格式错误");
        }
        //2、检查该手机号码是否在一分钟内获取过验证码
        //手机号码获取验证码时缓存时需要指定唯一的key: 验证码的key ， 记录获取次数的key ， 一分钟内记录该手机号码获取验证码的key
        String codeKey = "mss:code:" + mobile;
        String codeCountKey = "mss:count:" + mobile;
        String codeFlagKey = "mss:per:min:" + mobile;

        System.out.println(codeKey);
        System.out.println(codeCountKey);
        System.out.println(codeFlagKey);


        Boolean hasKey = redisTemplate.hasKey(codeFlagKey);
        if(hasKey){
            //表示一分钟内获取过验证码
            throw new RuntimeException("一分钟内获取验证码次数太多");
        }
        //3、检查该手机号码24小时内获取验证码的次数是否超过5次
        Object codeCountObj = redisTemplate.opsForValue().get(codeCountKey);
        int codeCount = 0;
        if(codeCountObj!=null){
            codeCount = Integer.parseInt(codeCountObj.toString());
        }
        if(codeCount>=5){
            throw new RuntimeException("一天内获取验证码次数太多");
        }
        //4、检查该手机号码是否已经注册[等注册写完后再完成]
        //5、给手机号码发送验证码
        String host = "http://dingxin.market.alicloudapi.com";
        String path = "/dx/sendSms";
        String method = "POST";
        String appcode = "68a4a030e0344c51b0e02b032da52273";
        Map<String, String> headers = new HashMap<String, String>();//请求头参数
        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
        headers.put("Authorization", "APPCODE " + appcode);
        Map<String, String> querys = new HashMap<String, String>();//请求参数：在url地址后拼接的参数
        querys.put("mobile", mobile);
        String code = RandomUtils.getSixBitRandom();
        querys.put("param", "code:"+code);
        querys.put("tpl_id", "TP1711063");
        Map<String, String> bodys = new HashMap<String, String>();//请求体内容
        try {
            HttpResponse response = HttpUtils.doPost(host, path, method, headers, querys, bodys);
            String s = EntityUtils.toString(response.getEntity(), "UTF-8");
            Gson gson = new Gson();
            Map<String,String> map = gson.fromJson(s, Map.class);
            String returnCode = map.get("return_code");
            if(!"00000".equals(returnCode)){
                //验证码发送失败
                throw new RuntimeException("验证码发送失败");
            }
            //6、将验证码存到redis中缓存:10分钟
            redisTemplate.opsForValue().set(codeKey , code , 10 , TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(codeFlagKey , code , 1 , TimeUnit.MINUTES );
            //7、增加手机号码获取短信的次数
            //该手机号码第一次获取验证码时需要初始化获取验证码的次数并设置过期时间，否则在之前的基础上+1
            if(codeCount==0){
                redisTemplate.opsForValue().set(codeCountKey , "1" , 1 , TimeUnit.DAYS);
            }else{
                redisTemplate.opsForValue().increment(codeCountKey);
            }
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("验证码发送失败");
        }

    }
}
