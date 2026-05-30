
import java.sql.*;

public class ShowTriggerDef {
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    public static void main(String[] args) throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT OBJECT_DEFINITION(OBJECT_ID('trg_NhapXuatKho')) AS TriggerDef";
            try (PreparedStatement st = conn.prepareStatement(sql);
                 ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    String def = rs.getString("TriggerDef");
                    System.out.println(def == null ? "(null)" : def);
                }
            }
        }
    }
}
