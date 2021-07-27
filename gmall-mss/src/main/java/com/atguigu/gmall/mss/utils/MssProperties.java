package com.atguigu.gmall.mss.utils;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;


@Data
@Component
//注意prefix要写到最后一个 "." 符号之前
@ConfigurationProperties(prefix="aliyun.mss")
public class MssProperties {

    public static String appSecret;//

    public static String appCode;//

    public static String host;// = "http://dingxin.market.alicloudapi.com";

    public static String path;// = "/dx/sendSms";

    public static String method;// = "POST";

    public static String templateId;// = "TP1711063";
}