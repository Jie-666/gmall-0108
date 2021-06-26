package com.atguigu.gmall.pms.service.impl;

import com.alibaba.csp.sentinel.util.StringUtil;

import com.atguigu.gmall.pms.Vo.SkuVo;
import com.atguigu.gmall.pms.Vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.Vo.SpuVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.SkuAttrValueService;
import com.atguigu.gmall.pms.service.SkuImagesService;
import com.atguigu.gmall.pms.service.SpuAttrValueService;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import com.atguigu.gmall.pms.service.SpuService;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {
    @Autowired
    private SpuDescMapper spuDescMapper;
    @Autowired
    private SpuAttrValueService spuAttrValueService;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private SkuImagesService skuImagesService;
    @Autowired
    private SkuAttrValueService skuAttrValueService;
    @Autowired
    private GmallSmsClient gmallSmsClient;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCidAndPage(PageParamVo paramVo, Long categoryId) {
        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();

        //如果categoryId不为0，需要查询本类
        if (categoryId != 0) {
            wrapper.eq("category_id", categoryId);
        }
        String key = paramVo.getKey();
        if (StringUtil.isNotBlank(key)) {
            wrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                wrapper
        );

        return new PageResultVo(page);
    }

    @Override
    public void bigSave(SpuVo spu) {
        //1.保存spu相关的3张表
        //1.1.保存pms_spu
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime()); // 新增时，更新时间和创建时间一致
        this.save(spu);
        Long spuId = spu.getId();// 获取新增后的spuId

        //1.2.保存pms_spu_desc
        List<String> spuImages = spu.getSpuImages();
        if (!CollectionUtils.isEmpty(spuImages)) {
            SpuDescEntity spuDescEntity = new SpuDescEntity();
            spuDescEntity.setSpuId(spuId);
            spuDescEntity.setDecript(StringUtils.join(spuImages, ","));
            spuDescMapper.insert(spuDescEntity);
        }

        //1.3.保存pms_spu_attr_value
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();

        if (!CollectionUtils.isEmpty(baseAttrs)) {
            //将SpuAttrValueVo 转换为 SpuAttrValueEntity 集合
            List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrs.stream()
                    .filter(spuAttrValueVo -> spuAttrValueVo.getAttrValue() != null)
                    .map(spuAttrValueVo -> {
                        SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                        BeanUtils.copyProperties(spuAttrValueVo, spuAttrValueEntity);
                        spuAttrValueEntity.setSpuId(spuId);
                        return spuAttrValueEntity;
                    }).collect(Collectors.toList());
            this.spuAttrValueService.saveBatch(spuAttrValueEntities);

        }

        //2.保存sku相关的3张表
        List<SkuVo> skus = spu.getSkus();
        if (CollectionUtils.isEmpty(skus)) {
            return;
        }
        //遍历skus，保存到pms_sku
        skus.forEach(skuVo -> {
            //2.1.保存pms_sku
            skuVo.setSpuId(spuId);
            skuVo.setCategoryId(spu.getCategoryId());
            skuVo.setBrandId(spu.getBrandId());
            //获取页面的图片列表
            List<String> images = skuVo.getImages();
            if (!CollectionUtils.isEmpty(images)) {
                skuVo.setDefaultImage(StringUtils.isBlank(skuVo.getDefaultImage()) ? images.get(0) : skuVo.getDefaultImage());
            }
            this.skuMapper.insert(skuVo);
            Long skuId = skuVo.getId();

            //2.2.保存pms_sku_images
            if (!CollectionUtils.isEmpty(images)) {

                this.skuImagesService.saveBatch(images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setUrl(image);
                    //如果当前图片和sku的默认图片地址相同，则设置为1，否则设置为0
                    skuImagesEntity.setDefaultStatus(StringUtils.equals(skuVo.getDefaultImage(), image) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList()));
            }

            //2.3.保存pms_sku_attr_value
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                saleAttrs.forEach(skuAttrValueEntity -> {
                    skuAttrValueEntity.setSkuId(skuId);
                });
                this.skuAttrValueService.saveBatch(saleAttrs);
            }

            //3.保存营销相关的三张表
            //3.1.保存sms_sku_bounds 积分信息表
            //3.2.保存sms_sku_full_reduction  满减信息表
            //3.3.保存sms_sku_ladder  打折信息表
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo, skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.gmallSmsClient.saleSales(skuSaleVo);


        });


    }

//    public static void main(String[] args) {
//        List<Book> books = Arrays.asList(
//                new Book("平凡世界", 29.0, true),
//                new Book("骆驼祥子", 39.0, false),
//                new Book("祥林嫂", 49.0, true),
//                new Book("鲁冰逊漂流记", 59.0, false),
//                new Book("格列夫哦呦及", 69.0, true),
//                new Book("海底两万里", 79.0, false)
//        );
    //map：把一个集合转换为另一个集合
//        books.stream().map(book -> book.getName()).collect(Collectors.toList()).forEach(System.out::println);
//        books.stream().map(book -> {
//            Person person = new Person();
//            person.setName(book.getName());
//            person.setPrice(book.getPrice());
//            return person;
//        }).collect(Collectors.toList()).forEach(System.out::println);
    //filter：过滤出需要的元素，连组成新的集合
    //books.stream().filter(book -> book.getPrice()>40).collect(Collectors.toList()).forEach(System.out::println);
    //books.stream().filter(Book::isInventory).collect(Collectors.toList()).forEach(System.out::println);

    //reduce：求和
//        List<Integer> arrs = Arrays.asList(2,3,4,5);
//        System.out.println(arrs.stream().reduce((a, b) -> a + b).get());
//
//        System.out.println(books.stream().map(Book::getPrice).reduce((a, b) -> a + b).get());

}

//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//class Book {
//    private String name;
//    private Double price;
//    private boolean isInventory;
//}
//@Data
//@ToString
//class Person{
//    private String name;
//    private Double price;
//}