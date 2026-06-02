import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

public class OrderReceivingPanel extends JPanel {
    private static final String SQLSERVER_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");
    private static final String PACKAGING_ORDER_TYPE = "Xuất";
    private static final String ORDERS_QUERY =
        "SELECT TOP 100 " +
        "       i.MaPhieu, i.MaHang, i.SoLuong, i.NgayPhieu, i.GhiChu, " +
        "       g.TenHang, g.DonViTinh, g.GiaBan, g.LaHangBan, g.SoLuongTon, " +
        "       COALESCE(g.KhoiLuong, 0) AS KhoiLuong " +
        "FROM InOut i " +
        "INNER JOIN Goods g ON g.MaHang = i.MaHang " +
        "WHERE i.LoaiPhieu = ? " +
        "ORDER BY i.NgayPhieu DESC, i.MaPhieu DESC";

    // Truy vấn kiểm tra đơn hàng đã có trong PackagingQueue chưa
    private static final String CHECK_QUEUE_QUERY =
        "SELECT COUNT(*) FROM PackagingQueue WHERE OrderCode = ?";

    // Truy vấn tính khối lượng đơn hàng từ DB
    private static final String WEIGHT_QUERY =
        "SELECT SUM(COALESCE(g.KhoiLuong, 0) * i.SoLuong) AS TotalWeight " +
        "FROM InOut i " +
        "INNER JOIN Goods g ON g.MaHang = i.MaHang " +
        "WHERE i.LoaiPhieu = ? AND i.GhiChu LIKE ?";

    // Truy vấn lấy QueueOrder tiếp theo cho một team trong ngày
    private static final String NEXT_QUEUE_ORDER_QUERY =
        "SELECT COALESCE(MAX(QueueOrder), 0) + 1 " +
        "FROM PackagingQueue " +
        "WHERE TeamId = ? AND QueueDate = CAST(SYSDATETIME() AS date)";

    // Truy vấn insert vào PackagingQueue (ItemCount = tổng số lượng đặt của đơn)
    private static final String INSERT_QUEUE_SQL =
        "INSERT INTO PackagingQueue " +
        "(OrderCode, DestCode, DestName, TeamId, TotalWeight, Status, QueueOrder, ItemCount) " +
        "VALUES (?, ?, ?, ?, ?, 'WAITING', ?, ?)";

    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("Ma don:\\s*([A-Z]{2}\\d{3}\\d{3})");
    private static final Pattern DESTINATION_PATTERN = Pattern.compile("Chuyen den:\\s*([^;]+)");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat MONEY_FORMAT = createMoneyFormat();
    private static final Color BOX_BG = UiTheme.SURFACE;
    private static final Color BOX_BORDER = UiTheme.BORDER;
    private static final Color SELECTED_BOX_BG = new Color(232, 247, 245);
    private static final Color DETAIL_BG = UiTheme.SURFACE;

    // Callback để thông báo PackagingShippingPanel khi có đơn mới
    private Runnable onOrderConfirmedCallback;

    private final List<OrderInfo> orders = new ArrayList<>();
    private final Set<String> confirmedOrderCodes = new HashSet<>();
    private final JPanel orderListPanel = new JPanel(new GridLayout(0, 4, 14, 14));
    private final JPanel detailPanel = new JPanel(new BorderLayout(10, 10));
    private OrderBoxPanel selectedBoxPanel;
    private OrderInfo selectedOrder;

    public OrderReceivingPanel() {
        buildUi();
        refreshData();
    }

    /** Đặt callback được gọi sau khi xác nhận đơn thành công vào PackagingQueue */
    public void setOnOrderConfirmedCallback(Runnable callback) {
        this.onOrderConfirmedCallback = callback;
    }

