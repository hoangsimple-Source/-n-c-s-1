
import java.sql.*;

public class ShowLoaiPhieuValues {
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    public static void main(String[] args) throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT DISTINCT LoaiPhieu FROM InOut ORDER BY LoaiPhieu";
            try (PreparedStatement st = conn.prepareStatement(sql);
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1));
                }
            }
        }
    }
}
