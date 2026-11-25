package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Chart;
import com.lixui.widget.control.DonutChart;
import com.lixui.widget.container.Table;
import com.lixui.widget.container.TreeView;
import com.lixui.widget.container.VirtualList;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.layout.Grid;
import com.lixui.widget.layout.GridTrack;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.List;

/**
 * Builds the dashboard scene with stat cards, an order table, activity feed, tree and charts.
 */
final class DashboardScene {
    private static final Color BLUE = Color.hex("#4F8EF7");
    private static final Color GREEN = Color.hex("#2E8B57");
    private static final Color ORANGE = Color.hex("#C26A17");
    private static final Color PURPLE = Color.hex("#7C4DDB");
    private static final Color MINT = Color.hex("#9BD1C1");

    private static final List<Activity> ACTIVITIES = List.of(
            new Activity("Order #1042 left the warehouse", "2 min", GREEN),
            new Activity("Invoice #2291 was paid", "6 min", BLUE),
            new Activity("Restock request awaiting approval", "11 min", ORANGE),
            new Activity("Weekly revenue report generated", "18 min", PURPLE),
            new Activity("New customer signed up", "24 min", MINT),
            new Activity("Refund #881 was processed", "31 min", ORANGE),
            new Activity("Low stock alert for item A-17", "37 min", Color.hex("#D9534F")),
            new Activity("Warehouse sync completed", "44 min", GREEN));

    private DashboardScene() {
    }

    static ShowcasePage create(ShowcaseKit kit) {
        Grid stats = new Grid();
        stats.setGap(16);
        stats.setColumns(GridTrack.proportional(1), GridTrack.proportional(1),
                GridTrack.proportional(1), GridTrack.proportional(1));
        stats.add(statCard(kit, "Net revenue", "$48,290", "+12.4%", GREEN));
        stats.add(statCard(kit, "New orders", "1,284", "+8.1%", BLUE));
        stats.add(statCard(kit, "Returning", "62.5%", "+3.2%", PURPLE));
        stats.add(statCard(kit, "Avg. order", "$37.60", "-1.4%", ORANGE));

        ShowcaseCard tableContent = kit.card();
        tableContent.with(kit.heading("Recent orders"))
                .with(kit.muted("Fulfilment queue · updated 2 minutes ago"))
                .with(createTable(kit));
        Widget tableCard = ShowcaseKit.fixHeight(tableContent, 311);

        ShowcaseCard activityContent = kit.card().padding(12);
        activityContent.body().setGap(8);
        activityContent.with(kit.heading("Live activity")).with(createActivityList(kit));
        Widget activityCard = ShowcaseKit.fixHeight(activityContent, 156);

        ShowcaseCard donutContent = kit.card().padding(14);
        donutContent.with(kit.heading("Revenue mix")).with(createDonut());
        Widget donutCard = ShowcaseKit.fixWidth(donutContent, 260);

        ShowcaseCard sparkContent = kit.card().padding(14);
        sparkContent.body().setGap(6);
        sparkContent.with(kit.heading("Active users"))
                .with(kit.value("8,412"))
                .with(kit.badge("+6.2% this week", GREEN))
                .with(createSparkline());
        Widget sparkCard = ShowcaseKit.fixWidth(sparkContent, 200);

        HBox chartRow = new HBox();
        chartRow.setGap(16);
        chartRow.add(donutCard).add(sparkCard);
        Widget charts = ShowcaseKit.fixHeight(chartRow, 228);

        ShowcaseCard treeContent = kit.card();
        treeContent.with(kit.heading("Workspace tree")).with(createTree());
        Widget treeCard = ShowcaseKit.fixHeight(treeContent, 228);

        VBox left = new VBox();
        left.setGap(16);
        left.add(tableCard).add(activityCard);

        VBox right = new VBox();
        right.setGap(16);
        right.add(charts).add(treeCard);

        HBox columns = new HBox();
        columns.setGap(16);
        columns.add(ShowcaseKit.fixWidth(left, 740)).add(ShowcaseKit.fixWidth(right, 476));

        VBox body = new VBox();
        body.setGap(16);
        body.add(stats).add(columns);
        columns.setGrow(1);
        return new ShowcasePage(kit, 1, 4, "Operations dashboard",
                "A compact control room built from cards, tables, lists and charts.",
                Theme.light(), Theme.dark(), false, body);
    }

