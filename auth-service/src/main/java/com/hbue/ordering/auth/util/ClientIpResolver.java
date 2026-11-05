package com.hbue.ordering.auth.util;
import jakarta.servlet.http.HttpServletRequest;
/** 沿用认证接口的受信任网关转发约定，仅用于限流，不参与身份授权。 */
public final class ClientIpResolver {
    private ClientIpResolver() {}
    public static String resolve(HttpServletRequest request) {
        String forwarded=request.getHeader("X-Forwarded-For");
        if(forwarded!=null && !forwarded.isBlank())return forwarded.split(",")[0].strip();
        String real=request.getHeader("X-Real-IP");
        if(real!=null && !real.isBlank())return real.strip();
        String remote=request.getRemoteAddr();return remote==null?"unknown":remote;
    }
}
