package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParamVo;
import com.atguigu.gmall.search.pojo.SearchResponseAttrValueVo;
import com.atguigu.gmall.search.pojo.SearchResponseVo;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.recycler.Recycler;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchService {
    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVo search(SearchParamVo paramVo) {

        try {
            SearchResponse response = this.restHighLevelClient.search(new SearchRequest(new String[]{"goods"}, builder(paramVo)), RequestOptions.DEFAULT);
//            System.out.println(response);

            //??????????????????????????????
            SearchResponseVo responseVo = this.parseResult(response);
            responseVo.setPageNum(paramVo.getPageNum());
            responseVo.setPageSize(paramVo.getPageSize());

            return responseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * ?????????????????????
     * @param response
     * @return
     */
    private SearchResponseVo parseResult(SearchResponse response) {
        SearchResponseVo responseVo = new SearchResponseVo();

        //??????hits?????????
        SearchHits hits = response.getHits();
        responseVo.setTotal(hits.getTotalHits());
        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Arrays.stream(hitsHits).map(hitsHit -> {
            String json = hitsHit.getSourceAsString(); //??????_source
            Goods goods = JSON.parseObject(json, Goods.class); //???????????????goods??????
            //?????????????????????  ?????????????????????
            Map<String, HighlightField> highlightFields = hitsHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            goods.setTitle(highlightField.fragments()[0].string());

            return goods;
        }).collect(Collectors.toList());
        responseVo.setGoodsList(goodsList);

        //?????????????????????
        Aggregations aggregations = response.getAggregations();

        //?????????????????????????????????id??????????????????????????????
        ParsedLongTerms brandIdAgg = (ParsedLongTerms) aggregations.get("brandIdAgg");
        List<? extends Terms.Bucket> brandBuckets = brandIdAgg.getBuckets(); //????????????id?????????????????????
        if (!CollectionUtils.isEmpty(brandBuckets)) {
            List<BrandEntity> brandEntities = brandBuckets.stream().map(bucket -> { //???????????????????????????
                BrandEntity brandEntity = new BrandEntity();
                brandEntity.setId(bucket.getKeyAsNumber().longValue()); //?????????key????????????id
                //????????????id?????????????????????
                Aggregations subBrandAggs = bucket.getAggregations();
                //?????????id???????????????????????????????????????
                ParsedStringTerms brandNameAgg = (ParsedStringTerms) subBrandAggs.get("brandNameAgg");
                List<? extends Terms.Bucket> brandNameAggBuckets = brandNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(brandNameAggBuckets)) {
                    //?????????????????????
                    brandEntity.setName(brandNameAggBuckets.get(0).getKeyAsString());
                }
                //?????????id?????????????????????logo????????????
                ParsedStringTerms logoAgg = (ParsedStringTerms) subBrandAggs.get("logoAgg");
                List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                if (!CollectionUtils.isEmpty(logoAggBuckets)) {
                    //???????????????logo
                    brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                }
                return brandEntity;
            }).collect(Collectors.toList());
            responseVo.setBrands(brandEntities);
        }

        //?????????????????????????????????id??????????????????????????????
        ParsedLongTerms categoryIdAgg = (ParsedLongTerms) aggregations.get("categoryIdAgg");
        List<? extends Terms.Bucket> categoryBuckets = categoryIdAgg.getBuckets(); //????????????id???????????????
        if (!CollectionUtils.isEmpty(categoryBuckets)) {
            List<CategoryEntity> categoryEntities = categoryBuckets.stream().map(bucket -> {
                CategoryEntity categoryEntity = new CategoryEntity();
                categoryEntity.setId(bucket.getKeyAsNumber().longValue()); //?????????key???????????????id
                //????????????id?????????????????????
                Aggregations subCategoryAggs = bucket.getAggregations();
                //?????????id????????????????????????????????????
                ParsedStringTerms categoryNameAgg = (ParsedStringTerms) subCategoryAggs.get("categoryNameAgg");
                List<? extends Terms.Bucket> categoryNameAggBuckets = categoryNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(categoryNameAggBuckets)) {
                    //??????????????????
                    categoryEntity.setName(categoryNameAggBuckets.get(0).getKeyAsString());
                }

                return categoryEntity;
            }).collect(Collectors.toList());
            responseVo.setCategories(categoryEntities);
        }

        //?????????????????????????????????????????????????????????
        ParsedNested attrAgg = (ParsedNested) aggregations.get("attrAgg"); //?????????????????????????????????
        //????????????????????????????????????id????????????
        ParsedLongTerms attrIdAgg = (ParsedLongTerms) attrAgg.getAggregations().get("attrIdAgg");
        //??????????????????id???????????????
        List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
        //???attrId???????????????SearchResponseAttrValueVo??????
        if (!CollectionUtils.isEmpty(attrIdAggBuckets)) {
            List<SearchResponseAttrValueVo> searchResponseAttrValueVos = attrIdAggBuckets.stream().map(bucket -> {
                SearchResponseAttrValueVo attrValueVo = new SearchResponseAttrValueVo();
                //???????????????attrValue???id
                attrValueVo.setAttrId(bucket.getKeyAsNumber().longValue());

                //??????attrId?????????????????????
                Aggregations aggs = bucket.getAggregations();

                ParsedStringTerms attrNameAgg = (ParsedStringTerms) aggs.get("attrNameAgg");
                List<? extends Terms.Bucket> attrNameAggBuckets = attrNameAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrNameAggBuckets)) {
                    attrValueVo.setAttrName(attrNameAggBuckets.get(0).getKeyAsString());
                }

                ParsedStringTerms attrValueAgg = (ParsedStringTerms) aggs.get("attrValueAgg");
                List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                if (!CollectionUtils.isEmpty(attrValueAggBuckets)) {
                    //????????????   ??????????????????private List<String> attrValues;
                    List<String> attrValues = attrValueAggBuckets.stream().map(MultiBucketsAggregation.Bucket::getKeyAsString).collect(Collectors.toList());
                    attrValueVo.setAttrValues(attrValues);
                }

                return attrValueVo;
            }).collect(Collectors.toList());
            responseVo.setFilters(searchResponseAttrValueVos);
        }


        return responseVo;
    }


    /**
     * ????????????DSL??????
     * @param paramVo
     * @return
     */
    private SearchSourceBuilder builder(SearchParamVo paramVo) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        String keyword = paramVo.getKeyword();
        if (StringUtils.isBlank(keyword)) {
            //TODO:?????????
            return sourceBuilder;
        }

        //1.?????????????????????
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);
        //1.1 ????????????
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.OR));
        //1.2 ??????
        //1.2.1 ????????????
        List<Long> brandId = paramVo.getBrandId();
        if (!CollectionUtils.isEmpty(brandId)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        }
        //1.2.2 ????????????
        List<Long> categoryId = paramVo.getCategoryId();
        if (!CollectionUtils.isEmpty(categoryId)) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryId));
        }
        //1.2.3 ??????????????????
        Double priceForm = paramVo.getPriceForm();
        Double priceTo = paramVo.getPriceTo();
        if (priceForm != null || priceTo != null) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price"); //??????????????????
            boolQueryBuilder.filter(rangeQuery);

            if (priceForm != null) {
                rangeQuery.gte(priceForm);
            }
            if (priceTo != null) {
                rangeQuery.lte(priceTo);
            }
        }
        //1.2.4 ?????????????????????
        Boolean store = paramVo.getStore();
        if (store != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }
        //1.2.5 ??????????????????
        List<String> props = paramVo.getProps();
        if (!CollectionUtils.isEmpty(props)) {
            props.forEach(prop -> {

                //????????????????????????????????????????????????????????????attrId,attrValue
                String[] attr = StringUtils.split(prop, ":");
                if (attr != null && attr.length == 2) { //?????????????????????????????????2????????????
                    //?????????????????????????????????????????????????????????????????????
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));

                    //bool??????????????????????????????must??????
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", attr[0]));
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", StringUtils.split(attr[1], "-")));
                }

            });
        }

        //2.????????????
        Integer sort = paramVo.getSort();

        switch (sort) {
            case 0:
                sourceBuilder.sort("_score", SortOrder.DESC);
                break;
            case 1:
                sourceBuilder.sort("price", SortOrder.DESC);
                break;
            case 2:
                sourceBuilder.sort("price", SortOrder.ASC);
                break;
            case 3:
                sourceBuilder.sort("sales", SortOrder.DESC);
                break;
            case 4:
                sourceBuilder.sort("createTime", SortOrder.DESC);
                break;
            default:
                throw new RuntimeException("???????????????????????????");
        }
        //3.??????
        Integer pageNum = paramVo.getPageNum();
        Integer pageSize = paramVo.getPageSize();
        sourceBuilder.from((pageNum - 1) * pageSize);
        sourceBuilder.size(pageSize);

        //4.??????
        sourceBuilder.highlighter(new HighlightBuilder()
                .field("title")
                .preTags("<font style='color:red;'>")
                .postTags("</font>")
        );

        //5.??????
        //5.1 ????????????
        sourceBuilder.aggregation(
                AggregationBuilders.terms("brandIdAgg").field("brandId")
                        .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName"))
                        .subAggregation(AggregationBuilders.terms("logoAgg").field("logo"))
        );
        //5.2 ????????????
        sourceBuilder.aggregation(
                AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                        .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName"))
        );
        //5.3 ??????????????????
        sourceBuilder.aggregation(
                AggregationBuilders.nested("attrAgg", "searchAttrs")
                        .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                                .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                                .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue"))
                        )

        );

        //6.???????????????
        sourceBuilder.fetchSource(new String[]{"skuId","title","subTitle","price","defaultImage"},null);


        System.out.println(sourceBuilder);
        return sourceBuilder;
    }
}
