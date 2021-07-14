package com.atguigu.gmall.search.listener;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValueVo;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ItemListener {

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GoodsRepository goodsRepository;


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("SEARCH_ITEM_QUEUE"),
            exchange = @Exchange(value = "PMS_ITEM_EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"item.insert"}
    ))
    public void listen(Long spuId, Channel channel, Message message) throws IOException {
        try {
            // 判断是否是垃圾消息
            if (spuId == null){
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            //根据spuId查询spu信息
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(spuId);
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity == null){ //如果根据SpuId查询spu，结果为空，说明spuId是无效信息
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            //根据spuId查询sku信息 ，返回sku信息集合
            ResponseVo<List<SkuEntity>> skuResponseVo = this.pmsClient.querySkusBySpuId(spuId);
            List<SkuEntity> skuEntities = skuResponseVo.getData();
            if (!CollectionUtils.isEmpty(skuEntities)) {

                //查询品牌和分类，同一个sku品牌、分类相同，在spu遍历中查询品牌和分类
                ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(spuEntity.getBrandId());
                BrandEntity brandEntity = brandEntityResponseVo.getData();
                ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(spuEntity.getCategoryId());
                CategoryEntity categoryEntity = categoryEntityResponseVo.getData();

                //把spu下所有的sku转化为goods，导入es
                List<Goods> goodsList = skuEntities.stream().map(skuEntity -> {
                    Goods goods = new Goods();

                    //sku 的相关信息
                    goods.setSkuId(skuEntity.getId());
                    goods.setDefaultImage(skuEntity.getDefaultImage());
                    goods.setTitle(skuEntity.getTitle());
                    goods.setSubTitle(skuEntity.getSubtitle());
                    goods.setPrice(skuEntity.getPrice().doubleValue());

                    //创建时间，spu属性
                    goods.setCreateTime(spuEntity.getCreateTime());
                    //库存和销量
                    //根据skuId查询库存销量，返回库存信息
                    ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkusBySkuId(skuEntity.getId());
                    List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
                    if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                        //查询销量信息                            mapToLong表示转为Long集合
                        goods.setSales(wareSkuEntities.stream().mapToLong(WareSkuEntity::getSales).reduce((a, b) -> a + b).getAsLong());
                        //是否有货                              anyMatch:任何一个sku有货  都返回true
                        goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                        //goods.setStore(wareSkuEntities.stream().allMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked()>0));
                    }

                    //查询品牌和分类，同一个sku品牌、分类相同，在spu遍历中查询品牌和分类  在sku循环、spu内外定义，可以减少查询次数
                    //品牌
                    if (brandEntity != null) {
                        goods.setBrandId(brandEntity.getId());
                        goods.setBrandName(brandEntity.getName());
                        goods.setLogo(brandEntity.getLogo());
                    }

                    //分类
                    if (categoryEntity != null) {
                        goods.setCategoryId(categoryEntity.getId());
                        goods.setCategoryName(categoryEntity.getName());
                    }

                    //检索类型规格参数  需要把sku类型检索参数和spu类型检索参数都放进该集合中
                    List<SearchAttrValueVo> attrValueVos = new ArrayList<>();
                    //sku类型检索类型参数
                    ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySearchAttrValuesBySkuId(skuEntity.getCategoryId(), skuEntity.getId());
                    List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
                    if (!CollectionUtils.isEmpty(skuAttrValueEntities)) {
                        //查询sku检索类型参数，放入attrValueVos中
                        attrValueVos.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                    SearchAttrValueVo searchAttrValueVo = new SearchAttrValueVo();
                                    BeanUtils.copyProperties(skuAttrValueEntity, searchAttrValueVo);
                                    return searchAttrValueVo;
                                }).collect(Collectors.toList())
                        );
                    }
                    //spu中检索类型参数
                    ResponseVo<List<SpuAttrValueEntity>> baseAttrResponseVo = this.pmsClient.querySearchAttrValuesBySpuId(skuEntity.getCategoryId(), spuEntity.getId());
                    List<SpuAttrValueEntity> spuAttrValueEntities = baseAttrResponseVo.getData();
                    if (!CollectionUtils.isEmpty(spuAttrValueEntities)) {
                        //查询spu检索类型参数，放入attrValueVos中
                        attrValueVos.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                                    SearchAttrValueVo searchAttrValueVo = new SearchAttrValueVo();
                                    BeanUtils.copyProperties(spuAttrValueEntity, searchAttrValueVo);
                                    return searchAttrValueVo;
                                }).collect(Collectors.toList())
                        );
                    }
                    //把规格参数聚合放入good对象中
                    goods.setSearchAttrs(attrValueVos);

                    return goods;
                }).collect(Collectors.toList());
                //存入es中
                this.goodsRepository.saveAll(goodsList);
            }
            // 参数二：是否批量确认
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            e.printStackTrace();
            if (message.getMessageProperties().getRedelivered()){
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            }else {
                // 不确认：参数：1-标记，2-是否批量操作，3-是否重新入队(true重新入队，false不重新入队，如果有死信队列会进入死信队列)
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }
}
