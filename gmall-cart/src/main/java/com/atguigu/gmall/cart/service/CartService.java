package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.mapper.CartMapper;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.pojo.UserInfo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.CartException;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.concurrent.ListenableFuture;
import rx.internal.schedulers.SchedulePeriodicHelper;
import rx.internal.util.SuppressAnimalSniffer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CartService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private CartAsyncService cartAsyncService;

    private static final String KEY_PREFIX = "cart:info:";
    private static final String PRICE_PREFIX = "cart:price:";

    public void addCart(Cart cart) {
        //获取用户的登录状态
        String userId = getUserId();
        //根据用户的id、userKey获取内层的map<skuId,cartJson>
        BoundHashOperations boundHashOps = redisTemplate.boundHashOps(KEY_PREFIX + userId);
        //判断当前用户的购物车是否包含当前商品
        String skuId = cart.getSkuId().toString();
        if (boundHashOps.hasKey(skuId)) {
            //如果包含：更新购物车商品数量
            //根据skuId查询到对应的购物车数据
            String cartJson = boundHashOps.get(skuId).toString();
            BigDecimal count = cart.getCount(); //用户添加商品的购物数量
            //将cartJson序列化为cart对象
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount().add(count));

            //更新mysql中的数据  更新购物车数据
            this.cartAsyncService.updateCart(userId, skuId, cart);
        } else {
            //如果购物车中不包含：添加商品记录
            cart.setUserId(userId);
            //数据库中skuId和count字段已经传递过来，需要初始化其他字段。
            //查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                throw new CartException("添加的商品不存在");
            }
            cart.setTitle(skuEntity.getTitle());
            cart.setPrice(skuEntity.getPrice());
            cart.setDefaultImage(skuEntity.getDefaultImage());
            //查询营销信息
            ResponseVo<List<ItemSaleVo>> ItemSaleResponseVo = this.smsClient.queryItemSalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> saleVos = ItemSaleResponseVo.getData();
            cart.setSales(JSON.toJSONString(saleVos));
            //查询销售信息
            ResponseVo<List<SkuAttrValueEntity>> SkuAttrResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrVo = SkuAttrResponseVo.getData();
            cart.setSaleAttrs(JSON.toJSONString(skuAttrVo));
            //查询库存
            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }

            cart.setCheck(true);

            //写入数据库  新增一条购物车
            this.cartAsyncService.insertCart(userId, cart);
            //添加实时价格缓存
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuEntity.getPrice().toString());
        }
        //写入数据库redis
        boundHashOps.put(skuId, JSON.toJSONString(cart));
    }


    private String getUserId() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userId = userInfo.getUserKey();
        if (userInfo.getUserId() != null) {
            userId = userInfo.getUserId().toString();
        }
        return userId;
    }

    public Cart queryCartBySkuId(Cart cart) {
        //获取登录信息
        String userId = this.getUserId();
        BoundHashOperations boundHashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!boundHashOps.hasKey(cart.getSkuId().toString())) {
            throw new CartException("当前用户购物车中没有对应的商品");
        }
        String cartJson = boundHashOps.get(cart.getSkuId().toString()).toString();
        return JSON.parseObject(cartJson, Cart.class);
    }

    @Async
    public void executor1() {
        try {
            System.out.println("executor1开始执行。。。。。。。。。。");
            TimeUnit.SECONDS.sleep(4);
            int i = 10 / 0;
            System.out.println("executor1执行结束。。。。。。。。。。");
        } catch (InterruptedException e) {
            AsyncResult.forExecutionException(e);
        }
        //return AsyncResult.forValue("hello executor1");
    }

    @Async
    public void executor2() {
        try {
            System.out.println("executor2开始执行。。。。。。。。。。");
            TimeUnit.SECONDS.sleep(5);
            System.out.println("executor2执行结束。。。。。。。。。。");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //return AsyncResult.forValue("hello executor2");

    }

    public List<Cart> queryCarts() {
        //1、获取userKey
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        String userKey = userInfo.getUserKey();
        //2、以userKey为key获取未登录购物车
        BoundHashOperations<String, Object, Object> unLoginHashOps = redisTemplate.boundHashOps(KEY_PREFIX + userKey);
        //获取到未登录的carts购物车集合
        List<Object> unLoginCartsJsons = unLoginHashOps.values();
        List<Cart> unLoginCarts = null;
        if (!CollectionUtils.isEmpty(unLoginCartsJsons)) {
            //未登录购物车
            unLoginCarts = unLoginCartsJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                //设置实时价格
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }
        //3、获取userId，如果userId为空，则直接返回未登录购物车
        Long userId = userInfo.getUserId();
        if (userId == null) {
            return unLoginCarts;
        }

        //4、合并未登录购物车，到 登录购物车中
        //4.1 获取登录购物车
        BoundHashOperations<String, Object, Object> loginHashOps = redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!CollectionUtils.isEmpty(unLoginCarts)) {

            //遍历未登录购物车
            unLoginCarts.forEach(cart -> {
                String skuId = cart.getSkuId().toString();
                BigDecimal count = cart.getCount();
                if (loginHashOps.hasKey(skuId)) {
                    //4.2如果登录购物车中包含未登录购物车记录  更新数量
                    //获取登录购物车记录
                    String cartJson = loginHashOps.get(skuId).toString();
                    //合并记录
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount().add(count));
                    //写入数据库

                    this.cartAsyncService.updateCart(userId.toString(), skuId, cart);
                } else {
                    //4.3 如果登录购物车不包含未登录购物车记录   新增一条购物车记录
                    //登录购物车中没有未登录购物车的记录
                    //设置userId  这里的cart是未登录购物车
                    cart.setUserId(userId.toString());
                    this.cartAsyncService.insertCart(userId.toString(), cart);
                }
                //写入到redis数据库
                loginHashOps.put(skuId, JSON.toJSONString(cart));
            });

            //5、删除未登录购物车
            this.redisTemplate.delete(KEY_PREFIX + userKey);
            this.cartAsyncService.deleteCartByUserKey(userKey);
        }


        //6、查询合并后的购物车，返回给页面
        List<Object> loginCartJsons = loginHashOps.values();
        if (!CollectionUtils.isEmpty(loginCartJsons)) {
            return loginCartJsons.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                cart.setCurrentPrice(new BigDecimal(this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId())));
                return cart;
            }).collect(Collectors.toList());
        }

        return null;
    }

    public void updateNum(Cart cart) {
        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!hashOps.hasKey(cart.getSkuId().toString())) {
            throw new CartException("没有这条购物车记录");
        }
        BigDecimal count = cart.getCount();

        String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
        cart = JSON.parseObject(cartJson, Cart.class);
        cart.setCount(count);

        hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        this.cartAsyncService.updateCart(userId, cart.getSkuId().toString(), cart);
    }

    public void updateStatus(Cart cart) {
        String userId = this.getUserId();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        if (!hashOps.hasKey(cart.getSkuId().toString())) {
            throw new CartException("没有这条购物车记录");
        }
        Boolean check = cart.getCheck();

        String cartJson = hashOps.get(cart.getSkuId().toString()).toString();
        cart = JSON.parseObject(cartJson, Cart.class);
        cart.setCheck(check);

        hashOps.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        this.cartAsyncService.updateCart(userId, cart.getSkuId().toString(), cart);
    }

    public void deleteCart(Long skuId) {
        String userId = this.getUserId();

        BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(KEY_PREFIX + userId);

        hashOps.delete(skuId.toString());
        this.cartAsyncService.deleteCartByUserIdAndSkuId(userId, skuId);
    }

    public List<Cart> queryCheckedCartByUserId(Long userId) {

        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        List<Object> values = hashOps.values();
        if (CollectionUtils.isEmpty(values)) {
            throw new CartException("你没有选中的购物车记录");
        }
        return values.stream().map(cartJson -> JSON.parseObject(cartJson.toString(), Cart.class))
                .filter(Cart::getCheck).collect(Collectors.toList());


    }
}