    private static Widget statCard(ShowcaseKit kit, String caption, String value,
                                   String delta, Color accent) {
        ShowcaseCard card = kit.card().padding(14).gap(6);
        card.with(kit.muted(caption)).with(kit.value(value)).with(kit.badge(delta, accent));
        card.setGrow(1);
        return ShowcaseKit.fixHeight(card, 116);
    }

    private static Table createTable(ShowcaseKit kit) {
        Texture tableIcon = kit.manager().context().acquireIcon("table.svg", 32);
        Texture userIcon = kit.manager().context().acquireIcon("user.svg", 32);
        Texture truckIcon = kit.manager().context().acquireIcon("truck.svg", 32);
        Texture coinIcon = kit.manager().context().acquireIcon("coin.svg", 32);
        Table table = new Table();
        table.setFillWidth(true);
        table.setRowHeight(30);
        table.setHeaderHeight(30);
        table.setSelectionMode(Table.SelectionMode.SINGLE);
        table.setColumnResizingEnabled(false);
        Table.Column order = table.addColumn("Order", 110);
        order.setIcon(tableIcon);
        Table.Column customer = table.addColumn("Customer", 230);
        customer.setIcon(userIcon);
        table.addColumn("Status", 130);
        Table.Column total = table.addColumn("Total", 110);
        total.setIcon(coinIcon);

        Table.Row first = table.addRow(new Table.Row(
                Table.Cell.of("#1042", truckIcon, 1042),
                Table.Cell.of("Aria Snow"),
                Table.Cell.of("Shipped"),
                Table.Cell.of("$128.00", null, 128f)));
        table.addRow(new Table.Row(
                Table.Cell.of("#1041", truckIcon, 1041),
                Table.Cell.of("Bran Voss"),
                Table.Cell.of("Packing"),
                Table.Cell.of("$64.50", null, 64.5f)));
        table.addRow(new Table.Row(
                Table.Cell.of("#1040", truckIcon, 1040),
                Table.Cell.of("Cyra Idris"),
                Table.Cell.of("Shipped"),
                Table.Cell.of("$212.00", null, 212f)));
        table.addRow(new Table.Row(
                Table.Cell.of("#1039", truckIcon, 1039),
                Table.Cell.of("Dain Holt"),
                Table.Cell.of("Delivered"),
                Table.Cell.of("$39.90", null, 39.9f)));
        table.addRow(new Table.Row(
                Table.Cell.of("#1038", truckIcon, 1038),
                Table.Cell.of("Elara Wynn"),
                Table.Cell.of("Delivered"),
                Table.Cell.of("$96.00", null, 96f)));
        table.addRow(new Table.Row(
                Table.Cell.of("#1037", truckIcon, 1037),
                Table.Cell.of("Fen Marr"),
                Table.Cell.of("Packing"),
                Table.Cell.of("$18.25", null, 18.25f)));
        table.setSort(3, Table.SortOrder.DESCENDING);
        table.selectRow(first);
        return table;
    }

    private static VirtualList createActivityList(ShowcaseKit kit) {
        VirtualList list = new VirtualList();
        list.setRowHeight(26);
        list.setItemCount(64);
        list.setSelectionMode(VirtualList.SelectionMode.SINGLE);
        list.setSelectedIndices(List.of(0));
        list.setGrow(1);
        list.setRowRenderer((index, bounds, state, renderer) ->
                drawActivityRow(kit, list, ACTIVITIES.get(index % ACTIVITIES.size()),
                        bounds, state, renderer));
        return list;
    }

