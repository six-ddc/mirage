package com.github.sixddc.mirage.delegate;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.springframework.util.PathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.UrlPathHelper;

import java.util.Collection;
import java.util.Map;

public class MappingDelegate {

    private String name;
    private String[] paths = new String[0];
    private RequestMethod[] methods = new RequestMethod[0];
    private String[] params = new String[0];
    private String[] headers = new String[0];
    private String[] consumes = new String[0];
    private String[] produces = new String[0];

    public MappingDelegate name(String name) {
        this.name = name;
        return this;
    }

    /**
     * @see RequestMapping#path()
     */
    public MappingDelegate path(String... paths) {
        this.paths = paths;
        return this;
    }

    public MappingDelegate path(Collection<String> paths) {
        return path(paths.toArray(new String[0]));
    }

    /**
     * @see RequestMapping#method()
     */
    public MappingDelegate method(String... methods) {
        this.methods = new RequestMethod[methods.length];
        for (int i = 0; i < methods.length; i++) {
            this.methods[i] = RequestMethod.valueOf(methods[i]);
        }
        return this;
    }

    public MappingDelegate method(Collection<String> methods) {
        return method(methods.toArray(new String[0]));
    }

    /**
     * @see RequestMapping#params()
     */
    public MappingDelegate params(String... params) {
        this.params = params;
        return this;
    }

    public MappingDelegate params(Collection<String> params) {
        return params(params.toArray(new String[0]));
    }

    /**
     * @see RequestMapping#headers()
     */
    public MappingDelegate header(String... headers) {
        this.headers = headers;
        return this;
    }

    public MappingDelegate header(Collection<String> headers) {
        return header(headers.toArray(new String[0]));
    }

    /**
     * @see RequestMapping#produces()
     */
    public MappingDelegate produce(String... produces) {
        this.produces = produces;
        return this;
    }

    public MappingDelegate produce(Collection<String> produces) {
        return produce(produces.toArray(new String[0]));
    }

    /**
     * @see RequestMapping#consumes()
     */
    public MappingDelegate consume(String... consumes) {
        this.consumes = consumes;
        return this;
    }

    public MappingDelegate consume(Collection<String> consumes) {
        return consume(consumes.toArray(new String[0]));
    }

    @SuppressWarnings("unchecked")
    static MappingDelegate createForMap(Map<String, Object> args) {
        MappingDelegate delegate = new MappingDelegate();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            InvokerHelper.invokeMethod(delegate, entry.getKey(), entry.getValue());
        }
        return delegate;
    }

    RequestMappingInfo buildRequestMappingInfo(UrlPathHelper urlPathHelper, PathMatcher pathMatcher) {
        RequestMappingInfo.BuilderConfiguration configuration = new RequestMappingInfo.BuilderConfiguration();
        configuration.setUrlPathHelper(urlPathHelper);
        configuration.setPathMatcher(pathMatcher);
        return RequestMappingInfo.paths(paths).methods(methods).params(params)
                .headers(headers).consumes(consumes).produces(produces)
                .mappingName(name).options(configuration).build();
    }
}
