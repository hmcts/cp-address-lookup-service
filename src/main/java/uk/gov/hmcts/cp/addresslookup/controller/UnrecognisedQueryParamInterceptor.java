package uk.gov.hmcts.cp.addresslookup.controller;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects any query parameter outside a handler method's declared {@code @RequestParam} set with
 * 400, before the handler runs. Derived from the matched method's {@link MethodParameter}s rather
 * than a hand-maintained per-endpoint list, so it stays correct as operations are added. A request
 * carrying an unrecognised parameter must not be silently accepted, since that could let a caller
 * believe a filter was applied when it was not (e.g. OS Places' own {@code bbox}/{@code dataset}
 * params, which this API deliberately does not expose).
 *
 * <p>Uses {@link MethodParameter#getParameterAnnotation}, not raw {@code java.lang.reflect}
 * introspection - the controller implements an OpenAPI-generated interface whose default methods
 * carry the {@code @RequestParam} annotations, and only Spring's own annotation lookup resolves
 * annotations declared on an implemented interface's method rather than the overriding class.
 */
@Component
public class UnrecognisedQueryParamInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
            final Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        final Set<String> allowed = declaredRequestParamNames(handlerMethod);
        if (allowed.isEmpty()) {
            return true;
        }

        final Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            final String paramName = paramNames.nextElement();
            if (!allowed.contains(paramName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unrecognised query parameter '" + paramName + "'");
            }
        }
        return true;
    }

    private static Set<String> declaredRequestParamNames(final HandlerMethod handlerMethod) {
        final Set<String> names = new HashSet<>();
        for (final MethodParameter parameter : handlerMethod.getMethodParameters()) {
            final RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
            if (requestParam != null) {
                names.add(!requestParam.name().isBlank() ? requestParam.name() : parameter.getParameterName());
            }
        }
        return names;
    }
}
