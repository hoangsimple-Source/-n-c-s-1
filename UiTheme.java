import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;

public final class UiTheme {
    // ── Màu nền & bề mặt ─────────────────────────────────────────────────────
    public static final Color APP_BG      = new Color(243, 244, 246);
    public static final Color SURFACE     = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(250, 252, 255);
    public static final Color BORDER      = new Color(218, 225, 235);

    // ── Màu chữ ───────────────────────────────────────────────────────────────
    public static final Color TEXT        = new Color(35,  43,  56);
    public static final Color MUTED_TEXT  = new Color(102, 112, 133);

    // ── Màu thương hiệu ───────────────────────────────────────────────────────
    public static final Color COFFEE      = new Color(86,  48,  25);
    public static final Color COFFEE_DARK = new Color(58,  32,  18);
    public static final Color CARAMEL     = new Color(184, 107, 43);

    // ── Màu accent ────────────────────────────────────────────────────────────
    public static final Color TEAL        = new Color(2,   132, 199);
    public static final Color TEAL_DARK   = new Color(22,  163, 74);
    public static final Color WARNING     = new Color(194, 82,  39);

    // ── Màu trạng thái (dùng trong PackagingShippingPanel) ───────────────────
    /** Chờ xử lý — cam/vàng */
    public static final Color STATUS_WAITING  = new Color(251, 146, 60);
    /** Đang xử lý — xanh dương */
    public static final Color STATUS_PACKING  = new Color(59,  130, 246);
    /** Đã hoàn tất — xanh lá */
    public static final Color STATUS_DONE     = new Color(34,  197, 94);
    /** Đầy tải — đỏ */
    public static final Color STATUS_FULL     = new Color(239, 68,  68);
    /** Trống — xám */
    public static final Color STATUS_EMPTY    = new Color(156, 163, 175);

    /** Màu nền card nhạt — dùng cho các khung thống kê */
    public static final Color CARD_BG         = new Color(249, 250, 251);
    public static final Color CARD_BORDER     = new Color(229, 231, 235);

    private UiTheme() {}

    // ── Font ─────────────────────────────────────────────────────────────────
    public static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    // ── Label tiêu đề trang ──────────────────────────────────────────────────
    public static JLabel pageTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(font(Font.BOLD, 25));
        label.setForeground(TEXT);
        return label;
    }

    public static JLabel pageSubtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(font(Font.PLAIN, 13));
        label.setForeground(MUTED_TEXT);
        return label;
    }

    public static JPanel pageHeader(String title, String subtitle) {
        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.add(pageTitle(title),    java.awt.BorderLayout.NORTH);
        panel.add(pageSubtitle(subtitle), java.awt.BorderLayout.SOUTH);
        return panel;
    }

    // ── Border ───────────────────────────────────────────────────────────────
    public static Border softBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(12, 14, 12, 14));
    }

    // ── Button styles ─────────────────────────────────────────────────────────
    public static void stylePrimaryButton(JButton button) {
        styleButtonBase(button);
        button.setBackground(TEAL);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
    }

    public static void styleSecondaryButton(JButton button) {
        styleButtonBase(button);
        button.setBackground(new Color(241, 245, 249));
        button.setForeground(TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(8, 14, 8, 14)));
    }

    public static void styleDangerButton(JButton button) {
        styleButtonBase(button);
        button.setBackground(WARNING);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
    }

    /** Nút nhỏ màu cam — dùng cho nút "Chi tiết" trong bảng xe */
    public static void styleDetailButton(JButton button) {
        styleButtonBase(button);
        button.setBackground(CARAMEL);
        button.setForeground(Color.WHITE);
        button.setFont(font(Font.BOLD, 12));
        button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
    }

    private static void styleButtonBase(JButton button) {
        button.setFont(font(Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
    }

    // ── Field styles ─────────────────────────────────────────────────────────
    public static void styleField(JComponent component) {
        component.setFont(font(Font.PLAIN, 13));
        component.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        if (component instanceof JTextField)  component.setBackground(Color.WHITE);
        if (component instanceof JTextArea)   component.setBackground(Color.WHITE);
        if (component instanceof JComboBox)   component.setBackground(Color.WHITE);
    }

    // ── Table styles ─────────────────────────────────────────────────────────
    public static void styleTable(JTable table) {
        table.setFont(font(Font.PLAIN, 13));
        table.setRowHeight(30);
        table.setGridColor(new Color(232, 237, 244));
        table.setSelectionBackground(new Color(218, 242, 238));
        table.setSelectionForeground(TEXT);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        JTableHeader header = table.getTableHeader();
        header.setFont(font(Font.BOLD, 13));
        header.setForeground(TEXT);
        header.setBackground(new Color(235, 241, 247));
        header.setReorderingAllowed(false);
    }

    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(SURFACE);
    }

    // ── Pill label ───────────────────────────────────────────────────────────
    public static JLabel pill(String text, Color bg, Color fg) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(font(Font.BOLD, 12));
        label.setForeground(fg);
        label.setOpaque(true);
        label.setBackground(bg);
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return label;
    }

    /**
     * Tạo card thống kê nhỏ (dùng trong PackagingShippingPanel header).
     * @param icon  ký tự emoji hoặc text icon
     * @param title dòng mô tả nhỏ phía trên
     * @param value giá trị số lớn ở giữa
     */
    public static JPanel statCard(String icon, String title, String value) {
        JPanel card = new JPanel(new java.awt.GridLayout(3, 1, 0, 2));
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JLabel iconLabel = new JLabel(icon + "  " + title);
        iconLabel.setFont(font(Font.PLAIN, 12));
        iconLabel.setForeground(MUTED_TEXT);

        JLabel valueLabel = new JLabel(value, SwingConstants.LEFT);
        valueLabel.setFont(font(Font.BOLD, 28));
        valueLabel.setForeground(TEXT);

        card.add(iconLabel);
        card.add(valueLabel);
        card.add(new JLabel()); // spacer
        return card;
    }
}