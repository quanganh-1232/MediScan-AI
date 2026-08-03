import java.sql.*;

public class CheckDb {
    public static void main(String[] args) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:mysql://localhost:4406/ort_db?useSSL=false&allowPublicKeyRetrieval=true", "root", "root123");
        ResultSet rs = c.getMetaData().getTables(null, null, "%", new String[] {"TABLE"});
        while(rs.next()) {
            String t = rs.getString("TABLE_NAME");
            ResultSet cRs = c.createStatement().executeQuery("SELECT COUNT(*) FROM " + t);
            cRs.next();
            System.out.println(t + ": " + cRs.getInt(1));
        }
    }
}
