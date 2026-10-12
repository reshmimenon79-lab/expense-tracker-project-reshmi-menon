package org.example;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

        public static void main(String[] args) throws Exception {
            try (var connection = Database.getConnection()) {
                System.out.println("PostgreSQL connected successfully!");
            }
        }
    }
