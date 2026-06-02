import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
// import javax.swing.SwingConstants;

public class ProfitStatisticsPanel extends JPanel {
    private static final String SQLSERVER_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    private static final String STOCK_ACTION_IN = "Nhập";
    private static final String STOCK_ACTION_OUT = "Xuất";

    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.###");
    private static final int RECENT_MONTH_LIMIT = 3;

    private final JComboBox<GoodsOption> goodsSelector = new JComboBox<>();
    private final FlowLineChartPanel flowChartPanel = new FlowLineChartPanel();
    private final TrendBarChartPanel trendChartPanel = new TrendBarChartPanel();
    private final MonthlyProfitLineChartPanel monthlyProfitChartPanel = new MonthlyProfitLineChartPanel();
    private final QuarterRevenueCostChartPanel quarterRevenueCostChartPanel = new QuarterRevenueCostChartPanel();
    private final JComboBox<String> trendGoodsSelector = new JComboBox<>();
    private final JLabel dailyProfitValueLabel = new JLabel("0 triệu VND");
    private final JLabel dailyOrdersValueLabel = new JLabel("0 đơn hàng đã xử lí hôm nay");
    private final JLabel monthlyProfitValueLabel = new JLabel("Tổng lợi nhuận tháng: 0 triệu VND");
    private final JLabel quarterRevenueValueLabel = new JLabel("Tổng doanh thu: 0 triệu VND");
    private final JLabel quarterCostValueLabel = new JLabel("Tổng chi phí: 0 triệu VND");
    private final JLabel quarterProfitValueLabel = new JLabel("Tổng lợi nhuận: 0 triệu VND");
    private final JPanel contentPanel = new JPanel();
    private final JScrollPane contentScrollPane;

    private JPanel profitSummarySectionPanel;
    private JPanel dailyProfitSectionPanel;
    private JPanel monthlyProfitSectionPanel;
    private JPanel quarterRevenueCostSectionPanel;
    private JPanel flowSectionPanel;
    private JPanel trendSectionPanel;
    private JScrollPane trendChartScrollPane;
    private boolean updatingGoodsModel;
    private boolean updatingTrendSelector;

