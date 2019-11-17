package com.github.sixddc.mirage.delegate;

import groovy.lang.Closure;
import groovy.time.TimeCategory;
import org.codehaus.groovy.runtime.GroovyCategorySupport;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RequestMethod {

    private static final List<Class> CATEGORY_CLASSES = new ArrayList<>(2);

    private Path scriptFile;
    private String scriptDesc;
    private String mappingName;

    private MappingDelegate parentMapping;
    private Map<String, Object> mappingArgs;
    private Closure mappingClosure;

    private Closure responseClosure;
    private List<String> directUrls;
    private RequestMappingInfo requestMappingInfo;

    static {
        CATEGORY_CLASSES.add(HttpServletRequestCategory.class);
        CATEGORY_CLASSES.add(TimeCategory.class);
    }

    RequestMethod(Path scriptFile, String scriptDesc, MappingDelegate parentMapping, Map<String, Object> mappingArgs, Closure responseClosure) {
        this.scriptFile = scriptFile;
        this.scriptDesc = scriptDesc;
        this.parentMapping = parentMapping;
        this.mappingArgs = mappingArgs;
        this.responseClosure = responseClosure;
    }

    RequestMethod(Path scriptFile, String scriptDesc, MappingDelegate parentMapping, Closure mappingClosure, Closure responseClosure) {
        this.scriptFile = scriptFile;
        this.scriptDesc = scriptDesc;
        this.parentMapping = parentMapping;
        this.mappingClosure = mappingClosure;
        this.responseClosure = responseClosure;
    }

    public List<String> getDirectUrls() {
        return directUrls;
    }

    public RequestMappingInfo getRequestMappingInfo() {
        return requestMappingInfo;
    }

    public void init(UrlPathHelper urlPathHelper, PathMatcher pathMatcher) {
        MappingDelegate delegate;
        if (mappingClosure != null) {
            delegate = new MappingDelegate();
            mappingClosure.setDelegate(delegate);
            mappingClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
            mappingClosure.call();
        } else {
            delegate = MappingDelegate.createForMap(mappingArgs);
        }
        requestMappingInfo = delegate.buildRequestMappingInfo(urlPathHelper, pathMatcher);
        if (parentMapping != null) {
            RequestMappingInfo parentRequestMappingInfo = parentMapping.buildRequestMappingInfo(urlPathHelper, pathMatcher);
            requestMappingInfo = parentRequestMappingInfo.combine(requestMappingInfo);
        }
        mappingName = requestMappingInfo.getName();

        directUrls = new ArrayList<>();
        for (String it : requestMappingInfo.getPatternsCondition().getPatterns()) {
            if (!pathMatcher.isPattern(it)) {
                directUrls.add(it);
            }
        }
    }

    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Objects.requireNonNull(responseClosure, toString() + ": response closure is empty");

        ResponseDelegate delegate = new ResponseDelegate(scriptFile, request, response);
        Closure c = responseClosure.curry(request);
        c.setDelegate(delegate);
        c.setResolveStrategy(Closure.DELEGATE_FIRST);
        GroovyCategorySupport.use(CATEGORY_CLASSES, c);

        delegate.respond();
    }

    @Override
    public String toString() {
        String methodDesc = mappingName != null ? mappingName : ("(" + requestMappingInfo.toString() + ")");
        return scriptDesc + "@" + methodDesc;
    }
}
