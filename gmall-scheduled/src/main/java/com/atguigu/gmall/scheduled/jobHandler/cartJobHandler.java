package com.atguigu.gmall.scheduled.jobHandler;

import com.atguigu.gmall.scheduled.mapper.CartMapper;
import com.atguigu.gmall.scheduled.pojo.Cart;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
public class cartJobHandler {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CartMapper cartMapper;

    private static final String EXCEPTION_KEY = "cart:exception";
    private static final String KEY_PREFIX = "cart:info:";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @XxlJob("asyncCartData")
    public ReturnT<String> asyncCartData(String param) {
        //在定时任务中获取失败信息
        BoundSetOperations<String, String> setOps = this.redisTemplate.boundSetOps(EXCEPTION_KEY);
        //随机删除集合中的一个元素，并返回信息
        String userId = setOps.pop();
        while (userId != null) {
            //数据同步
            //先删除当前用户mysql中的所有购物车
            this.cartMapper.delete(new UpdateWrapper<Cart>().eq("user_id", userId));

            //从reids中获取当前用户的购物车
            BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
            //获取当前用户redis中所有购物车记
            List<Object> cartJsons = hashOps.values();
            if (!CollectionUtils.isEmpty(cartJsons)) {
                //同步数据
                cartJsons.forEach(cartJson -> {
                    try {
                        //反序列化
                        Cart cart = MAPPER.readValue(cartJson.toString(), Cart.class);
                        this.cartMapper.insert(cart);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                });
            }


            //获取下一个用户
            userId = setOps.pop();

        }


        return ReturnT.SUCCESS;
    }
}
