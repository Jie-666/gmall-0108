package com.atguigu.gmall.pms.service.impl;

import com.alibaba.csp.sentinel.util.StringUtil;

import com.atguigu.gmall.pms.Vo.SkuVo;
import com.atguigu.gmall.pms.Vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.Vo.SpuVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.instrument.messaging.SleuthMessagingProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {
    @Autowired
    private SpuDescService descService;
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
    @Autowired
    private RabbitTemplate rabbitTemplate;

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

        //??????categoryId??????0?????????????????????
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

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spu) {
        //1.??????spu?????????3??????
        //1.1.??????pms_spu
        Long spuId = saveSpuInfo(spu);

        //1.2.??????pms_spu_desc
        //saveSpuDesc(spu, spuId);
        this.descService.saveSpuDesc(spu, spuId);


        // int i = 1/0;
        //1.3.??????pms_spu_attr_value
        saveBaseAttrs(spu, spuId);

        //2.??????sku?????????3??????
        saveSkuInfo(spu, spuId);

        this.rabbitTemplate.convertAndSend("PMS_ITEM_EXCHANGE", "item.insert", spuId);


    }

    private void saveSkuInfo(SpuVo spu, Long spuId) {
        List<SkuVo> skus = spu.getSkus();
        if (CollectionUtils.isEmpty(skus)) {
            return;
        }
        //??????skus????????????pms_sku
        skus.forEach(skuVo -> {
            //2.1.??????pms_sku
            skuVo.setSpuId(spuId);
            skuVo.setCategoryId(spu.getCategoryId());
            skuVo.setBrandId(spu.getBrandId());
            //???????????????????????????
            List<String> images = skuVo.getImages();
            if (!CollectionUtils.isEmpty(images)) {
                skuVo.setDefaultImage(StringUtils.isBlank(skuVo.getDefaultImage()) ? images.get(0) : skuVo.getDefaultImage());
            }
            this.skuMapper.insert(skuVo);
            Long skuId = skuVo.getId();

            //2.2.??????pms_sku_images
            if (!CollectionUtils.isEmpty(images)) {

                this.skuImagesService.saveBatch(images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setUrl(image);
                    //?????????????????????sku??????????????????????????????????????????1??????????????????0
                    skuImagesEntity.setDefaultStatus(StringUtils.equals(skuVo.getDefaultImage(), image) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList()));
            }

            //2.3.??????pms_sku_attr_value
            List<SkuAttrValueEntity> saleAttrs = skuVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)) {
                saleAttrs.forEach(skuAttrValueEntity -> {
                    skuAttrValueEntity.setSkuId(skuId);
                });
                this.skuAttrValueService.saveBatch(saleAttrs);
            }

            //3.??????????????????????????????
            //3.1.??????sms_sku_bounds ???????????????
            //3.2.??????sms_sku_full_reduction  ???????????????
            //3.3.??????sms_sku_ladder  ???????????????
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuVo, skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.gmallSmsClient.saleSales(skuSaleVo);


        });
    }

    private void saveBaseAttrs(SpuVo spu, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();

        if (!CollectionUtils.isEmpty(baseAttrs)) {
            //???SpuAttrValueVo ????????? SpuAttrValueEntity ??????
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
    }


    private Long saveSpuInfo(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime()); // ?????????????????????????????????????????????
        this.save(spu);
        Long spuId = spu.getId();// ??????????????????spuId
        return spuId;
    }

//    public static void main(String[] args) {
//        List<Book> books = Arrays.asList(
//                new Book("????????????", 29.0, true),
//                new Book("????????????", 39.0, false),
//                new Book("?????????", 49.0, true),
//                new Book("??????????????????", 59.0, false),
//                new Book("??????????????????", 69.0, true),
//                new Book("???????????????", 79.0, false)
//        );
    //map??????????????????????????????????????????
//        books.stream().map(book -> book.getName()).collect(Collectors.toList()).forEach(System.out::println);
//        books.stream().map(book -> {
//            Person person = new Person();
//            person.setName(book.getName());
//            person.setPrice(book.getPrice());
//            return person;
//        }).collect(Collectors.toList()).forEach(System.out::println);
    //filter???????????????????????????????????????????????????
    //books.stream().filter(book -> book.getPrice()>40).collect(Collectors.toList()).forEach(System.out::println);
    //books.stream().filter(Book::isInventory).collect(Collectors.toList()).forEach(System.out::println);

    //reduce?????????
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