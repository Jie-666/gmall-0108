package com.atguigu.gmall.order.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptor.LoginInterceptor;
import com.atguigu.gmall.order.pojo.OrderConfirmVo;

import com.atguigu.gmall.order.pojo.UserInfo;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.ums.entity.UserAddressEntity;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.wms.Vo.SkuLockVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jdk.nashorn.internal.runtime.options.OptionTemplate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderService {
    @Autowired
    private GmallUmsClient umsClient;
    @Autowired
    private GmallCartClient cartClient;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallOmsClient omsClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String KEY_PREFIX = "order:token";

    public OrderConfirmVo confirm() {

        OrderConfirmVo confirmVo = new OrderConfirmVo();

        //获取userId
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        //查询收货地址列表
        ResponseVo<List<UserAddressEntity>> addressResponseVo = this.umsClient.queryUserAddressByUserId(userId);
        List<UserAddressEntity> addressEntities = addressResponseVo.getData();
        confirmVo.setAddresses(addressEntities);
        //查询送货清单
        ResponseVo<List<Cart>> cartResponseVo = this.cartClient.queryCheckedCartByUserId(userId);
        List<Cart> carts = cartResponseVo.getData();
        if (CollectionUtils.isEmpty(carts)) {
            throw new OrderException("你没有要购买的商品，请去添加商品");
        }
        //把购物车集合转化为OrderItemVo集合
        List<OrderItemVo> items = carts.stream().map(cart -> { //支取购物车skuId，count
            OrderItemVo itemVo = new OrderItemVo();
            itemVo.setCount(cart.getCount());
            //根据skuId查询sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                itemVo.setSkuId(skuEntity.getId());
                itemVo.setTitle(skuEntity.getTitle());
                itemVo.setPrice(skuEntity.getPrice());
                itemVo.setDefaultImage(skuEntity.getDefaultImage());
                itemVo.setWeight(skuEntity.getWeight());
            }

            //查询营销信息
            ResponseVo<List<ItemSaleVo>> salesResponseVo = smsClient.queryItemSalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            itemVo.setSales(itemSaleVos);

            //查询销售属性
            ResponseVo<List<SkuAttrValueEntity>> SaleAttrResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = SaleAttrResponseVo.getData();
            itemVo.setSaleAttrs(skuAttrValueEntities);

            //查询是否有货
            ResponseVo<List<WareSkuEntity>> wareResponseVo = wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));

            }
            return itemVo;
        }).collect(Collectors.toList());
        confirmVo.setItems(items);

        //根据用户id查询用户信息
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if (userEntity != null) {
            confirmVo.setBounds(userEntity.getIntegration());
        }

        //放重  设置订单唯一标识
        String orderToken = IdWorker.getIdStr();
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, orderToken, 3, TimeUnit.HOURS);

        confirmVo.setOrderToken(orderToken);

        return confirmVo;

    }

    public void submit(OrderSubmitVo submitVo) {
        //1、判断是否重复提交：页面中的orderToken到redis中查询，查到了说明没有提交,可以放行
        String orderToken = submitVo.getOrderToken(); //页面的orderToken
        if (StringUtils.isBlank(orderToken)) {
            throw new OrderException("非法提交！");
        }
        String script = "if(redis.call('get',KEYS[1])==ARGV[1])" +
                " then" +
                "   return redis.call('del',KEYS[1]) " +
                "else " +
                "   return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), orderToken);
        if (!flag) {
            throw new OrderException("请不要重复提交！");
        }

        //2、验总价
        List<OrderItemVo> items = submitVo.getItems();
        if (CollectionUtils.isEmpty(items)) {
            throw new OrderException("你没有选中商品！");
        }
        BigDecimal totalPrice = submitVo.getTotalPrice(); //页面总价格
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            //查询实时单价
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {//如果skuEntity为空，返回小计为0
                return new BigDecimal(0);
            }
            //计算实时小计
            return skuEntity.getPrice().multiply(item.getCount());
        }).reduce((a, b) -> a.add(b)).get();
        if (totalPrice.compareTo(currentTotalPrice) != 0) {
            throw new OrderException("页面已过期，请刷新后重试！");
        }

        //3、验库存并锁库存
        ResponseVo<List<SkuLockVo>> wareResponseVo = this.wmsClient.checkAndLock(items.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setCount(item.getCount().intValue());
            return skuLockVo;
        }).collect(Collectors.toList()), orderToken);
        List<SkuLockVo> skuLockVos = wareResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuLockVos)){ //不为空，说明有锁定失败的商品

            throw new OrderException(JSON.toJSONString(skuLockVos));
        }
        //4、创建订单
        Long userId = LoginInterceptor.getUserInfo().getUserId();
        String username = LoginInterceptor.getUserInfo().getUsername();

        try {
            this.omsClient.saveOrder(submitVo, userId, username);
            //发送延时消息，定时关单
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","order.ttl",orderToken);
        } catch (Exception e) {
            //创建订单出现异常，发送消息标记为无效订单，并解锁库存
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","order.disable",orderToken);
            throw new OrderException("创建订单失败");
        }


        //5、删除购物车中对应商品的记录
        //封装需要删除的购物车数据
        Map<String,Object> msg = new HashMap<>();
        msg.put("userId", userId);
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        msg.put("skuIds", JSON.toJSONString(skuIds));

        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "cart.delete", msg);


    }
}
