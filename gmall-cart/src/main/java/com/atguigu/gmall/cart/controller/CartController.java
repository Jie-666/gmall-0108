package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.interceptor.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.bean.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.*;


import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("user/{userId}")
    @ResponseBody
    public ResponseVo<List<Cart>> queryCheckedCartByUserId(@PathVariable("userId")Long userId){
        List<Cart> carts = this.cartService.queryCheckedCartByUserId(userId);
        return ResponseVo.ok(carts);
    }



    @PostMapping("deleteCart")
    @ResponseBody
    public ResponseVo deleteCart(@RequestParam("skuId")Long skuId){
        this.cartService.deleteCart(skuId);
        return ResponseVo.ok();
    }


    /**
     * 更新商品选中状态
     * @param cart
     * @return
     */
    @PostMapping("updateStatus")
    @ResponseBody
    public ResponseVo updateStatus(@RequestBody Cart cart){
        this.cartService.updateStatus(cart);
        return ResponseVo.ok();

    }

    /**
     * 更新商品数量
     * @param cart
     * @return
     */
    @PostMapping("updateNum")
    @ResponseBody
    public ResponseVo updateNum(@RequestBody Cart cart){
        this.cartService.updateNum(cart);
        return ResponseVo.ok();

    }


    /**
     * 查询购物车
     */
    @GetMapping("cart.html")
    public String queryCarts(Model model){
        List<Cart> carts = this.cartService.queryCarts();
        model.addAttribute("carts",carts);
        return "cart";
    }



    /**
     * 添加购物车成功，重定向到购物车成功页
     *
     * @param cart
     * @return
     */
    @GetMapping
    public String addCart(Cart cart) {   //请求参数：?skuId=40&count=2
        this.cartService.addCart(cart);
        return "redirect:http://cart.gmall.com/addCart.html?skuId=" + cart.getSkuId() + "&count=" + cart.getCount();

    }

    /**
     * 跳转到添加成功页，回显数据
     * @param cart
     * @param model
     * @return
     */
    @GetMapping("addCart.html")
    public String queryCartBySkuId(Cart cart, Model model) {
        BigDecimal count = cart.getCount();
        cart = this.cartService.queryCartBySkuId(cart);
        cart.setCount(count);
        model.addAttribute("cart",cart);
        return "addCart";
    }

    @GetMapping("test")
    @ResponseBody
    public String test() throws ExecutionException, InterruptedException {
//        System.out.println(LoginInterceptor.getUserInfo());
        long now = System.currentTimeMillis();
        System.out.println("controller方法开始执行。。。。。。");
        this.cartService.executor1();
        this.cartService.executor2();


        //ListenableFuture<String> future1 = this.cartService.executor1();
        //ListenableFuture<String> future2 = this.cartService.executor2();

//        future1.addCallback(result->{
//            System.out.println("executor1异步执行成功："+result);
//        },ex->{
//            System.out.println("executor1异步执行失败"+ex);
//        });
//
//        future2.addCallback(result->{
//            System.out.println("executor2异步执行成功："+result);
//        },ex->{
//            System.out.println("executor2异步执行失败"+ex);
//        });


        //System.out.println(future1.get()+"======================"+future2.get());

        System.out.println("controller执行时间==========="+(System.currentTimeMillis()-now));
        return "test";
    }

}
