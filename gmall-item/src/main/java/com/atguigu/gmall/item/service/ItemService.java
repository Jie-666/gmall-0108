package com.atguigu.gmall.item.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.common.exception.ItemException;
import com.atguigu.gmall.item.config.ThreadPoolConfig;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemService {
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private TemplateEngine templateEngine;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public ItemVo loadData(Long skuId) {
        ItemVo itemVo = new ItemVo();
        //1. 根据skuId查询sku（已有）
        CompletableFuture<SkuEntity> skuFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                throw new ItemException("该skuId对应的商品不存在");
            }
            itemVo.setSkuId(skuId);
            itemVo.setTitle(skuEntity.getTitle());
            itemVo.setSubTitle(skuEntity.getSubtitle());
            itemVo.setPrice(skuEntity.getPrice());
            itemVo.setWeight(skuEntity.getWeight());
            itemVo.setDefaultImage(skuEntity.getDefaultImage());
            return skuEntity;
        }, threadPoolExecutor);

        //2. 根据sku中的三级分类id查询一二三级分类
        CompletableFuture<Void> catesFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<CategoryEntity>> catesResponseVo = this.pmsClient.queryLvl123CategoriseByCid3(skuEntity.getCategoryId());
            List<CategoryEntity> categoryEntities = catesResponseVo.getData();
            itemVo.setCategories(categoryEntities);
        }, threadPoolExecutor);
        //3. 根据sku中的品牌id查询品牌（已有）
        CompletableFuture<Void> brandFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            if (brandEntity != null) {
                itemVo.setBrandId(brandEntity.getId());
                itemVo.setBrandName(brandEntity.getName());
            }
        }, threadPoolExecutor);

        //4. 根据sku中的spuId查询spu信息（已有）
        CompletableFuture<Void> spuEntityFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                itemVo.setSpuid(spuEntity.getId());
                itemVo.setSpuName(spuEntity.getName());
            }
        }, threadPoolExecutor);
        //5. 根据skuId查询sku所有图片
        CompletableFuture<Void> imagesFuture = skuFuture.thenRunAsync(() -> {
            ResponseVo<List<SkuImagesEntity>> imagesResponseVo = this.pmsClient.queryImagesBySkuId(skuId);
            List<SkuImagesEntity> imagesEntities = imagesResponseVo.getData();
            itemVo.setImages(imagesEntities);
        }, threadPoolExecutor);

        //6. 根据skuId查询sku的所有营销信息
        CompletableFuture<Void> salesFuture = skuFuture.thenRunAsync(() -> {
            ResponseVo<List<ItemSaleVo>> salesResponseVo = this.smsClient.queryItemSalesBySkuId(skuId);
            List<ItemSaleVo> salesResponseVoData = salesResponseVo.getData();
            itemVo.setSales(salesResponseVoData);
        }, threadPoolExecutor);

        //7. 根据skuId查询sku的库存信息（已有）
        CompletableFuture<Void> wareFuture = skuFuture.thenRunAsync(() -> {
            ResponseVo<List<WareSkuEntity>> WareResponseVo = this.wmsClient.queryWareSkusBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = WareResponseVo.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
        }, threadPoolExecutor);
        //8. 根据sku中的spuId查询spu下的所有销售属性
        CompletableFuture<Void> saleAttrsFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<SaleAttrValueVo>> saleAttrsResponseVo = this.pmsClient.querySaleAttrValuesBySpuId(skuEntity.getSpuId());
            List<SaleAttrValueVo> saleAttrResponseVos = saleAttrsResponseVo.getData();
            itemVo.setSaleAttrs(saleAttrResponseVos);
        }, threadPoolExecutor);
        //9. 根据skuId查询当前sku的销售属性
        CompletableFuture<Void> saleAttrFuture = skuFuture.thenRunAsync(() -> {
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySkuAttrValuesBySkuId(skuId);
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                itemVo.setSaleAttr(skuAttrValueEntities.stream().collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue)));
            }
        }, threadPoolExecutor);
        //10. 根据sku中的spuId查询spu下所有sku：销售属性组合与skuId映射关系
        CompletableFuture<Void> mappingFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<String> mappingResponseVo = this.pmsClient.queryMappingBySpuId(skuEntity.getSpuId());
            String json = mappingResponseVo.getData();
            itemVo.setSkuJsons(json);
        }, threadPoolExecutor);
        //11. 根据sku中spuId查询spu的描述信息（已有）
        CompletableFuture<Void> descFuture = skuFuture.thenRunAsync(() -> {
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuId);
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if (spuDescEntity != null) {
                itemVo.setSpuImages(Arrays.asList(StringUtils.split(spuDescEntity.getDecript(), ",")));
            }
        }, threadPoolExecutor);
        //12. 根据分类id、spuId及skuId查询分组及组下的规格参数值
        CompletableFuture<Void> groupFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<GroupVo>> groupResponseVo = this.pmsClient.queryGroupsWithAttrValuesByCidAndSpuIdAndSkuId(skuEntity.getCategoryId(), skuEntity.getSpuId(), skuId);
            List<GroupVo> groupVos = groupResponseVo.getData();
            itemVo.setGroups(groupVos);
        }, threadPoolExecutor);

        CompletableFuture.allOf(groupFuture, descFuture, mappingFuture, saleAttrFuture, saleAttrsFuture, wareFuture, salesFuture, imagesFuture, spuEntityFuture, brandFuture, catesFuture).join();

        return itemVo;
    }

    public void asyncExecute(ItemVo itemVo) {
        threadPoolExecutor.execute(() -> {
            this.generateHtml(itemVo);
        });
    }

    public void generateHtml(ItemVo itemVo) {
        //模板引擎的上下文对象，通过该对象，给模板动态传递数据
        Context context = new Context();
        context.setVariable("itemVo", itemVo);

        //文件流  jdk1.8语法  自动释放。
        try (PrintWriter printWriter = new PrintWriter("E:\\1130尚硅谷\\01_谷粒商城0108\\html\\" + itemVo.getSkuId() + ".html")) {
            //模板引擎生成静态页面，参数1、模板名称，2、上下文对象，3、文件流
            templateEngine.process("item", context, printWriter);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {

        CompletableFuture<String> futuer = CompletableFuture.supplyAsync(() -> {
            System.out.println("通过CompletableFuture的supplyAsync方法初始化了一个多线程");
            //int i = 1/0;
            return "hello CompletableFuture";
        });
        futuer.thenApplyAsync((t) -> {

            System.out.println("================thenApply========================");
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("上一个方法的返回值信息t：" + t);
            return "hello,thenApply";
        });
        futuer.thenAcceptAsync((t) -> {
            System.out.println("================thenAccept========================");
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("上一个方法的返回值信息t：" + t);
        });

//        futuer.whenCompleteAsync((t, u) -> {
//
//            System.out.println("================whenComplete========================");
//
//            System.out.println("t: " + t);
//            System.out.println("u: " + u);
//        });
//        futuer.exceptionally(t ->{
//            System.out.println("=================exceptionally=================");
//            System.out.println("t:"+t);
//            return null;
//        });
        CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
            System.out.println("================runAsync========================");
            System.out.println("这里是runAsync");
        }).thenRunAsync(() -> {
            System.out.println("================thenAccept========================");
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("这里是thenAccept");
        });

        CompletableFuture.allOf(futuer, future2).join();

        System.out.println("这是mian方法" + Thread.currentThread().getName());
        System.in.read();
    }
}








