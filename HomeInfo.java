import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

public class HomeInfo extends JPanel {

    // Đặt file ảnh cùng thư mục gốc project (hoặc thư mục chạy .jar).
    private static final String INFOGRAPHIC_PATH = "Infographic.png";

    public HomeInfo(String managerId, String managerName) {
        setLayout(new BorderLayout());
        setBackground(UiTheme.APP_BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(new InfographicPanel(INFOGRAPHIC_PATH), BorderLayout.CENTER);
    }

    public void refreshData() {
        // Không có dữ liệu động — ảnh tĩnh, không cần làm mới.
    }

    private static final class InfographicPanel extends JPanel {
        private final java.awt.Image image;

        private InfographicPanel(String imagePath) {
            setOpaque(true);
            setBackground(UiTheme.SURFACE);
            setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
            ImageIcon icon = new ImageIcon(imagePath);
            image = (icon.getIconWidth() > 0) ? icon.getImage() : null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int panelW = getWidth();
            int panelH = getHeight();
            int imgW   = image.getWidth(this);
            int imgH   = image.getHeight(this);
            if (imgW <= 0 || imgH <= 0) {
                g2.dispose();
                return;
            }

            // Scale "contain": ảnh vừa khít khung, giữ nguyên tỉ lệ, căn giữa
            double scale  = Math.min((double) panelW / imgW, (double) panelH / imgH);
            int drawW = Math.max(1, (int) Math.round(imgW * scale));
            int drawH = Math.max(1, (int) Math.round(imgH * scale));
            int x = (panelW - drawW) / 2;
            int y = (panelH - drawH) / 2;

            g2.drawImage(image, x, y, drawW, drawH, this);
            g2.dispose();
        }
    }
}