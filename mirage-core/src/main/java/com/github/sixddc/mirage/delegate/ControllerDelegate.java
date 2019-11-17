package com.github.sixddc.mirage.delegate;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class ControllerDelegate {

    public static Logger log = LoggerFactory.getLogger(ControllerDelegate.class);

    private Path file;
    private String name;

    private final List<RequestMethod> mappings = new ArrayList<>();
    private String version;
    private MappingDelegate parentMapping;

    public ControllerDelegate(Path file) {
        this.file = file;
    }

    public ControllerDelegate(String name) {
        this.name = name;
    }

    public List<RequestMethod> getMappings() {
        return mappings;
    }

    public ControllerDelegate version(String version) {
        this.version = version;
        return this;
    }

    public ControllerDelegate mapping(Map<String, Object> mapping) {
        parentMapping = MappingDelegate.createForMap(mapping);
        return this;
    }

    public ControllerDelegate handle(Map<String, Object> args,
                                     @DelegatesTo(ResponseDelegate.class)
                                     @ClosureParams(value = FromString.class, options = "javax.servlet.http.HttpServletRequest") Closure closure) {
        mappings.add(new RequestMethod(file, toString(), parentMapping, args, closure));
        return this;
    }

    public ControllerDelegate handle(@DelegatesTo(MappingDelegate.class) Closure mapping,
                                     @DelegatesTo(ResponseDelegate.class)
                                     @ClosureParams(value = FromString.class, options = "javax.servlet.http.HttpServletRequest") Closure respond) {
        mappings.add(new RequestMethod(file, toString(), parentMapping, mapping, respond));
        return this;
    }

    public ControllerDelegate handle(String path,
                                     @DelegatesTo(ResponseDelegate.class)
                                     @ClosureParams(value = FromString.class, options = "javax.servlet.http.HttpServletRequest") Closure respond) {
        Map<String, Object> args = Collections.singletonMap("path", (Object) path);
        return handle(args, respond);
    }

    public ControllerDelegate get(String path,
                                  @DelegatesTo(ResponseDelegate.class)
                                  @ClosureParams(value = FromString.class, options = "javax.servlet.http.HttpServletRequest") Closure respond) {
        HashMap<String, Object> args = new HashMap<>();
        args.put("method", "GET");
        args.put("path", path);
        return handle(args, respond);
    }

    public ControllerDelegate post(String path,
                                   @DelegatesTo(ResponseDelegate.class)
                                   @ClosureParams(value = FromString.class, options = "javax.servlet.http.HttpServletRequest") Closure respond) {
        HashMap<String, Object> args = new HashMap<>();
        args.put("method", "POST");
        args.put("path", path);
        return handle(args, respond);
    }

    @Override
    public String toString() {
        return file != null ? file.toString() : name;
    }
}
