package org.eu.suisei.xray;

import java.util.ArrayList;
import java.util.List;

public class DashboardSection {
    public String id;
    public String title;
    public List<DashboardItem> items;

    public DashboardSection(String id, String title, List<DashboardItem> items) {
        this.id = id;
        this.title = title;
        this.items = items != null ? items : new ArrayList<>();
    }
}
