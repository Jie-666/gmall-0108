package com.atguigu.gmall.wms.mapper;

import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品库存
 *
 * @author kjj
 * @email kjj@atguigu.com
 * @date 2021-06-23 18:54:23
 */
@Mapper
public interface WareSkuMapper extends BaseMapper<WareSkuEntity> {
    //验库存：参数  什么商品+卖多少件
    public List<WareSkuEntity> checkStock(@Param("skuId") Long skuId, @Param("count") Integer count);

    //锁库存
    public int lock(@Param("id") Long id, @Param("count") Integer count);

    //释放扣库存
    public int unlock(@Param("id") Long id, @Param("count") Integer count);
}
