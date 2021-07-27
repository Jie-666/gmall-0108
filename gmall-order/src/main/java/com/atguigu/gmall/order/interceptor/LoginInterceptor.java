package com.atguigu.gmall.order.interceptor;


import com.atguigu.gmall.order.pojo.UserInfo;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {


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
        String userId = request.getHeader("userId");
        String username = request.getHeader("username");

        THREAD_LOCAL.set(new UserInfo(Long.valueOf(userId), null, username));


        return true;
    }

    public static UserInfo getUserInfo() {
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
