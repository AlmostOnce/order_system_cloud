package com.hbue.ordering.order.dto;
public record OrderTimeoutEvent(String eventId,Long orderId,long expiresAt) {
    public boolean valid() {return orderId!=null && orderId>0 && expiresAt>0 && ("order-timeout-v1:"+orderId).equals(eventId);}
    public static OrderTimeoutEvent of(Long id,long expires) {return new OrderTimeoutEvent("order-timeout-v1:"+id,id,expires);}
}
