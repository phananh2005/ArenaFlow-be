package com.phananh.arenaflow.api.security;

import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Resolve {@code @CurrentUser UUID} từ {@code Jwt.getSubject()}; không có JWT hoặc subject không phải UUID → AUTH_001.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && (UUID.class.equals(parameter.getParameterType()) || CurrentActor.class.equals(parameter.getParameterType()));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new BusinessException(ErrorCode.AUTH_001);
        }
        try {
            UUID userId = UUID.fromString(jwtAuthentication.getToken().getSubject());
            if (CurrentActor.class.equals(parameter.getParameterType())) {
                boolean admin = jwtAuthentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                return new CurrentActor(userId, admin);
            }
            return userId;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.AUTH_001);
        }
    }
}
