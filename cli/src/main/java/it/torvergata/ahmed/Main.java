package it.torvergata.ahmed;


import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new FileReader(FileDescriptor.in))){
            String line = "";
            System.out.println("Write quit to exit");
            while (!line.equals("quit")){
                System.out.println("Choose a lambda: ");
                line = reader.readLine();
                try {
                    double lambda = Double.parseDouble(line);

                }catch (NumberFormatException e){
                    System.out.println("error in the number passed please try another");
                    continue;
                }

            }
        } catch (IOException e) {
            Logger.getAnonymousLogger().severe("oh no!");
        }
    }
}