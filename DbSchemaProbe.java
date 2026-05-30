
import java.sql.*;

public class DbSchemaProbe {
    private static final String DB_URL = System.getenv().getOrDefault(
        "DB_URL",
        "jdbc:sqlserver://localhost:1433;databaseName=DACS;encrypt=true;trustServerCertificate=true"
    );
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "sa");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    public static void main(String[] args) throws Exception {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("== InOut columns ==");
            try (PreparedStatement st = conn.prepareStatement(
                "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='InOut' ORDER BY ORDINAL_POSITION");
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " : " + rs.getString(2));
                }
            }

            System.out.println("== Goods columns ==");
            try (PreparedStatement st = conn.prepareStatement(
                "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='Goods' ORDER BY ORDINAL_POSITION");
                 ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " : " + rs.getString(2));
                }
            }
        }
    }
}
