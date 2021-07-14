package com.atguigu.gmall.mss.utils;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


@Data
@Configuration
//注意prefix要写到最后一个 "." 符号之前
@ConfigurationProperties(prefix="aliyun.mms")
public class MssProperties {
    //@Value("${aliyun.mms.appSecret}")
    public static String appSecret;//
    //@Value("${aliyun.mms.appCode}")
    public static String appCode;//
    //@Value("${aliyun.mms.host}")
    public static String host;// = "http://dingxin.market.alicloudapi.com";
   // @Value("${aliyun.mms.path}")
    public static String path;// = "/dx/sendSms";
    //@Value("${aliyun.mms.method}")
    public static String method;// = "POST";
   // @Value("${aliyun.mms.templateId}")
    public static String templateId;// = "TP1711063";
}