module com.ntu.comp20081.cloudfilesystem {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql; 
    
    requires org.eclipse.paho.client.mqttv3;
    requires jsch;

    opens com.ntu.comp20081.cloudfilesystem to javafx.fxml;
    exports com.ntu.comp20081.cloudfilesystem;
}