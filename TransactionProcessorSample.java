package com.playtech.assignment;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


// This template shows input parameters format.
// It is otherwise not mandatory to use, you can write everything from scratch if you wish.
public class TransactionProcessorSample {

    public static void main(final String[] args) throws IOException {
        List<User> users = TransactionProcessorSample.readUsers(Paths.get(args[0]));
        List<Transaction> transactions = TransactionProcessorSample.readTransactions(Paths.get(args[1]));
        List<BinMapping> binMappings = TransactionProcessorSample.readBinMappings(Paths.get(args[2]));

        List<Event> events = TransactionProcessorSample.processTransactions(users, transactions, binMappings);

        TransactionProcessorSample.writeBalances(Paths.get(args[3]), users);
        TransactionProcessorSample.writeEvents(Paths.get(args[4]), events);
    }

    private static List<User> readUsers(final Path filePath) {
        List<User> users = new ArrayList<>();
        BufferedReader reader = null;
        try {
            File file = new File(String.valueOf(filePath));
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                users.add(new User(parts[0], parts[1], Double.parseDouble(parts[2]), parts[3], Integer.parseInt(parts[4]),
                        Double.parseDouble(parts[5]), Double.parseDouble(parts[6]), Double.parseDouble(parts[7]),
                        Double.parseDouble(parts[8])));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                assert reader != null;
                reader.close();
            } catch (IOException e) {
                System.out.println("Closing reader error");
            }
        }
        return users;
    }

    private static List<Transaction> readTransactions(final Path filePath) {
        List<Transaction> transactions = new ArrayList<>();
        BufferedReader reader = null;
        try {
            File file = new File(String.valueOf(filePath));
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                transactions.add(new Transaction(parts[0], parts[1], Double.parseDouble(parts[2]), parts[3], parts[4]));
            }
        } catch (IOException e) {
        throw new RuntimeException(e);
        } finally {
            try {
            assert reader != null;
            reader.close();
            } catch (IOException e) {
            System.out.println("Closing reader error");
            }
        }
        return transactions;
    }

    private static List<BinMapping> readBinMappings(final Path filePath) {
        // ToDo Implementation
        return new ArrayList<>();
    }

    private static List<Event> processTransactions(final List<User> users, final List<Transaction> transactions, final List<BinMapping> binMappings) {
        // ToDo Implementation
        return null;
    }

    private static void writeBalances(final Path filePath, final List<User> users) {
        // ToDo Implementation
    }

    private static void writeEvents(final Path filePath, final List<Event> events) throws IOException {
        try (final FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("transaction_id,status,message\n");
            for (final var event : events) {
                writer.append(event.transactionId).append(",").append(event.status).append(",").append(event.message).append("\n");
            }
        }
    }
}


class User {
    private String user_id;
    private String username;
    private double balance;
    private String country;
    private int frozen;
    private double deposit_min;
    private double deposit_max;
    private double withdraw_min;
    private double withdraw_max;

    public User(String user_id, String username, double balance, String country,
                int frozen, double deposit_min, double deposit_max,double withdraw_min, double withdraw_max){
        this.user_id = user_id;
        this.username = username;
        this.balance = balance;
        this.country = country;
        this.frozen = frozen;
        this.deposit_min = deposit_min;
        this.deposit_max = deposit_max;
        this.withdraw_min = withdraw_min;
        this.withdraw_max = withdraw_max;
    }


}

class Transaction {
    private String transaction_id;
    private String user_id;
    private double amount;
    private String type;
    private String method;
    private String accountNumber;
    public Transaction(String transaction_id, String user_id, double amount, String type,
                       String method, String accountNumber){
        this.transaction_id = transaction_id;
        this.user_id = user_id;
        this.amount = amount;
        this.type = type;
        this.method = method;
        this.accountNumber = accountNumber;
    }

}

class BinMapping {
}

class Event {
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_APPROVED = "APPROVED";

    public String transactionId;
    public String status;
    public String message;
}
