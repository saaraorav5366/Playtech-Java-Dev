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
        updateBalances(users, transactions, events);
        TransactionProcessorSample.writeBalances(Paths.get(args[3]), users);
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
        TreeMap<String, String> declinedTransactionTracker = new TreeMap<>();
        TreeMap<String, String> acceptedTransactionTracker = new TreeMap<>();
        Map<String, Set<String>> successfulDeposits = new HashMap<>();
        for (Transaction transaction : transactions) {
            if (verifyUser(transaction,events, acceptedTransactionTracker)){
                continue;
            }
            if (verifyTransactionIdAndUser(usedTransactionIds, transaction, events, users, declinedTransactionTracker)) {
                continue;
            }
            if (verifyDepositWithDraw(transaction, events, users, declinedTransactionTracker, successfulDeposits)){
                continue;
            }
            if (validatePaymentMethod(transaction, events, users, binMappings, declinedTransactionTracker)) {
                continue;
            }
            events.add(new Event(transaction.getTransaction_id(), Event.STATUS_APPROVED, "OK"));
            acceptedTransactionTracker.put(transaction.getUser_id(), transaction.getAccount_Number());
        }
        verifyUniqueAccount(transactions, events, declinedTransactionTracker);
        return events;
    }

    private static void writeBalances(final Path filePath, final List<User> users) {
        try (final FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("USER_ID,BALANCE\n");
            for (User user : users) {
                String balance = String.format("%.2f",user.getBalance());
                writer.append(user.getUser_id()).append(",").append(balance).append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeEvents(final Path filePath, final List<Event> events) throws IOException {
        try (final FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("transaction_id,status,message\n");
            for (final var event : events) {
                writer.append(event.transactionId).append(",").append(event.status).append(",").append(event.message).append("\n");
            }
        }
    }

    private static boolean verifyTransactionIdAndUser(Set<String> usedTransactionIds, Transaction transaction, List<Event> events, List<User> users, TreeMap<String, String> declinedTransactionTracker) {
        if (usedTransactionIds.contains(transaction.getTransaction_id())) {
            // Transaction ID is not unique
            events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Non-unique transaction ID"));
            declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
            return true;
        }
        usedTransactionIds.add(transaction.getTransaction_id());
        //  Verify that the user exists and is not frozen (users are loaded from a file, see "inputs").

        List<String> validUser_id = new ArrayList<>();
        for (User user : users) {
            validUser_id.add(user.getUser_id());
            if (user.getFrozen() == 1) {
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "User is frozen"));
                declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                return true;
            }
        }
        if (!validUser_id.contains(transaction.getUser_id())) {
            events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "user_id from Transactions not in Users"));
            declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
            return true;
        }
        return false;
    }

    private static boolean validatePaymentMethod(Transaction transaction, List<Event> events, List<User> users, List<BinMapping> binMappings, TreeMap<String, String> declinedTransactionTracker) {
        // Validate payment method
        if (Objects.equals(transaction.getMethod(), "TRANSFER")) {
            String iban = transaction.getAccount_Number().replaceAll("\\s", " ");

            for (User user : users) {
                if (Objects.equals(transaction.getUser_id(), user.getUser_id())) {
                    if (!iban.substring(0, 2).equals(user.getCountry())) {
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Country code does not exist or is wrong"));
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        return true;
                    }
                }
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

            if (!remainder.equals(BigInteger.ONE)) {
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Invalid IBAN number"));
                declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                return true;
            }
        } else if (Objects.equals(transaction.getMethod(), "CARD")) {
            String accountNumberPrefix = transaction.getAccount_Number().substring(0, 10); // Extract first 10 digits
            boolean binMatch = false;
            String cardType = null;
            boolean countryMatch = false;

            // Iterate through BIN mappings to find a match
            for (BinMapping binMapping : binMappings) {
                long rangeFrom = binMapping.getRangeFrom();
                long rangeTo = binMapping.getRangeTo();
                if (Long.parseLong(accountNumberPrefix) >= rangeFrom && Long.parseLong(accountNumberPrefix) <= rangeTo) {
                    binMatch = true;
                    cardType = binMapping.getType();
                    // Check if the country code matches for this BIN mapping
                    for (User user : users) {
                        if (Objects.equals(binMapping.getCountry().substring(0, 2), user.getCountry())) {
                            countryMatch = true;
                            break; // Exit the loop once a matching user country is found
                        }
                    }

                    // Exit the loop once a BIN match is found
                    break;
                }
            }

            // Check if a matching BIN was found
            if (!binMatch) {
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "BIN number not in range"));
                declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                return true;
            } else {
                // Check if the country code matches
                if (!countryMatch) {
                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Country code mismatch"));
                    declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                    return true;
                } else {
                    // Check if the card type is valid
                    if (!Objects.equals(cardType, "DC")) {
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Not a debit card transaction"));
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        return true;
                    }
                }
            }
        } else { // Other payment types must be declined
            events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Invalid payment method"));
            declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
            return true;
        }
        return false;

    }


    private static boolean verifyDepositWithDraw(Transaction transaction, List<Event> events, List<User> users, TreeMap<String, String> declinedTransactionTracker, Map<String, Set<String>> successfulDeposits) {
        double amount = transaction.getAmount();
        for (User user : users) {
            if (Objects.equals(transaction.getUser_id(), user.getUser_id())) {
                if (Objects.equals(transaction.getType(), "DEPOSIT")) {
                    if (amount <= 0 || (amount < user.getDeposit_min() || amount > user.getDeposit_max())) {
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Invalid amount or not within the bounds of deposit"));
                        return true;
                    } else {
                        successfulDeposits.computeIfAbsent(transaction.getUser_id(), k -> new HashSet<>()).add(transaction.getAccount_Number());
                    }

                } else if (Objects.equals(transaction.getType(), "WITHDRAW")) {
                    if (amount <= 0 || amount > user.getBalance() || (amount < user.getWithdraw_min() || amount > user.getWithdraw_max())) {
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Invalid amount or not within the bounds of withdraw"));
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        return true;
                    }
                    if (!successfulDeposits.containsKey(transaction.getUser_id()) || !successfulDeposits.get(transaction.getUser_id()).contains(transaction.getAccount_Number())) {
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Withdrawal not allowed with this account"));
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        return true;
                    }
                } else {
                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Neither deposit nor withdrawal"));
                    declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                    return true;
                }
            }
        }
        return false;
    }

    private static void verifyUniqueAccount(List<Transaction> transactions, List<Event> events, TreeMap<String, String> declinedTransactionTracker) {
        Map<String, Set<String>> userAccounts = new HashMap<>();


        // KINNITA ET IGA user_id CARD oleks sama account, unless previous transaction has been cancelled
        // KINNOTA et iga user_id oleks erinev CARD account vorreldes teiste user_idga
        for (Transaction transaction : transactions) {
            String user = transaction.getUser_id();
            String acc = transaction.getAccount_Number();

            if ("CARD".equals(transaction.getMethod())) {
                // If user ID is not in the map, add it with a new empty set
                userAccounts.putIfAbsent(user, new HashSet<>());
                // Add the account number to the set associated with the current user ID
                userAccounts.get(user).add(acc);

            }

        }
        // Iterate over userAccounts
        String keeper = null;
        for (Map.Entry<String, Set<String>> entry : userAccounts.entrySet()) {
            Set<String> accountNumbers = entry.getValue();

            boolean previousAccountValid = true;
            int count = 0;
            String ac = null;
            if (accountNumbers.size() > 1) {
                for (String accountNumber : accountNumbers) {
                    if (declinedTransactionTracker.containsValue(accountNumber)) {
                        count += 1;
                        ac = accountNumber;
                        previousAccountValid = false;
                        break;
                    } else {
                        count += 1;
                    }
                }
                if (count >= 2 && !previousAccountValid) {
                    keeper = ac;
                }
            }
        }

        int c = 0;
        for (Transaction transaction : transactions) {
            if (Objects.equals(transaction.getAccount_Number(), keeper) && c <= 0) {
                c += 1;
            } else if (Objects.equals(transaction.getAccount_Number(), keeper)) {
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Cannot withdraw with new account"));
            }
        }
    }

    private static boolean verifyUser(Transaction transaction, List<Event> events, Map<String, String>acceptedTransactionTracker){
        for (Map.Entry<String, String> entry : acceptedTransactionTracker.entrySet()) {
            String trans = entry.getKey();
            String account = entry.getValue();
            if(!Objects.equals(trans, transaction.getUser_id()) && Objects.equals(transaction.getAccount_Number(), account)){
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Account used by another account"));
                return true;
            }
        }
        return false;
    }

    private static void updateBalances(List<User> users, List<Transaction> transactions, List<Event> events) {
        for (Transaction transaction : transactions) {
            String userId = transaction.getUser_id();
            double amount = transaction.getAmount();

            boolean transactionAccepted = isTransactionAccepted(transaction, events);

            for (User user : users) {
                if (user.getUser_id().equals(userId)) {
                    if (transactionAccepted && transaction.getType().equals("DEPOSIT")) {
                        user.updateBalance(amount);
                    } else if (transactionAccepted && transaction.getType().equals("WITHDRAW")) {
                        user.updateBalance(-amount);
                    }
                    break;
                }
            }
        }
    }

    private static boolean isTransactionAccepted(Transaction transaction, List<Event> events) {
        for (Event event : events) {
            if (event.getTransaction_id().equals(transaction.getTransaction_id())) {
                return event.getStatus().equals(Event.STATUS_APPROVED);
            }
        }
        return false;
    }

}




