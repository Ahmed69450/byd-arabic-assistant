package com.byd.assistant.vehicle;

import com.byd.assistant.model.*;

public class BydVehicleControllerTest {
    public static void main(String[] args) {
        testExecuteMockMode();
        testDiLinkConstants();
        System.out.println("ALL_VEHICLE_CONTROLLER_TESTS_PASSED");
    }

    private static void testExecuteMockMode() {
        BydVehicleController controller = new BydVehicleController(null, true);

        boolean s1 = controller.execute(new VehicleIntent.ScreenRotate(ScreenOrientation.TOGGLE));
        if (!s1) throw new AssertionError("ScreenRotate failed");

        boolean s2 = controller.execute(new VehicleIntent.Climate(true, 22));
        if (!s2) throw new AssertionError("Climate failed");

        boolean s3 = controller.execute(new VehicleIntent.Window(true, WindowTarget.ALL));
        if (!s3) throw new AssertionError("Window failed");

        boolean s4 = controller.execute(new VehicleIntent.Volume(VolumeAction.UP));
        if (!s4) throw new AssertionError("Volume failed");

        boolean s5 = controller.execute(new VehicleIntent.Navigation());
        if (!s5) throw new AssertionError("Navigation failed");

        boolean s6 = controller.execute(new VehicleIntent.Media(MediaAction.NEXT));
        if (!s6) throw new AssertionError("Media failed");

        boolean s7 = controller.execute(new VehicleIntent.Unknown("foo"));
        if (s7) throw new AssertionError("Unknown should return false");
    }

    private static void testDiLinkConstants() {
        if (!DiLinkConstants.ACTION_ROTATE_SCREEN.equals("com.byd.intent.action.ROTATION_SCREEN")) {
            throw new AssertionError("Wrong DiLink rotate action");
        }
        if (!DiLinkConstants.SERVICE_BYD_AIR.equals("byd_auto_air")) {
            throw new AssertionError("Wrong BYD Air service name");
        }
    }
}
