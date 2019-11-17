package com.github.sixddc.mirage;

import com.github.sixddc.mirage.web.DslHandlerMapping;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FileWatcher {

    public Logger LOGGER = LoggerFactory.getLogger(FileWatcher.class);

    private final Set<Path> pathSet = new HashSet<>();
    private final Map<Path, FileTime> lastModifiedCache = new ConcurrentHashMap<>();

    private final DslHandlerMapping dslHandlerMapping;

    private String dslFileExtension = "mir";

    @Autowired
    public FileWatcher(DslHandlerMapping dslHandlerMapping) {
        this.dslHandlerMapping = dslHandlerMapping;
    }

    void addScript(String script) {
        dslHandlerMapping.registerScript(script);
    }

    void addFile(Path file) throws IOException {
        pathSet.add(file);
        if (isDslFile(file)) {
            checkAndRegisterFile(file);
        }
        Files.walkFileTree(file, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                if (isDslFile(f)) {
                    checkAndRegisterFile(f);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void refreshPathSet() {
        for (Path path : pathSet) {
            try {
                addFile(path);
            } catch (Exception e) {
                LOGGER.warn("add " + path + " error", e);
            }
        }
    }

    void setDslFileExtension(String ext) {
        dslFileExtension = ext;
    }

    private void checkAndRegisterFile(Path file) throws IOException {
        FileTime modifiedTime = lastModifiedTime(file);
        if (modifiedTime.equals(lastModifiedCache.get(file))) {
            return;
        }

        lastModifiedCache.put(file, modifiedTime);

        dslHandlerMapping.registerFile(file);
    }

    private boolean isDslFile(Path file) {
        return Files.isRegularFile(file) &&
                dslFileExtension.equals(FilenameUtils.getExtension(file.toString()));
    }

    private FileTime lastModifiedTime(Path file) throws IOException {
        return Files.getLastModifiedTime(file);
    }
}
