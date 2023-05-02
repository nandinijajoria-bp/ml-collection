package com.bharatpe.lending.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@Slf4j
public class FileUtil {

    public static void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.error("Error in deleting file.");
        }
    }

    /**
     * deleteFilesfromLocalStorage
     *
     * @param folderPath
     */
    public void deleteFilesfromLocalStorage(String folderPath) {

        log.info("file location to be deleted: {}", folderPath);
        try {

            Path path = Paths.get(folderPath);
            Files.deleteIfExists(path);

        } catch (Exception e) {
            log.error("Error in deleting local files: {}", e);
        }
    }

}
