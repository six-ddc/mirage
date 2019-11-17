package com.github.sixddc.mirage.delegate;

import com.github.javafaker.Faker;
import com.mifmif.common.regex.Generex;
import groovy.json.JsonBuilder;
import groovy.json.JsonDelegate;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import groovy.time.TimeDuration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.ProcessGroovyMethods;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class ResponseDelegate {

    private static final GStringTemplateEngine TEMPLATE_ENGINE = new GStringTemplateEngine(ResponseBody.class.getClassLoader());
    private static final List<String> DEFAULT_INDEX_FILES = Arrays.asList("index.html", "index.htm", "index.txt", "default.html", "default.htm", "default.txt");
    private static final Tika CONTENT_TYPE_TIKA = new Tika();

    private ResourceRegionHttpMessageConverter resourceRegionHttpMessageConverter = new ResourceRegionHttpMessageConverter();

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final ResponseBody responseBody;
    private final Random random;
    private final Path scriptFile;
    private Faker defaultFaker;
    private Faker localFaker;

    public ResponseDelegate(Path scriptFile, HttpServletRequest request, HttpServletResponse response) {
        this.scriptFile = scriptFile;
        this.request = request;
        this.response = response;
        this.random = new Random();
        this.responseBody = new ResponseBody();
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletRequest getReq() {
        return request;
    }

    public void code(int code) {
        response.setStatus(code);
    }

    public void header(String k, String v) {
        header(k, v, true);
    }

    public void header(String k, String v, boolean overwrite) {
        if (overwrite) {
            response.setHeader(k, v);
        } else {
            response.addHeader(k, v);
        }
    }

    public void header(Map<String, String> header) {
        header(header, true);
    }

    public void header(Map<String, String> header, boolean overwrite) {
        for (Map.Entry<String, String> entry : header.entrySet()) {
            header(entry.getKey(), entry.getValue(), overwrite);
        }
    }

    public void status(int code) {
        response.setStatus(code);
    }

    public void error(int code) throws IOException {
        response.sendError(code);
    }

    public <T> void error(int code, T msg) throws IOException {
        response.sendError(code, String.valueOf(msg));
    }

    public void redirect(String url) throws IOException {
        response.sendRedirect(url);
    }

    public void type(String contentType) {
        response.setContentType(contentType);
    }

    public void contentType(String tp) {
        response.setContentType(tp);
    }

    public void charset(String charset) {
        response.setCharacterEncoding(charset);
    }

    public void cookie(Map<String, Object> map) {
        Cookie cookie = new Cookie((String) map.get("name"), (String) map.get("value"));
        map.remove("name");
        map.remove("value");
        InvokerHelper.setProperties(cookie, map);
        response.addCookie(cookie);
    }

    public Faker getFaker() {
        if (defaultFaker == null) {
            defaultFaker = new Faker();
        }
        return defaultFaker;
    }

    public Random getRandom() {
        return random;
    }

    public ResponseBody getResponse() {
        return responseBody;
    }

    public ResponseBody getResp() {
        return responseBody;
    }

    public Faker getLocalFaker() {
        if (localFaker == null) {
            localFaker = new Faker(Locale.getDefault());
        }
        return localFaker;
    }

    public Faker faker(Locale locale) {
        return new Faker(locale);
    }

    public Faker faker(String language) {
        return faker(new Locale(language));
    }

    public Faker faker(String language, String country) {
        return faker(new Locale(language, country));
    }

    public Process exec(String cmd) throws IOException {
        return exec(cmd, null, null);
    }

    public Process exec(String cmd, Collection<String> env, String dir) throws IOException {
        String[] envArr = env == null ? null : env.toArray(new String[0]);
        File workDir = dir == null ? null : new File(dir);
        return Runtime.getRuntime().exec(cmd, envArr, workDir);
    }

    public Process exec(Collection<String> cmd) throws IOException {
        return exec(cmd, null, null);
    }

    public Process exec(Collection<String> cmd, Collection<String> env, String dir) throws IOException {
        String[] envArr = env == null ? null : env.toArray(new String[0]);
        File workDir = dir == null ? null : new File(dir);
        return Runtime.getRuntime().exec(cmd.toArray(new String[0]), envArr, workDir);
    }

    public Process bash(String cmd) throws IOException {
        return bash(cmd, null, null);
    }

    public Process bash(String cmd, Collection<String> env, String dir) throws IOException {
        String[] envArr = env == null ? null : env.toArray(new String[0]);
        File workDir = dir == null ? null : new File(dir);
        return Runtime.getRuntime().exec(new String[]{"bash", "-c", cmd}, envArr, workDir);
    }

    public void sleep(TimeDuration duration) throws InterruptedException {
        Thread.sleep(duration.toMilliseconds());
    }

    public void delay(TimeDuration duration) throws InterruptedException {
        sleep(duration);
    }

    void respond() throws IOException {
        response.getOutputStream().flush();
    }

    public class Random {

        public String uuid() {
            return UUID.randomUUID().toString();
        }

        public String forRegex(Pattern pattern) {
            return forRegex(pattern.pattern());
        }

        public String forRegex(String regex) {
            return new Generex(regex).random();
        }

        public long nextLong(long bound) {
            return ThreadLocalRandom.current().nextLong(bound);
        }

        public long nextLong(long origin, long bound) {
            return ThreadLocalRandom.current().nextLong(origin, bound);
        }

        public int nextInt(int bound) {
            return ThreadLocalRandom.current().nextInt(bound);
        }

        public int nextInt(int origin, int bound) {
            return ThreadLocalRandom.current().nextInt(origin, bound);
        }

        public boolean nextBool() {
            return ThreadLocalRandom.current().nextBoolean();
        }
    }

    public class ResponseBody {

        private Path resolveFile(String fileName) {
            Path path = Paths.get(fileName);
            if (!path.isAbsolute() && scriptFile != null) {
                return scriptFile.getParent().resolve(path);
            }
            return path;
        }

        public <T> void str(T body) throws IOException {
            if (body instanceof Closure) {
                Object obj = ((Closure) body).call();
                str(obj);
            } else if (body instanceof InputStream) {
                IOUtils.copy(((InputStream) body), response.getOutputStream());
            } else if (body instanceof Process) {
                IOUtils.copy(((Process) body).getInputStream(), response.getOutputStream());
                ProcessGroovyMethods.closeStreams((Process) body);
            } else {
                response.getOutputStream().print(String.valueOf(body));
            }
        }

        public <T> void println(T body) throws IOException {
            str(body);
            response.getOutputStream().println();
        }

        public void index(String fileName) throws IOException {
            String reqPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String bestMatchPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            AntPathMatcher apm = new AntPathMatcher();
            String urlPath = apm.extractPathWithinPattern(bestMatchPattern, reqPath);

            Path path = Paths.get(fileName).resolve(StringUtils.defaultString(urlPath, "."));
            if (!Files.isReadable(path)) {
                response.setStatus(404);
                return;
            }
            if (Files.isDirectory(path)) {
                for (String it : DEFAULT_INDEX_FILES) {
                    Path indexFile = path.resolve(it);
                    if (Files.isRegularFile(indexFile) && Files.isReadable(indexFile)) {
                        file(indexFile.toString());
                        return;
                    }
                }
            } else if (Files.isRegularFile(path)) {
                file(path.toString());
                return;
            }

            response.setStatus(404);
        }

        public void file(String fileName) throws IOException {
            file(fileName, null);
        }

        public void file(String fileName, String contentType) throws IOException {
            List<HttpRange> httpRanges = HttpRange.parseRanges(request.getHeader(HttpHeaders.RANGE));

            Path path = resolveFile(fileName);

            if (!Files.isReadable(path)) {
                response.setStatus(404);
                return;
            }
            if (contentType == null && response.getContentType() == null) {
                contentType = CONTENT_TYPE_TIKA.detect(path);
            }
            if (contentType != null) {
                response.setContentType(contentType);
            }

            if (CollectionUtils.isEmpty(httpRanges)) {
                try (FileInputStream is = new FileInputStream(path.toFile())) {
                    IOUtils.copy(is, response.getOutputStream());
                }
            } else {
                ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(response);
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                FileSystemResource resource = new FileSystemResource(path.toFile());
                MediaType mediaType = StringUtils.isEmpty(contentType) ? null : MediaType.parseMediaType(contentType);
                if (httpRanges.size() == 1) {
                    ResourceRegion resourceRegion = httpRanges.get(0).toResourceRegion(resource);
                    resourceRegionHttpMessageConverter.write(resourceRegion, mediaType, outputMessage);
                } else {
                    List<ResourceRegion> resourceRegions = HttpRange.toResourceRegions(httpRanges, resource);
                    resourceRegionHttpMessageConverter.write(resourceRegions, mediaType, outputMessage);
                }
            }
        }

        public void eval(String script) throws IOException, ClassNotFoundException {
            Map binding = Collections.singletonMap("request", request);
            eval(binding, script);
        }

        public void eval(Map binding, String script) throws IOException, ClassNotFoundException {
            Template template = TEMPLATE_ENGINE.createTemplate(script);
            str(template.make(binding).toString());
        }

        public void evalFile(String fileName) throws IOException, ClassNotFoundException {
            Map binding = Collections.singletonMap("request", request);
            evalFile(binding, fileName);
        }

        public void evalFile(Map binding, String fileName) throws IOException, ClassNotFoundException {
            Path file = resolveFile(fileName);
            Template template = TEMPLATE_ENGINE.createTemplate(file.toFile());
            str(template.make(binding).toString());
        }

        public void json(Map m) throws IOException {
            JsonBuilder jsonBuilder = new JsonBuilder();
            jsonBuilder.call(m);
            type(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getOutputStream().print(jsonBuilder.toString());
        }

        public void json(List l) throws IOException {
            JsonBuilder jsonBuilder = new JsonBuilder();
            jsonBuilder.call(l);
            type(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getOutputStream().print(jsonBuilder.toString());
        }

        public void json(Object... args) throws IOException {
            JsonBuilder jsonBuilder = new JsonBuilder();
            jsonBuilder.call(args);
            type(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getOutputStream().print(jsonBuilder.toString());
        }

        public void json(Iterable coll, @DelegatesTo(JsonDelegate.class) Closure c) throws IOException {
            JsonBuilder jsonBuilder = new JsonBuilder();
            jsonBuilder.call(coll, c);
            type(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getOutputStream().print(jsonBuilder.toString());
        }

        public void json(@DelegatesTo(JsonDelegate.class) Closure c) throws IOException {
            JsonBuilder jsonBuilder = new JsonBuilder();
            jsonBuilder.call(c);
            type(MediaType.APPLICATION_JSON_UTF8_VALUE);
            response.getOutputStream().print(jsonBuilder.toString());
        }
    }
}