/**
 * A class named User representing a user account.
 */
class User {
    /**
     * ID of the user.
     */
    private final String user_id;
    /**
     * Username associated with the user.
     */
    private String username;
    /**
     * Current balance in the user's account.
     */
    private double balance;
    /**
     * two-letter country code, ISO 3166-1 alpha-2 associated with the user.
     */
    private final String country;
    /**
     * Flag where 0 represents an active user, 1 represents frozen.
     */
    private final int frozen;
    /**
     * Minimum deposit amount allowed for the user.
     */
    private final double deposit_min;
    /**
     * Maximum deposit amount allowed for the user.
     */
    private final double deposit_max;
    /**
     * Minimum withdrawal amount allowed for the user.
     */
    private final double withdraw_min;
    /**
     * Maximum withdrawal amount allowed for the user.
     */
    private final double withdraw_max;

    /**
     * Constructor to initialize User objects.
     */
    public User(String user_id, String username, double balance, String country,
                int frozen, double deposit_min, double deposit_max,double withdraw_min, double withdraw_max){
        this.user_id = user_id;
        this.balance = balance;
        this.country = country;
        this.frozen = frozen;
        this.deposit_min = deposit_min;
        this.deposit_max = deposit_max;
        this.withdraw_min = withdraw_min;
        this.withdraw_max = withdraw_max;
    }

