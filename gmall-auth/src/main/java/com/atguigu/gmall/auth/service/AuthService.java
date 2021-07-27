package com.atguigu.gmall.auth.service;

import com.atguigu.gmall.auth.config.JwtProperties;
import com.atguigu.gmall.auth.feign.GmallUmsClient;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.AuthException;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.ums.api.GmallUmsApi;
import com.atguigu.gmall.ums.entity.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
@EnableConfigurationProperties(JwtProperties.class)
@Service
public class AuthService {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private GmallUmsClient umsClient;


    public void login(String loginName, String password, HttpServletRequest request, HttpServletResponse response) throws Exception {
        //1、调用ums接口，校验用户名密码是否正确
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUser(loginName, password);
        UserEntity userEntity = userEntityResponseVo.getData();
        //1、判断信息是否为空
        if (userEntity == null){
            throw new AuthException("用户名或密码不正确！");
        }
        //3、组装载荷
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userEntity.getId());
        map.put("username", userEntity.getUsername());

        //4、防止jwt盗用，加入IP地址
        String ip = IpUtils.getIpAddressAtService(request);
        map.put("ip", ip);
        //5、制作jwt
        String token = JwtUtils.generateToken(map, this.jwtProperties.getPrivateKey(), this.jwtProperties.getExpire());
        //6、将jwt类型的token放入cookie中
        CookieUtils.setCookie(request,response, this.jwtProperties.getCookieName(), token, this.jwtProperties.getExpire()*60);
        //7、把昵称放入cookie
        CookieUtils.setCookie(request,response, this.jwtProperties.getUnick(), userEntity.getUsername(), this.jwtProperties.getExpire()*60);

    }
}
