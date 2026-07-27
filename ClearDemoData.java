import java.sql.*;

public class ClearDemoData {
    public static void main(String[] args) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:mysql://localhost:4406/ort_db?useSSL=false&allowPublicKeyRetrieval=true", "root", "root123");
        
        String condition = "patient_id >= 79 AND patient_id <= 129";
        
        System.out.println("Deleting notifications...");
        c.createStatement().executeUpdate("DELETE FROM notifications WHERE user_id IN (SELECT user_id FROM patients WHERE " + condition + ")");
        
        System.out.println("Deleting imaging_records...");
        c.createStatement().executeUpdate("DELETE FROM imaging_records WHERE patient_id IN (SELECT user_id FROM patients WHERE " + condition + ")");
        
        System.out.println("Deleting appointment_status_history...");
        c.createStatement().executeUpdate("DELETE FROM appointment_status_history WHERE appointment_id IN (SELECT appointment_id FROM appointments WHERE " + condition + ")");
        
        System.out.println("Deleting appointments...");
        c.createStatement().executeUpdate("DELETE FROM appointments WHERE " + condition);
        
        System.out.println("Deleting medical_records...");
        c.createStatement().executeUpdate("DELETE FROM medical_records WHERE " + condition);
        
        System.out.println("Deleting patients...");
        int count = c.createStatement().executeUpdate("DELETE FROM patients WHERE " + condition);
        System.out.println("Deleted " + count + " patients.");
        
        System.out.println("Done.");
    }
}
