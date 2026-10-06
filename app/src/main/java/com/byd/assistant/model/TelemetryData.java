package com.byd.assistant.model;

import java.util.Objects;

/**
 * Vehicle telemetry snapshot containing battery SoC, estimated range,
 * fuel percentage, and diagnostic trouble code (DTC) health status.
 */
public class TelemetryData {
    public final int batterySoc;
    public final int rangeKm;
    public final int fuelLevel;
    public final boolean hasDtcFault;
    public final String diagnosticMessage;

    /**
     * Default constructor initialized with nominal operating values.
     */
    public TelemetryData() {
        this(85, 420, 75, false, "جميع الأنظمة تعمل بكفاءة");
    }

    public TelemetryData(int batterySoc, int rangeKm, int fuelLevel, boolean hasDtcFault, String diagnosticMessage) {
        this.batterySoc = Math.max(0, Math.min(100, batterySoc));
        this.rangeKm = Math.max(0, rangeKm);
        this.fuelLevel = Math.max(0, Math.min(100, fuelLevel));
        this.hasDtcFault = hasDtcFault;
        this.diagnosticMessage = (diagnosticMessage != null && !diagnosticMessage.trim().isEmpty())
                ? diagnosticMessage.trim()
                : (hasDtcFault ? "توجد تنبيهات فنية في فحص الأنظمة" : "جميع الأنظمة تعمل بكفاءة");
    }

    public int getBatterySoc() {
        return batterySoc;
    }

    public int getRangeKm() {
        return rangeKm;
    }

    public int getFuelLevel() {
        return fuelLevel;
    }

    public boolean hasDtcFault() {
        return hasDtcFault;
    }

    public String getDiagnosticMessage() {
        return diagnosticMessage;
    }

    public boolean isHealthy() {
        return !hasDtcFault;
    }

    /**
     * Provides concise Arabic summary description suitable for voice readout.
     */
    public String getArabicSummary() {
        return "نسبة شحن البطارية " + batterySoc + " بالمئة، المدى المتبقي " + rangeKm +
                " كيلومتر، ومستوى الوقود " + fuelLevel + " بالمئة. " + diagnosticMessage;
    }

    /**
     * Standard human-readable summary.
     */
    public String getSummary() {
        return "Battery: " + batterySoc + "%, Range: " + rangeKm + "km, Fuel: " + fuelLevel +
                "%, Status: " + diagnosticMessage;
    }

    @Override
    public String toString() {
        return "TelemetryData{" +
                "batterySoc=" + batterySoc +
                ", rangeKm=" + rangeKm +
                ", fuelLevel=" + fuelLevel +
                ", hasDtcFault=" + hasDtcFault +
                ", diagnosticMessage='" + diagnosticMessage + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TelemetryData that = (TelemetryData) o;
        return batterySoc == that.batterySoc &&
                rangeKm == that.rangeKm &&
                fuelLevel == that.fuelLevel &&
                hasDtcFault == that.hasDtcFault &&
                Objects.equals(diagnosticMessage, that.diagnosticMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batterySoc, rangeKm, fuelLevel, hasDtcFault, diagnosticMessage);
    }
}
