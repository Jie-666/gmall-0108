package com.atguigu.gmall.auth.config;

import com.atguigu.gmall.common.utils.RsaUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.io.File;
import java.security.PrivateKey;
import java.security.PublicKey;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String pubFilePath;
    private String priFilePath;
    private String secret;
    private String cookieName;
    private String unick;
    private Integer expire;

    private PublicKey publicKey;
    private PrivateKey privateKey;
    @PostConstruct
    public void init(){
        try {
            //判断公钥私钥文件是否存在
            File pubFile = new File(pubFilePath);
            File priFile = new File(priFilePath);
            //如果不存在，重新生成公钥私钥文件
            if (!pubFile.exists() || !priFile.exists()) {
                RsaUtils.generateKey(pubFilePath,priFilePath,secret);
            }
            //初始化公钥私钥
            this.publicKey = RsaUtils.getPublicKey(pubFilePath);
            this.privateKey = RsaUtils.getPrivateKey(priFilePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
