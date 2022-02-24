package util.module;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

    public static boolean writeBytes(File file, byte[] data, boolean isAppend) {
        if (file == null || data == null || data.length == 0) { return false; }

        BufferedOutputStream bufferedOutputStream = null;
        try {
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file, isAppend));
            bufferedOutputStream.write(data);
            return true;
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to write the file. (fileName={})", file.getAbsolutePath(), e);
            return false;
        } finally {
            try {
                if (bufferedOutputStream != null) {
                    bufferedOutputStream.close();
                }
            } catch (Exception e) {
                logger.warn("[FileManager] Fail to close the buffer stream. (fileName={})", file.getAbsolutePath());
            }
        }
    }


    public static boolean writeBytes(String fileName, byte[] data, boolean isAppend) {
        if (fileName == null) { return false; }

        BufferedOutputStream bufferedOutputStream = null;
        try {
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(fileName, isAppend));
            bufferedOutputStream.write(data);
            return true;
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to write the file. (fileName={})", fileName, e);
            return false;
        } finally {
            try {
                if (bufferedOutputStream != null) {
                    bufferedOutputStream.close();
                }
            } catch (Exception e) {
                logger.warn("[FileManager] Fail to close the buffer stream. (fileName={})", fileName, e);
            }
        }
    }

    public static byte[] readAllBytes(String fileName) {
        if (fileName == null) { return null; }

        BufferedInputStream bufferedInputStream = null;
        try {
            bufferedInputStream = new BufferedInputStream(new FileInputStream(fileName));
            return bufferedInputStream.readAllBytes();
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to read the file. (fileName={})", fileName, e);
            return null;
        } finally {
            try {
                if (bufferedInputStream != null) {
                    bufferedInputStream.close();
                }
            } catch (IOException e) {
                logger.warn("[FileManager] Fail to close the buffer stream. (fileName={})", fileName);
            }
        }
    }

    public static List<String> readAllLines(String fileName) {
        if (fileName == null) { return null; }

        BufferedReader bufferedReader = null;
        List<String> lines = new ArrayList<>();
        try {
            bufferedReader = new BufferedReader(new FileReader(fileName));
            String line;
            while( (line = bufferedReader.readLine()) != null ) {
                lines.add(line);
            }
            return lines;
        } catch (Exception e) {
            logger.warn("[FileManager] Fail to read the file. (fileName={})", fileName);
            return lines;
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                logger.warn("[FileManager] Fail to close the buffer reader. (fileName={})", fileName, e);
            }
        }
    }

    // [/home/uangel/udash/media] + [animal/tigers/tigers.mp4] > [/home/uangel/udash/media/animal/tigers/tigers.mp4]
    public static String concatFilePath(String from, String to) {
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
    public static String getParentPathFromUri(String uri) {
        if (uri == null) { return null; }
        if (!uri.contains("/")) { return uri; }
        return uri.substring(0, uri.lastIndexOf("/")).trim();
    }

    // [/home/uangel/udash/media/animal/tigers/tigers.mp4] > [tigers.mp4]
    public static String getFileNameWithExtensionFromUri(String uri) {
        if (uri == null) { return null; }
        if (!uri.contains("/")) { return uri; }

        int lastSlashIndex = uri.lastIndexOf("/");
        if (lastSlashIndex == (uri.length() - 1)) { return null; }
        return uri.substring(uri.lastIndexOf("/") + 1).trim();
    }

    // [/home/uangel/udash/media/animal/tigers/tigers.mp4] > [/home/uangel/udash/media/animal/tigers/tigers]
    public static String getFilePathWithoutExtensionFromUri(String uri) {
        if (uri == null) { return null; }
        if (!uri.contains(".")) { return uri; }
        return uri.substring(0, uri.lastIndexOf(".")).trim();
    }

    // [/home/uangel/udash/media/animal/tigers/tigers.mp4] > [tigers]
    public static String getFileNameFromUri(String uri) {
        uri = getFileNameWithExtensionFromUri(uri);
        if (uri == null) { return null; }
        if (!uri.contains(".")) { return uri; }

        uri = uri.substring(0, uri.lastIndexOf(".")).trim();
        return uri;
    }

    public static void deleteFile(String path) {
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

}