    public ProfitStatisticsPanel(String managerId, String managerName) {
        setLayout(new BorderLayout(10, 10));
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel quickMenu = buildQuickMenu();
        add(quickMenu, BorderLayout.NORTH);

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        profitSummarySectionPanel = buildProfitSummarySection();
        quarterRevenueCostSectionPanel = buildQuarterRevenueCostSection();
        flowSectionPanel = buildFlowSection();
        trendSectionPanel = buildTrendSection();

        contentPanel.add(profitSummarySectionPanel);
        contentPanel.add(Box.createVerticalStrut(16));
        contentPanel.add(quarterRevenueCostSectionPanel);
        contentPanel.add(Box.createVerticalStrut(16));
        contentPanel.add(flowSectionPanel);
        contentPanel.add(Box.createVerticalStrut(16));
        contentPanel.add(trendSectionPanel);
        contentPanel.add(Box.createVerticalStrut(8));

        contentScrollPane = new JScrollPane(
            contentPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        contentScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        contentScrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(contentScrollPane, BorderLayout.CENTER);

        goodsSelector.addActionListener(e -> {
            if (!updatingGoodsModel) {
                loadFlowChartData();
            }
        });

        initializeTrendInteractions();
        refreshData();
    }

    public void refreshData() {
        try {
            loadProfitSummaryData();
            loadQuarterRevenueCostData();
            loadGoodsSelectorData();
            loadFlowChartData();
            loadTrendChartData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể tải dữ liệu Thống kê lợi nhuận. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
            flowChartPanel.setData(Collections.emptyList());
            trendChartPanel.setData(Collections.emptyList());
            monthlyProfitChartPanel.setData(Collections.emptyList());
            quarterRevenueCostChartPanel.setData(Collections.emptyList());
            setProfitSummary(BigDecimal.ZERO, 0, BigDecimal.ZERO);
            setQuarterSummary(BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private JPanel buildQuickMenu() {
        JPanel quickMenu = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quickMenu.setOpaque(false);

        JLabel quickLabel = new JLabel("Truy cập nhanh:");
        quickLabel.setFont(UiTheme.font(Font.BOLD, 13));
        quickLabel.setForeground(UiTheme.TEXT);
        quickMenu.add(quickLabel);

        quickMenu.add(createSectionButton("Lợi nhuận", () -> scrollToSection(profitSummarySectionPanel)));
        quickMenu.add(createSectionButton("Doanh thu & chi phí", () -> scrollToSection(quarterRevenueCostSectionPanel)));
        quickMenu.add(createSectionButton("Biểu đồ lưu lượng", () -> scrollToSection(flowSectionPanel)));
        quickMenu.add(createSectionButton("Biểu đồ xu hướng", () -> scrollToSection(trendSectionPanel)));
        quickMenu.add(createSectionButton("Làm mới dữ liệu", this::refreshData));

        return quickMenu;
    }

    private JButton createSectionButton(String text, Runnable action) {
        JButton button = new JButton(text);
        UiTheme.styleSecondaryButton(button);
        button.addActionListener(e -> action.run());
        return button;
    }

    private JPanel buildProfitSummarySection() {
        JPanel row = new JPanel(new GridLayout(1, 2, 16, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        row.setPreferredSize(new Dimension(900, 320));

        dailyProfitSectionPanel = createSectionContainer("Lợi nhuận trong ngày");
        dailyProfitSectionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        dailyProfitSectionPanel.setPreferredSize(new Dimension(430, 300));

        JPanel dailyBody = new JPanel();
        dailyBody.setOpaque(false);
        dailyBody.setLayout(new BoxLayout(dailyBody, BoxLayout.Y_AXIS));
        dailyBody.setBorder(BorderFactory.createEmptyBorder(44, 18, 18, 18));

        dailyProfitValueLabel.setFont(UiTheme.font(Font.BOLD, 34));
        dailyProfitValueLabel.setForeground(UiTheme.TEAL_DARK);
        dailyProfitValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dailyBody.add(dailyProfitValueLabel);
        dailyBody.add(Box.createVerticalStrut(12));

        dailyOrdersValueLabel.setFont(UiTheme.font(Font.PLAIN, 15));
        dailyOrdersValueLabel.setForeground(UiTheme.MUTED_TEXT);
        dailyOrdersValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dailyBody.add(dailyOrdersValueLabel);
        dailyBody.add(Box.createVerticalGlue());

        JLabel formulaLabel = new JLabel("Công thức: (Giá bán - Giá nhập) x số lượng xuất");
        formulaLabel.setFont(UiTheme.font(Font.PLAIN, 13));
        formulaLabel.setForeground(new Color(95, 104, 124));
        formulaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dailyBody.add(formulaLabel);

        dailyProfitSectionPanel.add(dailyBody, BorderLayout.CENTER);

        monthlyProfitSectionPanel = createSectionContainer("Lợi nhuận theo tháng");
        monthlyProfitSectionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        monthlyProfitSectionPanel.setPreferredSize(new Dimension(430, 300));

        monthlyProfitValueLabel.setFont(UiTheme.font(Font.BOLD, 15));
        monthlyProfitValueLabel.setForeground(UiTheme.TEXT);
        monthlyProfitValueLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        monthlyProfitSectionPanel.add(monthlyProfitValueLabel, BorderLayout.NORTH);
        monthlyProfitSectionPanel.add(monthlyProfitChartPanel, BorderLayout.CENTER);

        row.add(dailyProfitSectionPanel);
        row.add(monthlyProfitSectionPanel);
        return row;
    }

    private JPanel buildQuarterRevenueCostSection() {
        JPanel section = createSectionContainer("Doanh thu và chi phí theo quý");
        section.setPreferredSize(new Dimension(900, 430));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 480));

        JLabel note = new JLabel("Biểu đồ cột kép theo 3 tháng trong quý hiện tại.");
        note.setFont(UiTheme.font(Font.PLAIN, 13));
        note.setForeground(UiTheme.MUTED_TEXT);
        note.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        section.add(note, BorderLayout.NORTH);

        section.add(quarterRevenueCostChartPanel, BorderLayout.CENTER);

        JPanel summaryPanel = new JPanel(new GridLayout(3, 1, 0, 4));
        summaryPanel.setOpaque(false);
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        styleQuarterSummaryLabel(quarterRevenueValueLabel, UiTheme.TEAL_DARK);
        styleQuarterSummaryLabel(quarterCostValueLabel, new Color(146, 76, 27));
        styleQuarterSummaryLabel(quarterProfitValueLabel, UiTheme.TEXT);
        summaryPanel.add(quarterRevenueValueLabel);
        summaryPanel.add(quarterCostValueLabel);
        summaryPanel.add(quarterProfitValueLabel);
        section.add(summaryPanel, BorderLayout.SOUTH);

        return section;
    }

    private void styleQuarterSummaryLabel(JLabel label, Color color) {
        label.setFont(UiTheme.font(Font.BOLD, 14));
        label.setForeground(color);
    }

    private JPanel buildFlowSection() {
        JPanel section = createSectionContainer("Biểu đồ lưu lượng");

        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JLabel note = new JLabel("Dữ liệu biểu đồ lưu lượng chỉ hiển thị trong 3 tháng gần nhất.");
        note.setFont(UiTheme.font(Font.PLAIN, 13));
        note.setForeground(UiTheme.MUTED_TEXT);
        note.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(note);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setOpaque(false);
        filterBar.add(new JLabel("Chọn hàng hóa:"));
        goodsSelector.setPreferredSize(new Dimension(260, 28));
        UiTheme.styleField(goodsSelector);
        filterBar.add(goodsSelector);
        filterBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(filterBar);

        section.add(topPanel, BorderLayout.NORTH);

        JScrollPane chartScroll = new JScrollPane(
            flowChartPanel,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        chartScroll.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        chartScroll.getViewport().setBackground(UiTheme.SURFACE);
        section.add(chartScroll, BorderLayout.CENTER);

        return section;
    }

    private JPanel buildTrendSection() {
        JPanel section = createSectionContainer("Biểu đồ xu hướng");

        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JLabel desc = new JLabel("Số lượng phiếu bán ra (phiếu xuất) của tất cả hàng hóa trong 3 tháng gần nhất");
        desc.setFont(UiTheme.font(Font.PLAIN, 13));
        desc.setForeground(UiTheme.MUTED_TEXT);
        desc.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(desc);

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBar.setOpaque(false);
        searchBar.add(new JLabel("Chọn hàng hóa:"));
        trendGoodsSelector.setPreferredSize(new Dimension(320, 28));
        UiTheme.styleField(trendGoodsSelector);
        trendGoodsSelector.setToolTipText("Chọn hàng hóa để tô sáng cột tương ứng");
        searchBar.add(trendGoodsSelector);
        searchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(searchBar);

        section.add(topPanel, BorderLayout.NORTH);

        trendChartScrollPane = new JScrollPane(
            trendChartPanel,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        trendChartScrollPane.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        trendChartScrollPane.getViewport().setBackground(UiTheme.SURFACE);
        section.add(trendChartScrollPane, BorderLayout.CENTER);

        return section;
    }

    private void initializeTrendInteractions() {
        trendGoodsSelector.addActionListener(e -> applyTrendSelection());
        trendChartPanel.setSelectionListener(this::syncTrendSelector);
    }

    private void applyTrendSelection() {
        if (updatingTrendSelector) {
            return;
        }
        TrendItem matched;
        if (trendGoodsSelector.getSelectedIndex() <= 0) {
            matched = trendChartPanel.clearSelection();
        } else {
            String selectedName = (String) trendGoodsSelector.getSelectedItem();
            matched = trendChartPanel.selectByName(selectedName);
        }

        if (matched != null) {
            trendGoodsSelector.setToolTipText(matched.name + " - Số lượng bán: " + matched.value);
            scrollToSelectedTrendBar();
        } else {
            trendGoodsSelector.setToolTipText("Chọn hàng hóa để tô sáng cột tương ứng");
        }
    }

    private void syncTrendSelector(TrendItem item) {
        updatingTrendSelector = true;
        if (item == null) {
            trendGoodsSelector.setSelectedIndex(0);
        } else {
            trendGoodsSelector.setSelectedItem(item.name);
            if (trendGoodsSelector.getSelectedIndex() < 0) {
                trendGoodsSelector.setSelectedIndex(0);
            }
        }
        updatingTrendSelector = false;

        if (item != null) {
            trendGoodsSelector.setToolTipText(item.name + " - Số lượng bán: " + item.value);
            scrollToSelectedTrendBar();
        } else {
            trendGoodsSelector.setToolTipText("Chọn hàng hóa để tô sáng cột tương ứng");
        }
    }

    private void scrollToSelectedTrendBar() {
        if (trendChartScrollPane == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Rectangle selectedBounds = trendChartPanel.getSelectedBarBounds();
            if (selectedBounds == null) {
                return;
            }
            Rectangle target = new Rectangle(
                Math.max(0, selectedBounds.x - 40),
                0,
                selectedBounds.width + 80,
                Math.max(1, trendChartPanel.getHeight())
            );
            trendChartPanel.scrollRectToVisible(target);
        });
    }
    private JPanel createSectionContainer(String title) {
        JPanel section = new JPanel(new BorderLayout(8, 8));
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setOpaque(true);
        section.setBackground(UiTheme.SURFACE);
        section.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(title),
                    BorderFactory.createEmptyBorder(8, 10, 10, 10)
                )
            )
        );
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
        section.setPreferredSize(new Dimension(900, 420));
        return section;
    }

    private void scrollToSection(JComponent section) {
        if (section == null) {
            return;
        }
        JScrollBar bar = contentScrollPane.getVerticalScrollBar();
        int target = Math.max(0, section.getY() - 8);
        bar.setValue(target);
    }

    private void loadGoodsSelectorData() throws SQLException {
        ensureSqlServerDriverLoaded();

        Integer selectedMaHang = null;
        GoodsOption current = (GoodsOption) goodsSelector.getSelectedItem();
        if (current != null) {
            selectedMaHang = current.maHang;
        }

        String sql = "SELECT MaHang, TenHang FROM Goods ORDER BY MaHang";
        DefaultComboBoxModel<GoodsOption> model = new DefaultComboBoxModel<>();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                model.addElement(new GoodsOption(rs.getInt("MaHang"), rs.getString("TenHang")));
            }
        }

        updatingGoodsModel = true;
        goodsSelector.setModel(model);
        if (selectedMaHang != null) {
            selectGoodsInCombo(selectedMaHang);
        } else if (goodsSelector.getItemCount() > 0) {
            goodsSelector.setSelectedIndex(0);
        }
        updatingGoodsModel = false;
    }

