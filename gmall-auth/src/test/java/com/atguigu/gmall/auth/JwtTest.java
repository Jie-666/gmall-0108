package com.atguigu.gmall.auth;

import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {

    // 别忘了创建D:\\project\rsa目录
	private static final String pubKeyPath = "E:\\1130尚硅谷\\01_谷粒商城0108\\project\\rsa.pub";
    private static final String priKeyPath = "E:\\1130尚硅谷\\01_谷粒商城0108\\project\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "23fg34s24@#$ASER");
    }

    @BeforeEach
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 1);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE2MjYyNjU5NzB9.MQXr-jqBc-TFKclrxHP0RBaceiZFnGSYS65xc6yHNYAE54RSw8-y6UgGzmFzUkgVA2S2mHHRphj7MrqAdf5OP-qLhupJsdtVXPjf2cpV4eO03GRMI6ExVt6qqAWL378Yzgde5-GYCnF9mOigtsLMXfO1hZEenJQkg3_k9mf5iR-DXh1aKJ2ZADXMRZIQ7aY57VgKI5QdXqdFkafSftRblT1m-XsAeX_lwxFHbcJtt0bV8IoxSUUPgRfldQo9JgWJmypNSuAWUt0CgP8wB9pwuZMUZbMoH2HYHKD0wQW5_aa7LNmSIPCgKcsTvYYLzHjsPrkzl3QtnXVgSwG2XbxrCw";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}