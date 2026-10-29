package org.eu.suisei.xray;

import java.util.ArrayList;
import java.util.List;

public class DashboardSection {
    public String id;
    public String title;
    // Optional resource key for built-in names; custom names have no key.
    public String titleKey;

    public String getDisplayTitle(android.content.Context context) {
        return DashboardConfigManager.localizedTitle(context, titleKey, title);
    }

    public List<DashboardItem> items;

    public DashboardSection(String id, String title, List<DashboardItem> items) {
        this.id = id;
        this.title = title;
        this.items = items != null ? items : new ArrayList<>();
    }
}
