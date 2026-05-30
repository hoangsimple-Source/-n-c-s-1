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
    private static final String STOCK_ACTION_IN = "Nhập";
    private static final String STOCK_ACTION_OUT = "Xuất";

    private static final String SQLSERVER_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    private static final String GOODS_QUERY =
        "SELECT MaHang, TenHang, DonViTinh, GiaNhap, GiaBan, LaHangBan, SoLuongTon " +
        "FROM Goods ORDER BY MaHang";

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
        this.operatorManagerId = normalizeText(managerId);
        this.operatorManagerName = normalizeText(managerName);
        buildUi();
        loadGoodsData();
    }

    public void refreshData() {
        loadGoodsData();
    }

    private void buildUi() {
        setLayout(new BorderLayout(10, 10));
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel topPanel = UiTheme.pageHeader("Kiểm tra kho hàng", "Danh sách hàng hóa hiện tại trong bảng Goods");
        add(topPanel, BorderLayout.NORTH);

        String[] columns = {"Mã hàng", "Tên hàng", "Đơn vị tính", "Giá nhập (triệu VND)", "Giá bán (triệu VND)", "Là hàng bán", "Số lượng tồn"};
        goodsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        goodsTable = new JTable(goodsTableModel);
        UiTheme.styleTable(goodsTable);
        goodsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        goodsTable.getSelectionModel().addListSelectionListener(e -> syncComboWithSelectedRow());
        JScrollPane tableScrollPane = new JScrollPane(goodsTable);
        UiTheme.styleScrollPane(tableScrollPane);
        add(tableScrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setOpaque(false);

        JPanel actionsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionsBar.setOpaque(false);
        JButton addButton = new JButton("Thêm số lượng");
        JButton removeButton = new JButton("Xóa số lượng");
        JButton updatePriceButton = new JButton("Thay đổi giá nhập");
        JButton addGoodsButton = new JButton("Thêm hàng hóa");
        JButton deleteGoodsButton = new JButton("Xóa hàng hóa");
        JButton refreshButton = new JButton("Làm mới");
        UiTheme.stylePrimaryButton(addButton);
        UiTheme.styleSecondaryButton(removeButton);
        UiTheme.styleSecondaryButton(updatePriceButton);
        UiTheme.styleSecondaryButton(addGoodsButton);
        UiTheme.styleDangerButton(deleteGoodsButton);
        UiTheme.styleSecondaryButton(refreshButton);

        addButton.addActionListener(e -> openStockActionPanel(STOCK_ACTION_IN));
        removeButton.addActionListener(e -> openStockActionPanel(STOCK_ACTION_OUT));
        updatePriceButton.addActionListener(e -> openUpdatePriceDialog());
        addGoodsButton.addActionListener(e -> openAddGoodsDialog());
        deleteGoodsButton.addActionListener(e -> handleDeleteGoods());
        refreshButton.addActionListener(e -> loadGoodsData());

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
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        JLabel actionTypeLabel = new JLabel("Loại phiếu:");
        actionTypeLabel.setFont(UiTheme.font(Font.BOLD, 13));
        actionTypeValueLabel = new JLabel("-");
        actionTypeValueLabel.setFont(UiTheme.font(Font.BOLD, 13));
        actionTypeValueLabel.setForeground(UiTheme.TEAL);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(actionTypeLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(actionTypeValueLabel, gbc);

        JLabel goodsLabel = new JLabel("Loại hàng:");
        goodsComboBox = new JComboBox<>();
        UiTheme.styleField(goodsComboBox);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(goodsLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(goodsComboBox, gbc);

        JLabel qtyLabel = new JLabel("Số lượng:");
        quantityField = new JTextField();
        UiTheme.styleField(quantityField);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(qtyLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(quantityField, gbc);

        JLabel noteLabel = new JLabel("Ghi chú:");
        noteArea = new JTextArea(3, 20);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        UiTheme.styleField(noteArea);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(noteLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(new JScrollPane(noteArea), gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton confirmButton = new JButton("Xác nhận");
        JButton cancelButton = new JButton("Đóng");
        UiTheme.stylePrimaryButton(confirmButton);
        UiTheme.styleSecondaryButton(cancelButton);

        confirmButton.addActionListener(e -> executeStockAction());
        cancelButton.addActionListener(e -> closeStockActionPanel());

        buttons.add(cancelButton);
        buttons.add(confirmButton);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(buttons, gbc);

        return panel;
    }

    private void openStockActionPanel(String actionType) {
        if (goodsComboBox.getItemCount() == 0) {
            JOptionPane.showMessageDialog(
                this,
                "Không có dữ liệu hàng hóa để thao tác.",
                "Thiếu dữ liệu",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        pendingStockAction = actionType;
        actionTypeValueLabel.setText(actionType);
        quantityField.setText("");
        noteArea.setText("");

        int selectedRow = goodsTable.getSelectedRow();
        if (selectedRow >= 0) {
            Integer maHang = (Integer) goodsTableModel.getValueAt(selectedRow, 0);
            selectGoodsInCombo(maHang);
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
        if (pendingStockAction == null) {
            return;
        }

        GoodsOption goods = (GoodsOption) goodsComboBox.getSelectedItem();
        if (goods == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn loại hàng.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int soLuong;
        try {
            soLuong = Integer.parseInt(quantityField.getText().trim());
            if (soLuong <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số lượng phải là số nguyên dương.", "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String ghiChu = noteArea.getText().trim();
        String stockAction = pendingStockAction;

        try {
            int remainingStock = saveInOutTransaction(goods.maHang, soLuong, stockAction, ghiChu);
            JOptionPane.showMessageDialog(this, "Cập nhật kho hàng thành công.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            if (STOCK_ACTION_OUT.equals(stockAction) && remainingStock < 10) {
                JOptionPane.showMessageDialog(
                    this,
                    "Cảnh báo: Mặt hàng còn dưới 10 món trong kho! Hãy nhanh chóng nhập hàng!"
                );
            }
            closeStockActionPanel();
            loadGoodsData();
            selectGoodsInTable(goods.maHang);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể thực hiện thao tác kho. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void openUpdatePriceDialog() {
        GoodsOption selectedGoods = getSelectedGoodsForManageAction();
        if (selectedGoods == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn loại hàng cần thay đổi giá nhập.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BigDecimal currentPrice;
        try {
            currentPrice = getCurrentGoodsPrice(selectedGoods.maHang);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể tải giá nhập hiện tại. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        JTextField priceField = new JTextField(currentPrice.toPlainString());
        UiTheme.styleField(priceField);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Hàng hóa:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(new JLabel(selectedGoods.toString()), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Giá nhập:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(priceField, gbc);

        while (true) {
            int option = JOptionPane.showConfirmDialog(
                this,
                formPanel,
                "Thay đổi giá nhập",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );
            if (option != JOptionPane.OK_OPTION) {
                return;
            }

            BigDecimal newPrice;
            try {
                newPrice = new BigDecimal(priceField.getText().trim());
                if (newPrice.signum() < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Giá nhập phải là số >= 0.", "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            try {
                updateGoodsPrice(selectedGoods.maHang, newPrice);
                JOptionPane.showMessageDialog(this, "Thay đổi giá nhập thành công.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                closeStockActionPanel();
                loadGoodsData();
                selectGoodsInTable(selectedGoods.maHang);
                return;
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(
                    this,
                    "Không thể thay đổi giá nhập. Chi tiết: " + ex.getMessage(),
                    "Lỗi dữ liệu",
                    JOptionPane.ERROR_MESSAGE
                );
                return;
            }
        }
    }

    private void openAddGoodsDialog() {
        JTextField maHangField = new JTextField();
        JTextField tenHangField = new JTextField();
        JTextField donViTinhField = new JTextField();
        JTextField giaNhapField = new JTextField();
        JTextField soLuongTonField = new JTextField("0");
        JComboBox<String> laHangBanCombo = new JComboBox<>(new String[] {"Có", "Không"});

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Mã hàng (để trống nếu tự tăng):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(maHangField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Tên hàng:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(tenHangField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Đơn vị tính:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(donViTinhField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Giá nhập:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(giaNhapField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Là hàng bán:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(laHangBanCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Số lượng tồn:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(soLuongTonField, gbc);

        while (true) {
            int option = JOptionPane.showConfirmDialog(
                this,
                formPanel,
                "Thêm hàng hóa",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );
            if (option != JOptionPane.OK_OPTION) {
                return;
            }

            Integer maHang = null;
            String maHangText = maHangField.getText().trim();
            if (!maHangText.isEmpty()) {
                try {
                    maHang = Integer.parseInt(maHangText);
                    if (maHang <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(
                        this,
                        "Mã hàng phải là số nguyên dương hoặc để trống.",
                        "Dữ liệu không hợp lệ",
                        JOptionPane.WARNING_MESSAGE
                    );
                    continue;
                }
            }

            String tenHang = tenHangField.getText().trim();
            if (tenHang.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập tên hàng.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            String donViTinh = donViTinhField.getText().trim();
            if (donViTinh.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đơn vị tính.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            BigDecimal giaNhap;
            try {
                giaNhap = new BigDecimal(giaNhapField.getText().trim());
                if (giaNhap.signum() < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Giá nhập phải là số >= 0.", "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            int soLuongTon;
            try {
                soLuongTon = Integer.parseInt(soLuongTonField.getText().trim());
                if (soLuongTon < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                    this,
                    "Số lượng tồn phải là số nguyên >= 0.",
                    "Dữ liệu không hợp lệ",
                    JOptionPane.WARNING_MESSAGE
                );
                continue;
            }

            boolean laHangBan = laHangBanCombo.getSelectedIndex() == 0;

            try {
                int insertedMaHang = insertGoods(maHang, tenHang, donViTinh, giaNhap, laHangBan, soLuongTon);
                JOptionPane.showMessageDialog(this, "Thêm hàng hóa thành công.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                loadGoodsData();
                if (insertedMaHang > 0) {
                    selectGoodsInCombo(insertedMaHang);
                    selectGoodsInTable(insertedMaHang);
                }
                return;
            } catch (SQLException ex) {
                String detail = ex.getMessage() == null ? "" : ex.getMessage();
                if (maHang == null && isMissingMaHangError(detail)) {
                    JOptionPane.showMessageDialog(
                        this,
                        "Bảng Goods yêu cầu nhập Mã hàng. Vui lòng nhập Mã hàng rồi thử lại.",
                        "Thiếu dữ liệu",
                        JOptionPane.WARNING_MESSAGE
                    );
                    continue;
                }
                if (maHang != null && isIdentityInsertError(detail)) {
                    JOptionPane.showMessageDialog(
                        this,
                        "Cột Mã hàng đang tự tăng. Vui lòng để trống Mã hàng rồi thử lại.",
                        "Dữ liệu không hợp lệ",
                        JOptionPane.WARNING_MESSAGE
                    );
                    continue;
                }

                JOptionPane.showMessageDialog(
                    this,
                    "Không thể thêm hàng hóa. Chi tiết: " + detail,
                    "Lỗi dữ liệu",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private int insertGoods(
        Integer maHang,
        String tenHang,
        String donViTinh,
        BigDecimal giaNhap,
        boolean laHangBan,
        int soLuongTon
    ) throws SQLException {
        ensureSqlServerDriverLoaded();
        BigDecimal giaBan = calculateSellingPrice(giaNhap);

        String insertWithMaSql =
            "INSERT INTO Goods (MaHang, TenHang, DonViTinh, GiaNhap, GiaBan, LaHangBan, SoLuongTon) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String insertWithoutMaSql =
            "INSERT INTO Goods (TenHang, DonViTinh, GiaNhap, GiaBan, LaHangBan, SoLuongTon) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            if (maHang != null) {
                try (PreparedStatement stmt = conn.prepareStatement(insertWithMaSql)) {
                    stmt.setInt(1, maHang);
                    stmt.setString(2, tenHang);
                    stmt.setString(3, donViTinh);
                    stmt.setBigDecimal(4, giaNhap);
                    stmt.setBigDecimal(5, giaBan);
                    stmt.setBoolean(6, laHangBan);
                    stmt.setInt(7, soLuongTon);
                    stmt.executeUpdate();
                }
                return maHang;
            }

            try (PreparedStatement stmt = conn.prepareStatement(insertWithoutMaSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, tenHang);
                stmt.setString(2, donViTinh);
                stmt.setBigDecimal(3, giaNhap);
                stmt.setBigDecimal(4, giaBan);
                stmt.setBoolean(5, laHangBan);
                stmt.setInt(6, soLuongTon);

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Không có bản ghi nào được thêm.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
                return -1;
            }
        }
    }

    private BigDecimal getCurrentGoodsPrice(int maHang) throws SQLException {
        ensureSqlServerDriverLoaded();

        String selectSql = "SELECT GiaNhap FROM Goods WHERE MaHang = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setInt(1, maHang);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("GiaNhap");
                }
            }
        }
        throw new SQLException("Mã hàng không tồn tại.");
    }

    private void updateGoodsPrice(int maHang, BigDecimal giaNhap) throws SQLException {
        ensureSqlServerDriverLoaded();
        BigDecimal giaBan = calculateSellingPrice(giaNhap);

        String updateSql = "UPDATE Goods SET GiaNhap = ?, GiaBan = ? WHERE MaHang = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            stmt.setBigDecimal(1, giaNhap);
            stmt.setBigDecimal(2, giaBan);
            stmt.setInt(3, maHang);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Mã hàng không tồn tại hoặc đã bị xóa.");
            }
        }
    }

    private BigDecimal calculateSellingPrice(BigDecimal giaNhap) {
        return giaNhap.multiply(new BigDecimal("1.2")).setScale(3, RoundingMode.HALF_UP);
    }

    private void handleDeleteGoods() {
        GoodsOption selectedGoods = getSelectedGoodsForManageAction();
        if (selectedGoods == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn loại hàng cần xóa.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int relatedInOutCount = countRelatedInOutRecords(selectedGoods.maHang);

            StringBuilder confirmMessage = new StringBuilder();
            confirmMessage.append("Bạn có chắc muốn xóa loại hàng này?\n").append(selectedGoods.toString());
            if (relatedInOutCount > 0) {
                confirmMessage
                    .append("\nLoại hàng này đang có ")
                    .append(relatedInOutCount)
                    .append(" phiếu nhập/xuất liên quan.")
                    .append("\nNếu tiếp tục, hệ thống sẽ xóa luôn các phiếu đó.");
            }

            int confirm = JOptionPane.showConfirmDialog(
                this,
                confirmMessage.toString(),
                "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            int deletedInOutCount = deleteGoods(selectedGoods.maHang);
            String logWarning = null;
            try {
                saveGoodsDeleteAuditLog(selectedGoods.maHang, selectedGoods.tenHang, deletedInOutCount);
            } catch (SQLException logEx) {
                logWarning = logEx.getMessage();
            }

            String successMessage = "Xóa hàng hóa thành công.";
            if (deletedInOutCount > 0) {
                successMessage += " Đã xóa " + deletedInOutCount + " phiếu nhập/xuất liên quan.";
            }
            JOptionPane.showMessageDialog(this, successMessage, "Thành công", JOptionPane.INFORMATION_MESSAGE);
            if (logWarning != null && !logWarning.trim().isEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    "Đã xóa hàng hóa nhưng không thể ghi nhật ký thao tác. Chi tiết: " + logWarning,
                    "Cảnh báo",
                    JOptionPane.WARNING_MESSAGE
                );
            }
            closeStockActionPanel();
            loadGoodsData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể xóa hàng hóa. Chi tiết: " + ex.getMessage(),
                "Lỗi dữ liệu",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private GoodsOption getSelectedGoodsForManageAction() {
        int selectedRow = goodsTable.getSelectedRow();
        if (selectedRow >= 0) {
            Object maHangValue = goodsTableModel.getValueAt(selectedRow, 0);
            Object tenHangValue = goodsTableModel.getValueAt(selectedRow, 1);
            if (maHangValue instanceof Integer) {
                return new GoodsOption((Integer) maHangValue, String.valueOf(tenHangValue));
            }
        }

        return goodsComboBox == null ? null : (GoodsOption) goodsComboBox.getSelectedItem();
    }

    private int countRelatedInOutRecords(int maHang) throws SQLException {
        ensureSqlServerDriverLoaded();

        String countSql = "SELECT COUNT(*) FROM InOut WHERE MaHang = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(countSql)) {
            stmt.setInt(1, maHang);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int deleteGoods(int maHang) throws SQLException {
        ensureSqlServerDriverLoaded();

        String deleteInOutSql = "DELETE FROM InOut WHERE MaHang = ?";
        String deleteGoodsSql = "DELETE FROM Goods WHERE MaHang = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try {
                int deletedInOutCount;
                try (PreparedStatement deleteInOutStmt = conn.prepareStatement(deleteInOutSql)) {
                    deleteInOutStmt.setInt(1, maHang);
                    deletedInOutCount = deleteInOutStmt.executeUpdate();
                }

                try (PreparedStatement deleteGoodsStmt = conn.prepareStatement(deleteGoodsSql)) {
                    deleteGoodsStmt.setInt(1, maHang);
                    int affectedRows = deleteGoodsStmt.executeUpdate();
                    if (affectedRows == 0) {
                        throw new SQLException("Mã hàng không tồn tại hoặc đã bị xóa.");
                    }
                }

                conn.commit();
                return deletedInOutCount;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void saveGoodsDeleteAuditLog(int maHang, String tenHang, int deletedInOutCount) throws SQLException {
        ensureSqlServerDriverLoaded();

        String insertLogSql =
            "INSERT INTO GoodsDeleteAudit " +
            "(MaHang, TenHang, DeletedInOutCount, ManagerID, ManagerName, Details) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            ensureDeleteAuditTableExists(conn);

            try (PreparedStatement insertStmt = conn.prepareStatement(insertLogSql)) {
                String detail = "Xóa hàng hóa tại màn hình Kiểm tra kho hàng.";
                insertStmt.setInt(1, maHang);
                insertStmt.setString(2, normalizeText(tenHang));
                insertStmt.setInt(3, deletedInOutCount);
                insertStmt.setString(4, operatorManagerId);
                insertStmt.setString(5, operatorManagerName);
                insertStmt.setString(6, detail);
                insertStmt.executeUpdate();
            }
        }
    }

    private void ensureDeleteAuditTableExists(Connection conn) throws SQLException {
        if (deleteAuditTableEnsured) {
            return;
        }

        String createTableSql =
            "IF OBJECT_ID(N'dbo.GoodsDeleteAudit', N'U') IS NULL " +
            "BEGIN " +
            "CREATE TABLE dbo.GoodsDeleteAudit (" +
            "AuditId INT IDENTITY(1,1) PRIMARY KEY, " +
            "MaHang INT NOT NULL, " +
            "TenHang NVARCHAR(255) NULL, " +
            "DeletedInOutCount INT NOT NULL, " +
            "ManagerID NVARCHAR(50) NULL, " +
            "ManagerName NVARCHAR(255) NULL, " +
            "DeletedAt DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(), " +
            "Details NVARCHAR(500) NULL" +
            "); " +
            "END";

        try (PreparedStatement stmt = conn.prepareStatement(createTableSql)) {
            stmt.execute();
        }
        deleteAuditTableEnsured = true;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isMissingMaHangError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("mahang") && (lower.contains("null") || lower.contains("cannot insert"));
    }

    private boolean isIdentityInsertError(String message) {
        return message.toLowerCase().contains("identity");
    }

    private int saveInOutTransaction(int maHang, int soLuong, String loaiPhieu, String ghiChu) throws SQLException {
        ensureSqlServerDriverLoaded();

        boolean isNhap = STOCK_ACTION_IN.equals(loaiPhieu);
        boolean isXuat = STOCK_ACTION_OUT.equals(loaiPhieu);
        if (!isNhap && !isXuat) {
            throw new SQLException("Loại phiếu không hợp lệ.");
        }

        String checkStockSql = "SELECT SoLuongTon FROM Goods WITH (UPDLOCK, ROWLOCK) WHERE MaHang = ?";
        String insertSql = "INSERT INTO InOut (MaHang, SoLuong, LoaiPhieu, GhiChu) VALUES (?, ?, ?, ?)";
        int remainingStock = 0;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement checkStmt = conn.prepareStatement(checkStockSql)) {
                    checkStmt.setInt(1, maHang);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Mã hàng không tồn tại.");
                        }

                        int soLuongTon = rs.getInt("SoLuongTon");
                        if (isXuat && soLuongTon < soLuong) {
                            throw new SQLException("Không đủ số lượng tồn để xuất.");
                        }
                        remainingStock = isNhap ? soLuongTon + soLuong : soLuongTon - soLuong;
                    }
                }

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, maHang);
                    insertStmt.setInt(2, soLuong);
                    insertStmt.setString(3, loaiPhieu);
                    if (ghiChu == null || ghiChu.isEmpty()) {
                        insertStmt.setNull(4, Types.NVARCHAR);
                    } else {
                        insertStmt.setString(4, ghiChu);
                    }
                    insertStmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return remainingStock;
    }

    private void loadGoodsData() {
        if (goodsTableModel == null) {
            return;
        }

        Integer selectedMaHang = null;
        GoodsOption selectedOption = goodsComboBox != null ? (GoodsOption) goodsComboBox.getSelectedItem() : null;
        if (selectedOption != null) {
            selectedMaHang = selectedOption.maHang;
        }

        goodsTableModel.setRowCount(0);
        DefaultComboBoxModel<GoodsOption> comboModel = new DefaultComboBoxModel<>();

        try {
            ensureSqlServerDriverLoaded();
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement stmt = conn.prepareStatement(GOODS_QUERY);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int maHang = rs.getInt("MaHang");
                    String tenHang = rs.getString("TenHang");
                    String donViTinh = rs.getString("DonViTinh");
                    BigDecimal giaNhap = rs.getBigDecimal("GiaNhap");
                    BigDecimal giaBan = rs.getBigDecimal("GiaBan");
                    boolean laHangBan = rs.getBoolean("LaHangBan");
                    int soLuongTon = rs.getInt("SoLuongTon");

                    goodsTableModel.addRow(new Object[] {
                        maHang,
                        tenHang,
                        donViTinh,
                        formatPrice(giaNhap),
                        formatPrice(giaBan),
                        laHangBan ? "Có" : "Không",
                        soLuongTon
                    });

                    comboModel.addElement(new GoodsOption(maHang, tenHang));
                }
            }

            goodsComboBox.setModel(comboModel);
            if (selectedMaHang != null) {
                selectGoodsInCombo(selectedMaHang);
                selectGoodsInTable(selectedMaHang);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this,
                "Không thể tải dữ liệu Goods. Chi tiết: " + ex.getMessage(),
                "Lỗi kết nối",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void syncComboWithSelectedRow() {
        int selectedRow = goodsTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        Object value = goodsTableModel.getValueAt(selectedRow, 0);
        if (value instanceof Integer) {
            selectGoodsInCombo((Integer) value);
        }
    }

    private void selectGoodsInCombo(int maHang) {
        for (int i = 0; i < goodsComboBox.getItemCount(); i++) {
            GoodsOption option = goodsComboBox.getItemAt(i);
            if (option != null && option.maHang == maHang) {
                goodsComboBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectGoodsInTable(int maHang) {
        for (int row = 0; row < goodsTableModel.getRowCount(); row++) {
            Object value = goodsTableModel.getValueAt(row, 0);
            if (value instanceof Integer && (Integer) value == maHang) {
                goodsTable.setRowSelectionInterval(row, row);
                goodsTable.scrollRectToVisible(goodsTable.getCellRect(row, 0, true));
                return;
            }
        }
    }

    private String formatPrice(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(3, RoundingMode.HALF_UP).toPlainString();
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
}
