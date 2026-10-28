package org.eu.suisei.xray;

public class CustomAction {
    public String id;
    public String name;
    public String command;
    public String conditionCommand;

    public CustomAction(String id, String name, String command) {
        this(id, name, command, "");
    }

    public CustomAction(String id, String name, String command, String conditionCommand) {
        this.id = id;
        this.name = name;
        this.command = command;
        this.conditionCommand = conditionCommand != null ? conditionCommand : "";
    }
}
