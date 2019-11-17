package com.github.sixddc.mirage.web;

import com.github.sixddc.mirage.delegate.ControllerDelegate;
import com.github.sixddc.mirage.delegate.RequestMethod;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.util.DelegatingScript;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.springframework.beans.BeansException;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class DslHandlerMapping extends AbstractHandlerMapping {

    private final static CompilerConfiguration DEFAULT_CONFIG = new CompilerConfiguration(CompilerConfiguration.DEFAULT);

    static {
        DEFAULT_CONFIG.setScriptBaseClass(DelegatingScript.class.getName());
    }

    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Map<String, Set<RequestMethod>> directUrlLookup = new HashMap<>();
    private final MultiValueMap<Path, RequestMethod> fileLookup = new LinkedMultiValueMap<>();
    private final Set<RequestMethod> notDirectDelegates = new HashSet<>();

    public DslHandlerMapping() {
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    private static Class loadScript(GroovyCodeSource codeSource) {
        GroovyClassLoader loader = AccessController.doPrivileged(new PrivilegedAction<GroovyClassLoader>() {
            @Override
            public GroovyClassLoader run() {
                return new GroovyClassLoader(DslHandlerMapping.class.getClassLoader(), DEFAULT_CONFIG);
            }
        });
        return loader.parseClass(codeSource, false);
    }

    @Override
    protected void initApplicationContext() throws BeansException {
        setInterceptors(new DslHandlerInterceptor());
        super.initApplicationContext();
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
        String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
        return lookupDslHandler(lookupPath, request);
    }

    public void registerFile(Path file) throws IOException {
        GroovyCodeSource codeSource = new GroovyCodeSource(file.toFile());
        Class clazz = loadScript(codeSource);

        Binding binding = new Binding();
        DelegatingScript ds = (DelegatingScript) InvokerHelper.createScript(clazz, binding);
        ControllerDelegate dsl = new ControllerDelegate(file);
        ds.setDelegate(dsl);
        ds.run();

        registerDslFile(file, dsl);
    }

    public void registerScript(String script) {
        String name = "script@" + Integer.toHexString(script.hashCode());
        GroovyCodeSource codeSource = new GroovyCodeSource(script, name, "/groovy/script");
        Class clazz = loadScript(codeSource);

        Binding binding = new Binding();
        DelegatingScript ds = (DelegatingScript) InvokerHelper.createScript(clazz, binding);
        ControllerDelegate dsl = new ControllerDelegate(name);
        ds.setDelegate(dsl);
        ds.run();

        registerDslScript(script, dsl);
    }

    private void registerDslFile(Path file, ControllerDelegate dsl) {
        readWriteLock.writeLock().lock();
        try {
            List<RequestMethod> delegates = dsl.getMappings();
            removeDelegates(fileLookup.put(file.toAbsolutePath(), delegates));
            addDelegates(delegates, dsl.toString());
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private void registerDslScript(String script, ControllerDelegate dsl) {
        readWriteLock.writeLock().lock();
        try {
            List<RequestMethod> delegates = dsl.getMappings();
            addDelegates(delegates, dsl.toString());
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private void removeDelegates(List<RequestMethod> delegates) {
        if (delegates == null) {
            return;
        }
        for (RequestMethod delegate : delegates) {
            List<String> directUrls = delegate.getDirectUrls();
            if (directUrls != null) {
                for (String directUrl : directUrls) {
                    directUrlLookup.get(directUrl).remove(delegate);
                }
            }
            notDirectDelegates.remove(delegate);
        }
    }

    private void addDelegates(List<RequestMethod> delegates, String desc) {
        if (delegates == null) {
            return;
        }
        for (RequestMethod delegate : delegates) {
            delegate.init(getUrlPathHelper(), getPathMatcher());

            List<String> directUrls = delegate.getDirectUrls();
            if (directUrls != null) {
                for (String directUrl : directUrls) {
                    Set<RequestMethod> sets = directUrlLookup.get(directUrl);
                    if (sets != null) {
                        sets.add(delegate);
                    } else {
                        sets = new HashSet<>(1);
                        sets.add(delegate);
                        directUrlLookup.put(directUrl, sets);
                    }
                }
            }
            notDirectDelegates.add(delegate);

            if (logger.isInfoEnabled()) {
                RequestMappingInfo requestMappingInfo = delegate.getRequestMappingInfo();
                logger.info("Mapped \"" + requestMappingInfo + "\" onto dsl [" + desc + "]");
            }
        }
    }

    /**
     * @see org.springframework.web.servlet.handler.AbstractHandlerMethodMapping#lookupHandlerMethod(String, HttpServletRequest)
     */
    private RequestMethod lookupDslHandler(String lookupPah, final HttpServletRequest request) throws Exception {
        readWriteLock.readLock().lock();
        try {
            List<Match> matches = new ArrayList<>();

            Set<RequestMethod> directPathMatches = directUrlLookup.get(lookupPah);
            if (!CollectionUtils.isEmpty(directPathMatches)) {
                addMatchingMappings(directPathMatches, matches, request);
            }

            if (matches.isEmpty()) {
                // No choice but to go through all mappings...
                addMatchingMappings(notDirectDelegates, matches, request);
            }

            if (matches.isEmpty()) {
                return handleNoMatch(notDirectDelegates, lookupPah, request);
            }

            Collections.sort(matches, new Comparator<Match>() {
                @Override
                public int compare(Match a, Match b) {
                    return a.mappingInfo.compareTo(b.mappingInfo, request);
                }
            });

            Match bestMatch = matches.get(0);
            if (matches.size() > 1) {
                if (CorsUtils.isPreFlightRequest(request)) {
                    // TODO
                }
                Match secondMatch = matches.get(1);
                if (bestMatch.mappingInfo.compareTo(secondMatch.mappingInfo, request) == 0) {
                    throw new IllegalStateException("Ambiguous handler methods mapped for HTTP path '" +
                            request.getRequestURL() + "': {" + bestMatch.delegate + ", " + secondMatch.delegate.toString() + "}");
                }
            }
            handleMatch(bestMatch.mappingInfo, bestMatch.delegate, lookupPah, request);
            return bestMatch.delegate;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    private void handleMatch(RequestMappingInfo mappingInfo, RequestMethod dsl, String lookupPath, HttpServletRequest request) {
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);

        String bestPattern;
        Map<String, String> uriVariables;
        Map<String, String> decodedUriVariables;

        Set<String> patterns = mappingInfo.getPatternsCondition().getPatterns();
        if (patterns.isEmpty()) {
            bestPattern = lookupPath;
            uriVariables = Collections.emptyMap();
            decodedUriVariables = Collections.emptyMap();
        } else {
            bestPattern = patterns.iterator().next();
            uriVariables = getPathMatcher().extractUriTemplateVariables(bestPattern, lookupPath);
            decodedUriVariables = getUrlPathHelper().decodePathVariables(request, uriVariables);
        }

        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestPattern);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, decodedUriVariables);

        if (isMatrixVariableContentAvailable()) {
            Map<String, MultiValueMap<String, String>> matrixVars = extractMatrixVariables(request, uriVariables);
            request.setAttribute(HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE, matrixVars);
        }

        Set<MediaType> producibleMediaTypes = mappingInfo.getProducesCondition().getProducibleMediaTypes();
        if (!producibleMediaTypes.isEmpty()) {
            request.setAttribute(PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, producibleMediaTypes);
        }
    }


    private boolean isMatrixVariableContentAvailable() {
        return !getUrlPathHelper().shouldRemoveSemicolonContent();
    }

    private Map<String, MultiValueMap<String, String>> extractMatrixVariables(
            HttpServletRequest request, Map<String, String> uriVariables) {

        Map<String, MultiValueMap<String, String>> result = new LinkedHashMap<String, MultiValueMap<String, String>>();
        for (Map.Entry<String, String> uriVar : uriVariables.entrySet()) {
            String uriVarValue = uriVar.getValue();

            int equalsIndex = uriVarValue.indexOf('=');
            if (equalsIndex == -1) {
                continue;
            }

            String matrixVariables;

            int semicolonIndex = uriVarValue.indexOf(';');
            if ((semicolonIndex == -1) || (semicolonIndex == 0) || (equalsIndex < semicolonIndex)) {
                matrixVariables = uriVarValue;
            } else {
                matrixVariables = uriVarValue.substring(semicolonIndex + 1);
                uriVariables.put(uriVar.getKey(), uriVarValue.substring(0, semicolonIndex));
            }

            MultiValueMap<String, String> vars = WebUtils.parseMatrixVariables(matrixVariables);
            result.put(uriVar.getKey(), getUrlPathHelper().decodeMatrixVariables(request, vars));
        }
        return result;
    }

    private RequestMethod handleNoMatch(Set<RequestMethod> dsls, String lookupPath, HttpServletRequest request) {
        return null;
    }

    private void addMatchingMappings(Set<RequestMethod> mappings, List<Match> matches, HttpServletRequest request) {
        for (RequestMethod delegate : mappings) {
            RequestMappingInfo matchingMappingInfo = delegate.getRequestMappingInfo().getMatchingCondition(request);
            if (matchingMappingInfo != null) {
                matches.add(new Match(delegate, matchingMappingInfo));
            }
        }
    }

    private static class Match {
        private RequestMethod delegate;
        private RequestMappingInfo mappingInfo;

        Match(RequestMethod delegate, RequestMappingInfo mappingInfo) {
            this.delegate = delegate;
            this.mappingInfo = mappingInfo;
        }
    }
}
