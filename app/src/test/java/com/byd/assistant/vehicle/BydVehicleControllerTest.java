package com.byd.assistant.vehicle;

import com.byd.assistant.model.*;

public class BydVehicleControllerTest {
    public static void main(String[] args) {
        testExecuteMockMode();
        testDiLinkConstants();
        testAmbientLighting();
        testTelemetryQuery();
        testFanSpeedAndRecirculation();
        testWindowVentilation();
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

    private static void testAmbientLighting() {
        BydVehicleController controller = new BydVehicleController(null, true);

        // Direct method call
        boolean ok1 = controller.setAmbientLightColor("أزرق", 80);
        if (!ok1) throw new AssertionError("setAmbientLightColor blue failed");

        boolean ok2 = controller.setAmbientLightColor("أحمر", 100);
        if (!ok2) throw new AssertionError("setAmbientLightColor red failed");

        boolean ok3 = controller.setAmbientLightColor("دافئ", 50);
        if (!ok3) throw new AssertionError("setAmbientLightColor warm failed");

        // Null context handling
        BydVehicleController nullContextController = new BydVehicleController(null);
        boolean okNull = nullContextController.setAmbientLightColor("أخضر", 60);
        if (!okNull) throw new AssertionError("setAmbientLightColor with null context should succeed safely");

        // Intent execution
        LightingIntent intent = new LightingIntent(true, "أزرق", 80);
        if (!intent.isEnabled()) throw new AssertionError("LightingIntent isEnabled failed");
        if (!"أزرق".equals(intent.getColorName())) throw new AssertionError("LightingIntent getColorName failed");
        if (intent.getBrightness() != 80) throw new AssertionError("LightingIntent getBrightness failed");

        boolean intentOk = controller.execute(intent);
        if (!intentOk) throw new AssertionError("controller.execute(LightingIntent) failed");
    }

    private static void testTelemetryQuery() {
        BydVehicleController controller = new BydVehicleController(null, true);

        TelemetryData telemetry = controller.getVehicleTelemetry();
        if (telemetry == null) throw new AssertionError("getVehicleTelemetry returned null");

        if (telemetry.batterySoc < 0 || telemetry.batterySoc > 100) {
            throw new AssertionError("Invalid battery SoC: " + telemetry.batterySoc);
        }
        if (telemetry.rangeKm <= 0) {
            throw new AssertionError("Invalid rangeKm: " + telemetry.rangeKm);
        }
        if (telemetry.fuelLevel < 0 || telemetry.fuelLevel > 100) {
            throw new AssertionError("Invalid fuelLevel: " + telemetry.fuelLevel);
        }
        if (telemetry.diagnosticMessage == null || telemetry.diagnosticMessage.isEmpty()) {
            throw new AssertionError("diagnosticMessage cannot be empty");
        }

        String summary = telemetry.getSummary();
        if (summary == null || !summary.contains(String.valueOf(telemetry.batterySoc))) {
            throw new AssertionError("telemetry.getSummary() invalid: " + summary);
        }

        // Null context test
        BydVehicleController nullContextController = new BydVehicleController(null);
        TelemetryData nullTel = nullContextController.getVehicleTelemetry();
        if (nullTel == null) throw new AssertionError("getVehicleTelemetry with null context returned null");
    }

    private static void testFanSpeedAndRecirculation() {
        BydVehicleController controller = new BydVehicleController(null, true);

        boolean fan1 = controller.setFanSpeed(3);
        if (!fan1) throw new AssertionError("setFanSpeed(3) failed");

        boolean fanMax = controller.setFanSpeed(7);
        if (!fanMax) throw new AssertionError("setFanSpeed(7) failed");

        boolean recircIn = controller.setRecirculation(true);
        if (!recircIn) throw new AssertionError("setRecirculation(true) failed");

        boolean recircOut = controller.setRecirculation(false);
        if (!recircOut) throw new AssertionError("setRecirculation(false) failed");

        // Null context safety
        BydVehicleController nullContextController = new BydVehicleController(null);
        if (!nullContextController.setFanSpeed(4)) throw new AssertionError("setFanSpeed with null context failed");
        if (!nullContextController.setRecirculation(true)) throw new AssertionError("setRecirculation with null context failed");
    }

    private static void testWindowVentilation() {
        BydVehicleController controller = new BydVehicleController(null, true);

        boolean vent = controller.ventWindows();
        if (!vent) throw new AssertionError("ventWindows failed");

        // Null context safety
        BydVehicleController nullContextController = new BydVehicleController(null);
        if (!nullContextController.ventWindows()) throw new AssertionError("ventWindows with null context failed");
    }

    private static void testDiLinkConstants() {
        if (!DiLinkConstants.ACTION_ROTATE_SCREEN.equals("com.byd.intent.action.ROTATION_SCREEN")) {
            throw new AssertionError("Wrong DiLink rotate action");
        }
        if (!DiLinkConstants.SERVICE_BYD_AIR.equals("byd_auto_air")) {
            throw new AssertionError("Wrong BYD Air service name");
        }

        // Ambient lighting constants
        if (!DiLinkConstants.ACTION_SET_AMBIENT_LIGHT.equals("com.byd.intent.action.SET_AMBIENT_LIGHT")) {
            throw new AssertionError("Wrong ACTION_SET_AMBIENT_LIGHT");
        }
        if (!DiLinkConstants.EXTRA_LIGHT_COLOR.equals("color")) {
            throw new AssertionError("Wrong EXTRA_LIGHT_COLOR");
        }
        if (!DiLinkConstants.EXTRA_LIGHT_BRIGHTNESS.equals("brightness")) {
            throw new AssertionError("Wrong EXTRA_LIGHT_BRIGHTNESS");
        }
        if (!DiLinkConstants.EXTRA_LIGHT_ENABLED.equals("enabled")) {
            throw new AssertionError("Wrong EXTRA_LIGHT_ENABLED");
        }

        // HVAC extras
        if (!DiLinkConstants.EXTRA_AC_RECIRCULATION.equals("recirculation")) {
            throw new AssertionError("Wrong EXTRA_AC_RECIRCULATION");
        }
        if (!DiLinkConstants.EXTRA_AC_FAN_SPEED.equals("fan_speed")) {
            throw new AssertionError("Wrong EXTRA_AC_FAN_SPEED");
        }

        // Window ventilation
        if (!DiLinkConstants.ACTION_WINDOW_VENT.equals("com.byd.intent.action.WINDOW_VENT")) {
            throw new AssertionError("Wrong ACTION_WINDOW_VENT");
        }
        if (!DiLinkConstants.MODE_VENTILATION.equals("ventilation")) {
            throw new AssertionError("Wrong MODE_VENTILATION");
        }
    }
}
