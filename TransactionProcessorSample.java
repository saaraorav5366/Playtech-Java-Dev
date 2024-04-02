package com.playtech.assignment;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


// This template shows input parameters format.
// It is otherwise not mandatory to use, you can write everything from scratch if you wish.
public class TransactionProcessorSample {

    public static void main(final String[] args) throws IOException {
        List<User> users = TransactionProcessorSample.readUsers(Paths.get(args[0]));
        List<Transaction> transactions = TransactionProcessorSample.readTransactions(Paths.get(args[1]));
        List<BinMapping> binMappings = TransactionProcessorSample.readBinMappings(Paths.get(args[2]));

        List<Event> events = TransactionProcessorSample.processTransactions(users, transactions, binMappings);
//
//        TransactionProcessorSample.writeBalances(Paths.get(args[3]), users);
        TransactionProcessorSample.writeEvents(Paths.get(args[4]), events);
    }

    private static List<User> readUsers(final Path filePath) {
        List<User> users = new ArrayList<>();
        BufferedReader reader;
        try {
            File file = new File(String.valueOf(filePath));
            reader = new BufferedReader(new FileReader(file));
            // Skip the first line (header line)
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                users.add(new User(parts[0], parts[1], Double.parseDouble(parts[2]), parts[3], Integer.parseInt(parts[4]),
                            Double.parseDouble(parts[5]), Double.parseDouble(parts[6]), Double.parseDouble(parts[7]),
                            Double.parseDouble(parts[8])));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return users;
    }

    private static List<Transaction> readTransactions(final Path filePath) {
        List<Transaction> transactions = new ArrayList<>();
        BufferedReader reader;
        try {
            File file = new File(String.valueOf(filePath));
            reader = new BufferedReader(new FileReader(file));
            // Skip the first line (header line)
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                transactions.add(new Transaction(parts[0], parts[1], parts[2], Double.parseDouble(parts[3]), parts[4], parts[5]));
            }
        } catch (IOException e) {
        throw new RuntimeException(e);
        }
        return transactions;
    }

    private static List<BinMapping> readBinMappings(final Path filePath) {
        List<BinMapping> binMappings = new ArrayList<>();
        BufferedReader reader;
        try {
            File file = new File(String.valueOf(filePath));
            reader = new BufferedReader(new FileReader(file));
            // Skip the first line (header line)
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                binMappings.add(new BinMapping(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2]), parts[3], parts[4]));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return binMappings;
    }

    private static List<Event> processTransactions(final List<User> users, final List<Transaction> transactions, final List<BinMapping> binMappings) {
        Set<String> usedTransactionIds = new HashSet<>();
        List<Event> events = new ArrayList<>();
        // Validate that the transaction ID is unique (not used before).
        for (Transaction transaction : transactions) {
            if (usedTransactionIds.contains(transaction.getTransaction_id())) {
                // Transaction ID is not unique
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Non-unique transaction ID"));
            }
            usedTransactionIds.add(transaction.getTransaction_id());
            //  Verify that the user exists and is not frozen (users are loaded from a file, see "inputs").
            Set<String> validCountry = new HashSet<>();
            for (User user : users) {
                validCountry.add(user.getCountry());
                if (user.getFrozen() == 1) {
                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "User is frozen"));
                }
            }
            // Validate payment method
            if (Objects.equals(transaction.getMethod(), "TRANSFER")){
                String iban = transaction.getAccount_Number().replaceAll("\\s"," ");


                if(!(validCountry.contains(iban.substring(0, 2)))){
                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Country code does not EE"));
                }
                // Move the first four characters to the end
                iban = iban.substring(4) + iban.substring(0, 4);
                // Replace letters with digits
                StringBuilder numericIBAN = new StringBuilder();
                for (char c : iban.toCharArray()) {
                    if (Character.isLetter(c)) {
                        numericIBAN.append(Character.getNumericValue(c));
                    } else {
                        numericIBAN.append(c);
                    }
                }
                // Convert to BigInteger
                BigInteger ibanValue = new BigInteger(numericIBAN.toString());

                // Calculate remainder
                BigInteger remainder = ibanValue.remainder(BigInteger.valueOf(97));

                if (!remainder.equals(BigInteger.ONE)){
                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Invalid IBAN number"));
                }
            } else if (Objects.equals(transaction.getMethod(), "CARD")) {
                String accountNumberPrefix = transaction.getAccount_Number().substring(0, 10); // Extract first 6 digits
                boolean binMatch = false;
                String cardType = null;
                // Iterate through BIN mappings to find a match
                for (BinMapping binMapping : binMappings) {
                    long rangeFrom = binMapping.getRangeFrom();
                    long rangeTo = binMapping.getRangeTo();
                    if (Long.parseLong(accountNumberPrefix) >= rangeFrom && Long.parseLong(accountNumberPrefix) <= rangeTo) {
                        binMatch = true;
                        cardType = binMapping.getType();
                        break;
                    }
                }

                // If a matching BIN is found, validate card type
                if (binMatch) {
                    if(!Objects.equals(cardType, "DC")){
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Not a debit card transaction"));
                    }
                } else {
//                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_APPROVED, "YEEEEE"));
                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "BIN number not in rangE"));
                }
            }
            else { // Other payment types must be declined
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Invalid payment method"));
            }
        }
        return events;
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

    public int getFrozen(){
        return this.frozen;
    }

    public String getCountry(){
        return this.country;
    }


}

class Transaction {
    private String transaction_id;
    private String user_id;
    private String type;
    private double amount;
    private String method;
    private String account_number;
    public Transaction(String transaction_id, String user_id, String type, double amount,
                       String method, String account_number){
        this.transaction_id = transaction_id;
        this.user_id = user_id;
        this.amount = amount;
        this.type = type;
        this.method = method;
        this.account_number = account_number;
    }

    public String getTransaction_id() {
        return this.transaction_id;
    }

    public String getMethod(){
        return this.method;
    }
    
    public String getAccount_Number(){
        return this.account_number;
    }
}

class BinMapping {
    private String name;
    private long rangeFrom;
    private long rangeTo;
    private String type;
    private String country;
    public BinMapping(String name, long rangeFrom, long rangeTo, String type, String country){
        this.name = name;
        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
        this.type = type;
        this.country = country;
    }

    public long getRangeFrom() {
        return this.rangeFrom;
    }

    public long getRangeTo() {
        return this.rangeTo;
    }

    public String getType() {
        return this.type;
    }
}

class Event {
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_APPROVED = "APPROVED";

    public String transactionId;
    public String status;
    public String message;

    public Event(String transactionId, String status, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }
}