    private static NumberFormat createMoneyFormat() {
        DecimalFormat format = (DecimalFormat) NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMinimumFractionDigits(3);
        format.setMaximumFractionDigits(3);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    private static String formatMillionVnd(double value) {
        return MONEY_FORMAT.format(value) + " triệu VND";
    }

    public void refreshData() {
        String selectedOrderCode = selectedOrder == null ? null : selectedOrder.orderCode;
        selectedOrder = null;
        selectedBoxPanel = null;

        try {
            loadOrdersFromDatabase();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể tải đơn hàng từ SQL Server. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
            orders.clear();
        }

        if (selectedOrderCode != null) {
            for (OrderInfo order : orders) {
                if (order.orderCode.equals(selectedOrderCode)) {
                    selectedOrder = order;
                    break;
                }
            }
        }
        renderOrders();
        if (selectedOrder == null) {
            showEmptyDetail();
        } else {
            showOrderDetail(selectedOrder);
        }
    }

    private void buildUi() {
        setLayout(new BorderLayout(12, 12));
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel topPanel = UiTheme.pageHeader("Tiếp nhận đơn hàng", "Danh sách đơn hàng đang chờ tiếp nhận để chuyển sang đóng gói");
        add(topPanel, BorderLayout.NORTH);

        orderListPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(
            orderListPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UiTheme.APP_BG);
        add(scrollPane, BorderLayout.CENTER);

        detailPanel.setPreferredSize(new Dimension(300, 0));
        detailPanel.setBackground(DETAIL_BG);
        detailPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiTheme.BORDER),
            BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));
        add(detailPanel, BorderLayout.EAST);
    }

    private void renderOrders() {
        orderListPanel.removeAll();
        if (orders.isEmpty()) {
            JLabel emptyLabel = new JLabel("Chưa có đơn hàng nào cần tiếp nhận.");
            emptyLabel.setFont(UiTheme.font(Font.PLAIN, 14));
            emptyLabel.setForeground(UiTheme.MUTED_TEXT);
            orderListPanel.add(emptyLabel);
        }
        for (OrderInfo order : orders) {
            OrderBoxPanel boxPanel = new OrderBoxPanel(order);
            if (order == selectedOrder) {
                boxPanel.setSelected(true);
                selectedBoxPanel = boxPanel;
            }
            boxPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectOrder(order, boxPanel);
                }
            });
            orderListPanel.add(boxPanel);
        }
        orderListPanel.revalidate();
        orderListPanel.repaint();
    }

    private void selectOrder(OrderInfo order, OrderBoxPanel boxPanel) {
        if (selectedBoxPanel != null) {
            selectedBoxPanel.setSelected(false);
        }
        selectedOrder = order;
        selectedBoxPanel = boxPanel;
        selectedBoxPanel.setSelected(true);
        showOrderDetail(order);
    }

    private void showEmptyDetail() {
        detailPanel.removeAll();

        JLabel titleLabel = new JLabel("Chi tiết đơn hàng");
        titleLabel.setFont(UiTheme.font(Font.BOLD, 18));
        titleLabel.setForeground(UiTheme.TEXT);
        detailPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel hintLabel = new JLabel(
            "<html><div style='font-size: 12px;'>Chọn một thùng hàng để xem thông tin chi tiết.</div></html>"
        );
        hintLabel.setForeground(UiTheme.MUTED_TEXT);
        detailPanel.add(hintLabel, BorderLayout.CENTER);

        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void showOrderDetail(OrderInfo order) {
        detailPanel.removeAll();

        JLabel titleLabel = new JLabel("Chi tiết đơn hàng");
        titleLabel.setFont(UiTheme.font(Font.BOLD, 18));
        titleLabel.setForeground(UiTheme.TEXT);
        detailPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1;

        addDetailRow(content, gbc, "Mã đơn", order.orderCode);
        addDetailRow(content, gbc, "Chuyển đến", order.destination);
        addDetailRow(content, gbc, "Ngày nhận", order.receivedDate.format(DATE_FORMAT));
        addDetailRow(content, gbc, "Tổng số lượng đặt", String.valueOf(order.itemCount));
        addDetailRow(content, gbc, "Số mặt hàng", String.valueOf(order.lines.size()));
        addDetailRow(content, gbc, "Tổng tạm tính", formatMillionVnd(order.totalPrice));
        addDetailRow(content, gbc, "Tổng khối lượng", String.format("%.3f kg", order.totalWeight));
        addDetailRow(content, gbc, "Mã phiếu", order.getReceiptIdsText());
        addDetailRow(content, gbc, "Trạng thái",
            order.readyForPackaging ? "Đã chuyển đến đóng gói" : "Chờ xác nhận");

        JLabel noteLabel = new JLabel("Danh sách món hàng");
        noteLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        gbc.gridy++;
        content.add(noteLabel, gbc);

        JTextArea noteArea = new JTextArea(order.getLinesText());
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setEditable(false);
        noteArea.setOpaque(false);
        noteArea.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        gbc.gridy++;
        content.add(noteArea, gbc);

        gbc.gridy++;
        gbc.weighty = 1;
        content.add(Box.createVerticalGlue(), gbc);

        detailPanel.add(content, BorderLayout.CENTER);

        JButton confirmButton = new JButton(
            order.readyForPackaging ? "Đã xác nhận" : "Xác nhận chuyển đến đóng gói"
        );
        UiTheme.stylePrimaryButton(confirmButton);
        confirmButton.setEnabled(!order.readyForPackaging);
        confirmButton.addActionListener(e -> confirmOrderForPackaging(order));
        detailPanel.add(confirmButton, BorderLayout.SOUTH);

        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void addDetailRow(JPanel content, GridBagConstraints gbc, String label, String value) {
        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(UiTheme.font(Font.BOLD, 12));
        labelComponent.setForeground(UiTheme.MUTED_TEXT);
        gbc.gridy++;
        content.add(labelComponent, gbc);

        JLabel valueComponent = new JLabel("<html>" + escapeHtml(value) + "</html>");
        valueComponent.setFont(UiTheme.font(Font.PLAIN, 13));
        valueComponent.setForeground(UiTheme.TEXT);
        gbc.gridy++;
        content.add(valueComponent, gbc);
    }

    private void confirmOrderForPackaging(OrderInfo order) {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Xác nhận đơn hàng " + order.orderCode + " có thể chuyển đến mục đóng gói?\n" +
            "Tổ đóng gói: " + order.teamName + "\n" +
            "Tổng khối lượng: " + String.format("%.3f kg", order.totalWeight),
            "Xác nhận đơn hàng",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        // Kiểm tra mã đơn có hợp lệ (có 2 chữ cái đầu nhận diện được không)
        if (order.destCode == null || order.teamId == 0) {
            JOptionPane.showMessageDialog(
                this,
                "Đơn hàng " + order.orderCode + " không có mã tỉnh thành hợp lệ (HN/NA/DN/LD/HC/CT).\n" +
                "Không thể chuyển sang đóng gói.",
                "Mã đơn không hợp lệ",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            insertToPackagingQueue(order);
            order.readyForPackaging = true;
            confirmedOrderCodes.add(order.orderCode);
            renderOrders();
            showOrderDetail(order);
            JOptionPane.showMessageDialog(
                this,
                "Đơn hàng " + order.orderCode + " đã được đưa vào hàng đợi đóng gói.\n" +
                "Tổ: " + order.teamName + " | Khối lượng: " + String.format("%.3f kg", order.totalWeight),
                "Đã xác nhận",
                JOptionPane.INFORMATION_MESSAGE
            );
            // Thông báo cho PackagingShippingPanel cập nhật
            if (onOrderConfirmedCallback != null) {
                onOrderConfirmedCallback.run();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể thêm vào hàng đợi đóng gói. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Thêm đơn hàng vào bảng PackagingQueue.
     * Nếu đơn đã tồn tại (trùng OrderCode) thì bỏ qua (idempotent).
     */
    private void insertToPackagingQueue(OrderInfo order) throws SQLException {
        ensureSqlServerDriverLoaded();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Kiểm tra đã có chưa
            try (PreparedStatement checkStmt = conn.prepareStatement(CHECK_QUEUE_QUERY)) {
                checkStmt.setString(1, order.orderCode);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return; // Đã có rồi, bỏ qua
                    }
                }
            }

            // Lấy QueueOrder tiếp theo
            int nextQueueOrder = 1;
            try (PreparedStatement nextStmt = conn.prepareStatement(NEXT_QUEUE_ORDER_QUERY)) {
                nextStmt.setInt(1, order.teamId);
                try (ResultSet rs = nextStmt.executeQuery()) {
                    if (rs.next()) {
                        nextQueueOrder = rs.getInt(1);
                    }
                }
            }

            // Insert vào PackagingQueue (ItemCount = tổng số lượng đặt của đơn)
            try (PreparedStatement insertStmt = conn.prepareStatement(INSERT_QUEUE_SQL)) {
                insertStmt.setString(1, order.orderCode);
                insertStmt.setString(2, order.destCode);
                insertStmt.setString(3, order.destination);
                insertStmt.setInt(4, order.teamId);
                insertStmt.setBigDecimal(5, BigDecimal.valueOf(order.totalWeight).setScale(3, RoundingMode.HALF_UP));
                insertStmt.setInt(6, nextQueueOrder);
                insertStmt.setInt(7, order.itemCount);  // dùng để tính thời gian đóng gói (1 món = 1s)
                insertStmt.executeUpdate();
            }
        }
    }

    private void loadOrdersFromDatabase() throws SQLException {
        ensureSqlServerDriverLoaded();
        orders.clear();
        Map<String, OrderInfo> groupedOrders = new LinkedHashMap<>();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(ORDERS_QUERY)) {
            stmt.setString(1, PACKAGING_ORDER_TYPE);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int orderId = rs.getInt("MaPhieu");
                    int maHang = rs.getInt("MaHang");
                    String note = normalizeText(rs.getString("GhiChu"));
                    String orderCode = extractOrderCode(note, orderId);
                    String destination = extractDestination(note);
                    String destCode = extractDestCode(orderCode);
                    int teamId = resolveTeamId(destCode);
                    String teamName = resolveTeamName(teamId);
                    Timestamp ngayPhieu = rs.getTimestamp("NgayPhieu");
                    LocalDate receivedDate = ngayPhieu == null
                        ? LocalDate.now()
                        : ngayPhieu.toLocalDateTime().toLocalDate();

                    OrderInfo order = groupedOrders.get(orderCode);
                    if (order == null) {
                        boolean alreadyConfirmed = confirmedOrderCodes.contains(orderCode)
                            || isOrderInQueue(conn, orderCode);
                        order = new OrderInfo(
                            orderCode,
                            destination,
                            destCode,
                            teamId,
                            teamName,
                            receivedDate,
                            alreadyConfirmed
                        );
                        groupedOrders.put(orderCode, order);
                    }
                    double khoiLuong = rs.getDouble("KhoiLuong");
                    int soLuong = rs.getInt("SoLuong");
                    order.addLine(new OrderLine(
                        orderId,
                        "MH-" + maHang,
                        rs.getString("TenHang"),
                        normalizeText(rs.getString("DonViTinh")),
                        rs.getDouble("GiaBan"),
                        rs.getInt("SoLuongTon"),
                        soLuong,
                        khoiLuong,
                        note
                    ));
                }
            }
        }
        orders.addAll(groupedOrders.values());
    }

    /** Kiểm tra đơn đã có trong PackagingQueue chưa (để đánh dấu readyForPackaging khi load lại) */
    private boolean isOrderInQueue(Connection conn, String orderCode) {
        try (PreparedStatement stmt = conn.prepareStatement(CHECK_QUEUE_QUERY)) {
            stmt.setString(1, orderCode);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    private String extractOrderCode(String note, int fallbackOrderId) {
        Matcher matcher = ORDER_CODE_PATTERN.matcher(note);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "PHIEU-" + fallbackOrderId;
    }

    private String extractDestination(String note) {
        Matcher matcher = DESTINATION_PATTERN.matcher(note);
        if (matcher.find()) {
            return normalizeText(matcher.group(1));
        }
        return "Chưa xác định";
    }

    /**
     * Lấy 2 chữ cái đầu mã đơn hàng làm mã tỉnh thành.
     * Trả về null nếu mã không đúng định dạng XX######.
     */
    private String extractDestCode(String orderCode) {
        if (orderCode != null && orderCode.matches("[A-Z]{2}\\d{6}")) {
            return orderCode.substring(0, 2);
        }
        return null;
    }

    /** Xác định tổ đóng gói từ mã tỉnh thành */
    private int resolveTeamId(String destCode) {
        if (destCode == null) return 0;
        switch (destCode) {
            case "HN": case "NA": return 1;
            case "DN": case "LD": return 2;
            case "HC": case "CT": return 3;
            default: return 0;
        }
    }

    private String resolveTeamName(int teamId) {
        switch (teamId) {
            case 1: return "Tổ 1 (HN/NA)";
            case 2: return "Tổ 2 (DN/LĐ)";
            case 3: return "Tổ 3 (HC/CT)";
            default: return "Không xác định";
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

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    private static class OrderBoxPanel extends JPanel {
        private final OrderInfo order;

        private OrderBoxPanel(OrderInfo order) {
            super(new BorderLayout());
            this.order = order;
            setPreferredSize(new Dimension(180, 112));
            setBackground(order.readyForPackaging ? new Color(232, 245, 233) : BOX_BG);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BOX_BORDER),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
            ));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel dateLabel = new JLabel(order.receivedDate.format(DATE_FORMAT));
            dateLabel.setFont(UiTheme.font(Font.PLAIN, 12));
            dateLabel.setForeground(UiTheme.MUTED_TEXT);
            add(dateLabel, BorderLayout.NORTH);

            JLabel codeLabel = new JLabel(order.orderCode, SwingConstants.CENTER);
            codeLabel.setFont(UiTheme.font(Font.BOLD, 22));
            codeLabel.setForeground(UiTheme.TEXT);
            add(codeLabel, BorderLayout.CENTER);

            JLabel quantityLabel = new JLabel(String.valueOf(order.itemCount), SwingConstants.RIGHT);
            quantityLabel.setFont(UiTheme.font(Font.BOLD, 13));
            quantityLabel.setForeground(UiTheme.TEAL_DARK);
            add(quantityLabel, BorderLayout.SOUTH);
        }

        private void setSelected(boolean selected) {
            setBackground(selected ? SELECTED_BOX_BG : order.readyForPackaging ? new Color(232, 245, 233) : BOX_BG);
            repaint();
        }
    }

    private static class OrderInfo {
        private final String orderCode;
        private final String destination;
        private final String destCode;   // 2 chữ cái: HN, NA, DN, LD, HC, CT
        private final int teamId;        // 1, 2, 3 hoặc 0 nếu không xác định
        private final String teamName;
        private final LocalDate receivedDate;
        private final List<OrderLine> lines = new ArrayList<>();
        private int itemCount;
        private double totalPrice;
        private double totalWeight;      // kg — tổng KhoiLuong * SoLuong
        private boolean readyForPackaging;

        private OrderInfo(
            String orderCode,
            String destination,
            String destCode,
            int teamId,
            String teamName,
            LocalDate receivedDate,
            boolean readyForPackaging
        ) {
            this.orderCode = orderCode;
            this.destination = destination;
            this.destCode = destCode;
            this.teamId = teamId;
            this.teamName = teamName;
            this.receivedDate = receivedDate;
            this.readyForPackaging = readyForPackaging;
        }

        private void addLine(OrderLine line) {
            lines.add(line);
            itemCount += line.quantity;
            totalPrice += line.price * line.quantity;
            totalWeight += line.khoiLuong * line.quantity;
        }

        private String getReceiptIdsText() {
            StringBuilder builder = new StringBuilder();
            for (OrderLine line : lines) {
                if (builder.length() > 0) builder.append(", ");
                builder.append(line.receiptId);
            }
            return builder.toString();
        }

        private String getLinesText() {
            StringBuilder builder = new StringBuilder();
            for (OrderLine line : lines) {
                if (builder.length() > 0) builder.append(System.lineSeparator());
                builder.append(line.goodsCode).append(" - ").append(line.goodsName)
                    .append(": ").append(line.quantity).append(" ").append(line.unit)
                    .append(", giá bán ").append(MONEY_FORMAT.format(line.price)).append(" triệu VND")
                    .append(", ton ").append(line.stockQuantity);
            }
            return builder.toString();
        }
    }

    private static class OrderLine {
        private final int receiptId;
        private final String goodsCode;
        private final String goodsName;
        private final String unit;
        private final double price;
        private final int stockQuantity;
        private final int quantity;
        private final double khoiLuong;  // kg / đơn vị
        @SuppressWarnings("unused")
        private final String note;

        private OrderLine(
            int receiptId,
            String goodsCode,
            String goodsName,
            String unit,
            double price,
            int stockQuantity,
            int quantity,
            double khoiLuong,
            String note
        ) {
            this.receiptId = receiptId;
            this.goodsCode = goodsCode;
            this.goodsName = goodsName;
            this.unit = unit;
            this.price = price;
            this.stockQuantity = stockQuantity;
            this.quantity = quantity;
            this.khoiLuong = khoiLuong;
            this.note = note;
        }
    }
}