    private static void drawActivityRow(ShowcaseKit kit, VirtualList list, Activity activity,
                                        Rect bounds, VirtualList.RowState state,
                                        RenderManager renderer) {
        Theme theme = list.context().theme();
        Font font = kit.font(FontStyle.REGULAR);
        if (font == null) {
            return;
        }
        float center = bounds.y() + bounds.height() / 2;
        if (state.selected()) {
            renderer.roundedRect(new Rect(bounds.x() + 2, bounds.y() + 2,
                    bounds.width() - 4, bounds.height() - 4), 5,
                    theme.primary().withAlpha(0.18f));
        }
        renderer.roundedRect(new Rect(bounds.x() + 10, center - 3, 6, 6), 3, activity.accent());
        float timeWidth = 52;
        float timeX = bounds.right() - timeWidth - 10;
        float messageWidth = Math.max(0, timeX - bounds.x() - 36);
        renderer.text(font, activity.label(), bounds.x() + 24, center, theme.text(),
                new TextStyle(messageWidth, 0, 1, TextOverflow.ELLIPSIS,
                        HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        renderer.text(font, activity.time(), timeX, center, theme.textMuted(),
                new TextStyle(timeWidth, 0, 1, TextOverflow.ELLIPSIS,
                        HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        renderer.rect(new Rect(bounds.x() + 10, bounds.bottom() - 1,
                Math.max(0, bounds.width() - 20), 1), theme.border().withAlpha(0.4f));
    }

    private static DonutChart createDonut() {
        DonutChart chart = new DonutChart();
        chart.setDiameter(118);
        chart.setInnerRadiusRatio(0.62f);
        chart.setCenterLabel("$48k");
        chart.setShowLegend(true);
        chart.setAnimationDuration(0.6f);
        chart.addSegment("Online", 18400, BLUE);
        chart.addSegment("Retail", 15200, GREEN);
        chart.addSegment("Partners", 9800, ORANGE);
        chart.addSegment("Other", 4890, PURPLE);
        chart.setGrow(1);
        return chart;
    }

    private static Chart createSparkline() {
        Chart chart = new Chart();
        chart.setMode(Chart.Mode.SPARKLINE);
        chart.addSeries("Active users", List.of(18f, 24f, 21f, 29f, 26f, 33f, 31f, 38f, 35f,
                42f, 39f, 46f), GREEN);
        chart.setAnimationDuration(0.6f);
        chart.setMinimumSize(0, 46);
        chart.setMaximumSize(10_000, 86);
        chart.setGrow(1);
        return chart;
    }

    private static TreeView createTree() {
        TreeView tree = new TreeView();
        tree.setRowHeight(26);
        tree.setIndent(16);
        TreeView.Node root = tree.addRoot("Aurora workspace", true);
        root.setIcon(ImageSource.icon("folder.svg"));
        TreeView.Node api = root.addChild("API contract");
        api.setIcon(ImageSource.icon("file.svg"));
        TreeView.Node data = root.addChild("Data backfill");
        data.setIcon(ImageSource.icon("folder.svg"));
        data.addChild("Accounts").setIcon(ImageSource.icon("file.svg"));
        TreeView.Node orders = data.addChild("Orders");
        orders.setIcon(ImageSource.icon("file.svg"));
        TreeView.Node console = root.addChild("Web console");
        console.setIcon(ImageSource.icon("file.svg"));
        TreeView.Node legacy = tree.addRoot("Legacy cleanup");
        legacy.setIcon(ImageSource.icon("folder.svg"));
        legacy.addChild("Archive").setIcon(ImageSource.icon("file.svg"));
        tree.select(console);
        tree.setGrow(1);
        return tree;
    }

    private record Activity(String label, String time, Color accent) {
    }
}
