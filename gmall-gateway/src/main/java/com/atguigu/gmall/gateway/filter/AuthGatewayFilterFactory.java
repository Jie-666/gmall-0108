package com.atguigu.gmall.gateway.filter;

import com.atguigu.gmall.common.utils.IpUtils;
import com.atguigu.gmall.common.utils.JwtUtils;
import com.atguigu.gmall.common.utils.RsaUtils;
import com.atguigu.gmall.gateway.config.JwtProperties;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.swing.text.ChangedCharSetException;
import java.io.File;
import java.net.URI;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@EnableConfigurationProperties(JwtProperties.class)
public class AuthGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthGatewayFilterFactory.PathConfig> {
    @Autowired
    private JwtProperties properties;

    /**
     * 重写父类的构造方法，告诉父类，这里使用的是PathConfig对象接收配置
     */
    public AuthGatewayFilterFactory() {
        super(PathConfig.class);
    }

    @Override
    public GatewayFilter apply(PathConfig config) {
        // 实现GatewaFilter接口
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {


                //System.out.println("我是局部过滤器，只拦截指定的路由的请求:" + config.paths);
                ServerHttpRequest request = exchange.getRequest();
                ServerHttpResponse response = exchange.getResponse();
                //获取当前请求的路径
                String carPath = request.getURI().getPath();

                //获取拦截路径名单
                List<String> paths = config.paths;
                //1、判断当前请求，路径不在拦截路径中的直接放行
                if (paths.stream().allMatch(path -> carPath.indexOf(path) == -1)) {
                    return chain.filter(exchange);
                }

                //2、获取token信息，同步请求：cookie中，异步请求：从token头中获取
                String token = request.getHeaders().getFirst(properties.getToken());
                //如果请求头中没有，从cookie中获取
                if (StringUtils.isBlank(token)) {
                    MultiValueMap<String, HttpCookie> cookies = request.getCookies();
                    if (!CollectionUtils.isEmpty(cookies) && cookies.containsKey(properties.getCookieName())) {
                        HttpCookie cookieName = cookies.getFirst(properties.getCookieName());
                        token = cookieName.getValue();
                    }
                }
                //3、判断token是否为空，为空则直接拦截并重定向到登录页面
                if (StringUtils.isBlank(token)) {
                    // 重定向到登录
                    // 303状态码表示由于请求对应的资源存在着另一个URI，应使用重定向获取请求的资源
                    response.setStatusCode(HttpStatus.SEE_OTHER); // 设置状态码303
                    //设置重定向回调地址
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete();
                }
                try {
                    //4、解析jwt类型的token，如果解析异常，重定向到登录页面
                    Map<String, Object> map = JwtUtils.getInfoFromToken(token, properties.getPublicKey());

                    //5、获取载荷中的ip(登录用户) 与 当前请求的ip地址(当前用户)
                    String ip = map.get("ip").toString();
                    String catIp = IpUtils.getIpAddressAtGateway(request);
                    if (!StringUtils.equals(ip, catIp)) {
                        response.setStatusCode(HttpStatus.SEE_OTHER); // 设置状态码303
                        //设置重定向回调地址
                        response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                        return response.setComplete();
                    }

                    //6、把解析后的用户信息传递给后续服务
                    //通过这种方式不能传递到其他微服务，只能改变局部的Request对象
//                    request.getHeaders().set("username", map.get("username").toString());
//                    request.getHeaders().set("userId", map.get("userId").toString());
                    //使用request.mutate(),修改全局的Request对象
                    request.mutate()
                            .header("username", map.get("username").toString())
                            .header("userId", map.get("userId").toString())
                            .build();
                    exchange.mutate().request(request).build();

                    //7、放行
                    return chain.filter(exchange);
                } catch (Exception e) {
                    e.printStackTrace();
                    response.setStatusCode(HttpStatus.SEE_OTHER); // 设置状态码303
                    //设置重定向回调地址
                    response.getHeaders().set(HttpHeaders.LOCATION, "http://sso.gmall.com/toLogin.html?returnUrl=" + request.getURI());
                    return response.setComplete();
                }
            }
        };
    }

    /**
     * 指定字段的读取速度
     * 可以通过不同的字段分别读取：/xxx , /yyy
     * 在这里希望通过List集合来接收多个字段
     *
     * @return
     */
    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("paths");
    }

    /**
     * 指定读取字段的结果集类型
     *
     * @return
     */
    @Override
    public ShortcutType shortcutType() {
        return ShortcutType.GATHER_LIST;
    }

    /**
     * 读取配置的内部类
     */
    @Data
    public static class PathConfig {
        //        public String key;
        //        private String value;
        private List<String> paths;
    }
}
