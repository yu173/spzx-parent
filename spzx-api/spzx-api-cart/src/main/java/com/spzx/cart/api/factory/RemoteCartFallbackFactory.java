package com.spzx.cart.api.factory;

import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.constant.ServiceNameConstants;
import com.spzx.common.core.domain.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;


//降级处理类：用OpenFeign组件提供的FallbackFactory工厂类实现的。
//另外，也可以使用Sentinel实现降级处理、熔断
@Component
public class RemoteCartFallbackFactory implements FallbackFactory<RemoteCartService> {

    private Logger log = LoggerFactory.getLogger(RemoteCartFallbackFactory.class);

    @Override
    public RemoteCartService create(Throwable throwable) {

        log.error("远程调用服务【{}】出现降级", ServiceNameConstants.CART_SERVICE);

        return new RemoteCartService() {

            @Override
            public R<List<CartInfo>> getCartCheckedList(Long userId, String source) {
                return R.fail("远程调用获取购物车选中商品列表失败-"+throwable.getMessage());
            }

            @Override
            public R<Boolean> updateCartPrice(Long userId, String source) {
                return R.fail("远程调用更新购物车价格失败-"+throwable.getMessage());
            }

            @Override
            public R<Boolean> deleteCartCheckedList(Long userId, String source) {
                return R.fail("远程调用删除购物车选中商品失败 - "+throwable.getMessage());
            }
        };
    }
}
