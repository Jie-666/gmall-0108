package com.atguigu.gmall.pms.mapper;

import com.atguigu.gmall.pms.entity.CommentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品评价
 * 
 * @author kjj
 * @email kjj@atguigu.com
 * @date 2021-06-22 18:47:27
 */
@Mapper
public interface CommentMapper extends BaseMapper<CommentEntity> {
	
}
