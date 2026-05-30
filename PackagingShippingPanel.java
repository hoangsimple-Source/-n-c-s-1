import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class PackagingShippingPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public PackagingShippingPanel() {
        buildUi();
    }

    private void buildUi() {
        setLayout(new BorderLayout(12, 12));
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(
            UiTheme.pageHeader(
                "Đóng gói & Vận chuyển",
                "Theo dõi luồng hàng hóa qua các tổ đóng gói và băng chuyền đến từng điểm giao"
            ),
            BorderLayout.NORTH
        );
        add(new PackagingFlowPanel(), BorderLayout.CENTER);
    }

    private static final class PackagingFlowPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int LANE_COUNT = 4;
        private static final int ANIMATION_DURATION_MS = 800;
        private static final int ANIMATION_DELAY_MS = 12;
        private static final Color DARK_BORDER = new Color(7, 35, 54);
        private static final Color GOODS_BG = new Color(244, 246, 248);
        private static final Color GOODS_HIGHLIGHT = new Color(210, 235, 255);
        private static final Color PACKING_DEFAULT = new Color(37, 99, 235);
        private static final Color PACKING_ACTIVE = new Color(2, 132, 199);
        private static final Color PACKING_DONE = new Color(22, 163, 74);
        private static final String[] DESTINATIONS = {"Hà Nội", "Nghệ An", "Đà Nẵng", "Lâm Đồng", " TP HCM", "Cần Thơ"};

        private final JButton[] goodsButtons = new JButton[LANE_COUNT];
        private final JPanel[] laneBelts = new JPanel[LANE_COUNT];
        private final JButton[] packingButtons = new JButton[LANE_COUNT];
        private final JLabel[] laneStatusLabels = new JLabel[LANE_COUNT];
        private final JLabel[] laneBadgeLabels = new JLabel[LANE_COUNT];
        private final LaneStage[] laneStages = new LaneStage[LANE_COUNT];
        private final JLabel conveyorLabel = new JLabel("Băng chuyền", SwingConstants.CENTER);
        private final JLabel[] destinationMetricLabels = new JLabel[DESTINATIONS.length];
        private final JButton[] destinationButtons = new JButton[DESTINATIONS.length];
        private final JLabel[] destinationBadgeLabels = new JLabel[DESTINATIONS.length];
        private final JLabel truckPlaceholderLabel = new JLabel("<html><div style='text-align:center;'>Xe vận chuyển<br/>chờ dữ liệu</div></html>", SwingConstants.CENTER);
        private Timer activeAnimation;
        private Timer pulseTimer;
        private float pulseAlpha = 0f;
        private boolean pulseGrowing = true;
        private int pulsingLane = -1;

        private PackagingFlowPanel() {
            setLayout(null);
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
            setPreferredSize(new Dimension(980, 560));
            createLaneComponents();
            createConveyorComponents();
            createDestinationComponents();
            createTruckPlaceholder();
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    layoutFlowComponents();
                }
            });
        }

        private void createLaneComponents() {
            for (int i = 0; i < LANE_COUNT; i++) {
                final int laneIndex = i;
                laneStages[i] = LaneStage.AT_SOURCE;
                laneBadgeLabels[i] = createBadgeLabel("0");
                goodsButtons[i] = createGoodsButton();
                laneBelts[i] = createLaneBelt();
                packingButtons[i] = createPackingButton("Tổ đóng<br/>gói " + (i + 1));
                laneStatusLabels[i] = createStatusLabel();
                goodsButtons[i].addActionListener(e -> moveGoodsToPacking(laneIndex));
                packingButtons[i].addActionListener(e -> moveGoodsToConveyor(laneIndex));

                add(laneBelts[i]);
                add(goodsButtons[i]);
                add(packingButtons[i]);
                add(laneStatusLabels[i]);
                add(laneBadgeLabels[i]);
            }
        }

        private void createConveyorComponents() {
            conveyorLabel.setOpaque(true);
            conveyorLabel.setBackground(new Color(128, 128, 126));
            conveyorLabel.setForeground(Color.BLACK);
            conveyorLabel.setFont(UiTheme.font(Font.PLAIN, 19));
            conveyorLabel.setVerticalAlignment(SwingConstants.TOP);
            conveyorLabel.setHorizontalAlignment(SwingConstants.LEFT);
            conveyorLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DARK_BORDER, 2),
                BorderFactory.createEmptyBorder(8, 8, 0, 8)
            ));
            add(conveyorLabel);
        }

        private void createDestinationComponents() {
            for (int i = 0; i < DESTINATIONS.length; i++) {
                final int destinationIndex = i;
                destinationMetricLabels[i] = createDestinationMetricLabel(destinationIndex == 0 ? "3,500\n/\n100,000" : "");
                destinationButtons[i] = createDestinationButton(DESTINATIONS[destinationIndex]);
                destinationButtons[i].addActionListener(e -> showDestinationDetails(destinationIndex));
                destinationBadgeLabels[i] = createBadgeLabel(destinationIndex == 0 ? "1" : "0");
                destinationBadgeLabels[i].setToolTipText("Nhấn để xem chi tiết điểm đến");
                destinationBadgeLabels[i].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        showDestinationDetails(destinationIndex);
                    }
                });
                add(destinationMetricLabels[i]);
                add(destinationButtons[i]);
                add(destinationBadgeLabels[i]);
            }
        }

        private JButton createGoodsButton() {
            JButton button = new JButton("<html><div style='text-align:center;'>Hàng<br/>hóa</div></html>");
            button.setFont(UiTheme.font(Font.BOLD, 18));
            button.setForeground(new Color(35, 43, 56));
            button.setBackground(GOODS_BG);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createLineBorder(DARK_BORDER, 2));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (button.isEnabled()) button.setBackground(GOODS_HIGHLIGHT);
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    button.setBackground(GOODS_BG);
                }
            });
            return button;
        }

        private JPanel createLaneBelt() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(new Color(142, 168, 187));
            panel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(64, 101, 128)));
            return panel;
        }

        private JButton createPackingButton(String htmlText) {
            JButton button = new RoundedButton("<html><div style='text-align:center;'>" + htmlText + "</div></html>", 16);
            button.setFont(UiTheme.font(Font.BOLD, 17));
            button.setForeground(Color.WHITE);
            button.setBackground(PACKING_DEFAULT);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return button;
        }

        private JLabel createStatusLabel() {
            JLabel label = new JLabel("Trống", SwingConstants.CENTER);
            label.setFont(UiTheme.font(Font.PLAIN, 16));
            label.setForeground(new Color(55, 65, 81));
            label.setOpaque(false);
            return label;
        }

        private JLabel createBadgeLabel(String text) {
            JLabel label = new CircleLabel(text);
            label.setFont(UiTheme.font(Font.BOLD, 16));
            label.setForeground(Color.WHITE);
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.setToolTipText("Thông báo trạng thái");
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showLaneNotifications();
                }
            });
            return label;
        }

        private void createTruckPlaceholder() {
            truckPlaceholderLabel.setOpaque(true);
            truckPlaceholderLabel.setBackground(new Color(242, 246, 249));
            truckPlaceholderLabel.setForeground(UiTheme.MUTED_TEXT);
            truckPlaceholderLabel.setFont(UiTheme.font(Font.BOLD, 16));
            truckPlaceholderLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(UiTheme.BORDER, 4.0f, 4.0f),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)
            ));
            add(truckPlaceholderLabel);
        }

        private JLabel createDestinationMetricLabel(String text) {
            JLabel label = new JLabel(toMetricHtml(text), SwingConstants.CENTER);
            label.setFont(UiTheme.font(Font.BOLD, 13));
            label.setForeground(Color.WHITE);
            label.setOpaque(true);
            label.setBackground(new Color(68, 211, 83));
            label.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 0, DARK_BORDER));
            return label;
        }

        private String toMetricHtml(String text) {
            if (text == null || text.isEmpty()) {
                return "";
            }
            return "<html><div style='text-align:center;'>" + text.replace("\n", "<br/>") + "</div></html>";
        }

        private JButton createDestinationButton(String text) {
            JButton button = new JButton(text);
            button.setFont(UiTheme.font(Font.BOLD, 15));
            button.setForeground(Color.WHITE);
            button.setBackground(new Color(198, 78, 18));
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DARK_BORDER, 2),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
            ));
            return button;
        }

        private void moveGoodsToPacking(int laneIndex) {
            if (isAnimating() || laneStages[laneIndex] != LaneStage.AT_SOURCE) {
                return;
            }
            Rectangle target = getPackingDockBounds(laneIndex);
            updateLaneState(laneIndex, LaneStage.MOVING_TO_PACKING);
            startPulse(laneIndex);
            animateGoods(laneIndex, goodsButtons[laneIndex].getBounds(), target, () -> {
                stopPulse();
                goodsButtons[laneIndex].setBackground(GOODS_BG);
                updateLaneState(laneIndex, LaneStage.AT_PACKING);
                repaint();
            });
        }

        private void moveGoodsToConveyor(int laneIndex) {
            if (isAnimating() || laneStages[laneIndex] != LaneStage.AT_PACKING) {
                return;
            }
            Rectangle start = getPackingDockBounds(laneIndex);
            Rectangle target = getConveyorDockBounds(laneIndex);
            goodsButtons[laneIndex].setBounds(start);
            goodsButtons[laneIndex].setVisible(true);
            updateLaneState(laneIndex, LaneStage.MOVING_TO_CONVEYOR);
            startPulse(laneIndex);
            animateGoods(laneIndex, start, target, () -> {
                stopPulse();
                goodsButtons[laneIndex].setBackground(GOODS_BG);
                updateLaneState(laneIndex, LaneStage.AT_CONVEYOR);
                setComponentZOrder(goodsButtons[laneIndex], 0);
                repaint();
            });
        }

        private void animateGoods(int laneIndex, Rectangle from, Rectangle to, Runnable onComplete) {
            JButton goodsButton = goodsButtons[laneIndex];
            setComponentZOrder(goodsButton, 0);

            long startedAt = System.currentTimeMillis();
            activeAnimation = new Timer(ANIMATION_DELAY_MS, e -> {
                double elapsed = System.currentTimeMillis() - startedAt;
                double progress = Math.min(1.0, elapsed / ANIMATION_DURATION_MS);
                double eased = easeInOut(progress);
                int x = interpolate(from.x, to.x, eased);
                int y = interpolate(from.y, to.y, eased);
                goodsButton.setBounds(x, y, from.width, from.height);
                repaint();

                if (progress >= 1.0) {
                    activeAnimation.stop();
                    activeAnimation = null;
                    goodsButton.setBounds(to);
                    onComplete.run();
                }
            });
            activeAnimation.start();
        }

        private void startPulse(int laneIndex) {
            pulsingLane = laneIndex;
            pulseAlpha = 0f;
            pulseGrowing = true;
            if (pulseTimer != null) pulseTimer.stop();
            pulseTimer = new Timer(30, e -> {
                if (pulseGrowing) {
                    pulseAlpha += 0.08f;
                    if (pulseAlpha >= 1f) { pulseAlpha = 1f; pulseGrowing = false; }
                } else {
                    pulseAlpha -= 0.08f;
                    if (pulseAlpha <= 0f) { pulseAlpha = 0f; pulseGrowing = true; }
                }
                if (pulsingLane >= 0) {
                    goodsButtons[pulsingLane].setBackground(interpolateColor(GOODS_BG, GOODS_HIGHLIGHT, pulseAlpha));
                }
            });
            pulseTimer.start();
        }

        private void stopPulse() {
            if (pulseTimer != null) { pulseTimer.stop(); pulseTimer = null; }
            pulsingLane = -1;
        }

        private Color interpolateColor(Color a, Color b, float t) {
            int r = (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t);
            int g = (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
            int bl = (int)(a.getBlue() + (b.getBlue()  - a.getBlue())  * t);
            return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, bl));
        }

        private boolean isAnimating() {
            return activeAnimation != null && activeAnimation.isRunning();
        }

        private int interpolate(int from, int to, double progress) {
            return (int) Math.round(from + (to - from) * progress);
        }

        private double easeInOut(double progress) {
            return progress < 0.5
                ? 2 * progress * progress
                : 1 - Math.pow(-2 * progress + 2, 2) / 2;
        }

        private Rectangle getPackingDockBounds(int laneIndex) {
            Rectangle packing = packingButtons[laneIndex].getBounds();
            Rectangle goods = goodsButtons[laneIndex].getBounds();
            return new Rectangle(
                packing.x + (packing.width - goods.width) / 2,
                packing.y + (packing.height - goods.height) / 2,
                goods.width,
                goods.height
            );
        }

        private Rectangle getConveyorDockBounds(int laneIndex) {
            Rectangle conveyor = conveyorLabel.getBounds();
            Rectangle goods = goodsButtons[laneIndex].getBounds();
            int y = laneBelts[laneIndex].getY() + (laneBelts[laneIndex].getHeight() - goods.height) / 2;
            return new Rectangle(
                conveyor.x + (conveyor.width - goods.width) / 2,
                y,
                goods.width,
                goods.height
            );
        }

        private void updateLaneState(int laneIndex, LaneStage stage) {
            laneStages[laneIndex] = stage;
            switch (stage) {
                case AT_SOURCE:
                    laneStatusLabels[laneIndex].setText("Trống");
                    laneBadgeLabels[laneIndex].setText("0");
                    packingButtons[laneIndex].setBackground(PACKING_DEFAULT);
                    break;
                case MOVING_TO_PACKING:
                    laneStatusLabels[laneIndex].setText("Đang vận chuyển");
                    packingButtons[laneIndex].setBackground(PACKING_ACTIVE);
                    break;
                case AT_PACKING:
                    laneStatusLabels[laneIndex].setText("Đang đóng gói");
                    laneBadgeLabels[laneIndex].setText("1");
                    packingButtons[laneIndex].setBackground(PACKING_ACTIVE);
                    break;
                case MOVING_TO_CONVEYOR:
                    laneStatusLabels[laneIndex].setText("Lên băng chuyền");
                    packingButtons[laneIndex].setBackground(PACKING_DONE);
                    break;
                case AT_CONVEYOR:
                    laneStatusLabels[laneIndex].setText("Trống");
                    laneBadgeLabels[laneIndex].setText("0");
                    packingButtons[laneIndex].setBackground(PACKING_DEFAULT);
                    break;
                default:
                    break;
            }
        }

        private void showLaneNotifications() {
            StringBuilder message = new StringBuilder();
            for (int i = 0; i < LANE_COUNT; i++) {
                message
                    .append("Làn ")
                    .append(i + 1)
                    .append(": ")
                    .append(laneStatusLabels[i].getText())
                    .append('\n');
            }
            JOptionPane.showMessageDialog(
                this,
                message.toString(),
                "Thông báo đóng gói",
                JOptionPane.INFORMATION_MESSAGE
            );
        }

        private void showDestinationDetails(int destinationIndex) {
            JOptionPane.showMessageDialog(
                this,
                "Điểm đến: " + DESTINATIONS[destinationIndex] + "\n" +
                "Số đơn hiện tại: " + destinationBadgeLabels[destinationIndex].getText() + "\n" +
                "Tình trạng băng chuyền: " + (destinationMetricLabels[destinationIndex].getText().isEmpty() ? "Chưa có dữ liệu" : "Hoạt động"),
                "Thông báo điểm đến",
                JOptionPane.INFORMATION_MESSAGE
            );
        }

        private void layoutFlowComponents() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            int left = Math.max(24, w / 28);
            int top = Math.max(48, h / 12);
            int laneGap = Math.max(112, (h - top - 70) / LANE_COUNT);
            int goodsW = 100;
            int goodsH = 58;
            int beltH = 38;
            int packW = 130;
            int packH = 80;
            int conveyorW = 120;
            int metricW = 88;
            int destinationW = 130;
            int destinationH = 58;
            int badgeSize = 38;

            int destinationX = Math.max(w - destinationW - 24, left + 760);
            int metricX = destinationX - metricW - 8;
            int conveyorX = metricX - conveyorW - 16;
            int packX = left + goodsW + 76;
            int beltStartX = left + goodsW;
            int beltEndX = conveyorX - 8;
            int statusX = packX + packW + 24;
            int statusW = Math.max(120, beltEndX - statusX - 12);

            for (int i = 0; i < LANE_COUNT; i++) {
                int centerY = top + i * laneGap;
                laneBelts[i].setBounds(beltStartX, centerY - beltH / 2, Math.max(60, beltEndX - beltStartX), beltH);
                packingButtons[i].setBounds(packX, centerY - packH / 2, packW, packH);
                laneStatusLabels[i].setBounds(statusX, centerY - 24, statusW, 48);
                laneBadgeLabels[i].setBounds(left + goodsW - 20, centerY - goodsH / 2 - 22, badgeSize, badgeSize);
                setComponentZOrder(laneBelts[i], getComponentCount() - 1);
                if (!isAnimating()) {
                    layoutGoodsByState(i, left, centerY, goodsW, goodsH);
                }
                setComponentZOrder(laneBadgeLabels[i], 0);
            }

            int conveyorTop = top - 80;
            int conveyorBottom = top + (LANE_COUNT - 1) * laneGap + 76;
            conveyorLabel.setBounds(conveyorX, conveyorTop, conveyorW, Math.min(h - conveyorTop - 16, conveyorBottom - conveyorTop));

            int destinationTop = conveyorTop + 58;
            int destinationGap = Math.max(90, (conveyorLabel.getHeight() - 54) / DESTINATIONS.length);
            for (int i = 0; i < DESTINATIONS.length; i++) {
                int centerY = destinationTop + i * destinationGap;
                destinationMetricLabels[i].setBounds(metricX, centerY - destinationH / 2, metricW, destinationH);
                destinationButtons[i].setBounds(destinationX, centerY - destinationH / 2, destinationW, destinationH);
                destinationBadgeLabels[i].setBounds(destinationX + destinationW - 24, centerY - destinationH / 2 - 16, badgeSize, badgeSize);
                setComponentZOrder(destinationBadgeLabels[i], 0);
            }

            int truckW = 160;
            int truckH = 96;
            int truckX = destinationX + destinationW + 24;
            int truckY = destinationTop;
            if (truckX + truckW + 24 > w) {
                truckX = Math.max(left, w - truckW - 24);
                truckY = destinationTop + destinationGap * DESTINATIONS.length + 16;
            }
            truckPlaceholderLabel.setBounds(truckX, truckY, truckW, truckH);
            truckPlaceholderLabel.setVisible(true);
        }

        private void layoutGoodsByState(int laneIndex, int sourceX, int centerY, int goodsW, int goodsH) {
            Rectangle source = new Rectangle(sourceX, centerY - goodsH / 2, goodsW, goodsH);
            LaneStage stage = laneStages[laneIndex];
            if (stage == LaneStage.AT_PACKING) {
                goodsButtons[laneIndex].setBounds(centeredBounds(source, packingButtons[laneIndex].getBounds()));
                goodsButtons[laneIndex].setVisible(true);
            } else if (stage == LaneStage.AT_CONVEYOR) {
                goodsButtons[laneIndex].setBounds(getConveyorDockBounds(laneIndex));
                goodsButtons[laneIndex].setVisible(true);
            } else {
                goodsButtons[laneIndex].setBounds(source);
                goodsButtons[laneIndex].setVisible(true);
            }
            setComponentZOrder(goodsButtons[laneIndex], 0);
        }

        private Rectangle centeredBounds(Rectangle sizeSource, Rectangle anchor) {
            return new Rectangle(
                anchor.x + (anchor.width - sizeSource.width) / 2,
                anchor.y + (anchor.height - sizeSource.height) / 2,
                sizeSource.width,
                sizeSource.height
            );
        }

        @Override
        public void doLayout() {
            super.doLayout();
            layoutFlowComponents();
        }

        @Override
        public Component add(Component comp) {
            Component added = super.add(comp);
            layoutFlowComponents();
            return added;
        }

        /*
         * Animation contract for future shipping-to-truck work:
         * 1. Goods button is the animated cargo block for each lane.
         * 2. LaneStage.AT_CONVEYOR is the stable extension point after real order
         *    data is attached.
         * 3. The next animation should start from getConveyorDockBounds(laneIndex)
         *    and route toward a truck/destination component without resetting the
         *    source or packing states.
         */
        private enum LaneStage {
            AT_SOURCE,
            MOVING_TO_PACKING,
            AT_PACKING,
            MOVING_TO_CONVEYOR,
            AT_CONVEYOR
        }

        private static final class RoundedButton extends JButton {
            private static final long serialVersionUID = 1L;
            private final int arc;

            private RoundedButton(String text, int arc) {
                super(text);
                this.arc = arc;
                setOpaque(false);
                setContentAreaFilled(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }
        }

        private static final class CircleLabel extends JLabel {
            private static final long serialVersionUID = 1L;

            private CircleLabel(String text) {
                super(text, SwingConstants.CENTER);
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.RED);
                g2.fillOval(2, 2, getWidth() - 4, getHeight() - 4);
                g2.setColor(DARK_BORDER);
                g2.drawOval(2, 2, getWidth() - 4, getHeight() - 4);
                g2.dispose();
                super.paintComponent(g);
            }
        }
    }
}