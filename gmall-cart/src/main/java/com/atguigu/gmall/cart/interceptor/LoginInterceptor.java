package com.atguigu.gmall.cart.interceptor;

import com.atguigu.gmall.cart.config.JwtProperties;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.utils.CookieUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtProperties properties;

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 前值方法，controller方法执行之前执行
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //获取userKey
        String userKey = CookieUtils.getCookieValue(request, this.properties.getUserKey());
        //如果为空，生成一个userKey，放入cookie
        if (StringUtils.isBlank(userKey)){
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request, response, this.properties.getUserKey(), userKey, this.properties.getExpire());
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserKey(userKey);
        //request.setAttribute("userKey", userKey);
        //获取userId
        // 获取token，如果token不为空，获取userId
        String token = CookieUtils.getCookieValue(request, this.properties.getCookieName());
        if (StringUtils.isNotBlank(token)){
            Map<String, Object> map = JwtUtils.getInfoFromToken(token, this.properties.getPublicKey());
            Long userId = Long.valueOf(map.get("userId").toString());
            userInfo.setUserId(userId);
        }

        THREAD_LOCAL.set(userInfo);

        return true;
    }

    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    /**
     * 后置方法，controller方法执行之后执行
     *
     * @param request
     * @param response
     * @param handler
     * @param modelAndView
     * @throws Exception
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        //System.out.println("这是后置方法");
    }

    /**
     * 完成方法，在视图渲染完成之后执行
     *
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //System.out.println("这是完成时方法");
        //由于使用的是线程池，所以每次使用完请求结束后，都要释放资源。
        THREAD_LOCAL.remove();
    }
}
