package process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessManager {

    private static final Logger logger = LoggerFactory.getLogger(ProcessManager.class);

    public static void runProcessNoWait(String command) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);

        BufferedReader stdOut = null;
        Process process = null;
        try {
            logger.debug("[ProcessManager] PROCESS (command={})", command);
            process = processBuilder.start();

            String line;
            StringBuilder output = new StringBuilder();
            stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = stdOut.readLine()) != null) {
                output.append(line);
            }

            logger.debug("[ProcessManager] Success to process. [command={}, output={}]", command, output);
        } catch (Exception e) {
            logger.warn("ProcessManager.runProcess.Exception (command={})", command, e);
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

    public static void runProcessWait(String command) {
        BufferedReader stdOut = null;
        Process process = null;
        try {
            logger.debug("[ProcessManager] PROCESS (command={})", command);
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

            logger.debug("[ProcessManager] Success to process. (command={})", command);
        } catch (Exception e) {
            logger.warn("ProcessManager.runProcess.Exception (command={})", command, e);
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
