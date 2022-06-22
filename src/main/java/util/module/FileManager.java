package util.module;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FileManager {

    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

    public boolean isExist(String fileName) {
        if (fileName == null) { return false; }

        File file = new File(fileName);
        return file.exists();
    }

    public boolean mkdirs(String fileName) {
        if (fileName == null) { return false; }

        File file = new File(fileName);
        return file.mkdirs();
    }

    public boolean writeBytes(File file, byte[] data, boolean isAppend) {
        if (file == null || data == null || data.length == 0) { return false; }

        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file, isAppend))) {
            bufferedOutputStream.write(data);
            return true;
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to write the file. (fileName={})", file.getAbsolutePath(), e);
            return false;
        }
    }

    public boolean writeBytes(String fileName, byte[] data, boolean isAppend) {
        if (fileName == null) { return false; }

        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(fileName, isAppend))) {
            bufferedOutputStream.write(data);
            return true;
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to write the file. (fileName={})", fileName, e);
            return false;
        }
    }

    public boolean writeString(String fileName, String data, boolean isAppend) {
        if (fileName == null) { return false; }

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(fileName, isAppend))) {
            bufferedWriter.write(data);
            return true;
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to write the file. (fileName={})", fileName, e);
            return false;
        }
    }

    public byte[] readAllBytes(String fileName) {
        if (fileName == null) { return new byte[0]; }

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(fileName))) {
            return bufferedInputStream.readAllBytes();
        } catch (Exception e) {
            //logger.warn("[FileManager] Fail to read the file. (fileName={})", fileName);
            return new byte[0];
        }
    }

    public List<String> readAllLines(String fileName) {
        if (fileName == null) { return Collections.emptyList(); }

        List<String> lines = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while( (line = bufferedReader.readLine()) != null ) {
                lines.add(line);
            }
            return lines;
        } catch (Exception e) {
            //logger.warn("[FileManager] Fail to read the file. (fileName={})", fileName);
            return lines;
        }
    }

    // [/home/uangel/udash/media] + [animal/tigers/tigers.mp4] > [/home/uangel/udash/media/animal/tigers/tigers.mp4]
    public String concatFilePath(String from, String to) {
        if (from == null) { return null; }
        if (to == null) { return from; }

        String resultPath = from.trim();
        if (!to.startsWith(File.separator)) {
            if (!resultPath.endsWith(File.separator)) {
                resultPath += File.separator;
            }
        } else {
            if (resultPath.endsWith(File.separator)) {
                resultPath = resultPath.substring(0, resultPath.lastIndexOf("/"));
            }
        }

        resultPath += to.trim();
        return resultPath;
    }

    // [/home/uangel/udash/media/animal/tigers/tigers.mp4] > [/home/uangel/udash/media/animal/tigers]
    public String getParentPathFromUri(String uri) {
        if (uri == null) { return null; }
        if (!uri.contains("/")) { return uri; }
        return uri.substring(0, uri.lastIndexOf("/")).trim();
    }

    // [/home/uangel/udash/media/animal/tigers/tigers.mp4] > [tigers.mp4]
    public String getFileNameWithExtensionFromUri(String uri) {
        if (uri == null) { return null; }
        if (!uri.contains("/")) { return uri; }

        int lastSlashIndex = uri.lastIndexOf("/");
        if (lastSlashIndex == (uri.length() - 1)) { return null; }
        return uri.substring(uri.lastIndexOf("/") + 1).trim();
    }

    // [/home/uangel/udash/media/animal/tigers/tigers.mp4] > [/home/uangel/udash/media/animal/tigers/tigers]
    public String getFilePathWithoutExtensionFromUri(String uri) {
        if (uri == null) { return null; }
        if (!uri.contains(".")) { return uri; }
        return uri.substring(0, uri.lastIndexOf(".")).trim();
    }

    // [/home/uangel/udash/media/animal/tigers/tigers.mp4] > [tigers]
    public String getFileNameFromUri(String uri) {
        uri = getFileNameWithExtensionFromUri(uri);
        if (uri == null) { return null; }
        if (!uri.contains(".")) { return uri; }

        uri = uri.substring(0, uri.lastIndexOf(".")).trim();
        return uri;
    }

    public void deleteFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            logger.warn("[FileManager] Fail to delete the file. File is not exist. (path={})", path);
            return;
        }

        try {
            if (file.isDirectory()) {
                FileUtils.deleteDirectory(file);
            } else {
                FileUtils.fileDelete(path);
            }
            logger.debug("[FileManager] Success to delete the file. (path={})", path);
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to delete the file. (path={})", path, e);
        }
    }

    public void deleteFile(File file) {
        if (file == null) { return; }

        try {
            if (file.isDirectory()) {
                FileUtils.deleteDirectory(file);
            } else {
                FileUtils.fileDelete(file.getAbsolutePath());
            }
            logger.debug("[FileManager] Success to delete the file. (path={})", file.getAbsolutePath());
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to delete the file. (path={})", file.getAbsolutePath(), e);
        }
    }

    public void deleteOldFilesBySecond(String rootPath, String[] exceptFileNameList, String[] exceptFileExtensionList, long limitTime) throws IOException {
        File rootPathFile = new File(rootPath);
        File[] files = rootPathFile.listFiles();
        if (files == null || files.length == 0) { return; }

        for (File curFile : files) {
            if (curFile == null || !curFile.exists() || curFile.isDirectory()) { continue; }

            ///////////////////////////////
            boolean isExcepted = false;
            // 1) 제외할 파일 이름 확인
            if (exceptFileNameList != null) {
                for (String exceptFileName : exceptFileNameList) {
                    if (curFile.getAbsolutePath().contains(exceptFileName)) {
                        isExcepted = true;
                        break;
                    }
                }
            }
            // 2) 제외할 파일 확장자 확인
            if (exceptFileExtensionList != null) {
                for (String exceptFileExtension : exceptFileExtensionList) {
                    String curFileExtension = FileUtils.getExtension(curFile.getAbsolutePath());
                    if (curFileExtension.isEmpty()) {
                        continue;
                    }
                    if (curFileExtension.equals(exceptFileExtension)) {
                        isExcepted = true;
                        break;
                    }
                }
            }
            if (isExcepted) { continue; }
            ///////////////////////////////

            ///////////////////////////////
            // 3) 파일 마지막 수정 시간 확인
            long lastModifiedTime = getLastModificationSecondTime(curFile);
            if (lastModifiedTime >= limitTime) { // 제한 시간 [이상] 경과
                Files.delete(curFile.toPath());
                logger.trace("[FileManager] Old file({}) is deleted. (lastModifiedTime=[{}]sec, limitTime=[{}]sec)",
                        curFile.getAbsolutePath(),
                        lastModifiedTime, limitTime
                );
            }
            ///////////////////////////////
        }
    }

    public void deleteOldDirectoriesBySecond(File rootDir, List<String> exceptDirNameList, long limitTime) throws IOException {
        if (rootDir == null || rootDir.isFile()) { return; }

        File[] dirs = rootDir.listFiles();
        if (dirs == null || dirs.length == 0) { return; }

        for (File curDir : dirs) {
            if (curDir == null || !curDir.exists() || curDir.isFile()) { continue; }

            ///////////////////////////////
            boolean isExcepted = false;
            // 1) 제외할 디렉토리 이름 확인
            if (exceptDirNameList != null) {
                for (String exceptDirName : exceptDirNameList) {
                    if (curDir.getAbsolutePath().endsWith(exceptDirName)) {
                        isExcepted = true;
                        break;
                    }
                }
            }
            if (isExcepted) { continue; }
            ///////////////////////////////

            ///////////////////////////////
            // 2) 파일 마지막 수정 시간 확인
            long lastModifiedTime = getLastModificationSecondTime(curDir);
            if (lastModifiedTime >= limitTime) { // 제한 시간 [이상] 경과
                deleteFile(curDir);
                logger.warn("[FileManager] Old Directory({}) is deleted. (lastModifiedTime=[{}]sec, limitTime=[{}]sec)",
                        curDir.getAbsolutePath(),
                        lastModifiedTime, limitTime
                );
            }
            ///////////////////////////////
        }
    }

    private long getLastModificationSecondTime(File file) throws IOException {
        Path attribPath = file.toPath();
        BasicFileAttributes basicAttr = Files.readAttributes(attribPath, BasicFileAttributes.class);
        return (System.currentTimeMillis() - basicAttr.lastModifiedTime().to(TimeUnit.MILLISECONDS)) / 1000;
    }

}