    /**
     * Getter methods for retrieving objects of the User class.
     */
    public String getUser_id() {
        return this.user_id;
    }

    public int getFrozen(){
        return this.frozen;
    }

    public double getBalance() {
        return this.balance;
    }

    public String getCountry(){
        return this.country;
    }

    public double getDeposit_max() {
        return this.deposit_max;
    }

    public double getDeposit_min() {
        return this.deposit_min;
    }

    public double getWithdraw_max() {
        return this.withdraw_max;
    }

    public double getWithdraw_min() {
        return withdraw_min;
    }

    /**
     * Method to update every account's balance.
     */
    public void updateBalance(double amount) {
        this.balance += amount;
    }
}

/**
 * A class named Transaction representing a financial transaction.
 */
class Transaction {
    /**
     * ID of the transaction.
     */
    private final String transaction_id;
    /**
     * ID of the user.
     */
    private final String user_id;
    /**
     * Transaction type (allowed values are DEPOSIT or WITHDRAW).
     */
    private final String type;
    /**
     * Amount of the transaction.
     */
    private final double amount;
    /**
     * Payment method used for the transaction.
     */
    private final String method;
    /**
     * Account number associated with the transaction.
     */
    private final String account_number;

    /**
     * Constructor to initialize Transaction objects.
     */
    public Transaction(String transaction_id, String user_id, String type, double amount,
                       String method, String account_number){
        this.transaction_id = transaction_id;
        this.user_id = user_id;
        this.amount = amount;
        this.type = type;
        this.method = method;
        this.account_number = account_number;
    }

    /**
     * Getter methods for retrieving objects of the Transaction class.
     */
    public String getTransaction_id() {
        return this.transaction_id;
    }

    public String getUser_id() {
        return this.user_id;
    }

    public String getType() {
        return this.type;
    }

    public String getMethod(){
        return this.method;
    }
    
    public String getAccount_Number(){
        return this.account_number;
    }

    public double getAmount() {
        return this.amount;
    }
}

/**
 * A class named BinMapping representing a mapping between BIN ranges and card types.
 */
class BinMapping {
    /**
     * Issuing bank name.
     */
    private String name;
    /**
     * The lowest possible card number (first 10 digits of card number) that would be identified within this card range, inclusive.
     */
    private final long rangeFrom;
    /**
     * The highest possible card number (first 10 digits of card number) that would be identified within this card range, inclusive.
     */
    private final long rangeTo;
    /**
     * Type of card associated with the BIN range (either debit or credit).
     */
    private final String type;
    /**
     * Three-letter country code, ISO 3166-1 alpha-3 that is associated with the BIN range.
     */
    private final String country;

    /**
     * Constructor to initialize BinMapping objects.
     */
    public BinMapping(String name, long rangeFrom, long rangeTo, String type, String country){
        this.name = name;
        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
        this.type = type;
        this.country = country;
    }

    /**
     * Getter methods for retrieving objects of the BinMapping class.
     */
    public long getRangeFrom() {
        return this.rangeFrom;
    }

    public long getRangeTo() {
        return this.rangeTo;
    }

    public String getType() {
        return this.type;
    }
    public String getCountry(){
        return this.country;
    }
}

/**
 * A class named Event representing an event related to a transaction.
 */
class Event {

    /**
     * Define constants for the status of the event.
     */
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_APPROVED = "APPROVED";

    /**
     * ID of the transaction associated with the event.
     */
    public String transactionId;
    /**
     *  Status of the event (either DECLINED or APPROVED).
     */
    public String status;
    /**
     * Additional message describing the event.
     */
    public String message;

    /**
     * Constructor to initialize Event objects.
     */
    public Event(String transactionId, String status, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }

    /**
     * Getter methods for retrieving the status and transaction id of the event.
     */
    public String getStatus(){
        return this.status;
    }
    public String getTransaction_id() {
        return this.transactionId;
    }
}
