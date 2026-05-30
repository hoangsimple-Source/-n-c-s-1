
import java.sql.*;

public class CheckDbTriggers {
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    public static void main(String[] args) throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String q1 = "SELECT t.name AS TableName, tr.name AS TriggerName FROM sys.triggers tr JOIN sys.tables t ON tr.parent_id = t.object_id WHERE t.name IN ('Goods','InOut') ORDER BY t.name, tr.name";
            try (PreparedStatement st = conn.prepareStatement(q1);
                 ResultSet rs = st.executeQuery()) {
                System.out.println("== Triggers on Goods/InOut ==");
                while (rs.next()) {
                    System.out.println(rs.getString("TableName") + " -> " + rs.getString("TriggerName"));
                }
            }

            String q2 = "SELECT c.name, ty.name AS TypeName, c.is_identity FROM sys.columns c JOIN sys.tables t ON c.object_id=t.object_id JOIN sys.types ty ON c.user_type_id=ty.user_type_id WHERE t.name='InOut' AND c.name='LoaiPhieu'";
            try (PreparedStatement st = conn.prepareStatement(q2);
                 ResultSet rs = st.executeQuery()) {
                System.out.println("== InOut.LoaiPhieu column ==");
                while (rs.next()) {
                    System.out.println(rs.getString("name") + " type=" + rs.getString("TypeName"));
                }
            }
        }
    }
}
