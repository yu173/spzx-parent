package com.spzx.cart.api.factory;

import com.spzx.cart.api.RemoteCartService;
import com.spzx.common.core.constant.ServiceNameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;


//降级处理类：用OpenFeign组件提供的FallbackFactory工厂类实现的。
//另外，也可以使用Sentinel实现降级处理、熔断
@Component
public class RemoteCartFallbackFactory implements FallbackFactory<RemoteCartService> {

    private Logger log = LoggerFactory.getLogger(RemoteCartFallbackFactory.class);

    @Override
    public RemoteCartService create(Throwable throwable) {

        log.error("远程调用服务【{}】出现降级", ServiceNameConstants.CART_SERVICE);

        return new RemoteCartService() {

        };
    }
}
