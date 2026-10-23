package com.petgrooming.pet_system.config;

import com.petgrooming.pet_system.annotation.CurrentUser;
import com.petgrooming.pet_system.model.User;
import com.petgrooming.pet_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 解析 {@link CurrentUser} 參數：從 LoginInterceptor 放進 request 的登入帳號取得登入者。 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /** LoginInterceptor 驗證 JWT 後寫入的 request 屬性名稱。 */
    public static final String USERNAME_ATTRIBUTE = "tokenUsername";

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && (type == String.class || type == User.class);
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String username = (String) webRequest.getAttribute(USERNAME_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (parameter.getParameterType() == String.class || username == null) {
            return username;
        }
        return userRepository.findByUsername(username).orElse(null);
    }
}
