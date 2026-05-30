
import java.sql.*;

public class ShowLoaiPhieuCodepoints {
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    private static String hexCodepoints(String s) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(cp -> sb.append(String.format("U+%04X ", cp)));
        return sb.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT DISTINCT LoaiPhieu FROM InOut ORDER BY LoaiPhieu";
            try (PreparedStatement st = conn.prepareStatement(sql);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    String v = rs.getString(1);
                    System.out.println(v + " -> " + hexCodepoints(v));
                }
            }
        }
    }
}
