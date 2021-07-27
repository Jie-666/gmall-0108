package com.atguigu.gmall.oms.mapper;

import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单项信息
 * 
 * @author kjj
 * @email kjj@atguigu.com
 * @date 2021-07-20 00:20:56
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {
	
}
