import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbLoginProbe {
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    public static void main(String[] args) throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("Connected to: " + conn.getCatalog());
            String sql =
                "SELECT ManagerID, TenQuanLi, MatKhau, TrangThai " +
                "FROM Manager " +
                "ORDER BY ManagerID";
            try (PreparedStatement st = conn.prepareStatement(sql);
                 ResultSet rs = st.executeQuery()) {
                System.out.println("== Manager login accounts ==");
                while (rs.next()) {
                    System.out.printf(
                        "%s | %s | password=%s | active=%s%n",
                        rs.getString("ManagerID"),
                        rs.getString("TenQuanLi"),
                        rs.getString("MatKhau"),
                        rs.getBoolean("TrangThai")
                    );
                }
            }
        }
    }
}
