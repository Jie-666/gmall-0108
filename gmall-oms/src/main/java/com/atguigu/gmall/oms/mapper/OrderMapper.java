package com.atguigu.gmall.oms.mapper;

import com.atguigu.gmall.oms.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.security.PrivateKey;

/**
 * 订单
 * 
 * @author kjj
 * @email kjj@atguigu.com
 * @date 2021-07-20 00:20:56
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {
	public int updateStatus(@Param("orderToken") String orderToken,@Param("expect") Integer expect,@Param("target") Integer target);
}
