import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.Timer;

/**
 * PackagingShippingPanel — giao diện Đóng gói & Vận chuyển.
 *
 * Layout 4 cột:
 *   [1. Hàng hóa đầu vào] → [2. Tổ đóng gói] → [3. Khu vực chờ] → [4. Xe / Điểm giao hàng]
 *
 * Quy tắc sức chứa khu vực chờ:
 *   - Đơn < 70 kg    = 2.5%
 *   - 70–200 kg      = 5%
 *   - > 200 kg       = 8%
 *
 * Tải trọng xe: tối đa 900 kg/xe, xe tự rời khi đầy, thông báo dạng toast phía dưới.
 */
public class PackagingShippingPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private DashboardPanel dashboard;

    public PackagingShippingPanel() { buildUi(); }

    public void refreshFromDb() {
        if (dashboard != null) dashboard.pollAndRefresh();
    }

    private void buildUi() {
        setLayout(new BorderLayout(0, 12));
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Header
        add(UiTheme.pageHeader(
            "Đóng gói & Vận chuyển",
            "Theo dõi luồng hàng hóa từ tổ đóng gói đến các điểm giao hàng"
        ), BorderLayout.NORTH);

        dashboard = new DashboardPanel();
        add(dashboard, BorderLayout.CENTER);
    }

    // =========================================================================
    // DashboardPanel — toàn bộ UI + logic
    // =========================================================================
    static final class DashboardPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        // ── DB ───────────────────────────────────────────────────────────────
        private static final String DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        private static final String DB_URL = System.getenv().getOrDefault("DB_URL",
            "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true");
        private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
        private static final String DB_PASS = System.getenv().getOrDefault("DB_PASSWORD", "123456");

        private static final String POLL_SQL =
            "SELECT Id, OrderCode, DestCode, DestName, TeamId, TotalWeight, ItemCount, Status " +
            "FROM PackagingQueue ORDER BY QueueDate ASC, QueueOrder ASC";
        private static final String TODAY_SQL =
            "SELECT COUNT(*) FROM PackagingQueue WHERE QueueDate = CAST(SYSDATETIME() AS date)";
        private static final String UPDATE_SQL =
            "UPDATE PackagingQueue SET Status=?, UpdatedAt=SYSDATETIME() WHERE Id=?";

        // ── Hằng số ──────────────────────────────────────────────────────────
        private static final int    TEAM_COUNT       = 3;
        private static final double MAX_HOLD_PERCENT = 100.0; // sức chứa khu vực chờ
        private static final int    HOLD_PROGRESS_SCALE = 10; // 0.1%
        private static final double MAX_TRUCK_KG     = 900.0;
        private static final int    TRUCK_RESET_MS   = 15_000;
        private static final int    COUNTDOWN_MS     = 1_000;
        private static final int    DB_POLL_MS       = 5_000;
        private static final String[] DEST_CODES     = {"HN","NA","DN","LD","HC","CT"};
        private static final String[] DEST_NAMES     = {"Hà Nội","Nghệ An","Đà Nẵng","Lâm Đồng","TP. HCM","Cần Thơ"};

        // ── Dữ liệu ──────────────────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        private final Deque<QueueItem>[] teamQueues = new Deque[TEAM_COUNT];
        private final QueueItem[]        packingNow = new QueueItem[TEAM_COUNT];
        private final Timer[]            packTimers = new Timer[TEAM_COUNT];
        private final int[]              packSec    = new int[TEAM_COUNT];

        // Khu vực chờ (dùng chung cho cả 3 tổ)
        private int holdItemCount = 0;      // số kiện hàng đang ở khu vực chờ
        private double holdPercentUsed = 0.0; // phần trăm sức chứa đang chiếm

        // Xe theo điểm đến
        private final double[]          truckKg       = new double[6];
        private final boolean[]         truckGone     = new boolean[6]; // xe đang đi
        @SuppressWarnings("unchecked")
        private final Deque<QueueItem>[] truckOverflow = new Deque[6];

        // Số liệu thống kê
        private int statTotal, statPacking, statDone, statWaiting;

        // ── UI: 4 stat cards ─────────────────────────────────────────────────
        private final JLabel lbTotal   = bigStatLabel("0");
        private final JLabel lbPacking = bigStatLabel("0");
        private final JLabel lbDone    = bigStatLabel("0");
        private final JLabel lbWaiting = bigStatLabel("0");

        // ── UI: Cột 1 — Hàng hóa đầu vào ────────────────────────────────────
        private final JLabel lbQueueCount   = new JLabel("0", SwingConstants.CENTER);
        private final JLabel lbTodayCount   = new JLabel("Hàng hôm nay         0");
        private final JLabel lbPendingCount = new JLabel("Chưa xử lý           0");

        // ── UI: Cột 2 — Tổ đóng gói ─────────────────────────────────────────
        private final JLabel[]  teamStatusBadge = new JLabel[TEAM_COUNT];
        private final JLabel[]  teamProcessLb   = new JLabel[TEAM_COUNT];
        private final JLabel[]  teamWaitLb      = new JLabel[TEAM_COUNT];
        private final JProgressBar[] teamProgress = new JProgressBar[TEAM_COUNT];

        // ── UI: Cột 3 — Khu vực chờ ─────────────────────────────────────────
        private final JLabel        lbHoldCount    = new JLabel("0", SwingConstants.CENTER);
        private final JProgressBar  holdProgress   =
            new JProgressBar(0, (int) (MAX_HOLD_PERCENT * HOLD_PROGRESS_SCALE));
        private final JLabel        lbLoadingStatus = new JLabel(" ");  // trạng thái chất hàng

        // ── UI: Cột 4 — Xe / Điểm giao hàng ─────────────────────────────────
        private final JLabel[]  destKgLabel   = new JLabel[6];
        private final JButton[] destDetailBtn = new JButton[6];
        private final JLabel[]  destStatusDot = new JLabel[6];

        // ── UI: Toast thông báo xe đi ─────────────────────────────────────────
        private final JPanel     toastPanel = new JPanel(new BorderLayout());
        private final JLabel     toastLabel = new JLabel("", SwingConstants.LEFT);
        private       Timer      toastTimer;

        DashboardPanel() {
            setLayout(new BorderLayout(0, 12));
            setBackground(UiTheme.APP_BG);

            for (int i = 0; i < TEAM_COUNT; i++) teamQueues[i] = new ArrayDeque<>();
            for (int i = 0; i < 6; i++) truckOverflow[i] = new ArrayDeque<>();

            add(buildStatRow(),  BorderLayout.NORTH);
            add(buildMainRow(),  BorderLayout.CENTER);
            add(buildToast(),    BorderLayout.SOUTH);

            // Poll DB định kỳ
            Timer dbTimer = new Timer(DB_POLL_MS, e -> pollAndRefresh());
            dbTimer.start();
            new Timer(700, e -> { pollAndRefresh(); ((Timer)e.getSource()).stop(); }).start();
        }

        // ── Stat row (4 cards) ───────────────────────────────────────────────
        private JPanel buildStatRow() {
            JPanel row = new JPanel(new GridLayout(1, 4, 10, 0));
            row.setOpaque(false);
            row.add(statCard("Tổng đơn hôm nay",  lbTotal,   UiTheme.TEAL));
            row.add(statCard("Đang xử lý",         lbPacking, UiTheme.STATUS_PACKING));
            row.add(statCard("Đã đóng gói",        lbDone,    UiTheme.STATUS_DONE));
            row.add(statCard("Chờ vận chuyển",     lbWaiting, UiTheme.STATUS_WAITING));
            return row;
        }

        private JPanel statCard(String title, JLabel valueLb, Color accentColor) {
            JPanel card = new JPanel(new BorderLayout(0, 4));
            card.setBackground(UiTheme.SURFACE);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

            JLabel titleLb = new JLabel(title);
            titleLb.setFont(UiTheme.font(Font.PLAIN, 12));
            titleLb.setForeground(UiTheme.MUTED_TEXT);

            valueLb.setFont(UiTheme.font(Font.BOLD, 30));
            valueLb.setForeground(accentColor);

            card.add(titleLb, BorderLayout.NORTH);
            card.add(valueLb, BorderLayout.CENTER);
            return card;
        }

        // ── Main row (4 cột) ─────────────────────────────────────────────────
        private JPanel buildMainRow() {
            JPanel row = new JPanel(new GridLayout(1, 4, 10, 0));
            row.setOpaque(false);
            row.add(buildCol1());
            row.add(buildCol2());
            row.add(buildCol3());
            row.add(buildCol4());
            return row;
        }

        // Cột 1 — Hàng hóa đầu vào
        private JPanel buildCol1() {
            JPanel card = makeCard("1. HÀNG HÓA ĐẦU VÀO", UiTheme.STATUS_DONE);

            lbQueueCount.setFont(UiTheme.font(Font.BOLD, 52));
            lbQueueCount.setForeground(UiTheme.STATUS_DONE);
            lbQueueCount.setAlignmentX(CENTER_ALIGNMENT);

            JLabel subLb = new JLabel("Kiện hàng đang chờ", SwingConstants.CENTER);
            subLb.setFont(UiTheme.font(Font.PLAIN, 13));
            subLb.setForeground(UiTheme.MUTED_TEXT);

            lbTodayCount.setFont(UiTheme.font(Font.PLAIN, 12));
            lbTodayCount.setForeground(UiTheme.TEXT);
            lbPendingCount.setFont(UiTheme.font(Font.PLAIN, 12));
            lbPendingCount.setForeground(UiTheme.TEXT);

            JSeparator sep = new JSeparator();
            sep.setForeground(UiTheme.CARD_BORDER);

            // Panel chứa 2 dòng thống kê, căn trái
            JPanel statsBottom = new JPanel(new GridLayout(2, 1, 0, 4));
            statsBottom.setOpaque(false);
            statsBottom.add(lbTodayCount);
            statsBottom.add(lbPendingCount);

            JPanel center = new JPanel(new BorderLayout(0, 0));
            center.setOpaque(false);

            // Phần trên: số lớn + sub text, căn giữa
            JPanel topArea = new JPanel();
            topArea.setOpaque(false);
            topArea.setLayout(new BoxLayout(topArea, BoxLayout.Y_AXIS));
            topArea.add(Box.createVerticalGlue());
            topArea.add(centered(lbQueueCount));
            topArea.add(centered(subLb));
            topArea.add(Box.createVerticalGlue());

            // Phần dưới: separator + stats căn trái
            JPanel bottomArea = new JPanel();
            bottomArea.setOpaque(false);
            bottomArea.setLayout(new BoxLayout(bottomArea, BoxLayout.Y_AXIS));
            bottomArea.add(sep);
            bottomArea.add(Box.createVerticalStrut(8));
            bottomArea.add(statsBottom);

            center.add(topArea, BorderLayout.CENTER);
            center.add(bottomArea, BorderLayout.SOUTH);
            card.add(center, BorderLayout.CENTER);
            return card;
        }

        // Cột 2 — Tổ đóng gói (3 tổ)
        private JPanel buildCol2() {
            JPanel card = makeCard("2. TỔ ĐÓNG GÓI", UiTheme.STATUS_PACKING);
            JPanel body = new JPanel(new GridLayout(TEAM_COUNT, 1, 0, 8));
            body.setOpaque(false);
            body.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

            for (int t = 0; t < TEAM_COUNT; t++) {
                body.add(buildTeamRow(t));
            }
            card.add(body, BorderLayout.CENTER);
            return card;
        }

        private JPanel buildTeamRow(int t) {
            JPanel row = new JPanel(new BorderLayout(8, 4));
            row.setBackground(UiTheme.CARD_BG);
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

            // Header: tên tổ + badge trạng thái
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel nameLb = new JLabel("Tổ đóng gói " + (t + 1));
            nameLb.setFont(UiTheme.font(Font.BOLD, 13));
            nameLb.setForeground(UiTheme.TEXT);

            teamStatusBadge[t] = pill("Trống", UiTheme.STATUS_EMPTY, Color.WHITE);
            header.add(nameLb, BorderLayout.WEST);
            header.add(teamStatusBadge[t], BorderLayout.EAST);

            // Body: progress bar đếm ngược + info
            teamProgress[t] = new JProgressBar(0, 100);
            teamProgress[t].setValue(0);
            teamProgress[t].setStringPainted(false);
            teamProgress[t].setBackground(new Color(229, 231, 235));
            teamProgress[t].setForeground(UiTheme.STATUS_PACKING);
            teamProgress[t].setPreferredSize(new Dimension(0, 6));
            teamProgress[t].setBorder(BorderFactory.createEmptyBorder());

            teamProcessLb[t] = new JLabel("Đang xử lý:  —");
            teamProcessLb[t].setFont(UiTheme.font(Font.PLAIN, 12));
            teamProcessLb[t].setForeground(UiTheme.MUTED_TEXT);

            teamWaitLb[t] = new JLabel("Chờ:  0");
            teamWaitLb[t].setFont(UiTheme.font(Font.PLAIN, 12));
            teamWaitLb[t].setForeground(UiTheme.MUTED_TEXT);

            JPanel info = new JPanel(new GridLayout(2, 1, 0, 2));
            info.setOpaque(false);
            info.add(teamProcessLb[t]);
            info.add(teamWaitLb[t]);

            JPanel body = new JPanel(new BorderLayout(0, 6));
            body.setOpaque(false);
            body.add(teamProgress[t], BorderLayout.NORTH);
            body.add(info, BorderLayout.CENTER);

            row.add(header, BorderLayout.NORTH);
            row.add(body, BorderLayout.CENTER);
            return row;
        }

        // Cột 3 — Khu vực chờ
        private JPanel buildCol3() {
            JPanel card = makeCard("3. KHU VỰC CHỜ", new Color(139, 92, 246));

            lbHoldCount.setFont(UiTheme.font(Font.BOLD, 52));
            lbHoldCount.setForeground(new Color(139, 92, 246));

            JLabel subLb = new JLabel("Kiện hàng trên khu vực chờ", SwingConstants.CENTER);
            subLb.setFont(UiTheme.font(Font.PLAIN, 13));
            subLb.setForeground(UiTheme.MUTED_TEXT);

            holdProgress.setStringPainted(false);
            holdProgress.setBackground(new Color(229, 231, 235));
            holdProgress.setForeground(new Color(139, 92, 246));
            holdProgress.setPreferredSize(new Dimension(0, 10));
            holdProgress.setBorder(BorderFactory.createEmptyBorder());

            JLabel capacityLb = new JLabel("Sức chứa tối đa   " + formatPercent(MAX_HOLD_PERCENT));
            capacityLb.setFont(UiTheme.font(Font.PLAIN, 12));
            capacityLb.setForeground(UiTheme.MUTED_TEXT);

            // 3 dòng ghi chú riêng biệt, căn trái
            JLabel note1 = makeNoteLine("< 70 kg  =  2.5%");
            JLabel note2 = makeNoteLine("70 – 200 kg  =  5%");
            JLabel note3 = makeNoteLine("> 200 kg  =  8%");
            JPanel notesPanel = new JPanel(new GridLayout(3, 1, 0, 2));
            notesPanel.setOpaque(false);
            notesPanel.add(note1);
            notesPanel.add(note2);
            notesPanel.add(note3);

            // Phần trên: số + sub text căn giữa
            JPanel topArea = new JPanel();
            topArea.setOpaque(false);
            topArea.setLayout(new BoxLayout(topArea, BoxLayout.Y_AXIS));
            topArea.add(Box.createVerticalGlue());
            topArea.add(centered(lbHoldCount));
            topArea.add(centered(subLb));
            topArea.add(Box.createVerticalGlue());

            // Phần dưới: thanh progress + ghi chú, căn trái
            JPanel bottomArea = new JPanel();
            bottomArea.setOpaque(false);
            bottomArea.setLayout(new BoxLayout(bottomArea, BoxLayout.Y_AXIS));
            lbLoadingStatus.setFont(UiTheme.font(Font.PLAIN, 11));
            lbLoadingStatus.setForeground(UiTheme.STATUS_PACKING);

            bottomArea.add(capacityLb);
            bottomArea.add(Box.createVerticalStrut(6));
            bottomArea.add(holdProgress);
            bottomArea.add(Box.createVerticalStrut(4));
            bottomArea.add(lbLoadingStatus);
            bottomArea.add(Box.createVerticalStrut(4));
            bottomArea.add(notesPanel);

            JPanel center = new JPanel(new BorderLayout(0, 0));
            center.setOpaque(false);
            center.add(topArea, BorderLayout.CENTER);
            center.add(bottomArea, BorderLayout.SOUTH);
            card.add(center, BorderLayout.CENTER);
            return card;
        }

        private static JLabel makeNoteLine(String text) {
            JLabel lb = new JLabel(text);
            lb.setFont(UiTheme.font(Font.PLAIN, 11));
            lb.setForeground(UiTheme.STATUS_EMPTY);
            return lb;
        }

        // Cột 4 — Xe / Điểm giao hàng
        private JPanel buildCol4() {
            JPanel card = makeCard("4. XE / ĐIỂM GIAO HÀNG", UiTheme.CARAMEL);
            JPanel body = new JPanel(new GridLayout(6, 1, 0, 4));
            body.setOpaque(false);
            body.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

            for (int d = 0; d < 6; d++) {
                body.add(buildDestRow(d));
            }
            card.add(body, BorderLayout.CENTER);
            return card;
        }

        private JPanel buildDestRow(int d) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBackground(UiTheme.SURFACE);
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

            // Tên điểm đến
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            left.setOpaque(false);
            destStatusDot[d] = new JLabel("●");
            destStatusDot[d].setFont(UiTheme.font(Font.PLAIN, 14));
            destStatusDot[d].setForeground(UiTheme.STATUS_EMPTY);
            JLabel nameLb = new JLabel(DEST_NAMES[d]);
            nameLb.setFont(UiTheme.font(Font.BOLD, 13));
            nameLb.setForeground(UiTheme.TEXT);
            left.add(destStatusDot[d]);
            left.add(nameLb);

            // Kg / 900
            destKgLabel[d] = new JLabel("0 / 900", SwingConstants.CENTER);
            destKgLabel[d].setFont(UiTheme.font(Font.PLAIN, 12));
            destKgLabel[d].setForeground(UiTheme.MUTED_TEXT);

            // Nút Chi tiết
            final int di = d;
            destDetailBtn[d] = new JButton("Chi tiết");
            UiTheme.styleDetailButton(destDetailBtn[d]);
            destDetailBtn[d].addActionListener(e -> showDestDetail(di));

            row.add(left, BorderLayout.WEST);
            row.add(destKgLabel[d], BorderLayout.CENTER);
            row.add(destDetailBtn[d], BorderLayout.EAST);
            return row;
        }

        // ── Toast ─────────────────────────────────────────────────────────────
        private JPanel buildToast() {
            toastPanel.setOpaque(false);
            toastPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            toastLabel.setFont(UiTheme.font(Font.PLAIN, 12));
            toastLabel.setForeground(UiTheme.MUTED_TEXT);
            toastLabel.setVisible(false);
            toastPanel.add(toastLabel, BorderLayout.WEST);
            // Legend cố định bên phải
            JPanel legend = buildLegend();
            toastPanel.add(legend, BorderLayout.EAST);
            return toastPanel;
        }

        private JPanel buildLegend() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            p.setOpaque(false);
            p.add(legendItem(UiTheme.STATUS_WAITING, "Chờ xử lý"));
            p.add(legendItem(UiTheme.STATUS_PACKING, "Đang xử lý"));
            p.add(legendItem(UiTheme.STATUS_DONE,    "Đã hoàn tất"));
            p.add(legendItem(UiTheme.STATUS_FULL,    "Đầy tải"));
            p.add(legendItem(UiTheme.STATUS_EMPTY,   "Trống"));
            return p;
        }

        private JPanel legendItem(Color color, String text) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            p.setOpaque(false);
            JLabel dot = new JLabel("●");
            dot.setFont(UiTheme.font(Font.PLAIN, 14));
            dot.setForeground(color);
            JLabel lb = new JLabel(text);
            lb.setFont(UiTheme.font(Font.PLAIN, 12));
            lb.setForeground(UiTheme.MUTED_TEXT);
            p.add(dot);
            p.add(lb);
            return p;
        }

        // ── DB polling ────────────────────────────────────────────────────────
        void pollAndRefresh() {
            List<QueueItem> rows = new ArrayList<>();
            int todayCount = 0;
            try {
                ensureDriver();
                try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    try (PreparedStatement st = c.prepareStatement(POLL_SQL);
                         ResultSet rs = st.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new QueueItem(
                                rs.getInt("Id"),
                                rs.getString("OrderCode"),
                                rs.getString("DestCode").trim(),
                                rs.getString("DestName"),
                                rs.getInt("TeamId"),
                                rs.getDouble("TotalWeight"),
                                rs.getInt("ItemCount"),
                                rs.getString("Status")
                            ));
                        }
                    }
                    try (PreparedStatement st = c.prepareStatement(TODAY_SQL);
                         ResultSet rs = st.executeQuery()) {
                        if (rs.next()) todayCount = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) { return; }

            final List<QueueItem> finalRows = rows;
            final int finalToday = todayCount;
            SwingUtilities.invokeLater(() -> applyData(finalRows, finalToday));
        }

        private void applyData(List<QueueItem> rows, int todayCount) {
            // Đơn chưa theo dõi → thêm vào hàng đợi tổ
            for (QueueItem item : rows) {
                if (!"WAITING".equals(item.status)) continue;
                if (isTracked(item.id)) continue;
                int ti = item.teamId - 1;
                if (ti >= 0 && ti < TEAM_COUNT) {
                    teamQueues[ti].addLast(item);
                    // Tự động bắt đầu đóng gói nếu tổ rảnh
                    if (packingNow[ti] == null) startPacking(ti);
                }
            }

            // Tính thống kê
            statTotal   = todayCount;
            statPacking = 0; statDone = 0; statWaiting = 0;
            for (QueueItem item : rows) {
                switch (item.status) {
                    case "WAITING":  case "PACKING": statPacking++; break;
                    case "ON_CONVEYOR": statWaiting++; break;
                    case "AT_DEST": case "SHIPPED": statDone++; break;
                }
            }
            int totalWaiting = 0;
            for (int t = 0; t < TEAM_COUNT; t++) totalWaiting += teamQueues[t].size();

            // Cập nhật UI thống kê
            lbTotal.setText(String.valueOf(statTotal));
            lbPacking.setText(String.valueOf(statPacking));
            lbDone.setText(String.valueOf(statDone));
            lbWaiting.setText(String.valueOf(statWaiting));
            lbQueueCount.setText(String.valueOf(totalWaiting));
            lbTodayCount.setText(  padRow("Hàng hôm nay",  todayCount));
            lbPendingCount.setText(padRow("Chưa xử lý",    totalWaiting));

            refreshTeamUi();
            refreshHoldUi();
            refreshDestUi();
        }

        private String padRow(String label, int val) {
            return "<html><table width='100%'><tr><td>" + label +
                "</td><td align='right'><b>" + val + "</b></td></tr></table></html>";
        }

        // ── Đóng gói ──────────────────────────────────────────────────────────
        private void startPacking(int t) {
            if (packingNow[t] != null || teamQueues[t].isEmpty()) return;
            QueueItem item = teamQueues[t].pollFirst();
            packingNow[t] = item;
            packSec[t]    = item.itemCount;
            updateDbStatus(item.id, "PACKING");

            // Cập nhật badge ngay
            refreshTeamRow(t);

            // Timer đếm ngược
            if (packTimers[t] != null) packTimers[t].stop();
            packTimers[t] = new Timer(COUNTDOWN_MS, null);
            packTimers[t].addActionListener(e -> {
                packSec[t]--;
                refreshTeamRow(t);
                if (packSec[t] <= 0) {
                    packTimers[t].stop();
                    finishPacking(t);
                }
            });
            packTimers[t].start();
        }

        private void finishPacking(int t) {
            QueueItem item = packingNow[t];
            if (item == null) return;

            // Tính phần trăm sức chứa cần chiếm
            double holdPercent = weightToHoldPercent(item.totalWeight);

            if (holdPercentUsed + holdPercent > MAX_HOLD_PERCENT) {
                // Khu vực chờ đầy — chờ chỗ trống (thử lại sau 2s)
                Timer retry = new Timer(2000, e -> { finishPacking(t); ((Timer)e.getSource()).stop(); });
                retry.setRepeats(false);
                retry.start();
                return;
            }

            // Chuyển vào khu vực chờ
            holdItemCount++;
            holdPercentUsed += holdPercent;
            packingNow[t] = null;
            updateDbStatus(item.id, "ON_CONVEYOR");
            refreshTeamUi();
            refreshHoldUi();

            // Bắt đầu đơn tiếp theo ngay (tổ rảnh) trong khi chờ chất hàng lên xe
            if (!teamQueues[t].isEmpty()) startPacking(t);

            // Delay chất hàng lên xe theo khối lượng: <70kg=5s, 70-200kg=7s, >200kg=10s
            int delaySec = loadingDelaySec(item.totalWeight);
            final int[] secLeft = {delaySec};
            lbLoadingStatus.setText("Đang chất hàng lên xe: " + delaySec + "s...");

            Timer loadTimer = new Timer(1000, null);
            loadTimer.addActionListener(e -> {
                secLeft[0]--;
                if (secLeft[0] > 0) {
                    lbLoadingStatus.setText("Đang chất hàng lên xe: " + secLeft[0] + "s...");
                } else {
                    loadTimer.stop();
                    lbLoadingStatus.setText(" ");
                    placeOnTruck(item, holdPercent);
                }
            });
            loadTimer.start();
        }

        private int loadingDelaySec(double kg) {
            if (kg < 70)   return 5;
            if (kg <= 200) return 7;
            return 10;
        }

        private double weightToHoldPercent(double kg) {
            if (kg < 70)  return 2.5;
            if (kg <= 200) return 5.0;
            return 8.0;
        }

        // ── Xe ────────────────────────────────────────────────────────────────
        private void placeOnTruck(QueueItem item, double holdPercent) {
            int di = destIdx(item.destCode);
            if (di < 0) { releaseHoldSpace(holdPercent); return; }

            if (truckGone[di]) {
                // Xe đang đợi thay — đơn vào overflow chờ xe mới
                truckOverflow[di].addLast(item);
                updateDbStatus(item.id, "AT_DEST");
                releaseHoldSpace(holdPercent);
                refreshHoldUi();
                return;
            }

            double newKg = truckKg[di] + item.totalWeight;
            if (newKg > MAX_TRUCK_KG) {
                if (truckKg[di] > 0) {
                    // Trường hợp: xe đang có hàng, thêm đơn này sẽ vượt tải trọng
                    // → Thông báo cụ thể, xe đi ngay với tải hiện tại, đơn chờ xe mới
                    showToast("Xe " + DEST_NAMES[di]
                        + " hiện không thể tải được đơn " + item.orderCode
                        + " (" + String.format("%.0f", item.totalWeight) + " kg)"
                        + " vì vượt quá tổng tải trọng."
                        + " Bạn phải đợi xe khác.");
                    truckDeparture(di, item, holdPercent, false); // xe đi, đơn chờ xe sau
                } else {
                    // Trường hợp hiếm: xe trống nhưng một đơn đã > 900kg
                    // → Vẫn tải lên (không có lựa chọn nào khác)
                    truckKg[di] = item.totalWeight;
                    releaseHoldSpace(holdPercent);
                    updateDbStatus(item.id, "AT_DEST");
                    refreshDestUi();
                    refreshHoldUi();
                }
            } else if (newKg == MAX_TRUCK_KG) {
                // Vừa đủ tải — tải lên rồi xe đi ngay
                truckKg[di] = newKg;
                releaseHoldSpace(holdPercent);
                updateDbStatus(item.id, "AT_DEST");
                truckDeparture(di, null, 0, true); // xe đầy, đi ngay, không có đơn trigger
            } else {
                // Còn chỗ — tải bình thường
                truckKg[di] = newKg;
                releaseHoldSpace(holdPercent);
                updateDbStatus(item.id, "AT_DEST");
                refreshDestUi();
                refreshHoldUi();
            }
        }

        /**
         * Điều xe đi ngay.
         * @param triggerItem  đơn không vừa (sẽ vào overflow), null nếu xe vừa đủ tải
         * @param holdPercentToRelease  phần trăm khu vực chờ cần giải phóng cho triggerItem
         * @param naturalFull  true = xe đầy tự nhiên, false = xe đi do đơn mới không vừa
         */
        private void truckDeparture(int di, QueueItem triggerItem,
                                    double holdPercentToRelease, boolean naturalFull) {
            if (triggerItem != null) {
                // Đơn không vừa → chờ xe mới
                truckOverflow[di].addLast(triggerItem);
                updateDbStatus(triggerItem.id, "AT_DEST");
                releaseHoldSpace(holdPercentToRelease);
            }

            double departedKg = truckKg[di];
            truckKg[di]   = 0;
            truckGone[di] = true;
            refreshDestUi();
            refreshHoldUi();

            if (naturalFull) {
                showToast("Xe đến " + DEST_NAMES[di]
                    + " đã rời đi vì đạt đủ tải trọng ("
                    + String.format("%.0f", departedKg) + " kg)."
                    + " Xe tiếp theo sẽ đến sau 15s.");
            }
            // Trường hợp không phải naturalFull: toast đã hiện trước đó trong placeOnTruck

            // Xe mới sau 15s
            Timer t = new Timer(TRUCK_RESET_MS, e -> {
                ((Timer)e.getSource()).stop();
                SwingUtilities.invokeLater(() -> resetTruck(di));
            });
            t.setRepeats(false);
            t.start();
        }

        private void resetTruck(int di) {
            truckGone[di] = false;
            showToast("Xe mới đến " + DEST_NAMES[di] + " đã sẵn sàng nhận hàng.");
            // Nạp overflow vào xe mới
            while (!truckOverflow[di].isEmpty()) {
                QueueItem nxt = truckOverflow[di].peekFirst();
                if (truckKg[di] + nxt.totalWeight > MAX_TRUCK_KG) {
                    // Đơn đầu tiên trong overflow cũng không vừa xe mới
                    truckOverflow[di].pollFirst();
                    showToast("Xe " + DEST_NAMES[di]
                        + " hiện không thể tải được đơn " + nxt.orderCode
                        + " (" + String.format("%.0f", nxt.totalWeight) + " kg)"
                        + " vì vượt quá tổng tải trọng. Bạn phải đợi xe khác.");
                    truckDeparture(di, nxt, 0, false);
                    return;
                }
                truckOverflow[di].pollFirst();
                truckKg[di] += nxt.totalWeight;
                updateDbStatus(nxt.id, "SHIPPED");
            }
            refreshDestUi();
        }

        // ── Chi tiết xe ───────────────────────────────────────────────────────
        private void showDestDetail(int di) {
            String status = truckGone[di] ? "⏳ Đang đợi xe mới"
                : truckKg[di] > MAX_TRUCK_KG * 0.85 ? "⚠️ Gần đầy"
                : "✅ Sẵn sàng";

            JOptionPane.showMessageDialog(this,
                "Điểm đến:      " + DEST_NAMES[di] + "\n" +
                "Tải hiện tại:  " + String.format("%.1f / %.0f kg", truckKg[di], MAX_TRUCK_KG) + "\n" +
                "Đơn đang chờ:  " + truckOverflow[di].size() + "\n" +
                "Trạng thái:    " + status,
                "Chi tiết — " + DEST_NAMES[di],
                JOptionPane.INFORMATION_MESSAGE);
        }

        // ── Toast ─────────────────────────────────────────────────────────────
        private void showToast(String msg) {
            toastLabel.setText(msg);
            toastLabel.setForeground(UiTheme.WARNING);
            toastLabel.setVisible(true);
            if (toastTimer != null) toastTimer.stop();
            toastTimer = new Timer(8000, e -> {
                toastLabel.setVisible(false);
                ((Timer)e.getSource()).stop();
            });
            toastTimer.setRepeats(false);
            toastTimer.start();
        }

        // ── Refresh UI ────────────────────────────────────────────────────────
        private void refreshTeamUi() {
            for (int t = 0; t < TEAM_COUNT; t++) refreshTeamRow(t);
        }

        private void refreshTeamRow(int t) {
            QueueItem cur = packingNow[t];
            int queueSize = teamQueues[t].size();
            teamWaitLb[t].setText("Chờ:  " + queueSize);

            if (cur == null) {
                teamStatusBadge[t].setText("Trống");
                teamStatusBadge[t].setBackground(UiTheme.STATUS_EMPTY);
                teamProcessLb[t].setText("Đang xử lý:  —");
                teamProgress[t].setValue(0);
                teamProgress[t].setForeground(UiTheme.STATUS_EMPTY);
            } else {
                int total   = cur.itemCount;
                int elapsed = total - packSec[t];
                int pct     = total > 0 ? (int)(100.0 * elapsed / total) : 100;

                if (packSec[t] > 0) {
                    teamStatusBadge[t].setText("Đang xử lý");
                    teamStatusBadge[t].setBackground(UiTheme.STATUS_PACKING);
                    teamProgress[t].setForeground(UiTheme.STATUS_PACKING);
                } else {
                    teamStatusBadge[t].setText("Hoàn tất");
                    teamStatusBadge[t].setBackground(UiTheme.STATUS_DONE);
                    teamProgress[t].setForeground(UiTheme.STATUS_DONE);
                }
                teamProcessLb[t].setText("Đang xử lý:  " + cur.orderCode +
                    "  (" + packSec[t] + "s còn lại)");
                teamProgress[t].setValue(pct);
            }
        }

        private void refreshHoldUi() {
            lbHoldCount.setText(String.valueOf(holdItemCount));
            int progressValue = (int) Math.round(
                Math.min(holdPercentUsed, MAX_HOLD_PERCENT) * HOLD_PROGRESS_SCALE);
            holdProgress.setValue(progressValue);
            boolean full = holdPercentUsed >= MAX_HOLD_PERCENT;
            holdProgress.setForeground(full ? UiTheme.STATUS_FULL : new Color(139, 92, 246));
        }

        private void releaseHoldSpace(double percent) {
            holdItemCount = Math.max(0, holdItemCount - 1);
            holdPercentUsed = Math.max(0.0, holdPercentUsed - percent);
        }

        private static String formatPercent(double percent) {
            if (Math.abs(percent - Math.rint(percent)) < 0.0001) {
                return String.format("%.0f%%", percent);
            }
            return String.format("%.1f%%", percent);
        }

        private void refreshDestUi() {
            for (int d = 0; d < 6; d++) {
                double kg = truckKg[d];
                boolean gone = truckGone[d];
                destKgLabel[d].setText(String.format("%.0f / 900", kg));
                if (gone) {
                    destStatusDot[d].setForeground(UiTheme.STATUS_WAITING);
                } else if (kg > MAX_TRUCK_KG * 0.85) {
                    destStatusDot[d].setForeground(UiTheme.STATUS_FULL);
                } else if (kg > 0) {
                    destStatusDot[d].setForeground(UiTheme.STATUS_PACKING);
                } else {
                    destStatusDot[d].setForeground(UiTheme.STATUS_EMPTY);
                }
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────────
        private boolean isTracked(int id) {
            for (int t = 0; t < TEAM_COUNT; t++) {
                if (packingNow[t] != null && packingNow[t].id == id) return true;
                for (QueueItem q : teamQueues[t]) if (q.id == id) return true;
            }
            for (Deque<QueueItem> dq : truckOverflow)
                for (QueueItem q : dq) if (q.id == id) return true;
            return false;
        }

        private int destIdx(String code) {
            for (int i = 0; i < DEST_CODES.length; i++)
                if (DEST_CODES[i].equals(code)) return i;
            return -1;
        }

        private void updateDbStatus(int id, String status) {
            Thread th = new Thread(() -> {
                try {
                    ensureDriver();
                    try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                         PreparedStatement st = c.prepareStatement(UPDATE_SQL)) {
                        st.setString(1, status);
                        st.setInt(2, id);
                        st.executeUpdate();
                    }
                } catch (SQLException ignored) {}
            }, "db-update");
            th.setDaemon(true);
            th.start();
        }

        private void ensureDriver() throws SQLException {
            try { Class.forName(DRIVER); }
            catch (ClassNotFoundException e) { throw new SQLException("Không tìm thấy JDBC driver.", e); }
        }

        // ── Factory helpers ───────────────────────────────────────────────────
        private static JPanel makeCard(String title, Color accentColor) {
            JPanel card = new JPanel(new BorderLayout(0, 10));
            card.setBackground(UiTheme.SURFACE);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

            JLabel titleLb = new JLabel(title);
            titleLb.setFont(UiTheme.font(Font.BOLD, 12));
            titleLb.setForeground(accentColor);
            titleLb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.CARD_BORDER));
            titleLb.setPreferredSize(new Dimension(0, 28));
            card.add(titleLb, BorderLayout.NORTH);
            return card;
        }

        private static JLabel bigStatLabel(String text) {
            JLabel lb = new JLabel(text, SwingConstants.LEFT);
            lb.setFont(UiTheme.font(Font.BOLD, 30));
            return lb;
        }

        private static JLabel pill(String text, Color bg, Color fg) {
            JLabel lb = new JLabel(text, SwingConstants.CENTER);
            lb.setFont(UiTheme.font(Font.BOLD, 11));
            lb.setForeground(fg);
            lb.setOpaque(true);
            lb.setBackground(bg);
            lb.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            return lb;
        }

        private static Component centered(JComponent c) {
            c.setAlignmentX(CENTER_ALIGNMENT);
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
            p.setOpaque(false);
            p.add(c);
            return p;
        }

        // ── Data model ────────────────────────────────────────────────────────
        static final class QueueItem {
            final int    id, teamId, itemCount;
            final String orderCode, destCode, destName, status;
            final double totalWeight;

            QueueItem(int id, String orderCode, String destCode, String destName,
                      int teamId, double totalWeight, int itemCount, String status) {
                this.id = id; this.orderCode = orderCode; this.destCode = destCode;
                this.destName = destName; this.teamId = teamId;
                this.totalWeight = totalWeight; this.itemCount = itemCount;
                this.status = status;
            }
        }
    }
}
