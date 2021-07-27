package com.atguigu.gmall.wms.Vo;

import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId; //锁定的商品id
    private Integer count; //购买的数量
    private Boolean lock;   //锁定的数量
    private Long wareSkuId; //锁定成功时，记录锁定的仓库的id，方便减粗存，解锁库存
    private String orderToken;  //方便以订单为单位缓存单位的锁定信息
}
