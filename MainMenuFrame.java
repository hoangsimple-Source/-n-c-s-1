import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;


public class MainMenuFrame extends JFrame {
    private static final String APP_TITLE = "Hệ thống quản lí kinh doanh nội bộ công ty TECOFFEE";

    private static final String CARD_HOME = "home";
    private static final String CARD_ORDER_PLACEMENT = "order_placement";
    private static final String CARD_ORDER_RECEIVING = "order_receiving";
    private static final String CARD_STOCK_CHECK = "stock_check";
    private static final String CARD_FEATURE_3 = "feature_3";
    private static final String CARD_FEATURE_4 = "feature_4";

    private static final String MENU_HOME = "Trang chủ";
    private static final String MENU_ORDER_PLACEMENT = "Đặt hàng";
    private static final String MENU_ORDER_RECEIVING = "Tiếp nhận đơn hàng";
    private static final String MENU_STOCK_CHECK = "Kiểm tra kho hàng";
    private static final String MENU_FEATURE_3 = "Đóng gói & Vận chuyển";
    private static final String MENU_FEATURE_4 = "Thống kê lợi nhuận";

    private static final int MENU_BUTTON_WIDTH = 230;
    private static final int MENU_BUTTON_HEIGHT = 42;
    private static final int MENU_BUTTON_GAP = 8;

    private static final Color LEFT_MENU_BG = UiTheme.COFFEE_DARK;
    private static final Color MENU_BUTTON_ACTIVE_BG = UiTheme.CARAMEL;
    private static final Color MENU_BUTTON_ACTIVE_FG = Color.WHITE;
    private static final Color MENU_BUTTON_INACTIVE_FG = new Color(238, 210, 188);

    private final CardLayout contentLayout;
    private final JPanel contentPanel;
    private final Map<String, JButton> menuButtons = new LinkedHashMap<>();
    private final StockCheckPanel stockCheckPanel;
    private final JPanel homeInfoPanel;
    private final ProfitStatisticsPanel profitStatisticsPanel;
    private final OrderPlacementPanel orderPlacementPanel;
    private final JPanel orderReceivingPanel;
    private final JPanel packagingShippingPanel;

    public MainMenuFrame(String managerId, String managerName) {
        setTitle(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 680);
        setMinimumSize(new Dimension(960, 600));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.APP_BG);
        root.add(buildLeftMenu(managerId, managerName), BorderLayout.WEST);

