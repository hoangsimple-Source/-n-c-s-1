import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

public class StockCheckPanel extends JPanel {
    private static final String STOCK_ACTION_IN  = "Nhập";
    private static final String STOCK_ACTION_OUT = "Xuất";

    private static final String SQLSERVER_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String DB_URL  = System.getenv().getOrDefault("DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    // Lấy đúng thứ tự cột yêu cầu: MaHang, TenHang, DonViTinh, KhoiLuong, GiaNhap, GiaBan, LaHangBan, SoLuongTon
    private static final String GOODS_QUERY =
        "SELECT MaHang, TenHang, DonViTinh, KhoiLuong, GiaNhap, GiaBan, LaHangBan, SoLuongTon " +
        "FROM Goods ORDER BY MaHang";

    // Chỉ số cột trong bảng (dùng lại nhiều chỗ, tránh magic number)
    private static final int COL_MA_HANG    = 0;
    private static final int COL_TEN_HANG   = 1;
    private static final int COL_DVT        = 2;
    private static final int COL_KHOI_LUONG = 3;
    private static final int COL_GIA_NHAP   = 4;
    private static final int COL_GIA_BAN    = 5;
    private static final int COL_LA_HANG_BAN = 6;
    private static final int COL_SO_LUONG_TON = 7;

    private DefaultTableModel goodsTableModel;
    private JTable goodsTable;
    private JComboBox<GoodsOption> goodsComboBox;
    private JTextField quantityField;
    private JTextArea noteArea;
    private JLabel actionTypeValueLabel;
    private JPanel stockActionPanel;
    private String pendingStockAction;
    private final String operatorManagerId;
    private final String operatorManagerName;
    private boolean deleteAuditTableEnsured;

    public StockCheckPanel() {
        this(null, null);
    }

    public StockCheckPanel(String managerId, String managerName) {
        this.operatorManagerId   = normalizeText(managerId);
        this.operatorManagerName = normalizeText(managerName);
        buildUi();
        loadGoodsData();
    }

    public void refreshData() {
        loadGoodsData();
    }

    // ── Build UI ──────────────────────────────────────────────────────────────
    private void buildUi() {
        setLayout(new BorderLayout(10, 10));
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(UiTheme.pageHeader("Kiểm tra kho hàng",
            "Danh sách hàng hóa hiện tại trong bảng Goods"), BorderLayout.NORTH);

        // Cột theo thứ tự yêu cầu: MaHang, TenHang, DonViTinh, KhoiLuong, GiaNhap, GiaBan, LaHangBan, SoLuongTon
        String[] columns = {
            "Mã hàng", "Tên hàng", "Đơn vị tính",
            "Khối lượng (kg)", "Giá nhập (triệu VND)", "Giá bán (triệu VND)",
            "Là hàng bán", "Số lượng tồn"
        };
        goodsTableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };

        goodsTable = new JTable(goodsTableModel);
        UiTheme.styleTable(goodsTable);
        goodsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        goodsTable.getSelectionModel().addListSelectionListener(e -> syncComboWithSelectedRow());

        // Độ rộng cột hợp lý
        int[] colWidths = {70, 220, 90, 110, 140, 140, 90, 100};
        for (int i = 0; i < colWidths.length; i++) {
            goodsTable.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
        }

        JScrollPane tableScrollPane = new JScrollPane(goodsTable);
        UiTheme.styleScrollPane(tableScrollPane);
        add(tableScrollPane, BorderLayout.CENTER);

        // Thanh nút + panel nhập liệu
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setOpaque(false);

        JPanel actionsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionsBar.setOpaque(false);

        JButton addButton         = new JButton("Thêm số lượng");
        JButton removeButton      = new JButton("Xóa số lượng");
        JButton updatePriceButton = new JButton("Thay đổi giá nhập");
        JButton addGoodsButton    = new JButton("Thêm hàng hóa");
        JButton deleteGoodsButton = new JButton("Xóa hàng hóa");
        JButton refreshButton     = new JButton("Làm mới");

        UiTheme.stylePrimaryButton(addButton);
        UiTheme.styleSecondaryButton(removeButton);
        UiTheme.styleSecondaryButton(updatePriceButton);
        UiTheme.styleSecondaryButton(addGoodsButton);
        UiTheme.styleDangerButton(deleteGoodsButton);
        UiTheme.styleSecondaryButton(refreshButton);

        addButton.addActionListener(e         -> openStockActionPanel(STOCK_ACTION_IN));
        removeButton.addActionListener(e      -> openStockActionPanel(STOCK_ACTION_OUT));
        updatePriceButton.addActionListener(e -> openUpdatePriceDialog());
        addGoodsButton.addActionListener(e    -> openAddGoodsDialog());
        deleteGoodsButton.addActionListener(e -> handleDeleteGoods());
        refreshButton.addActionListener(e     -> loadGoodsData());

        actionsBar.add(addButton);
        actionsBar.add(removeButton);
        actionsBar.add(updatePriceButton);
        actionsBar.add(addGoodsButton);
        actionsBar.add(deleteGoodsButton);
        actionsBar.add(refreshButton);

        stockActionPanel = buildStockActionPanel();
        stockActionPanel.setVisible(false);

        bottomPanel.add(actionsBar, BorderLayout.NORTH);
        bottomPanel.add(stockActionPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel buildStockActionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiTheme.BORDER),
            BorderFactory.createEmptyBorder(8, 10, 10, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(5, 5, 5, 5);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        JLabel actionTypeLabel = new JLabel("Loại phiếu:");
        actionTypeLabel.setFont(UiTheme.font(Font.BOLD, 13));
        actionTypeValueLabel = new JLabel("-");
        actionTypeValueLabel.setFont(UiTheme.font(Font.BOLD, 13));
        actionTypeValueLabel.setForeground(UiTheme.TEAL);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(actionTypeLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(actionTypeValueLabel, gbc);

        goodsComboBox = new JComboBox<>();
        UiTheme.styleField(goodsComboBox);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Loại hàng:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(goodsComboBox, gbc);

        quantityField = new JTextField();
        UiTheme.styleField(quantityField);
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("Số lượng:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(quantityField, gbc);

        noteArea = new JTextArea(3, 20);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        UiTheme.styleField(noteArea);
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JLabel("Ghi chú:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(new JScrollPane(noteArea), gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton confirmButton = new JButton("Xác nhận");
        JButton cancelButton  = new JButton("Đóng");
        UiTheme.stylePrimaryButton(confirmButton);
        UiTheme.styleSecondaryButton(cancelButton);
        confirmButton.addActionListener(e -> executeStockAction());
        cancelButton.addActionListener(e  -> closeStockActionPanel());
        buttons.add(cancelButton);
        buttons.add(confirmButton);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(buttons, gbc);
        return panel;
    }

    // ── Mở panel nhập/xuất ────────────────────────────────────────────────────
    private void openStockActionPanel(String actionType) {
        if (goodsComboBox.getItemCount() == 0) {
            JOptionPane.showMessageDialog(this,
                "Không có dữ liệu hàng hóa để thao tác.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }
        pendingStockAction = actionType;
        actionTypeValueLabel.setText(actionType);
        quantityField.setText("");
        noteArea.setText("");

        int selectedRow = goodsTable.getSelectedRow();
        if (selectedRow >= 0) {
            selectGoodsInCombo((Integer) goodsTableModel.getValueAt(selectedRow, COL_MA_HANG));
        } else {
            goodsComboBox.setSelectedIndex(0);
        }
        stockActionPanel.setVisible(true);
        stockActionPanel.revalidate();
        quantityField.requestFocusInWindow();
    }

    private void closeStockActionPanel() {
        stockActionPanel.setVisible(false);
        pendingStockAction = null;
    }

    private void executeStockAction() {
        if (pendingStockAction == null) return;
        GoodsOption goods = (GoodsOption) goodsComboBox.getSelectedItem();
        if (goods == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn loại hàng.",
                "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int soLuong;
        try {
            soLuong = Integer.parseInt(quantityField.getText().trim());
            if (soLuong <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số lượng phải là số nguyên dương.",
                "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String ghiChu = noteArea.getText().trim();
        String stockAction = pendingStockAction;
        try {
            int remaining = saveInOutTransaction(goods.maHang, soLuong, stockAction, ghiChu);
            JOptionPane.showMessageDialog(this, "Cập nhật kho hàng thành công.",
                "Thành công", JOptionPane.INFORMATION_MESSAGE);
            if (STOCK_ACTION_OUT.equals(stockAction) && remaining < 10) {
                JOptionPane.showMessageDialog(this,
                    "Cảnh báo: Mặt hàng còn dưới 10 món trong kho! Hãy nhanh chóng nhập hàng!");
            }
            closeStockActionPanel();
            loadGoodsData();
            selectGoodsInTable(goods.maHang);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Không thể thực hiện thao tác kho. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Thay đổi giá nhập ─────────────────────────────────────────────────────
    private void openUpdatePriceDialog() {
        GoodsOption sel = getSelectedGoodsForAction();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn loại hàng cần thay đổi giá nhập.",
                "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }
        BigDecimal currentPrice;
        try {
            currentPrice = getCurrentGoodsPrice(sel.maHang);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Không thể tải giá nhập hiện tại. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JTextField priceField = new JTextField(currentPrice.toPlainString());
        UiTheme.styleField(priceField);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("Hàng hóa:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(new JLabel(sel.toString()), gbc);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        form.add(new JLabel("Giá nhập:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        form.add(priceField, gbc);

        while (true) {
            int option = JOptionPane.showConfirmDialog(this, form, "Thay đổi giá nhập",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) return;
            BigDecimal newPrice;
            try {
                newPrice = new BigDecimal(priceField.getText().trim());
                if (newPrice.signum() < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Giá nhập phải là số >= 0.",
                    "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            try {
                updateGoodsPrice(sel.maHang, newPrice);
                JOptionPane.showMessageDialog(this, "Thay đổi giá nhập thành công.",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
                closeStockActionPanel();
                loadGoodsData();
                selectGoodsInTable(sel.maHang);
                return;
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                    "Không thể thay đổi giá nhập. Chi tiết: " + ex.getMessage(),
                    "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
    }

    // ── Thêm hàng hóa (bao gồm khối lượng) ───────────────────────────────────
    private void openAddGoodsDialog() {
        JTextField maHangField     = new JTextField();
        JTextField tenHangField    = new JTextField();
        JTextField donViTinhField  = new JTextField();
        JTextField khoiLuongField  = new JTextField("0.000");   // ← thêm mới
        JTextField giaNhapField    = new JTextField();
        JTextField soLuongTonField = new JTextField("0");
        JComboBox<String> laHangBanCombo = new JComboBox<>(new String[]{"Có", "Không"});

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill   = GridBagConstraints.HORIZONTAL;

        Object[][] rows = {
            {"Mã hàng:",                         maHangField},
            {"Tên hàng:",                        tenHangField},
            {"Đơn vị tính:",                     donViTinhField},
            {"Khối lượng (kg):",                 khoiLuongField},   // ← hàng mới
            {"Giá nhập (triệu VND):",            giaNhapField},
            {"Là hàng bán:",                     laHangBanCombo},
            {"Số lượng tồn:",                    soLuongTonField},
        };
        for (int i = 0; i < rows.length; i++) {
            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0;
            form.add(new JLabel((String) rows[i][0]), gbc);
            gbc.gridx = 1; gbc.weightx = 1;
            form.add((java.awt.Component) rows[i][1], gbc);
        }

        while (true) {
            int option = JOptionPane.showConfirmDialog(this, form, "Thêm hàng hóa",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) return;

            // Validate mã hàng
            Integer maHang = null;
            String maHangText = maHangField.getText().trim();
            if (!maHangText.isEmpty()) {
                try {
                    maHang = Integer.parseInt(maHangText);
                    if (maHang <= 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this,
                        "Mã hàng phải là số nguyên dương hoặc để trống.",
                        "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
            }

            // Validate tên hàng
            String tenHang = tenHangField.getText().trim();
            if (tenHang.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên hàng.",
                    "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            // Validate đơn vị tính
            String donViTinh = donViTinhField.getText().trim();
            if (donViTinh.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đơn vị tính.",
                    "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            // Validate khối lượng
            BigDecimal khoiLuong;
            try {
                khoiLuongField.setText(khoiLuongField.getText().trim());
                String klText = khoiLuongField.getText();
                khoiLuong = klText.isEmpty() ? BigDecimal.ZERO
                    : new BigDecimal(klText);
                if (khoiLuong.signum() < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                    "Khối lượng phải là số >= 0 (VD: 1.500).",
                    "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            // Validate giá nhập
            BigDecimal giaNhap;
            try {
                giaNhap = new BigDecimal(giaNhapField.getText().trim());
                if (giaNhap.signum() < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Giá nhập phải là số >= 0.",
                    "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            // Validate số lượng tồn
            int soLuongTon;
            try {
                soLuongTon = Integer.parseInt(soLuongTonField.getText().trim());
                if (soLuongTon < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Số lượng tồn phải là số nguyên >= 0.",
                    "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            boolean laHangBan = laHangBanCombo.getSelectedIndex() == 0;

            try {
                int insertedMa = insertGoods(maHang, tenHang, donViTinh, khoiLuong,
                    giaNhap, laHangBan, soLuongTon);
                JOptionPane.showMessageDialog(this, "Thêm hàng hóa thành công.",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
                loadGoodsData();
                if (insertedMa > 0) {
                    selectGoodsInCombo(insertedMa);
                    selectGoodsInTable(insertedMa);
                }
                return;
            } catch (SQLException ex) {
                String detail = ex.getMessage() == null ? "" : ex.getMessage();
                if (maHang == null && isMissingMaHangError(detail)) {
                    JOptionPane.showMessageDialog(this,
                        "Bảng Goods yêu cầu nhập Mã hàng. Vui lòng nhập Mã hàng rồi thử lại.",
                        "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                if (maHang != null && isIdentityInsertError(detail)) {
                    JOptionPane.showMessageDialog(this,
                        "Cột Mã hàng đang tự tăng. Vui lòng để trống Mã hàng rồi thử lại.",
                        "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                JOptionPane.showMessageDialog(this,
                    "Không thể thêm hàng hóa. Chi tiết: " + detail,
                    "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── DB: Insert hàng hóa (có KhoiLuong) ───────────────────────────────────
    private int insertGoods(Integer maHang, String tenHang, String donViTinh,
                            BigDecimal khoiLuong, BigDecimal giaNhap,
                            boolean laHangBan, int soLuongTon) throws SQLException {
        ensureSqlServerDriverLoaded();
        BigDecimal giaBan = calculateSellingPrice(giaNhap);

        String sqlWithMa =
            "INSERT INTO Goods (MaHang, TenHang, DonViTinh, KhoiLuong, GiaNhap, GiaBan, LaHangBan, SoLuongTon) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlWithoutMa =
            "INSERT INTO Goods (TenHang, DonViTinh, KhoiLuong, GiaNhap, GiaBan, LaHangBan, SoLuongTon) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            if (maHang != null) {
                try (PreparedStatement st = conn.prepareStatement(sqlWithMa)) {
                    st.setInt(1, maHang);
                    st.setString(2, tenHang);
                    st.setString(3, donViTinh);
                    st.setBigDecimal(4, khoiLuong.setScale(3, RoundingMode.HALF_UP));
                    st.setBigDecimal(5, giaNhap);
                    st.setBigDecimal(6, giaBan);
                    st.setBoolean(7, laHangBan);
                    st.setInt(8, soLuongTon);
                    st.executeUpdate();
                }
                return maHang;
            }
            try (PreparedStatement st = conn.prepareStatement(sqlWithoutMa, Statement.RETURN_GENERATED_KEYS)) {
                st.setString(1, tenHang);
                st.setString(2, donViTinh);
                st.setBigDecimal(3, khoiLuong.setScale(3, RoundingMode.HALF_UP));
                st.setBigDecimal(4, giaNhap);
                st.setBigDecimal(5, giaBan);
                st.setBoolean(6, laHangBan);
                st.setInt(7, soLuongTon);
                int affected = st.executeUpdate();
                if (affected == 0) throw new SQLException("Không có bản ghi nào được thêm.");
                try (ResultSet keys = st.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
                return -1;
            }
        }
    }

    // ── DB: Các thao tác khác ─────────────────────────────────────────────────
    private BigDecimal getCurrentGoodsPrice(int maHang) throws SQLException {
        ensureSqlServerDriverLoaded();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement st = conn.prepareStatement(
                 "SELECT GiaNhap FROM Goods WHERE MaHang = ?")) {
            st.setInt(1, maHang);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("GiaNhap");
            }
        }
        throw new SQLException("Mã hàng không tồn tại.");
    }

    private void updateGoodsPrice(int maHang, BigDecimal giaNhap) throws SQLException {
        ensureSqlServerDriverLoaded();
        BigDecimal giaBan = calculateSellingPrice(giaNhap);
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement st = conn.prepareStatement(
                 "UPDATE Goods SET GiaNhap=?, GiaBan=? WHERE MaHang=?")) {
            st.setBigDecimal(1, giaNhap);
            st.setBigDecimal(2, giaBan);
            st.setInt(3, maHang);
            if (st.executeUpdate() == 0) throw new SQLException("Mã hàng không tồn tại hoặc đã bị xóa.");
        }
    }

    private BigDecimal calculateSellingPrice(BigDecimal giaNhap) {
        return giaNhap.multiply(new BigDecimal("1.2")).setScale(3, RoundingMode.HALF_UP);
    }

    private void handleDeleteGoods() {
        GoodsOption sel = getSelectedGoodsForAction();
        if (sel == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn loại hàng cần xóa.",
                "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            int relatedCount = countRelatedInOutRecords(sel.maHang);
            StringBuilder msg = new StringBuilder("Bạn có chắc muốn xóa loại hàng này?\n")
                .append(sel);
            if (relatedCount > 0)
                msg.append("\nLoại hàng có ").append(relatedCount)
                   .append(" phiếu nhập/xuất liên quan.\nNếu tiếp tục, hệ thống sẽ xóa luôn các phiếu đó.");

            if (JOptionPane.showConfirmDialog(this, msg.toString(), "Xác nhận xóa",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;

            int deletedInOut = deleteGoods(sel.maHang);
            String logWarning = null;
            try {
                saveGoodsDeleteAuditLog(sel.maHang, sel.tenHang, deletedInOut);
            } catch (SQLException logEx) { logWarning = logEx.getMessage(); }

            String successMsg = "Xóa hàng hóa thành công.";
            if (deletedInOut > 0) successMsg += " Đã xóa " + deletedInOut + " phiếu nhập/xuất liên quan.";
            JOptionPane.showMessageDialog(this, successMsg, "Thành công", JOptionPane.INFORMATION_MESSAGE);
            if (logWarning != null && !logWarning.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Đã xóa hàng hóa nhưng không thể ghi nhật ký. Chi tiết: " + logWarning,
                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            }
            closeStockActionPanel();
            loadGoodsData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Không thể xóa hàng hóa. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu", JOptionPane.ERROR_MESSAGE);
        }
    }

    private GoodsOption getSelectedGoodsForAction() {
        int row = goodsTable.getSelectedRow();
        if (row >= 0) {
            Object ma  = goodsTableModel.getValueAt(row, COL_MA_HANG);
            Object ten = goodsTableModel.getValueAt(row, COL_TEN_HANG);
            if (ma instanceof Integer) return new GoodsOption((Integer) ma, String.valueOf(ten));
        }
        return goodsComboBox == null ? null : (GoodsOption) goodsComboBox.getSelectedItem();
    }

    private int countRelatedInOutRecords(int maHang) throws SQLException {
        ensureSqlServerDriverLoaded();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement st = conn.prepareStatement("SELECT COUNT(*) FROM InOut WHERE MaHang=?")) {
            st.setInt(1, maHang);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int deleteGoods(int maHang) throws SQLException {
        ensureSqlServerDriverLoaded();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try {
                int deletedInOut;
                try (PreparedStatement st = conn.prepareStatement("DELETE FROM InOut WHERE MaHang=?")) {
                    st.setInt(1, maHang); deletedInOut = st.executeUpdate();
                }
                try (PreparedStatement st = conn.prepareStatement("DELETE FROM Goods WHERE MaHang=?")) {
                    st.setInt(1, maHang);
                    if (st.executeUpdate() == 0) throw new SQLException("Mã hàng không tồn tại hoặc đã bị xóa.");
                }
                conn.commit();
                return deletedInOut;
            } catch (SQLException ex) { conn.rollback(); throw ex; }
            finally { conn.setAutoCommit(true); }
        }
    }

    private void saveGoodsDeleteAuditLog(int maHang, String tenHang, int deletedInOutCount) throws SQLException {
        ensureSqlServerDriverLoaded();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            ensureDeleteAuditTableExists(conn);
            try (PreparedStatement st = conn.prepareStatement(
                "INSERT INTO GoodsDeleteAudit (MaHang,TenHang,DeletedInOutCount,ManagerID,ManagerName,Details) " +
                "VALUES (?,?,?,?,?,?)")) {
                st.setInt(1, maHang);
                st.setString(2, normalizeText(tenHang));
                st.setInt(3, deletedInOutCount);
                st.setString(4, operatorManagerId);
                st.setString(5, operatorManagerName);
                st.setString(6, "Xóa hàng hóa tại màn hình Kiểm tra kho hàng.");
                st.executeUpdate();
            }
        }
    }

    private void ensureDeleteAuditTableExists(Connection conn) throws SQLException {
        if (deleteAuditTableEnsured) return;
        try (PreparedStatement st = conn.prepareStatement(
            "IF OBJECT_ID(N'dbo.GoodsDeleteAudit',N'U') IS NULL " +
            "BEGIN CREATE TABLE dbo.GoodsDeleteAudit (" +
            "AuditId INT IDENTITY(1,1) PRIMARY KEY," +
            "MaHang INT NOT NULL,TenHang NVARCHAR(255) NULL," +
            "DeletedInOutCount INT NOT NULL,ManagerID NVARCHAR(50) NULL," +
            "ManagerName NVARCHAR(255) NULL," +
            "DeletedAt DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()," +
            "Details NVARCHAR(500) NULL) END")) {
            st.execute();
        }
        deleteAuditTableEnsured = true;
    }

    private int saveInOutTransaction(int maHang, int soLuong, String loaiPhieu, String ghiChu) throws SQLException {
        ensureSqlServerDriverLoaded();
        boolean isNhap = STOCK_ACTION_IN.equals(loaiPhieu);
        boolean isXuat = STOCK_ACTION_OUT.equals(loaiPhieu);
        if (!isNhap && !isXuat) throw new SQLException("Loại phiếu không hợp lệ.");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try {
                int remaining;
                try (PreparedStatement st = conn.prepareStatement(
                    "SELECT SoLuongTon FROM Goods WITH (UPDLOCK,ROWLOCK) WHERE MaHang=?")) {
                    st.setInt(1, maHang);
                    try (ResultSet rs = st.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Mã hàng không tồn tại.");
                        int ton = rs.getInt("SoLuongTon");
                        if (isXuat && ton < soLuong) throw new SQLException("Không đủ số lượng tồn để xuất.");
                        remaining = isNhap ? ton + soLuong : ton - soLuong;
                    }
                }
                try (PreparedStatement st = conn.prepareStatement(
                    "INSERT INTO InOut (MaHang,SoLuong,LoaiPhieu,GhiChu) VALUES (?,?,?,?)")) {
                    st.setInt(1, maHang);
                    st.setInt(2, soLuong);
                    st.setString(3, loaiPhieu);
                    if (ghiChu == null || ghiChu.isEmpty()) st.setNull(4, Types.NVARCHAR);
                    else st.setString(4, ghiChu);
                    st.executeUpdate();
                }
                conn.commit();
                return remaining;
            } catch (SQLException ex) { conn.rollback(); throw ex; }
            finally { conn.setAutoCommit(true); }
        }
    }

    // ── Load dữ liệu ─────────────────────────────────────────────────────────
    private void loadGoodsData() {
        if (goodsTableModel == null) return;

        Integer selectedMa = null;
        GoodsOption cur = goodsComboBox != null ? (GoodsOption) goodsComboBox.getSelectedItem() : null;
        if (cur != null) selectedMa = cur.maHang;

        goodsTableModel.setRowCount(0);
        DefaultComboBoxModel<GoodsOption> comboModel = new DefaultComboBoxModel<>();

        try {
            ensureSqlServerDriverLoaded();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement st = conn.prepareStatement(GOODS_QUERY);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int      maHang     = rs.getInt("MaHang");
                    String   tenHang    = rs.getString("TenHang");
                    String   donViTinh  = rs.getString("DonViTinh");
                    BigDecimal khoiLuong = rs.getBigDecimal("KhoiLuong"); // có thể NULL
                    BigDecimal giaNhap  = rs.getBigDecimal("GiaNhap");
                    BigDecimal giaBan   = rs.getBigDecimal("GiaBan");
                    boolean  laHangBan  = rs.getBoolean("LaHangBan");
                    int      soLuongTon = rs.getInt("SoLuongTon");

                    // KhoiLuong NULL → hiển thị trống
                    String klText = (khoiLuong == null) ? ""
                        : khoiLuong.setScale(3, RoundingMode.HALF_UP).toPlainString();

                    goodsTableModel.addRow(new Object[]{
                        maHang,
                        tenHang,
                        donViTinh,
                        klText,                                             // COL_KHOI_LUONG
                        formatPrice(giaNhap),                               // COL_GIA_NHAP
                        formatPrice(giaBan),                                // COL_GIA_BAN
                        laHangBan ? "Có" : "Không",                        // COL_LA_HANG_BAN
                        soLuongTon                                          // COL_SO_LUONG_TON
                    });
                    comboModel.addElement(new GoodsOption(maHang, tenHang));
                }
            }
            goodsComboBox.setModel(comboModel);
            if (selectedMa != null) {
                selectGoodsInCombo(selectedMa);
                selectGoodsInTable(selectedMa);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                "Không thể tải dữ liệu Goods. Chi tiết: " + ex.getMessage(),
                "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void syncComboWithSelectedRow() {
        int row = goodsTable.getSelectedRow();
        if (row < 0) return;
        Object val = goodsTableModel.getValueAt(row, COL_MA_HANG);
        if (val instanceof Integer) selectGoodsInCombo((Integer) val);
    }

    private void selectGoodsInCombo(int maHang) {
        for (int i = 0; i < goodsComboBox.getItemCount(); i++) {
            GoodsOption o = goodsComboBox.getItemAt(i);
            if (o != null && o.maHang == maHang) { goodsComboBox.setSelectedIndex(i); return; }
        }
    }

    private void selectGoodsInTable(int maHang) {
        for (int r = 0; r < goodsTableModel.getRowCount(); r++) {
            Object val = goodsTableModel.getValueAt(r, COL_MA_HANG);
            if (val instanceof Integer && (Integer) val == maHang) {
                goodsTable.setRowSelectionInterval(r, r);
                goodsTable.scrollRectToVisible(goodsTable.getCellRect(r, 0, true));
                return;
            }
        }
    }

    private String formatPrice(BigDecimal val) {
        if (val == null) return "";
        return val.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private void ensureSqlServerDriverLoaded() throws SQLException {
        try { Class.forName(SQLSERVER_DRIVER); }
        catch (ClassNotFoundException ex) {
            throw new SQLException(
                "Không tìm thấy JDBC driver SQL Server (mssql-jdbc). " +
                "Hãy thêm file mssql-jdbc-*.jar vào classpath.", ex);
        }
    }

    private static String normalizeText(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private boolean isMissingMaHangError(String msg) {
        String l = msg.toLowerCase();
        return l.contains("mahang") && (l.contains("null") || l.contains("cannot insert"));
    }

    private boolean isIdentityInsertError(String msg) {
        return msg.toLowerCase().contains("identity");
    }

    // ── Inner classes ─────────────────────────────────────────────────────────
    private static class GoodsOption {
        final int    maHang;
        final String tenHang;
        GoodsOption(int maHang, String tenHang) {
            this.maHang  = maHang;
            this.tenHang = tenHang;
        }
        @Override public String toString() { return maHang + " - " + tenHang; }
    }
}