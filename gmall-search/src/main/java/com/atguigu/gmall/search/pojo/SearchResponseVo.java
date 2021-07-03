package com.atguigu.gmall.search.pojo;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {
    //品牌参数过滤
    private List<BrandEntity> brands;
    //分类参数过滤
    private List<CategoryEntity> categories;
    //规格参数过滤
    private List<SearchResponseAttrValueVo> filters;
    //分页
    private Long total; //总记录数
    private Integer pageNum;
    private Integer pageSize;

    private List<Goods> goodsList;
}
