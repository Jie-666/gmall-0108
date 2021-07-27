package com.atguigu.gmall.oms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.oms.entity.OrderReturnReasonEntity;

import java.util.Map;

/**
 * 退货原因
 *
 * @author kjj
 * @email kjj@atguigu.com
 * @date 2021-07-20 00:20:56
 */
public interface OrderReturnReasonService extends IService<OrderReturnReasonEntity> {

    PageResultVo queryPage(PageParamVo paramVo);
}

