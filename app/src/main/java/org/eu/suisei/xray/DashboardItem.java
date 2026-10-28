package org.eu.suisei.xray;

public class DashboardItem {
    public static final String TYPE_SWITCH = "SWITCH";
    public static final String TYPE_BUTTON = "BUTTON";

    public String id;
    public String title;
    public String type; // SWITCH or BUTTON
    public String icon; // icon identifier, e.g. "shield", "bug"
    public String onCommand;
    public String offCommand;
    public String stateCheckCommand;
    public String summaryCommand;
    public String disableConditionCommand;
    public boolean isCore;

    public DashboardItem(String id, String title, String type, String icon,
                         String onCommand, String offCommand, String stateCheckCommand,
                         String summaryCommand, String disableConditionCommand, boolean isCore) {
        this.id = id;
        this.title = title;
        this.type = type != null ? type : TYPE_BUTTON;
        this.icon = icon != null ? icon : "shield";
        this.onCommand = onCommand != null ? onCommand : "";
        this.offCommand = offCommand != null ? offCommand : "";
        this.stateCheckCommand = stateCheckCommand != null ? stateCheckCommand : "";
        this.summaryCommand = summaryCommand != null ? summaryCommand : "";
        this.disableConditionCommand = disableConditionCommand != null ? disableConditionCommand : "";
        this.isCore = isCore;
    }
}
