package com.hbue.ordering.product.rating;
import com.hbue.ordering.comment.api.ProductRatingChangedEvent;
import java.util.function.Consumer;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.ErrorMessage;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="comment.messaging.enabled",havingValue="true")
public class ProductRatingMessaging {
    @Bean
    public Consumer<ProductRatingChangedEvent> productRatingChanged(ProductRatingProjection ratings) {
        return ratings::accept;
    }
    @Bean
    public Consumer<ErrorMessage> productRatingRetryError() {
        return error->{throw new IllegalStateException("评分投影消费失败，交由Broker重试",error.getPayload());};
    }
}
