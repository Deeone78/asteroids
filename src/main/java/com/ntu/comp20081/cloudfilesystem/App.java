package com.ntu.comp20081.cloudfilesystem;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) {
        System.out.println("==========================================");
        System.out.println("SYSTEM STARTING: LB Cloud System v2.1");
        System.out.println("==========================================");

        try {
            DBConn.initializeDatabase(); 
            
            SQLiteDBConn.initDatabase(); 
            
            System.out.println("LOG: Remote and Local databases initialized.");
        } catch (Exception e) {
            System.err.println("LOG ERROR: Database setup failed: " + e.getMessage());
        }

        try {
            scene = new Scene(loadFXML("primary"), 900, 600);
            stage.setScene(scene);
            stage.setTitle("LB Cloud Management System - Enterprise Node");
            System.out.println("LOG: Displaying Secure Authentication Gateway...");
            stage.show();
        } catch (Exception e) {
            System.err.println("LOG NOTE: GUI Launch Error: " + e.getMessage());
        }
    }

    public static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    public static void setRoot(Parent root) {
        scene.setRoot(root);
    }

    public static Scene getScene() {
        return scene;
    }

    public static FXMLLoader getFXMLLoader(String fxml) {
        return new FXMLLoader(App.class.getResource(fxml + ".fxml"));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        System.out.println("LOG: Java Application main method triggered.");
        launch();
    }
}