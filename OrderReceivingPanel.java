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
        "       g.TenHang, g.DonViTinh, g.GiaBan, g.LaHangBan, g.SoLuongTon " +
        "FROM InOut i " +
        "INNER JOIN Goods g ON g.MaHang = i.MaHang " +
        "WHERE i.LoaiPhieu = ? " +
        "ORDER BY i.NgayPhieu DESC, i.MaPhieu DESC";
    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("Ma don:\\s*([A-Z]{2}\\d{3}\\d{3})");
    private static final Pattern DESTINATION_PATTERN = Pattern.compile("Chuyen den:\\s*([^;]+)");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat MONEY_FORMAT = createMoneyFormat();
    private static final Color BOX_BG = UiTheme.SURFACE;
    private static final Color BOX_BORDER = UiTheme.BORDER;
    private static final Color SELECTED_BOX_BG = new Color(232, 247, 245);
    private static final Color DETAIL_BG = UiTheme.SURFACE;

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
        addDetailRow(content, gbc, "Mã phiếu", order.getReceiptIdsText());
        addDetailRow(content, gbc, "Trạng thái", order.readyForPackaging ? "Đã chuyển đến đóng gói" : "Chờ xác nhận");

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
            "Xác nhận đơn hàng " + order.orderCode + " có thể chuyển đến mục đóng gói?",
            "Xác nhận đơn hàng",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        order.readyForPackaging = true;
        confirmedOrderCodes.add(order.orderCode);
        renderOrders();
        showOrderDetail(order);
        JOptionPane.showMessageDialog(
            this,
            "Đơn hàng " + order.orderCode + " đã sẵn sàng chuyển đến đóng gói.",
            "Đã xác nhận",
            JOptionPane.INFORMATION_MESSAGE
        );
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
                    Timestamp ngayPhieu = rs.getTimestamp("NgayPhieu");
                    LocalDate receivedDate = ngayPhieu == null
                        ? LocalDate.now()
                        : ngayPhieu.toLocalDateTime().toLocalDate();
                    OrderInfo order = groupedOrders.get(orderCode);
                    if (order == null) {
                        order = new OrderInfo(
                            orderCode,
                            destination,
                            receivedDate,
                            confirmedOrderCodes.contains(orderCode)
                        );
                        groupedOrders.put(orderCode, order);
                    }
                    order.addLine(new OrderLine(
                        orderId,
                        "MH-" + maHang,
                        rs.getString("TenHang"),
                        normalizeText(rs.getString("DonViTinh")),
                        rs.getDouble("GiaBan"),
                        rs.getInt("SoLuongTon"),
                        rs.getInt("SoLuong"),
                        note
                    ));
                }
            }
        }
        orders.addAll(groupedOrders.values());
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
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

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
        private final LocalDate receivedDate;
        private final List<OrderLine> lines = new ArrayList<>();
        private int itemCount;
        private double totalPrice;
        private boolean readyForPackaging;

        private OrderInfo(
            String orderCode,
            String destination,
            LocalDate receivedDate,
            boolean readyForPackaging
        ) {
            this.orderCode = orderCode;
            this.destination = destination;
            this.receivedDate = receivedDate;
            this.readyForPackaging = readyForPackaging;
        }

        private void addLine(OrderLine line) {
            lines.add(line);
            itemCount += line.quantity;
            totalPrice += line.price * line.quantity;
        }

        private String getReceiptIdsText() {
            StringBuilder builder = new StringBuilder();
            for (OrderLine line : lines) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(line.receiptId);
            }
            return builder.toString();
        }

        private String getLinesText() {
            StringBuilder builder = new StringBuilder();
            for (OrderLine line : lines) {
                if (builder.length() > 0) {
                    builder.append(System.lineSeparator());
                }
                builder
                    .append(line.goodsCode)
                    .append(" - ")
                    .append(line.goodsName)
                    .append(": ")
                    .append(line.quantity)
                    .append(" ")
                    .append(line.unit)
                    .append(", giá bán ")
                    .append(formatMillionVnd(line.price))
                    .append(", ton ")
                    .append(line.stockQuantity);
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
            String note
        ) {
            this.receiptId = receiptId;
            this.goodsCode = goodsCode;
            this.goodsName = goodsName;
            this.unit = unit;
            this.price = price;
            this.stockQuantity = stockQuantity;
            this.quantity = quantity;
            this.note = note;
        }
    }
}
