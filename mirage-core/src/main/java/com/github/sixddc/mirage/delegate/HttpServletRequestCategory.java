package com.github.sixddc.mirage.delegate;

import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unchecked")
public class HttpServletRequestCategory {

    private static String PARAMS_MAP = "DSL_PARAMS_MAP";

    public static Map getPathVariables(HttpServletRequest request) {
        return (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    }

    public static HttpHeaders getHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    public static String getBody(HttpServletRequest request) throws IOException {
        return IOGroovyMethods.getText(request.getInputStream());
    }

    public static Map<String, Object> getParams(HttpServletRequest request) {
        Map<String, Object> params = (Map<String, Object>) request.getAttribute(PARAMS_MAP);
        if (params == null) {
            params = new HashMap<>();
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                if (entry.getValue().length == 1) {
                    params.put(entry.getKey(), entry.getValue()[0]);
                } else {
                    params.put(entry.getKey(), Arrays.asList(entry.getValue()));
                }
            }
            request.setAttribute(PARAMS_MAP, params);
        }
        return params;
    }

    public static Map<String, Object> getForms(HttpServletRequest request) {
        return getParams(request);
    }

    public static Cookie cookie(HttpServletRequest request, String name) {
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie;
            }
        }
        return null;
    }

    public static String getUrl(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String query = request.getQueryString();
        if (StringUtils.hasText(query)) {
            url.append('?').append(query);
        }
        return url.toString();
    }

    public static String getIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
