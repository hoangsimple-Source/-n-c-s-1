import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class LoginForm extends JFrame {
    private static final String SQLSERVER_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    // MOC_ANH_NEN_LOGIN: Đổi tên/đường dẫn ảnh nền tại đây.
    private static final String LOGIN_BACKGROUND_IMAGE_PATH = "nền form.jpg";
    private static final ImageIcon LOGIN_BACKGROUND_IMAGE_ICON = new ImageIcon(LOGIN_BACKGROUND_IMAGE_PATH);
    private static final Color LOGIN_BACKGROUND_COLOR = new Color(87, 200, 245);
    // MOC_CHINH_MAU_CHU_TIEU_DE: Đổi màu chữ tiêu đề tại đây.
    private static final Color LOGIN_TITLE_TEXT_COLOR = new Color(220, 245, 255);
    // MOC_CHINH_KHOI_NOI_BAT: Trắng với độ trong suốt 60%.
    private static final Color INPUT_HIGHLIGHT_COLOR = new Color(255, 255, 255, 153);

    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    // Đăng nhập bằng ManagerID + mật khẩu dạng thường.
    private static final String ACCOUNT_QUERY =
        "SELECT TOP 1 ManagerID, TenQuanLi " +
        "FROM Manager " +
        "WHERE ManagerID = ? " +
        "  AND TrangThai = 1 " +
        "  AND MatKhau = ?";

    private final JTextField managerIdField;
    private final JPasswordField passwordField;

    public LoginForm() {
        setTitle("Đăng nhập ứng dụng");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(720, 520);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel rootPanel = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                Image bgImage = LOGIN_BACKGROUND_IMAGE_ICON.getImage();
                if (LOGIN_BACKGROUND_IMAGE_ICON.getIconWidth() > 0 && LOGIN_BACKGROUND_IMAGE_ICON.getIconHeight() > 0) {
                    g2.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
                } else {
                    g2.setColor(LOGIN_BACKGROUND_COLOR);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.dispose();
            }
        };
        rootPanel.setOpaque(false);
        rootPanel.setBorder(BorderFactory.createEmptyBorder(28, 34, 28, 34));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.setPreferredSize(new Dimension(10, 130));

        JLabel titleLabel = new JLabel("ĐĂNG NHẬP ỨNG DỤNG QUẢN LÍ", JLabel.CENTER);
        titleLabel.setFont(UiTheme.font(Font.BOLD, 34));
        titleLabel.setForeground(LOGIN_TITLE_TEXT_COLOR);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        titlePanel.add(titleLabel, BorderLayout.SOUTH);
        rootPanel.add(titlePanel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel userLabel = new JLabel("ManagerID:");
        userLabel.setFont(UiTheme.font(Font.BOLD, 13));
        userLabel.setForeground(UiTheme.TEXT);
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(userLabel, gbc);

        managerIdField = new JTextField();
        managerIdField.setPreferredSize(new Dimension(280, 36));
        UiTheme.styleField(managerIdField);
        gbc.gridx = 1;
        formPanel.add(managerIdField, gbc);

        JLabel passLabel = new JLabel("Mật khẩu:");
        passLabel.setFont(UiTheme.font(Font.BOLD, 13));
        passLabel.setForeground(UiTheme.TEXT);
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(passLabel, gbc);

        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(280, 36));
        UiTheme.styleField(passwordField);
        gbc.gridx = 1;
        formPanel.add(passwordField, gbc);

        JPanel inputHighlightPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(INPUT_HIGHLIGHT_COLOR);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
            }
        };
        inputHighlightPanel.setOpaque(false);
        inputHighlightPanel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        inputHighlightPanel.add(formPanel);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(inputHighlightPanel);
        rootPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton loginButton = new JButton("Đăng nhập");
        JButton clearButton = new JButton("Xóa");
        loginButton.setPreferredSize(new Dimension(132, 38));
        clearButton.setPreferredSize(new Dimension(92, 38));
        UiTheme.stylePrimaryButton(loginButton);
        UiTheme.styleSecondaryButton(clearButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(loginButton);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);

        clearButton.addActionListener(e -> {
            managerIdField.setText("");
            passwordField.setText("");
            managerIdField.requestFocusInWindow();
        });

        loginButton.addActionListener(e -> handleLogin());
        passwordField.addActionListener(e -> handleLogin());

        setContentPane(rootPanel);
    }

    private void handleLogin() {
        String managerId = managerIdField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (managerId.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Vui lòng điền đầy đủ thông tin.",
                "Thất bại",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            AccountInfo account = authenticate(managerId, password);
            if (account != null) {
                JOptionPane.showMessageDialog(
                    this,
                    "Đăng nhập thành công!",
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE
                );
                openMainMenuFrame(account);
                dispose();
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Thông tin đăng nhập không trùng khớp.",
                    "Thất bại",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (SQLException ex) {
            String detail = ex.getMessage();
            if (detail != null && detail.toLowerCase().contains("no suitable driver")) {
                detail =
                    "Không tìm thấy JDBC driver SQL Server (mssql-jdbc).\n" +
                    "Hãy thêm file mssql-jdbc-*.jar vào classpath của project.";
            }

            JOptionPane.showMessageDialog(
                this,
                "Không thể kết nối SQL Server. Chi tiết: " + detail,
                "Lỗi kết nối",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private AccountInfo authenticate(String managerId, String password) throws SQLException {
        ensureSqlServerDriverLoaded();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(ACCOUNT_QUERY)) {
            stmt.setString(1, managerId);
            stmt.setString(2, password);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new AccountInfo(rs.getString("ManagerID"), rs.getString("TenQuanLi"));
            }
        }
    }

    private void ensureSqlServerDriverLoaded() throws SQLException {
        try {
            Class.forName(SQLSERVER_DRIVER);
        } catch (ClassNotFoundException ex) {
            throw new SQLException(
                "Không tìm thấy JDBC driver SQL Server (mssql-jdbc). " +
                "Hãy thêm file mssql-jdbc-*.jar vào classpath.",
                ex
            );
        }
    }

    private void openMainMenuFrame(AccountInfo account) {
        MainMenuFrame mainMenuFrame = new MainMenuFrame(account.managerId, account.managerName);
        mainMenuFrame.setVisible(true);
    }

    private static class AccountInfo {
        private final String managerId;
        private final String managerName;

        private AccountInfo(String managerId, String managerName) {
            this.managerId = managerId;
            this.managerName = managerName;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Ignore and keep default look and feel.
            }
            LoginForm frame = new LoginForm();
            frame.setVisible(true);
        });
    }
}
