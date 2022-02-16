package process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessManager {

    private static final Logger logger = LoggerFactory.getLogger(ProcessManager.class);

    public static void runProcessWait(String command, String filePath) {
        BufferedReader stdOut = null;
        Process process = null;
        try {
            logger.debug("[ProcessManager] PROCESS (command={}, filePath={})", command, filePath);
            process = Runtime.getRuntime().exec(command);

            String str;
            stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((str = stdOut.readLine()) != null) {
                logger.debug(str);
            }

            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new RuntimeException("[ProcessManager] exit code is not 0 [" + exitValue + "]");
            }

            logger.debug("[ProcessManager] Success to convert. (fileName={})", filePath);
        } catch (Exception e) {
            logger.warn("ProcessManager.runProcess.Exception", e);
        } finally {
            if (process != null) {
                process.destroy();
            }

            if (stdOut != null) {
                try {
                    stdOut.close();
                } catch (IOException e) {
                    logger.warn("[ProcessManager] Fail to close the BufferReader.", e);
                }
            }
        }
    }

}
