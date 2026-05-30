import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

public final class OrderPlacementPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final String SQLSERVER_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");
    private static final String ORDER_TYPE = "Xuất";
    private static final String GOODS_QUERY =
        "SELECT MaHang, TenHang, GiaBan, SoLuongTon " +
        "FROM Goods " +
        "WHERE COALESCE(LaHangBan, 0) <> 0 " +
        "ORDER BY TenHang, MaHang";

    private static final int MAX_ORDER_QUANTITY = 999;
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.000");
    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("Ma don:\\s*([A-Z]{2})(\\d{3})(\\d{3})");
    private static final DestinationOption[] DESTINATION_OPTIONS = {
        // TODO: Sua danh sach nay theo cac diem den ban muon hien thi trong hop chon "Chuyen den".
        // Tham so 1 la ten diem den, tham so 2 la 2 chu cai viet tat dung de tao ma don.
        new DestinationOption("Hà Nội", "HN"),
        new DestinationOption("Nghệ An", "NA"),
        new DestinationOption("Đà Nẵng", "DN"),
        new DestinationOption("Lâm Đồng", "LD"),
        new DestinationOption("TP Hồ Chí Minh", "HC"),
        new DestinationOption("Cần Thơ", "CT"),
    };

    private final transient List<ProductRow> productRows = new ArrayList<>();
    private final ProductTableModel productTableModel = new ProductTableModel();
    private final JTable goodsTable = new JTable(productTableModel);
    private final JLabel totalItemsLabel = new JLabel("0");
    private final JLabel totalMoneyLabel = new JLabel("0");
    private final JComboBox<DestinationOption> destinationComboBox = new JComboBox<>(DESTINATION_OPTIONS);
    private final transient Runnable orderConfirmedCallback;

    public OrderPlacementPanel() {
        this(null);
    }

    public OrderPlacementPanel(Runnable orderConfirmedCallback) {
        this.orderConfirmedCallback = orderConfirmedCallback;
        buildUi();
        refreshData();
    }

    public final void refreshData() {
        try {
            loadGoodsFromDatabase();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể tải danh sách hàng hóa từ SQL Server. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
            productRows.clear();
        }
        renderGoodsRows();
        updateTotals();
    }

    private void buildUi() {
        setLayout(new BorderLayout(12, 12));
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel topPanel = UiTheme.pageHeader("Đặt hàng", "Chọn mặt hàng, điều chỉnh số lượng và xác nhận đơn mua");
        add(topPanel, BorderLayout.NORTH);

        configureGoodsTable();
        JScrollPane scrollPane = new JScrollPane(
            goodsTable,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        UiTheme.styleScrollPane(scrollPane);
        add(scrollPane, BorderLayout.CENTER);

        add(buildSummaryPanel(), BorderLayout.SOUTH);
    }

    private void configureGoodsTable() {
        UiTheme.styleTable(goodsTable);
        goodsTable.setRowHeight(52);
        goodsTable.setShowHorizontalLines(true);
        goodsTable.setShowVerticalLines(true);
        goodsTable.setIntercellSpacing(new Dimension(0, 1));
        goodsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        goodsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        goodsTable.setDefaultRenderer(Object.class, new ProductCellRenderer());
        goodsTable.setDefaultRenderer(Integer.class, new ProductCellRenderer());

        TableColumnModel columns = goodsTable.getColumnModel();
        columns.getColumn(0).setPreferredWidth(80);
        columns.getColumn(1).setPreferredWidth(430);
        columns.getColumn(2).setPreferredWidth(140);
        columns.getColumn(3).setPreferredWidth(130);
        columns.getColumn(4).setPreferredWidth(170);
        columns.getColumn(4).setCellRenderer(new QuantityStepperRenderer());
        columns.getColumn(4).setCellEditor(new QuantityStepperEditor());
    }

    private JPanel buildSummaryPanel() {
        JPanel summaryPanel = new JPanel(new BorderLayout(10, 10));
        summaryPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiTheme.BORDER),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        summaryPanel.setBackground(UiTheme.SURFACE);

        JPanel totals = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        totals.setOpaque(false);
        totals.add(createSummaryItem("Tổng số mặt hàng muốn mua:", totalItemsLabel));
        totals.add(createSummaryItem("Tổng tiền:", totalMoneyLabel, "triệu đồng"));
        totals.add(createDestinationSelector());
        summaryPanel.add(totals, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);

        JButton clearButton = new JButton("Xóa lựa chọn");
        clearButton.setPreferredSize(new Dimension(132, 38));
        UiTheme.styleSecondaryButton(clearButton);
        clearButton.addActionListener(e -> clearSelections());

        JButton confirmButton = new JButton("Xác nhận đặt hàng");
        confirmButton.setPreferredSize(new Dimension(172, 38));
        UiTheme.stylePrimaryButton(confirmButton);
        confirmButton.addActionListener(e -> confirmOrder());
        buttons.add(clearButton);
        buttons.add(confirmButton);
        summaryPanel.add(buttons, BorderLayout.EAST);
        return summaryPanel;
    }

    private JPanel createSummaryItem(String label, JLabel valueLabel) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel(label);
        titleLabel.setFont(UiTheme.font(Font.BOLD, 13));
        titleLabel.setForeground(UiTheme.MUTED_TEXT);
        valueLabel.setFont(UiTheme.font(Font.BOLD, 16));
        valueLabel.setForeground(UiTheme.TEXT);
        panel.add(titleLabel);
        panel.add(valueLabel);
        return panel;
    }

    private JPanel createSummaryItem(String label, JLabel valueLabel, String suffix) {
        JPanel panel = createSummaryItem(label, valueLabel);
        JLabel suffixLabel = new JLabel(suffix);
        suffixLabel.setFont(UiTheme.font(Font.PLAIN, 13));
        panel.add(suffixLabel);
        return panel;
    }

    private JPanel createDestinationSelector() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel("Chuyển đến:");
        titleLabel.setFont(UiTheme.font(Font.BOLD, 13));
        titleLabel.setForeground(UiTheme.MUTED_TEXT);
        destinationComboBox.setPreferredSize(new Dimension(170, 30));
        UiTheme.styleField(destinationComboBox);
        panel.add(titleLabel);
        panel.add(destinationComboBox);
        return panel;
    }

    private void renderGoodsRows() {
        productTableModel.fireTableDataChanged();
    }

    private ProductRow buildProductRow(GoodsItem item) {
        JCheckBox selectBox = new JCheckBox();
        int spinnerMax = Math.min(MAX_ORDER_QUANTITY, Math.max(0, item.soLuongTon));
        JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(0, 0, spinnerMax, 1));

        ProductRow productRow = new ProductRow(item, selectBox, quantitySpinner);
        boolean inStock = item.soLuongTon > 0;
        selectBox.setEnabled(inStock);
        quantitySpinner.setEnabled(inStock);
        return productRow;
    }
    private void clearSelections() {
        for (ProductRow productRow : productRows) {
            productRow.selectBox.setSelected(false);
            productRow.quantitySpinner.setValue(0);
        }
        productTableModel.fireTableDataChanged();
        updateTotals();
    }

    private void updateTotals() {
        int totalItems = 0;
        double totalMoney = 0;
        for (ProductRow productRow : productRows) {
            int quantity = productRow.getQuantity();
            if (quantity > 0) {
                totalItems += quantity;
                totalMoney += productRow.item.giaBan * quantity;
            }
        }
        totalItemsLabel.setText(String.valueOf(totalItems));
        totalMoneyLabel.setText(formatMoney(totalMoney));
    }

    private void confirmOrder() {
        List<ProductRow> selectedProducts = getSelectedProducts();
        if (selectedProducts.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Vui lòng chọn ít nhất một mặt hàng và số lượng đặt mua.",
                "Thiếu thông tin",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        DestinationOption destination = getSelectedDestination();
        if (destination == null) {
            JOptionPane.showMessageDialog(
                this,
                "Vui lòng chọn điểm đến trước khi xác nhận đặt hàng.",
                "Thiếu thông tin",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
            this,
            "Xác nhận đặt " + totalItemsLabel.getText() + " món hàng chuyển đến " +
                destination.name + " với tổng tiền " + totalMoneyLabel.getText() + " triệu đồng?",
            "Xác nhận đặt hàng",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            String orderCode = saveOrderTransactions(selectedProducts, destination);
            JOptionPane.showMessageDialog(
                this,
                "Đặt hàng thành công. Mã đơn " + orderCode + " đã chuyển sang mục Tiếp nhận đơn hàng.",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE
            );
            refreshData();
            if (orderConfirmedCallback != null) {
                orderConfirmedCallback.run();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể tạo đơn hàng. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private List<ProductRow> getSelectedProducts() {
        List<ProductRow> selectedProducts = new ArrayList<>();
        for (ProductRow productRow : productRows) {
            if (productRow.getQuantity() > 0) {
                selectedProducts.add(productRow);
            }
        }
        return selectedProducts;
    }

    private void loadGoodsFromDatabase() throws SQLException {
        ensureSqlServerDriverLoaded();
        productRows.clear();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(GOODS_QUERY);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                GoodsItem item = new GoodsItem(
                    rs.getInt("MaHang"),
                    normalizeText(rs.getString("TenHang")),
                    rs.getDouble("GiaBan"),
                    rs.getInt("SoLuongTon")
                );
                productRows.add(buildProductRow(item));
            }
        }
    }

    private DestinationOption getSelectedDestination() {
        Object selected = destinationComboBox.getSelectedItem();
        return selected instanceof DestinationOption ? (DestinationOption) selected : null;
    }

    private String saveOrderTransactions(List<ProductRow> selectedProducts, DestinationOption destination) throws SQLException {
        ensureSqlServerDriverLoaded();
        String checkStockSql = "SELECT SoLuongTon FROM Goods WITH (UPDLOCK, ROWLOCK) WHERE MaHang = ?";
        String insertSql = "INSERT INTO InOut (MaHang, SoLuong, LoaiPhieu, GhiChu) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try {
                int totalQuantity = calculateTotalQuantity(selectedProducts);
                String orderCode = buildNextOrderCode(conn, destination, totalQuantity);
                for (ProductRow productRow : selectedProducts) {
                    int currentStock = getCurrentStock(conn, checkStockSql, productRow.item.maHang);
                    int quantity = productRow.getQuantity();
                    if (quantity > currentStock) {
                        throw new SQLException(
                            "Hàng " + productRow.item.tenHang + " chỉ còn " + currentStock + " món trong kho."
                        );
                    }

                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, productRow.item.maHang);
                        insertStmt.setInt(2, quantity);
                        insertStmt.setString(3, ORDER_TYPE);
                        insertStmt.setString(
                            4,
                            "Ma don: " + orderCode +
                            "; Chuyen den: " + destination.name +
                            "; Don dat hang tu chuc nang Dat hang. Tong tam tinh dong hang: " +
                            formatMoney(productRow.item.giaBan * quantity) + " trieu dong."
                        );
                        insertStmt.executeUpdate();
                    }
                }
                conn.commit();
                return orderCode;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private int calculateTotalQuantity(List<ProductRow> selectedProducts) {
        int totalQuantity = 0;
        for (ProductRow productRow : selectedProducts) {
            totalQuantity += productRow.getQuantity();
        }
        return totalQuantity;
    }

    private String buildNextOrderCode(Connection conn, DestinationOption destination, int totalQuantity) throws SQLException {
        if (totalQuantity > MAX_ORDER_QUANTITY) {
            throw new SQLException("Tong so luong dat trong mot don khong duoc vuot qua 999 de tao ma don 3 chu so.");
        }
        int normalizedQuantity = Math.max(0, totalQuantity);
        int nextSequence = findNextSequence(conn, destination.code);
        if (nextSequence > 999) {
            throw new SQLException("So thu tu ma don cho diem den " + destination.name + " da vuot qua 999.");
        }
        return destination.code + String.format(Locale.ROOT, "%03d%03d", normalizedQuantity, nextSequence);
    }

    private int findNextSequence(Connection conn, String destinationCode) throws SQLException {
        String querySql = "SELECT GhiChu FROM InOut WHERE GhiChu LIKE ?";
        int maxSequence = 0;
        try (PreparedStatement stmt = conn.prepareStatement(querySql)) {
            stmt.setString(1, "%Ma don: " + destinationCode + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Matcher matcher = ORDER_CODE_PATTERN.matcher(normalizeText(rs.getString("GhiChu")));
                    while (matcher.find()) {
                        if (destinationCode.equals(matcher.group(1))) {
                            maxSequence = Math.max(maxSequence, Integer.parseInt(matcher.group(3)));
                        }
                    }
                }
            }
        }
        return maxSequence + 1;
    }

    private int getCurrentStock(Connection conn, String checkStockSql, int maHang) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(checkStockSql)) {
            stmt.setInt(1, maHang);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Mã hàng " + maHang + " không tồn tại.");
                }
                return rs.getInt("SoLuongTon");
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

    private String formatMoney(double value) {
        return MONEY_FORMAT.format(value);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private static String normalizeDestinationCode(String code) {
        String normalized = Normalizer.normalize(code == null ? "" : code, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^A-Za-z]", "")
            .toUpperCase(Locale.ROOT);
        if (normalized.length() >= 2) {
            return normalized.substring(0, 2);
        }
        return String.format(Locale.ROOT, "%-2s", normalized).replace(' ', 'X');
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

    private static class GoodsItem {
        private final int maHang;
        private final String tenHang;
        private final double giaBan;
        private final int soLuongTon;

        private GoodsItem(int maHang, String tenHang, double giaBan, int soLuongTon) {
            this.maHang = maHang;
            this.tenHang = tenHang;
            this.giaBan = giaBan;
            this.soLuongTon = soLuongTon;
        }
    }

    private static class DestinationOption {
        private final String name;
        private final String code;

        private DestinationOption(String name, String code) {
            this.name = name == null ? "" : name.trim();
            this.code = normalizeDestinationCode(code);
        }

        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    private static class ProductRow {
        private final GoodsItem item;
        private final JCheckBox selectBox;
        private final JSpinner quantitySpinner;

        private ProductRow(GoodsItem item, JCheckBox selectBox, JSpinner quantitySpinner) {
            this.item = item;
            this.selectBox = selectBox;
            this.quantitySpinner = quantitySpinner;
        }

        private int getQuantity() {
            Object value = quantitySpinner.getValue();
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }
    }

    private final class ProductTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final String[] columns = {
            "Ch\u1ecdn h\u00e0ng",
            "T\u00ean h\u00e0ng",
            "Gi\u00e1 b\u00e1n",
            "S\u1ed1 l\u01b0\u1ee3ng t\u1ed3n",
            "S\u1ed1 l\u01b0\u1ee3ng \u0111\u1eb7t mua"
        };

        @Override
        public int getRowCount() {
            return productRows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return Boolean.class;
            }
            if (columnIndex == 3 || columnIndex == 4) {
                return Integer.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= productRows.size()) {
                return false;
            }
            ProductRow row = productRows.get(rowIndex);
            return row.item.soLuongTon > 0 && (columnIndex == 0 || columnIndex == 4);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ProductRow row = productRows.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return row.selectBox.isSelected();
                case 1:
                    return "<html><b>" + escapeHtml(row.item.tenHang) + "</b><br/>M\u00e3 h\u00e0ng: MH-" + row.item.maHang + "</html>";
                case 2:
                    return formatMoney(row.item.giaBan);
                case 3:
                    return row.item.soLuongTon;
                case 4:
                    return row.getQuantity();
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ProductRow row = productRows.get(rowIndex);
            if (columnIndex == 0) {
                boolean selected = Boolean.TRUE.equals(value);
                row.selectBox.setSelected(selected);
                if (selected && row.getQuantity() == 0) {
                    row.quantitySpinner.setValue(1);
                }
                if (!selected) {
                    row.quantitySpinner.setValue(0);
                }
            }
            if (columnIndex == 4 && value instanceof Number) {
                int quantity = ((Number) value).intValue();
                int max = Math.min(MAX_ORDER_QUANTITY, Math.max(0, row.item.soLuongTon));
                row.quantitySpinner.setValue(Math.max(0, Math.min(quantity, max)));
                row.selectBox.setSelected(row.getQuantity() > 0);
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
            updateTotals();
        }
    }

    private final class ProductCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
        ) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            setFont(UiTheme.font(column == 1 ? Font.PLAIN : Font.BOLD, 13));
            setForeground(UiTheme.TEXT);
            setHorizontalAlignment(column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? UiTheme.SURFACE : UiTheme.SURFACE_ALT);
            }
            int modelRow = table.convertRowIndexToModel(row);
            if (column == 3 && modelRow >= 0 && modelRow < productRows.size() && productRows.get(modelRow).item.soLuongTon <= 0) {
                setForeground(new Color(170, 50, 35));
            }
            return component;
        }
    }

    private final class QuantityStepperRenderer implements TableCellRenderer {
        private final QuantityStepperPanel panel = new QuantityStepperPanel(false);

        @Override
        public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
        ) {
            int quantity = value instanceof Number ? ((Number) value).intValue() : 0;
            panel.setQuantity(quantity);
            panel.setBackground(isSelected ? table.getSelectionBackground() : row % 2 == 0 ? UiTheme.SURFACE : UiTheme.SURFACE_ALT);
            return panel;
        }
    }

    private final class QuantityStepperEditor extends AbstractCellEditor implements TableCellEditor {
        private static final long serialVersionUID = 1L;
        private final QuantityStepperPanel panel = new QuantityStepperPanel(true);
        private ProductRow editingRow;

        private QuantityStepperEditor() {
            panel.minusButton.addActionListener(e -> changeQuantity(-1));
            panel.plusButton.addActionListener(e -> changeQuantity(1));
        }

        @Override
        public Component getTableCellEditorComponent(
            JTable table,
            Object value,
            boolean isSelected,
            int row,
            int column
        ) {
            int modelRow = table.convertRowIndexToModel(row);
            editingRow = productRows.get(modelRow);
            panel.setQuantity(editingRow.getQuantity());
            panel.setBackground(table.getSelectionBackground());
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return editingRow == null ? 0 : editingRow.getQuantity();
        }

        private void changeQuantity(int delta) {
            if (editingRow == null) {
                return;
            }
            int max = Math.min(MAX_ORDER_QUANTITY, Math.max(0, editingRow.item.soLuongTon));
            int quantity = Math.max(0, Math.min(editingRow.getQuantity() + delta, max));
            editingRow.quantitySpinner.setValue(quantity);
            editingRow.selectBox.setSelected(quantity > 0);
            panel.setQuantity(quantity);
            int rowIndex = productRows.indexOf(editingRow);
            if (rowIndex >= 0) {
                productTableModel.fireTableRowsUpdated(rowIndex, rowIndex);
            }
            updateTotals();
        }
    }

    private static final class QuantityStepperPanel extends JPanel {
        private final JButton minusButton = new JButton("-");
        private final JButton plusButton = new JButton("+");
        private final JLabel quantityLabel = new JLabel("0", SwingConstants.CENTER);

        private QuantityStepperPanel(boolean editable) {
            super(new GridBagLayout());
            setOpaque(true);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new java.awt.Insets(2, 5, 2, 5);
            gbc.gridy = 0;

            styleStepButton(minusButton, editable);
            styleStepButton(plusButton, editable);
            quantityLabel.setPreferredSize(new Dimension(38, 34));
            quantityLabel.setFont(UiTheme.font(Font.BOLD, 20));
            quantityLabel.setForeground(UiTheme.TEXT);

            gbc.gridx = 0;
            add(minusButton, gbc);
            gbc.gridx = 1;
            add(quantityLabel, gbc);
            gbc.gridx = 2;
            add(plusButton, gbc);
        }

        private void setQuantity(int quantity) {
            quantityLabel.setText(String.valueOf(quantity));
        }

        private static void styleStepButton(JButton button, boolean editable) {
            button.setPreferredSize(new Dimension(44, 44));
            button.setFont(UiTheme.font(Font.BOLD, 24));
            button.setFocusPainted(false);
            button.setEnabled(true);
            button.setForeground(UiTheme.TEXT);
            button.setBackground(new Color(245, 246, 248));
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 213, 219)),
                BorderFactory.createEmptyBorder(0, 0, 3, 0)
            ));
        }
    }
}