    private void loadProfitSummaryData() throws SQLException {
        ensureSqlServerDriverLoaded();

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);

        BigDecimal dailyProfit = BigDecimal.ZERO;
        int dailyOrders = 0;
        String dailySql =
            "SELECT COALESCE(SUM((COALESCE(g.GiaBan, 0) - COALESCE(g.GiaNhap, 0)) * i.SoLuong), 0) AS LoiNhuan, " +
            "       COUNT(*) AS SoDonHang " +
            "FROM InOut i " +
            "INNER JOIN Goods g ON g.MaHang = i.MaHang " +
            "WHERE i.LoaiPhieu = ? " +
            "  AND COALESCE(g.LaHangBan, 0) <> 0 " +
            "  AND CAST(i.NgayPhieu AS date) = ?";

        List<ProfitPoint> monthlyPoints = new ArrayList<>();
        BigDecimal monthlyTotal = BigDecimal.ZERO;
        String monthlySql =
            "SELECT CAST(i.NgayPhieu AS date) AS Ngay, " +
            "       COALESCE(SUM((COALESCE(g.GiaBan, 0) - COALESCE(g.GiaNhap, 0)) * i.SoLuong), 0) AS LoiNhuan " +
            "FROM InOut i " +
            "INNER JOIN Goods g ON g.MaHang = i.MaHang " +
            "WHERE i.LoaiPhieu = ? " +
            "  AND COALESCE(g.LaHangBan, 0) <> 0 " +
            "  AND i.NgayPhieu >= ? " +
            "  AND i.NgayPhieu < ? " +
            "GROUP BY CAST(i.NgayPhieu AS date) " +
            "ORDER BY Ngay";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (PreparedStatement stmt = conn.prepareStatement(dailySql)) {
                stmt.setString(1, STOCK_ACTION_OUT);
                stmt.setDate(2, Date.valueOf(today));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dailyProfit = readMoney(rs, "LoiNhuan");
                        dailyOrders = rs.getInt("SoDonHang");
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(monthlySql)) {
                stmt.setString(1, STOCK_ACTION_OUT);
                stmt.setDate(2, Date.valueOf(monthStart));
                stmt.setDate(3, Date.valueOf(nextMonthStart));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Date date = rs.getDate("Ngay");
                        BigDecimal profit = readMoney(rs, "LoiNhuan");
                        if (date != null) {
                            monthlyPoints.add(new ProfitPoint(date.toLocalDate(), profit));
                            monthlyTotal = monthlyTotal.add(profit);
                        }
                    }
                }
            }
        }

        setProfitSummary(dailyProfit, dailyOrders, monthlyTotal);
        monthlyProfitChartPanel.setData(monthlyPoints);
    }

    private void setProfitSummary(BigDecimal dailyProfit, int dailyOrders, BigDecimal monthlyTotal) {
        dailyProfitValueLabel.setText(formatMoney(dailyProfit) + " triệu VND");
        dailyOrdersValueLabel.setText(dailyOrders + " đơn hàng đã xử lí hôm nay");
        monthlyProfitValueLabel.setText("Tổng lợi nhuận tháng: " + formatMoney(monthlyTotal) + " triệu VND");
    }

    private void loadQuarterRevenueCostData() throws SQLException {
        ensureSqlServerDriverLoaded();

        LocalDate today = LocalDate.now();
        int quarterStartMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate quarterStart = LocalDate.of(today.getYear(), quarterStartMonth, 1);
        LocalDate nextQuarterStart = quarterStart.plusMonths(3);

        List<RevenueCostPoint> points = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LocalDate month = quarterStart.plusMonths(i);
            points.add(new RevenueCostPoint(month.format(MONTH_LABEL_FORMAT), BigDecimal.ZERO, BigDecimal.ZERO));
        }

        String sql =
            "SELECT YEAR(i.NgayPhieu) AS Nam, " +
            "       MONTH(i.NgayPhieu) AS Thang, " +
            "       COALESCE(SUM(COALESCE(g.GiaBan, 0) * i.SoLuong), 0) AS DoanhThu, " +
            "       COALESCE(SUM(COALESCE(g.GiaNhap, 0) * i.SoLuong), 0) AS ChiPhi " +
            "FROM InOut i " +
            "INNER JOIN Goods g ON g.MaHang = i.MaHang " +
            "WHERE i.LoaiPhieu = ? " +
            "  AND COALESCE(g.LaHangBan, 0) <> 0 " +
            "  AND i.NgayPhieu >= ? " +
            "  AND i.NgayPhieu < ? " +
            "GROUP BY YEAR(i.NgayPhieu), MONTH(i.NgayPhieu) " +
            "ORDER BY Nam, Thang";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, STOCK_ACTION_OUT);
            stmt.setDate(2, Date.valueOf(quarterStart));
            stmt.setDate(3, Date.valueOf(nextQuarterStart));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int year = rs.getInt("Nam");
                    int monthValue = rs.getInt("Thang");
                    int index = (year == today.getYear()) ? monthValue - quarterStartMonth : -1;
                    if (index >= 0 && index < points.size()) {
                        LocalDate month = LocalDate.of(year, monthValue, 1);
                        points.set(index, new RevenueCostPoint(
                            month.format(MONTH_LABEL_FORMAT),
                            readMoney(rs, "DoanhThu"),
                            readMoney(rs, "ChiPhi")
                        ));
                    }
                }
            }
        }

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (RevenueCostPoint point : points) {
            totalRevenue = totalRevenue.add(point.revenue);
            totalCost = totalCost.add(point.cost);
        }

        quarterRevenueCostChartPanel.setData(points);
        setQuarterSummary(totalRevenue, totalCost);
    }

    private void setQuarterSummary(BigDecimal totalRevenue, BigDecimal totalCost) {
        BigDecimal safeRevenue = totalRevenue == null ? BigDecimal.ZERO : totalRevenue;
        BigDecimal safeCost = totalCost == null ? BigDecimal.ZERO : totalCost;
        quarterRevenueValueLabel.setText("Tổng doanh thu: " + formatMoney(safeRevenue) + " triệu VND");
        quarterCostValueLabel.setText("Tổng chi phí: " + formatMoney(safeCost) + " triệu VND");
        quarterProfitValueLabel.setText("Tổng lợi nhuận: " + formatMoney(safeRevenue.subtract(safeCost)) + " triệu VND");
    }

    private BigDecimal readMoney(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        return MONEY_FORMAT.format(safeValue);
    }

    private void loadFlowChartData() {
        GoodsOption selected = (GoodsOption) goodsSelector.getSelectedItem();
        if (selected == null) {
            flowChartPanel.setData(Collections.emptyList());
            return;
        }

        String sql =
            "SELECT CAST(NgayPhieu AS date) AS Ngay, " +
            "       SUM(CASE " +
            "               WHEN LoaiPhieu = ? THEN SoLuong " +
            "               WHEN LoaiPhieu = ? THEN -SoLuong " +
            "               ELSE 0 " +
            "           END) AS LuuLuong " +
            "FROM InOut " +
            "WHERE MaHang = ? " +
            "  AND NgayPhieu >= ? " +
            "GROUP BY CAST(NgayPhieu AS date) " +
            "ORDER BY Ngay";

        List<FlowPoint> points = new ArrayList<>();
        try {
            ensureSqlServerDriverLoaded();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, STOCK_ACTION_IN);
                stmt.setString(2, STOCK_ACTION_OUT);
                stmt.setInt(3, selected.maHang);
                stmt.setDate(4, Date.valueOf(getRecentLimitStartDate()));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        LocalDate ngay = rs.getDate("Ngay").toLocalDate();
                        int luuLuong = rs.getInt("LuuLuong");
                        points.add(new FlowPoint(ngay, luuLuong));
                    }
                }
            }
            flowChartPanel.setData(points);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể tải dữ liệu Biểu đồ lưu lượng. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
            flowChartPanel.setData(Collections.emptyList());
        }
    }

    private void loadTrendChartData() throws SQLException {
        ensureSqlServerDriverLoaded();

        String sql =
            "SELECT g.TenHang, " +
            "       SUM(i.SoLuong) AS SoLuongBan " +
            "FROM InOut i " +
            "INNER JOIN Goods g ON g.MaHang = i.MaHang " +
            "WHERE i.LoaiPhieu = ? " +
            "  AND COALESCE(g.LaHangBan, 0) <> 0 " +
            "  AND i.NgayPhieu >= ? " +
            "GROUP BY g.MaHang, g.TenHang " +
            "HAVING SUM(i.SoLuong) > 0 " +
            "ORDER BY SoLuongBan DESC, g.TenHang";

        List<TrendItem> items = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, STOCK_ACTION_OUT);
            stmt.setDate(2, Date.valueOf(getRecentLimitStartDate()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tenHang = rs.getString("TenHang");
                    int soLuongBan = rs.getInt("SoLuongBan");
                    items.add(new TrendItem(tenHang, soLuongBan));
                }
            }
        }

        trendChartPanel.setData(items);
        updateTrendSelectorModel(items);
        applyTrendSelection();
    }

    private LocalDate getRecentLimitStartDate() {
        return LocalDate.now().minusMonths(RECENT_MONTH_LIMIT);
    }

    private void updateTrendSelectorModel(List<TrendItem> items) {
        String previouslySelected = (String) trendGoodsSelector.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("Tất cả hàng hóa");
        for (TrendItem item : items) {
            model.addElement(item.name);
        }

        updatingTrendSelector = true;
        trendGoodsSelector.setModel(model);
        if (previouslySelected != null) {
            trendGoodsSelector.setSelectedItem(previouslySelected);
            if (trendGoodsSelector.getSelectedIndex() < 0) {
                trendGoodsSelector.setSelectedIndex(0);
            }
        } else {
            trendGoodsSelector.setSelectedIndex(0);
        }
        updatingTrendSelector = false;
    }

    private void selectGoodsInCombo(int maHang) {
        for (int i = 0; i < goodsSelector.getItemCount(); i++) {
            GoodsOption option = goodsSelector.getItemAt(i);
            if (option != null && option.maHang == maHang) {
                goodsSelector.setSelectedIndex(i);
                return;
            }
        }
    }

    private void ensureSqlServerDriverLoaded() throws SQLException {
        try {
            Class.forName(SQLSERVER_DRIVER);
        } catch (ClassNotFoundException ex) {
            throw new SQLException(
                "Không tìm thấy JDBC driver SQL Server (mssql-jdbc). Hãy thêm file mssql-jdbc-*.jar vào classpath.",
                ex
            );
        }
    }

    private static class GoodsOption {
        private final int maHang;
        private final String tenHang;

        private GoodsOption(int maHang, String tenHang) {
            this.maHang = maHang;
            this.tenHang = tenHang;
        }

        @Override
        public String toString() {
            return maHang + " - " + tenHang;
        }
    }

    private static class FlowPoint {
        private final LocalDate date;
        private final int value;

        private FlowPoint(LocalDate date, int value) {
            this.date = date;
            this.value = value;
        }
    }

    private static class ProfitPoint {
        private final LocalDate date;
        private final BigDecimal value;

        private ProfitPoint(LocalDate date, BigDecimal value) {
            this.date = date;
            this.value = value == null ? BigDecimal.ZERO : value;
        }
    }

    private static class RevenueCostPoint {
        private final String label;
        private final BigDecimal revenue;
        private final BigDecimal cost;

        private RevenueCostPoint(String label, BigDecimal revenue, BigDecimal cost) {
            this.label = label;
            this.revenue = revenue == null ? BigDecimal.ZERO : revenue;
            this.cost = cost == null ? BigDecimal.ZERO : cost;
        }
    }

    private static class TrendItem {
        private final String name;
        private final int value;

        private TrendItem(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    private static class QuarterRevenueCostChartPanel extends JPanel {
        private List<RevenueCostPoint> points = Collections.emptyList();

        private QuarterRevenueCostChartPanel() {
            setOpaque(true);
            setBackground(UiTheme.SURFACE);
            setPreferredSize(new Dimension(900, 280));
            setMinimumSize(new Dimension(700, 260));
        }

        private void setData(List<RevenueCostPoint> points) {
            this.points = new ArrayList<>(points);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int left = 68;
            int right = 24;
            int top = 44;
            int bottom = 54;
            int chartWidth = Math.max(1, getWidth() - left - right);
            int chartHeight = Math.max(1, getHeight() - top - bottom);

            g2.setColor(UiTheme.SURFACE_ALT);
            g2.fillRect(left, top, chartWidth, chartHeight);

            drawLegend(g2, left, 14);

            if (points.isEmpty()) {
                g2.setColor(new Color(118, 126, 145));
                g2.drawString("Chưa có dữ liệu doanh thu và chi phí trong quý hiện tại.", left + 8, top + 24);
                g2.dispose();
                return;
            }

            double maxValue = getMaxValue();
            g2.setColor(new Color(206, 214, 230));
            for (int i = 0; i <= 4; i++) {
                int y = top + i * chartHeight / 4;
                g2.drawLine(left, y, left + chartWidth, y);
                double tickValue = maxValue - maxValue * i / 4.0;
                g2.setColor(new Color(105, 113, 131));
                g2.drawString(formatTick(tickValue), 8, y + 4);
                g2.setColor(new Color(206, 214, 230));
            }

            int groupCount = points.size();
            int groupWidth = Math.max(1, chartWidth / Math.max(1, groupCount));
            int barWidth = Math.max(22, Math.min(56, groupWidth / 5));
            int pairGap = 8;

            for (int i = 0; i < groupCount; i++) {
                RevenueCostPoint point = points.get(i);
                int groupLeft = left + i * groupWidth;
                int centerX = groupLeft + groupWidth / 2;
                int revenueHeight = mapValueToHeight(point.revenue.doubleValue(), maxValue, chartHeight);
                int costHeight = mapValueToHeight(point.cost.doubleValue(), maxValue, chartHeight);
                int revenueX = centerX - barWidth - pairGap / 2;
                int costX = centerX + pairGap / 2;
                int baseY = top + chartHeight;

                g2.setColor(UiTheme.TEAL);
                g2.fillRect(revenueX, baseY - revenueHeight, barWidth, revenueHeight);
                g2.setColor(UiTheme.TEAL_DARK);
                g2.drawRect(revenueX, baseY - revenueHeight, barWidth, revenueHeight);

                g2.setColor(new Color(199, 132, 74));
                g2.fillRect(costX, baseY - costHeight, barWidth, costHeight);
                g2.setColor(new Color(146, 76, 27));
                g2.drawRect(costX, baseY - costHeight, barWidth, costHeight);

                String label = point.label;
                int labelWidth = g2.getFontMetrics().stringWidth(label);
                g2.setColor(new Color(95, 104, 124));
                g2.drawString(label, centerX - labelWidth / 2, baseY + 24);
            }

            g2.setColor(new Color(50, 57, 73));
            g2.drawString("Đơn vị: triệu VND", 8, top - 16);
            g2.dispose();
        }

        private void drawLegend(Graphics2D g2, int x, int y) {
            g2.setColor(UiTheme.TEAL);
            g2.fillRect(x, y, 14, 14);
            g2.setColor(new Color(50, 57, 73));
            g2.drawString("Doanh thu", x + 20, y + 12);

            int secondX = x + 118;
            g2.setColor(new Color(199, 132, 74));
            g2.fillRect(secondX, y, 14, 14);
            g2.setColor(new Color(50, 57, 73));
            g2.drawString("Chi phí", secondX + 20, y + 12);
        }

        private double getMaxValue() {
            double maxValue = 0.0;
            for (RevenueCostPoint point : points) {
                maxValue = Math.max(maxValue, point.revenue.doubleValue());
                maxValue = Math.max(maxValue, point.cost.doubleValue());
            }
            return Math.max(maxValue, 1.0);
        }

        private int mapValueToHeight(double value, double maxValue, int chartHeight) {
            int rawHeight = (int) Math.round(Math.max(0.0, value) / maxValue * chartHeight);
            return Math.max(value > 0.0 ? 2 : 0, rawHeight);
        }

        private String formatTick(double value) {
            if (Math.abs(value) >= 1000.0) {
                return String.valueOf(Math.round(value));
            }
            if (Math.abs(value) >= 10.0) {
                return String.format(Locale.ROOT, "%.0f", value);
            }
            return String.format(Locale.ROOT, "%.1f", value);
        }
    }

    private static class FlowLineChartPanel extends JPanel {
        private List<FlowPoint> points = Collections.emptyList();

        private FlowLineChartPanel() {
            setOpaque(true);
            setBackground(UiTheme.SURFACE);
            setPreferredSize(new Dimension(900, 290));
            setMinimumSize(new Dimension(700, 280));
        }

        private void setData(List<FlowPoint> points) {
            this.points = new ArrayList<>(points);
            int preferredWidth = Math.max(900, 140 + this.points.size() * 56);
            setPreferredSize(new Dimension(preferredWidth, 290));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int left = 58;
            int right = 20;
            int top = 35;
            int bottom = 56;
            int chartWidth = Math.max(1, getWidth() - left - right);
            int chartHeight = Math.max(1, getHeight() - top - bottom);

            g2.setColor(UiTheme.SURFACE_ALT);
            g2.fillRect(left, top, chartWidth, chartHeight);

            if (points.isEmpty()) {
                g2.setColor(new Color(118, 126, 145));
                g2.drawString("Chưa có dữ liệu lưu lượng cho hàng hóa được chọn.", left + 8, top + 24);
                g2.dispose();
                return;
            }

            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (FlowPoint point : points) {
                min = Math.min(min, point.value);
                max = Math.max(max, point.value);
            }
            min = Math.min(min, 0);
            max = Math.max(max, 0);
            if (min == max) {
                min -= 1;
                max += 1;
            }

            g2.setColor(new Color(206, 214, 230));
            for (int i = 0; i <= 4; i++) {
                int y = top + i * chartHeight / 4;
                g2.drawLine(left, y, left + chartWidth, y);
                int tickValue = max - (max - min) * i / 4;
                g2.setColor(new Color(105, 113, 131));
                g2.drawString(String.valueOf(tickValue), 10, y + 4);
                g2.setColor(new Color(206, 214, 230));
            }

            int zeroY = mapValueToY(0, min, max, top, chartHeight);
            g2.setColor(new Color(128, 135, 155));
            g2.drawLine(left, zeroY, left + chartWidth, zeroY);

            g2.setColor(UiTheme.TEAL);
            int count = points.size();
            int previousX = -1;
            int previousY = -1;
            for (int i = 0; i < count; i++) {
                int x = count == 1 ? left + chartWidth / 2 : left + i * chartWidth / (count - 1);
                int y = mapValueToY(points.get(i).value, min, max, top, chartHeight);

                if (i > 0) {
                    g2.drawLine(previousX, previousY, x, y);
                }
                previousX = x;
                previousY = y;
            }

            g2.setColor(UiTheme.TEAL_DARK);
            for (int i = 0; i < count; i++) {
                int x = count == 1 ? left + chartWidth / 2 : left + i * chartWidth / (count - 1);
                int y = mapValueToY(points.get(i).value, min, max, top, chartHeight);
                g2.fillOval(x - 3, y - 3, 6, 6);
            }

            int labelStep = Math.max(1, count / 8);
            g2.setColor(new Color(95, 104, 124));
            for (int i = 0; i < count; i++) {
                if (i % labelStep != 0 && i != count - 1) {
                    continue;
                }
                int x = count == 1 ? left + chartWidth / 2 : left + i * chartWidth / (count - 1);
                String label = points.get(i).date.format(DATE_LABEL_FORMAT);
                int width = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, x - width / 2, top + chartHeight + 24);
            }

            g2.setColor(new Color(50, 57, 73));
            g2.drawString("Ngày phiếu", left + chartWidth / 2 - 26, top + chartHeight + 44);
            g2.drawString("Đơn vị: số lượng (nhập + / xuất -)", 10, top - 20);
            g2.dispose();
        }

        private int mapValueToY(int value, int min, int max, int top, int height) {
            double ratio = (double) (value - min) / (double) (max - min);
            return top + height - (int) Math.round(ratio * height);
        }
    }

    private static class MonthlyProfitLineChartPanel extends JPanel {
        private List<ProfitPoint> points = Collections.emptyList();

        private MonthlyProfitLineChartPanel() {
            setOpaque(true);
            setBackground(UiTheme.SURFACE);
            setPreferredSize(new Dimension(420, 230));
            setMinimumSize(new Dimension(360, 220));
        }

        private void setData(List<ProfitPoint> points) {
            this.points = new ArrayList<>(points);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int left = 58;
            int right = 18;
            int top = 28;
            int bottom = 46;
            int chartWidth = Math.max(1, getWidth() - left - right);
            int chartHeight = Math.max(1, getHeight() - top - bottom);

            g2.setColor(UiTheme.SURFACE_ALT);
            g2.fillRect(left, top, chartWidth, chartHeight);

            if (points.isEmpty()) {
                g2.setColor(new Color(118, 126, 145));
                g2.drawString("Chưa có dữ liệu lợi nhuận trong tháng này.", left + 8, top + 24);
                g2.dispose();
                return;
            }

            double min = 0.0;
            double max = 0.0;
            for (ProfitPoint point : points) {
                double value = point.value.doubleValue();
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            if (Double.compare(min, max) == 0) {
                min -= 1.0;
                max += 1.0;
            }

            g2.setColor(new Color(206, 214, 230));
            for (int i = 0; i <= 4; i++) {
                int y = top + i * chartHeight / 4;
                g2.drawLine(left, y, left + chartWidth, y);
                double tickValue = max - (max - min) * i / 4.0;
                g2.setColor(new Color(105, 113, 131));
                g2.drawString(formatTick(tickValue), 8, y + 4);
                g2.setColor(new Color(206, 214, 230));
            }

            int zeroY = mapValueToY(0.0, min, max, top, chartHeight);
            g2.setColor(new Color(128, 135, 155));
            g2.drawLine(left, zeroY, left + chartWidth, zeroY);

            g2.setColor(UiTheme.TEAL);
            int count = points.size();
            int previousX = -1;
            int previousY = -1;
            for (int i = 0; i < count; i++) {
                int x = count == 1 ? left + chartWidth / 2 : left + i * chartWidth / (count - 1);
                int y = mapValueToY(points.get(i).value.doubleValue(), min, max, top, chartHeight);

                if (i > 0) {
                    g2.drawLine(previousX, previousY, x, y);
                }
                previousX = x;
                previousY = y;
            }

            g2.setColor(UiTheme.TEAL_DARK);
            for (int i = 0; i < count; i++) {
                int x = count == 1 ? left + chartWidth / 2 : left + i * chartWidth / (count - 1);
                int y = mapValueToY(points.get(i).value.doubleValue(), min, max, top, chartHeight);
                g2.fillOval(x - 3, y - 3, 6, 6);
            }

            int labelStep = Math.max(1, count / 6);
            g2.setColor(new Color(95, 104, 124));
            for (int i = 0; i < count; i++) {
                if (i % labelStep != 0 && i != count - 1) {
                    continue;
                }
                int x = count == 1 ? left + chartWidth / 2 : left + i * chartWidth / (count - 1);
                String label = points.get(i).date.format(DATE_LABEL_FORMAT);
                int width = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, x - width / 2, top + chartHeight + 22);
            }

            g2.setColor(new Color(50, 57, 73));
            g2.drawString("Đơn vị: triệu VND", 8, top - 10);
            g2.dispose();
        }

        private int mapValueToY(double value, double min, double max, int top, int height) {
            double ratio = (value - min) / (max - min);
            return top + height - (int) Math.round(ratio * height);
        }

        private String formatTick(double value) {
            if (Math.abs(value) >= 1000.0) {
                return String.valueOf(Math.round(value));
            }
            if (Math.abs(value) >= 10.0) {
                return String.format(Locale.ROOT, "%.0f", value);
            }
            return String.format(Locale.ROOT, "%.1f", value);
        }
    }

    private static class TrendBarChartPanel extends JPanel {
        private static final int LEFT_PADDING = 58;
        private static final int RIGHT_PADDING = 20;
        private static final int TOP_PADDING = 35;
        private static final int BOTTOM_PADDING = 120;

        private List<TrendItem> items = Collections.emptyList();
        private final List<Rectangle> barBounds = new ArrayList<>();
        private Consumer<TrendItem> selectionListener = item -> {
        };
        private int selectedIndex = -1;

        private TrendBarChartPanel() {
            setOpaque(true);
            setBackground(UiTheme.SURFACE);
            setPreferredSize(new Dimension(900, 300));
            setMinimumSize(new Dimension(700, 300));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    handleBarClick(e.getPoint());
                }
            });
        }

        private void setData(List<TrendItem> items) {
            this.items = new ArrayList<>(items);
            int preferredWidth = Math.max(900, 130 + this.items.size() * 72);
            setPreferredSize(new Dimension(preferredWidth, 300));
            if (selectedIndex >= this.items.size()) {
                selectedIndex = -1;
            }
            barBounds.clear();
            revalidate();
            repaint();
        }

        private void setSelectionListener(Consumer<TrendItem> selectionListener) {
            this.selectionListener = selectionListener == null ? item -> {
            } : selectionListener;
        }

        private TrendItem selectByName(String name) {
            String normalizedName = normalizeKey(name);
            if (normalizedName.isEmpty()) {
                return clearSelection();
            }
            for (int i = 0; i < items.size(); i++) {
                TrendItem item = items.get(i);
                if (normalizeKey(item.name).equals(normalizedName)) {
                    setSelectedIndex(i, false);
                    return item;
                }
            }
            return clearSelection();
        }

        private TrendItem clearSelection() {
            setSelectedIndex(-1, false);
            return null;
        }

        private Rectangle getSelectedBarBounds() {
            if (selectedIndex < 0 || selectedIndex >= items.size()) {
                return null;
            }
            if (selectedIndex < barBounds.size()) {
                return new Rectangle(barBounds.get(selectedIndex));
            }
            return calculateBarBounds(selectedIndex);
        }

        private void handleBarClick(Point point) {
            for (int i = 0; i < barBounds.size(); i++) {
                if (barBounds.get(i).contains(point)) {
                    setSelectedIndex(i, true);
                    return;
                }
            }
            setSelectedIndex(-1, true);
        }

        private void setSelectedIndex(int index, boolean notifyListener) {
            int boundedIndex = index >= 0 && index < items.size() ? index : -1;
            boolean changed = boundedIndex != selectedIndex;
            selectedIndex = boundedIndex;
            if (changed) {
                repaint();
            }
            if (notifyListener) {
                selectionListener.accept(getSelectedItem());
            }
        }

        private TrendItem getSelectedItem() {
            if (selectedIndex < 0 || selectedIndex >= items.size()) {
                return null;
            }
            return items.get(selectedIndex);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int chartWidth = Math.max(1, getWidth() - LEFT_PADDING - RIGHT_PADDING);
            int chartHeight = Math.max(1, getHeight() - TOP_PADDING - BOTTOM_PADDING);

            g2.setColor(UiTheme.SURFACE_ALT);
            g2.fillRect(LEFT_PADDING, TOP_PADDING, chartWidth, chartHeight);

            if (items.isEmpty()) {
                barBounds.clear();
                g2.setColor(new Color(118, 126, 145));
                g2.drawString("Chưa có dữ liệu xu hướng bán ra.", LEFT_PADDING + 8, TOP_PADDING + 24);
                g2.dispose();
                return;
            }

            int maxValue = getMaxValue();

            g2.setColor(new Color(206, 214, 230));
            for (int i = 0; i <= 4; i++) {
                int y = TOP_PADDING + i * chartHeight / 4;
                g2.drawLine(LEFT_PADDING, y, LEFT_PADDING + chartWidth, y);
                int tickValue = maxValue - (maxValue * i / 4);
                g2.setColor(new Color(105, 113, 131));
                g2.drawString(String.valueOf(tickValue), 10, y + 4);
                g2.setColor(new Color(206, 214, 230));
            }

            int count = items.size();
            boolean hasSelection = selectedIndex >= 0 && selectedIndex < count;
            barBounds.clear();

            for (int i = 0; i < count; i++) {
                Rectangle barBound = calculateBarBounds(i);
                barBounds.add(barBound);
                boolean isSelected = hasSelection && i == selectedIndex;
                Color barColor = isSelected ? new Color(26, 188, 156) : UiTheme.TEAL;
                g2.setColor(barColor);
                g2.fillRect(barBound.x, barBound.y, barBound.width, barBound.height);

                TrendItem item = items.get(i);
                String label = abbreviate(item.name, 12);
                int width = g2.getFontMetrics().stringWidth(label);
                g2.setColor(new Color(95, 104, 124));
                g2.drawString(label, barBound.x + barBound.width / 2 - width / 2, TOP_PADDING + chartHeight + 20);
            }

            g2.setColor(new Color(50, 57, 73));
            g2.drawString("Tên hàng hóa", LEFT_PADDING + chartWidth / 2 - 34, TOP_PADDING + chartHeight + 44);
            g2.drawString("Đơn vị: số lượng bán ra", 10, TOP_PADDING - 20);
            g2.dispose();
        }

        private int getMaxValue() {
            int maxValue = 0;
            for (TrendItem item : items) {
                maxValue = Math.max(maxValue, item.value);
            }
            return Math.max(maxValue, 1);
        }

        private Rectangle calculateBarBounds(int index) {
            int chartWidth = Math.max(1, getWidth() - LEFT_PADDING - RIGHT_PADDING);
            int chartHeight = Math.max(1, getHeight() - TOP_PADDING - BOTTOM_PADDING);
            int count = Math.max(1, items.size());
            int gap = 30;
            int availableBarArea = Math.max(1, chartWidth - gap * (count + 1));
            int barWidth = Math.max(12, availableBarArea / count);

            TrendItem item = items.get(index);
            int maxValue = getMaxValue();
            int rawHeight = (int) Math.round((double) Math.max(0, item.value) / maxValue * chartHeight);
            int barHeight = Math.max(2, rawHeight);
            int x = LEFT_PADDING + gap + index * (barWidth + gap);
            int y = TOP_PADDING + chartHeight - barHeight;
            return new Rectangle(x, y, barWidth, barHeight);
        }

        private String normalizeKey(String text) {
            if (text == null) {
                return "";
            }
            String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
            normalized = normalized.replaceAll("\\p{M}+", "");
            return normalized.toLowerCase(Locale.ROOT).trim();
        }

        private String abbreviate(String text, int maxLen) {
            if (text == null) {
                return "";
            }
            String trimmed = text.trim();
            if (trimmed.length() <= maxLen) {
                return trimmed;
            }
            return trimmed.substring(0, Math.max(0, maxLen - 3)) + "...";
        }
    }
}
