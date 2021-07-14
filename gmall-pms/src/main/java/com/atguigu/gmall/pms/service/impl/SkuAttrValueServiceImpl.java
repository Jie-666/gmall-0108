package com.atguigu.gmall.pms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.SkuEntity;
import com.atguigu.gmall.pms.entity.SpuEntity;
import com.atguigu.gmall.pms.mapper.AttrMapper;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuMapper;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.sun.javafx.collections.MappingChange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SkuAttrValueMapper;
import com.atguigu.gmall.pms.entity.SkuAttrValueEntity;
import com.atguigu.gmall.pms.service.SkuAttrValueService;
import org.springframework.util.CollectionUtils;


@Service("skuAttrValueService")
public class SkuAttrValueServiceImpl extends ServiceImpl<SkuAttrValueMapper, SkuAttrValueEntity> implements SkuAttrValueService {
    @Autowired
    private AttrMapper attrMapper;
    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private SkuAttrValueMapper attrValueMapper;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SkuAttrValueEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SkuAttrValueEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public List<SkuAttrValueEntity> querySearchAttrValuesBySkuId(Long cid, Long skuId) {
        //1.查新检索类型的规格参数
        List<AttrEntity> attrEntities = this.attrMapper.selectList(new QueryWrapper<AttrEntity>().eq("category_id", cid).eq("search_type", 1));
        if (CollectionUtils.isEmpty(attrEntities)) {
            return null;
        }
        //2.查询检索类型的规格参数的值
        List<Long> attrIds = attrEntities.stream().map(AttrEntity::getId).collect(Collectors.toList());
        return this.list(new QueryWrapper<SkuAttrValueEntity>().eq("sku_id", skuId).in("attr_id", attrIds));


    }

    @Override
    public List<SaleAttrValueVo> querySaleAttrValuesBySpuId(Long spuId) {
        //查询spu下的所有的sku  ===> sku集合
        List<SkuEntity> skuEntities = this.skuMapper.selectList(new QueryWrapper<SkuEntity>().eq("spu_id", spuId));
        if (CollectionUtils.isEmpty(skuEntities)) {
            return null;
        }
        List<Long> skuIds = skuEntities.stream().map(SkuEntity::getId).collect(Collectors.toList());
        //查询sku下所有的销售属性
        List<SkuAttrValueEntity> skuAttrValueEntities = this.list(new QueryWrapper<SkuAttrValueEntity>().in("sku_id", skuIds).orderByAsc("attr_id"));
        if (CollectionUtils.isEmpty(skuAttrValueEntities)) {
            return null;
        }
        //把销售属性处理成
        // [{attrId: 3, attrName: '颜色', attrValues: '白色','黑色','粉色'},
        // {attrId: 8, attrName: '内存', attrValues: '6G','8G','12G'},
        // {attrId: 9, attrName: '存储', attrValues: '128G','256G','512G'}]
        List<SaleAttrValueVo> saleAttrValueVos = new ArrayList<>();
        // 分组以attrId为key，以attrId对应的四条数据为value
        Map<Long, List<SkuAttrValueEntity>> map = skuAttrValueEntities.stream().collect(Collectors.groupingBy(SkuAttrValueEntity::getAttrId));
        map.forEach((attrId,skuAttrValueEntityList)->{
            //需要把每一个kv结构转化为一个SaleAttrValueVo模型
            SaleAttrValueVo saleAttrValueVo = new SaleAttrValueVo();
            saleAttrValueVo.setAttrId(attrId);
            //既然存在这样的kv结构，必然最少存在一个数据，这里取第一条数据的Name
            saleAttrValueVo.setAttrName(skuAttrValueEntityList.get(0).getAttrName());
            // 回去每个分组中的attrValue的set集合
            saleAttrValueVo.setAttrValues(skuAttrValueEntityList.stream().map(SkuAttrValueEntity::getAttrValue).collect(Collectors.toSet()));

            saleAttrValueVos.add(saleAttrValueVo);
        });

        return saleAttrValueVos;
    }

    @Override
    public String queryMappingBySpuId(Long spuId) {
        //现根据spuId查询sku的列表==>skuId集合
        List<SkuEntity> skuEntities = this.skuMapper.selectList(new QueryWrapper<SkuEntity>().eq("spu_id", spuId));
        if (CollectionUtils.isEmpty(skuEntities)){
            return null;
        }
        List<Long> skuIds = skuEntities.stream().map(SkuEntity::getId).collect(Collectors.toList());

        //查询映射关系
        List<Map<String,Object>> maps = this.attrValueMapper.queryMappingBySpuId(skuIds);
        if (CollectionUtils.isEmpty(maps)){
            return null;
        }
        Map<String,Long> mappingMap = maps.stream().collect(Collectors.toMap(map -> map.get("attr_values").toString(),  map ->(Long) map.get("sku_id")));
        //序列化json字符串，返回
        return  JSON.toJSONString(mappingMap);


    }

}