package com.atguigu.gmall.oms.vo;

import com.atguigu.gmall.ums.entity.UserAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVo {
    //收获地址
    private UserAddressEntity address;
    //购物积分
    private Integer bounds;
    //物流公司
    private String deliveryCompany;
    //送货清单
    private List<OrderItemVo> items;
    //防重唯一标识
    private String orderToken;
    //支付方式
    private Integer payType;
    //总价格   验总价
    private BigDecimal totalPrice;

    //发票   买家留言
}