        homeInfoPanel = createHomeInfoPanel();
        profitStatisticsPanel = new ProfitStatisticsPanel(managerId, managerName);
        orderPlacementPanel = new OrderPlacementPanel(() -> showCard(CARD_ORDER_RECEIVING));
        orderReceivingPanel = createPanelSafely("OrderReceivingPanel");
        stockCheckPanel = new StockCheckPanel(managerId, managerName);
        packagingShippingPanel = createPanelSafely("PackagingShippingPanel");
            setOrderConfirmedCallbackSafely(orderReceivingPanel, () -> {
                try {
                    java.lang.reflect.Method m = packagingShippingPanel.getClass().getMethod("refreshFromDb");
                    m.invoke(packagingShippingPanel);
                } catch (Throwable ignored) {
                }
            });

        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);
        contentPanel.setBackground(UiTheme.APP_BG);
        contentPanel.add(homeInfoPanel, CARD_HOME);
        contentPanel.add(orderPlacementPanel, CARD_ORDER_PLACEMENT);
        contentPanel.add(orderReceivingPanel, CARD_ORDER_RECEIVING);
        contentPanel.add(stockCheckPanel, CARD_STOCK_CHECK);
        contentPanel.add(packagingShippingPanel, CARD_FEATURE_3);
        contentPanel.add(profitStatisticsPanel, CARD_FEATURE_4);
        root.add(contentPanel, BorderLayout.CENTER);

        setContentPane(root);
        showCard(CARD_HOME);
    }

    private JPanel createHomeInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        panel.setBackground(UiTheme.APP_BG);

        JLabel titleLabel = UiTheme.pageTitle("Trang chủ");
        panel.add(titleLabel, BorderLayout.NORTH);

        JLabel messageLabel = new JLabel(
            "<html><div style='font-size: 14px; padding-top: 8px;'>Trang chủ sẽ được cập nhật sau.</div></html>"
        );
        panel.add(messageLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPanelSafely(String className) {
        try {
            Class<?> cls = Class.forName(className);
            Object obj = cls.getDeclaredConstructor().newInstance();
            if (obj instanceof JPanel) {
                return (JPanel) obj;
            }
        } catch (Throwable ignored) {
            // Fall through to placeholder
        }
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setOpaque(false);
        JLabel label = new JLabel("Module '" + className + "' không khả dụng");
        label.setFont(UiTheme.font(Font.PLAIN, 14));
        label.setForeground(UiTheme.MUTED_TEXT);
        placeholder.add(label, BorderLayout.CENTER);
        return placeholder;
    }

    private void invokeRefreshIfPresent(Object panel) {
        if (panel == null) return;
        try {
            java.lang.reflect.Method m = panel.getClass().getMethod("refreshData");
            m.invoke(panel);
        } catch (NoSuchMethodException e) {
            // ignore - no refresh method
        } catch (Throwable ignored) {
            // ignore other invocation issues
        }
    }

    private void setOrderConfirmedCallbackSafely(Object panel, Runnable cb) {
        if (panel == null) return;
        try {
            java.lang.reflect.Method m = panel.getClass().getMethod("setOnOrderConfirmedCallback", Runnable.class);
            m.invoke(panel, cb);
        } catch (Throwable ignored) {
        }
    }

    private JPanel buildLeftMenu(String managerId, String managerName) {
        JPanel menuPanel = new JPanel(new BorderLayout());
        menuPanel.setPreferredSize(new Dimension(280, 0));
        menuPanel.setBackground(LEFT_MENU_BG);
        menuPanel.setBorder(BorderFactory.createEmptyBorder(22, 18, 18, 18));

        JLabel header = new JLabel("<html><div style='text-align:center;'>TECOFFEE<br/>QUẢN LÍ KINH DOANH</div></html>", SwingConstants.CENTER);
        header.setFont(UiTheme.font(Font.BOLD, 20));
        header.setForeground(Color.WHITE);
        menuPanel.add(header, BorderLayout.NORTH);

        String displayName = (managerName == null || managerName.trim().isEmpty())
            ? "(chưa cập nhật)"
            : managerName.trim();
        JLabel accountInfo = new JLabel(
            "<html>ManagerID: " + managerId + "<br/>Tên quản lí: " + displayName + "</html>"
        );
        accountInfo.setFont(UiTheme.font(Font.PLAIN, 13));
        accountInfo.setForeground(MENU_BUTTON_INACTIVE_FG);
        accountInfo.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(102, 64, 43)),
            BorderFactory.createEmptyBorder(16, 0, 16, 0)
        ));

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(accountInfo, BorderLayout.NORTH);
        center.add(buildMenuButtons(), BorderLayout.CENTER);
        menuPanel.add(center, BorderLayout.CENTER);

        return menuPanel;
    }

    private JPanel buildMenuButtons() {
        JPanel buttonPanel = new JPanel(new GridLayout(6, 1, 0, MENU_BUTTON_GAP));
        buttonPanel.setOpaque(false);
        buttonPanel.setPreferredSize(new Dimension(MENU_BUTTON_WIDTH, 6 * MENU_BUTTON_HEIGHT + 5 * MENU_BUTTON_GAP));

        JButton homeButton = buildMenuButton(MENU_HOME, CARD_HOME);
        JButton orderPlacementButton = buildMenuButton(MENU_ORDER_PLACEMENT, CARD_ORDER_PLACEMENT);
        JButton orderReceivingButton = buildMenuButton(MENU_ORDER_RECEIVING, CARD_ORDER_RECEIVING);
        JButton stockButton = buildMenuButton(MENU_STOCK_CHECK, CARD_STOCK_CHECK);
        JButton feature3Button = buildMenuButton(MENU_FEATURE_3, CARD_FEATURE_3);
        JButton feature4Button = buildMenuButton(MENU_FEATURE_4, CARD_FEATURE_4);

        buttonPanel.add(homeButton);
        buttonPanel.add(orderPlacementButton);
        buttonPanel.add(orderReceivingButton);
        buttonPanel.add(stockButton);
        buttonPanel.add(feature3Button);
        buttonPanel.add(feature4Button);

        JPanel leftAlignedWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftAlignedWrapper.setOpaque(false);
        leftAlignedWrapper.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        leftAlignedWrapper.add(buttonPanel);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(leftAlignedWrapper, BorderLayout.NORTH);
        return wrapper;
    }

    private JButton buildMenuButton(String text, String cardName) {
        JButton button = new JButton(text);
        button.setFont(UiTheme.font(Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT));
        button.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setForeground(MENU_BUTTON_INACTIVE_FG);

        menuButtons.put(cardName, button);
        button.addActionListener(e -> showCard(cardName));
        return button;
    }

    private JPanel createFeaturePanel(String title, String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        panel.setBackground(UiTheme.APP_BG);

        JLabel titleLabel = UiTheme.pageTitle(title);
        panel.add(titleLabel, BorderLayout.NORTH);

        JLabel messageLabel = new JLabel(
            "<html><div style='font-size: 14px; padding-top: 8px;'>" + message + "</div></html>"
        );
        panel.add(messageLabel, BorderLayout.CENTER);
        return panel;
    }

    private void showCard(String cardName) {
        if (CARD_HOME.equals(cardName)) {
            // Home panel is now empty placeholder
        }
        if (CARD_STOCK_CHECK.equals(cardName)) {
            stockCheckPanel.refreshData();
        }
        if (CARD_ORDER_PLACEMENT.equals(cardName)) {
            orderPlacementPanel.refreshData();
        }
        if (CARD_ORDER_RECEIVING.equals(cardName)) {
            invokeRefreshIfPresent(orderReceivingPanel);
        }
        if (CARD_FEATURE_4.equals(cardName)) {
            profitStatisticsPanel.refreshData();
        }
        contentLayout.show(contentPanel, cardName);
        updateActiveMenuButton(cardName);
    }

    private void updateActiveMenuButton(String activeCardName) {
        for (Map.Entry<String, JButton> entry : menuButtons.entrySet()) {
            boolean isActive = entry.getKey().equals(activeCardName);
            JButton button = entry.getValue();
            button.setOpaque(isActive);
            button.setContentAreaFilled(isActive);
            button.setBackground(isActive ? MENU_BUTTON_ACTIVE_BG : null);
            button.setForeground(isActive ? MENU_BUTTON_ACTIVE_FG : MENU_BUTTON_INACTIVE_FG);
            button.repaint();
        }
    }
}
