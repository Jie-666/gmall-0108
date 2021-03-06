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

        //??????userId
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getUserId();

        //????????????????????????
        ResponseVo<List<UserAddressEntity>> addressResponseVo = this.umsClient.queryUserAddressByUserId(userId);
        List<UserAddressEntity> addressEntities = addressResponseVo.getData();
        confirmVo.setAddresses(addressEntities);
        //??????????????????
        ResponseVo<List<Cart>> cartResponseVo = this.cartClient.queryCheckedCartByUserId(userId);
        List<Cart> carts = cartResponseVo.getData();
        if (CollectionUtils.isEmpty(carts)) {
            throw new OrderException("????????????????????????????????????????????????");
        }
        //???????????????????????????OrderItemVo??????
        List<OrderItemVo> items = carts.stream().map(cart -> { //???????????????skuId???count
            OrderItemVo itemVo = new OrderItemVo();
            itemVo.setCount(cart.getCount());
            //??????skuId??????sku
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(cart.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity != null) {
                itemVo.setSkuId(skuEntity.getId());
                itemVo.setTitle(skuEntity.getTitle());
                itemVo.setPrice(skuEntity.getPrice());
                itemVo.setDefaultImage(skuEntity.getDefaultImage());
                itemVo.setWeight(skuEntity.getWeight());
            }

            //??????????????????
            ResponseVo<List<ItemSaleVo>> salesResponseVo = smsClient.queryItemSalesBySkuId(cart.getSkuId());
            List<ItemSaleVo> itemSaleVos = salesResponseVo.getData();
            itemVo.setSales(itemSaleVos);

            //??????????????????
            ResponseVo<List<SkuAttrValueEntity>> SaleAttrResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(cart.getSkuId());
            List<SkuAttrValueEntity> skuAttrValueEntities = SaleAttrResponseVo.getData();
            itemVo.setSaleAttrs(skuAttrValueEntities);

            //??????????????????
            ResponseVo<List<WareSkuEntity>> wareResponseVo = wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));

            }
            return itemVo;
        }).collect(Collectors.toList());
        confirmVo.setItems(items);

        //????????????id??????????????????
        ResponseVo<UserEntity> userEntityResponseVo = this.umsClient.queryUserById(userId);
        UserEntity userEntity = userEntityResponseVo.getData();
        if (userEntity != null) {
            confirmVo.setBounds(userEntity.getIntegration());
        }

        //??????  ????????????????????????
        String orderToken = IdWorker.getIdStr();
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, orderToken, 3, TimeUnit.HOURS);

        confirmVo.setOrderToken(orderToken);

        return confirmVo;

    }

    public void submit(OrderSubmitVo submitVo) {
        //1??????????????????????????????????????????orderToken???redis???????????????????????????????????????,????????????
        String orderToken = submitVo.getOrderToken(); //?????????orderToken
        if (StringUtils.isBlank(orderToken)) {
            throw new OrderException("???????????????");
        }
        String script = "if(redis.call('get',KEYS[1])==ARGV[1])" +
                " then" +
                "   return redis.call('del',KEYS[1]) " +
                "else " +
                "   return 0 end";
        Boolean flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Boolean.class), Arrays.asList(KEY_PREFIX + orderToken), orderToken);
        if (!flag) {
            throw new OrderException("????????????????????????");
        }

        //2????????????
        List<OrderItemVo> items = submitVo.getItems();
        if (CollectionUtils.isEmpty(items)) {
            throw new OrderException("????????????????????????");
        }
        BigDecimal totalPrice = submitVo.getTotalPrice(); //???????????????
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            //??????????????????
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(item.getSkuId());
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {//??????skuEntity????????????????????????0
                return new BigDecimal(0);
            }
            //??????????????????
            return skuEntity.getPrice().multiply(item.getCount());
        }).reduce((a, b) -> a.add(b)).get();
        if (totalPrice.compareTo(currentTotalPrice) != 0) {
            throw new OrderException("???????????????????????????????????????");
        }

        //3????????????????????????
        ResponseVo<List<SkuLockVo>> wareResponseVo = this.wmsClient.checkAndLock(items.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setCount(item.getCount().intValue());
            return skuLockVo;
        }).collect(Collectors.toList()), orderToken);
        List<SkuLockVo> skuLockVos = wareResponseVo.getData();
        if (!CollectionUtils.isEmpty(skuLockVos)){ //??????????????????????????????????????????

            throw new OrderException(JSON.toJSONString(skuLockVos));
        }
        //4???????????????
        Long userId = LoginInterceptor.getUserInfo().getUserId();
        String username = LoginInterceptor.getUserInfo().getUsername();

        try {
            this.omsClient.saveOrder(submitVo, userId, username);
            //?????????????????????????????????
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","order.ttl",orderToken);
        } catch (Exception e) {
            //??????????????????????????????????????????????????????????????????????????????
            this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","order.disable",orderToken);
            throw new OrderException("??????????????????");
        }


        //5??????????????????????????????????????????
        //????????????????????????????????????
        Map<String,Object> msg = new HashMap<>();
        msg.put("userId", userId);
        List<Long> skuIds = items.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        msg.put("skuIds", JSON.toJSONString(skuIds));

        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "cart.delete", msg);


    }
}
