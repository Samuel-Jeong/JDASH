package dash;

import cam.RemoteCameraService;
import org.junit.Test;

public class RemoteCameraTest {

    @Test
    public void test() {
        RemoteCameraService remoteCameraService = new RemoteCameraService();
        remoteCameraService.start();
    }
}
