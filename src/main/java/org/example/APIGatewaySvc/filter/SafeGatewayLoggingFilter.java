//package org.example.APIGatewaySvc.filter;
//
//import lombok.extern.slf4j.Slf4j;
//import org.reactivestreams.Publisher;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
//import org.springframework.core.Ordered;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.core.io.buffer.DataBufferFactory;
//import org.springframework.core.io.buffer.DataBufferUtils;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.nio.charset.StandardCharsets;
//
///**
// * 응답 본문을 안전하게 로깅하는 Global Filter.
// * ServerHttpResponseDecorator를 사용하여 응답 스트림을 소비하지 않고 로깅합니다.
// * 이 필터는 백엔드에서 4xx/5xx 응답과 함께 본문이 반환될 때 발생할 수 있는
// * UnsupportedOperationException을 방지합니다.
// */
//@Slf4j
//@Component
//public class SafeGatewayLoggingFilter implements GlobalFilter, Ordered {
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        ServerHttpResponse originalResponse = exchange.getResponse();
//        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
//
//        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
//            @Override
//            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
//                if (body instanceof Flux) {
//                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
//                    return super.writeWith(fluxBody.map(dataBuffer -> {
//                        // dataBuffer의 내용을 byte 배열로 읽어옵니다. (이 작업은 버퍼를 소비합니다)
//                        byte[] content = new byte[dataBuffer.readableByteCount()];
//                        dataBuffer.read(content);
//
//                        // 소비된 버퍼를 다시 릴리즈합니다.
//                        DataBufferUtils.release(dataBuffer);
//
//                        // 로깅
//                        String bodyStr = new String(content, StandardCharsets.UTF_8);
//                        log.info("Response Body: {}", bodyStr);
//
//                        // 다음 필터 체인을 위해 동일한 내용의 새로운 버퍼를 생성하여 반환합니다.
//                        return bufferFactory.wrap(content);
//                    }));
//                }
//                return super.writeWith(body); // Flux가 아니면 그대로 통과
//            }
//        };
//        return chain.filter(exchange.mutate().response(decoratedResponse).build());
//    }
//
//    @Override
//    public int getOrder() {
//        // 응답을 쓰는 필터(NettyWriteResponseFilter) 직전에 실행되어야 합니다.
//        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
//    }
//}