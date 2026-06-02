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
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public class HomeInfo extends JPanel {
    private static final String SQLSERVER_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    private static final String STOCK_ACTION_IN = "Nh\u1EADp";
    private static final String STOCK_ACTION_OUT = "Xu\u1EA5t";

    private static final DateTimeFormatter DATE_LABEL_FORMAT = DateTimeFormatter.ofPattern("dd/MM");

    private final JComboBox<GoodsOption> goodsSelector = new JComboBox<>();
    private final FlowLineChartPanel flowChartPanel = new FlowLineChartPanel();
    private final TrendBarChartPanel trendChartPanel = new TrendBarChartPanel();
    private final InfographicPanel infographicPanel = new InfographicPanel();
    private final JComboBox<String> trendGoodsSelector = new JComboBox<>();
    private final JPanel contentPanel = new JPanel();
    private final JScrollPane contentScrollPane;

    private JPanel infographicSectionPanel;
    private JPanel flowSectionPanel;
    private JPanel trendSectionPanel;
    private JScrollPane trendChartScrollPane;
    private boolean updatingGoodsModel;
    private boolean updatingTrendSelector;

    public HomeInfo(String managerId, String managerName) {
        setLayout(new BorderLayout(10, 10));
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel quickMenu = buildQuickMenu();
        add(quickMenu, BorderLayout.NORTH);

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        infographicSectionPanel = buildInfographicSection();
        flowSectionPanel = buildFlowSection();
        trendSectionPanel = buildTrendSection();

        contentPanel.add(infographicSectionPanel);
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
        loadCompanyInfographic();
        refreshData();
    }

    // Đường dẫn ảnh thông tin công ty — đặt file ảnh cùng thư mục với file .jar hoặc thư mục gốc project.
    private static final String COMPANY_INFOGRAPHIC_PATH = "tecoffee_info.png";

    private void loadCompanyInfographic() {
        ImageIcon icon = new ImageIcon(COMPANY_INFOGRAPHIC_PATH);
        if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
            infographicPanel.setInfographic(icon);
            infographicSectionPanel.setVisible(true);
        }
    }

    public void refreshData() {
        try {
            loadGoodsSelectorData();
            loadFlowChartData();
            loadTrendChartData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể tải dữ liệu Trang chủ. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
            flowChartPanel.setData(Collections.emptyList());
            trendChartPanel.setData(Collections.emptyList());
        }
    }

    private JPanel buildQuickMenu() {
        JPanel quickMenu = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quickMenu.setOpaque(false);

        JLabel quickLabel = new JLabel("Truy cập nhanh:");
        quickLabel.setFont(UiTheme.font(Font.BOLD, 13));
        quickLabel.setForeground(UiTheme.TEXT);
        quickMenu.add(quickLabel);

        quickMenu.add(createSectionButton("Thông tin công ty", () -> scrollToSection(infographicSectionPanel)));
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

    private JPanel buildInfographicSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setOpaque(true);
        section.setBackground(UiTheme.SURFACE);
        section.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 480));
        section.setPreferredSize(new Dimension(900, 460));
        infographicPanel.setPreferredSize(new Dimension(900, 460));
        section.add(infographicPanel, BorderLayout.CENTER);
        section.setVisible(true);
        return section;
    }

    private JPanel buildFlowSection() {
        JPanel section = createSectionContainer("Biểu đồ lưu lượng");

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setOpaque(false);
        filterBar.add(new JLabel("Chọn hàng hóa:"));
        goodsSelector.setPreferredSize(new Dimension(260, 28));
        UiTheme.styleField(goodsSelector);
        filterBar.add(goodsSelector);

        section.add(filterBar, BorderLayout.NORTH);

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

        JLabel desc = new JLabel("Số lượng phiếu bán ra (phiếu xuất) của tất cả hàng hóa");
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

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Date ngay = rs.getDate("Ngay");
                        int luuLuong = rs.getInt("LuuLuong");
                        if (ngay != null) {
                            points.add(new FlowPoint(ngay.toLocalDate(), luuLuong));
                        }
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
            "GROUP BY g.MaHang, g.TenHang " +
            "HAVING SUM(i.SoLuong) > 0 " +
            "ORDER BY SoLuongBan DESC, g.TenHang";

        List<TrendItem> items = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, STOCK_ACTION_OUT);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(new TrendItem(rs.getString("TenHang"), rs.getInt("SoLuongBan")));
                }
            }
        }

        trendChartPanel.setData(items);
        updateTrendSelectorModel(items);
        applyTrendSelection();
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

    private static class TrendItem {
        private final String name;
        private final int value;

        private TrendItem(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    private static class InfographicPanel extends JPanel {
        private ImageIcon infographic;

        private InfographicPanel() {
            setOpaque(true);
            setBackground(UiTheme.SURFACE);
            setMinimumSize(new Dimension(700, 260));
        }

        private void setInfographic(ImageIcon infographic) {
            this.infographic = infographic;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (infographic == null || infographic.getIconWidth() <= 0 || infographic.getIconHeight() <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Phủ kín toàn bộ panel, giữ tỉ lệ ảnh (fit-width, căn giữa dọc)
            int panelW = getWidth();
            int panelH = getHeight();
            int imgW = infographic.getIconWidth();
            int imgH = infographic.getIconHeight();

            double scaleW = (double) panelW / imgW;
            double scaleH = (double) panelH / imgH;
            double scale = Math.max(scaleW, scaleH); // cover: phủ kín

            int drawW = Math.max(1, (int) Math.round(imgW * scale));
            int drawH = Math.max(1, (int) Math.round(imgH * scale));
            int x = (panelW - drawW) / 2;
            int y = (panelH - drawH) / 2;

            g2.drawImage(infographic.getImage(), x, y, drawW, drawH, this);
            g2.dispose();
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
                int tickValue = maxValue - maxValue * i / 4;
                g2.setColor(new Color(105, 113, 131));
                g2.drawString(String.valueOf(tickValue), 10, y + 4);
                g2.setColor(new Color(206, 214, 230));
            }

            int count = items.size();
            boolean hasSelection = selectedIndex >= 0 && selectedIndex < count;
            barBounds.clear();

            for (int i = 0; i < count; i++) {
                TrendItem item = items.get(i);
                Rectangle bar = calculateBarBounds(i);
                barBounds.add(bar);

                boolean isSelected = hasSelection && i == selectedIndex;
                Composite originalComposite = g2.getComposite();
                if (hasSelection && !isSelected) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
                }

                Color fillColor = isSelected ? UiTheme.TEAL : UiTheme.TEAL_DARK;
                Color borderColor = isSelected ? new Color(146, 76, 27) : UiTheme.TEAL_DARK;
                g2.setColor(fillColor);
                g2.fillRect(bar.x, bar.y, bar.width, bar.height);
                g2.setColor(borderColor);
                g2.drawRect(bar.x, bar.y, bar.width, bar.height);

                if (bar.width >= 26) {
                    String valueText = String.valueOf(item.value);
                    int valueWidth = g2.getFontMetrics().stringWidth(valueText);
                    g2.setColor(new Color(72, 81, 100));
                    g2.drawString(valueText, bar.x + (bar.width - valueWidth) / 2, bar.y - 4);
                }

                String label = abbreviate(item.name, 15);
                int textWidth = g2.getFontMetrics().stringWidth(label);
                g2.setColor(isSelected ? new Color(53, 62, 82) : new Color(95, 104, 124));
                g2.drawString(label, bar.x + (bar.width - textWidth) / 2, TOP_PADDING + chartHeight + 20);
                g2.setComposite(originalComposite);
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