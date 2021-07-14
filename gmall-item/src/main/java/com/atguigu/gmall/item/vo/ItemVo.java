package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {

    //面包屑所需的参数
    //一二三级分类  v
    private List<CategoryEntity> categories;
    //品牌信息  v
    private Long brandId;
    private String brandName;
    //spu信息  v
    private Long spuid;
    private String spuName;

    //中间详细信息  v
    private Long skuId;
    private String subTitle;
    private String title;
    private BigDecimal price;
    private Integer weight;
    private String defaultImage;

    //营销信息  v
    private List<ItemSaleVo> sales;

    //是否有货 v
    private Boolean store = false;

    //sku的图片列表  v
    private List<SkuImagesEntity> images;

    // sku所属spu下的所有sku的销售属性
    // [{attrId: 3, attrName: '颜色', attrValues: '白色','黑色','粉色'},
    // {attrId: 8, attrName: '内存', attrValues: '6G','8G','12G'},
    // {attrId: 9, attrName: '存储', attrValues: '128G','256G','512G'}]
    private List<SaleAttrValueVo> saleAttrs;

    //当前sku的销售属性:{3:'白天白', 4:'12G', 5:'256'}
    private Map<Long, String> saleAttr;

    //为了页面跳转，需要销售属性组合与skuId的映射关系
    // {'白天白,8G,128G':100，'白天白,12G,256G':101}
    private String skuJsons;

    //商品描述
    private List<String> spuImages;

    //规格参数分组列表
    private List<GroupVo> groups;


}
