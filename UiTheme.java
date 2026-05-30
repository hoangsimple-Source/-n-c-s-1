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
    public static final Color APP_BG = new Color(243, 244, 246);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(250, 252, 255);
    public static final Color BORDER = new Color(218, 225, 235);
    public static final Color TEXT = new Color(35, 43, 56);
    public static final Color MUTED_TEXT = new Color(102, 112, 133);
    public static final Color COFFEE = new Color(86, 48, 25);
    public static final Color COFFEE_DARK = new Color(58, 32, 18);
    public static final Color CARAMEL = new Color(184, 107, 43);
    public static final Color TEAL = new Color(2, 132, 199);
    public static final Color TEAL_DARK = new Color(22, 163, 74);
    public static final Color WARNING = new Color(194, 82, 39);

    private UiTheme() {
    }

    public static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

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
        panel.add(pageTitle(title), java.awt.BorderLayout.NORTH);
        panel.add(pageSubtitle(subtitle), java.awt.BorderLayout.SOUTH);
        return panel;
    }

    public static Border softBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)
        );
    }

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
            BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));
    }

    public static void styleDangerButton(JButton button) {
        styleButtonBase(button);
        button.setBackground(WARNING);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
    }

    private static void styleButtonBase(JButton button) {
        button.setFont(font(Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
    }

    public static void styleField(JComponent component) {
        component.setFont(font(Font.PLAIN, 13));
        component.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(6, 9, 6, 9)
        ));
        if (component instanceof JTextField) {
            component.setBackground(Color.WHITE);
        }
        if (component instanceof JTextArea) {
            component.setBackground(Color.WHITE);
        }
        if (component instanceof JComboBox) {
            component.setBackground(Color.WHITE);
        }
    }

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

    public static JLabel pill(String text, Color bg, Color fg) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(font(Font.BOLD, 12));
        label.setForeground(fg);
        label.setOpaque(true);
        label.setBackground(bg);
        label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return label;
    }
}
