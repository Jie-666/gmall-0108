package com.atguigu.gmall.mss.service;


import com.aliyun.oss.ClientException;

public interface MssService {
    /**
     * 发送短信
     * @param mobile 电话号码
     * @param checkCode 验证码
     * @throws ClientException
     */
    void send(String mobile, String checkCode) throws Exception;
}