package com.atguigu.gmall.pms.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 属性分组
 * 
 * @author kjj
 * @email kjj@atguigu.com
 * @date 2021-06-22 18:47:27
 */
@Data
@TableName("pms_attr_group")
public class AttrGroupEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 分组id
	 */
	@TableId
	private Long id;
	/**
	 * 组名
	 */
	private String name;
	/**
	 * 排序
	 */
	private Integer sort;
	/**
	 * 组图标
	 */
	private String icon;
	/**
	 * 所属分类id
	 */
	private Long categoryId;
	/**
	 * 备注
	 */
	private String remark;
	/**
	 * 组下的分类规格
	 */
	@TableField(exist = false)
	private List<AttrEntity> attrEntities;
}
