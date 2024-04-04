package com.playtech.assignment;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * A class named TransactionProcessorSample which contains methods for processing transactions,
 * reading input data from files, and writing output data to files.
 */
public class TransactionProcessorSample {

    /**
     * Main method to execute the transaction processing.
     *
     * @param args Command-line arguments:
     *             args[0]: Path to the file containing user data.
     *             args[1]: Path to the file containing transaction data.
     *             args[2]: Path to the file containing BIN mappings.
     *             args[3]: Path to write the balances output file.
     *             args[4]: Path to write the events output file.
     * @throws IOException If an I/O error occurs while reading or writing files.
     */
    public static void main(final String[] args) throws IOException {
        // Read users, transactions, and BIN mappings from files
        List<User> users = TransactionProcessorSample.readUsers(Paths.get(args[0]));
        List<Transaction> transactions = TransactionProcessorSample.readTransactions(Paths.get(args[1]));
        List<BinMapping> binMappings = TransactionProcessorSample.readBinMappings(Paths.get(args[2]));

        // Process transactions and generate events
        List<Event> events = TransactionProcessorSample.processTransactions(users, transactions, binMappings);
        // Process transactions and generate events
        updateBalances(users, transactions, events);
        // Write updated user balances to a file
        TransactionProcessorSample.writeBalances(Paths.get(args[3]), users);
        // Write events to a file
        TransactionProcessorSample.writeEvents(Paths.get(args[4]), events);
    }

    /**
     * Reads users from a CSV file.
     *
     * @param filePath The path to the CSV file containing users.
     * @return         The list of users read from the file.
     */
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
            reader.close(); // Close the reader
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return users;
    }

    /**
     * Reads transactions from a CSV file.
     *
     * @param filePath The path to the CSV file containing transactions.
     * @return         The list of transactions read from the file.
     */
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
            reader.close(); // Close the reader
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
            reader.close(); // Close the reader
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return binMappings;
    }

    /**
     * Processes transactions and generates events based on transaction validation.
     *
     * @param users           The list of users.
     * @param transactions    The list of transactions to process.
     * @param binMappings     The list of BIN mappings.
     * @return                The list of events generated during transaction processing.
     */
    private static List<Event> processTransactions(final List<User> users, final List<Transaction> transactions, final List<BinMapping> binMappings) {
        // Set containing transaction ids that have already been used
        Set<String> usedTransactionIds = new HashSet<>();
        // List of all th events
        List<Event> events = new ArrayList<>();
        // Maps declined transaction ids to the amount that was declined
        TreeMap<String, String> declinedTransactionTracker = new TreeMap<>();
        // Maps accepted transaction ids to the amount that was accepted
        TreeMap<String, String> acceptedTransactionTracker = new TreeMap<>();
        // Maps successful deposit transaction ids to the amount that was deposited
        Map<String, Set<String>> successfulDeposits = new HashMap<>();
        // Process each transaction
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
            // If none of the above if statements is reached, then transaction is approved
            events.add(new Event(transaction.getTransaction_id(), Event.STATUS_APPROVED, "OK"));
            acceptedTransactionTracker.put(transaction.getUser_id(), transaction.getAccount_Number());
        }
        // Verify unique account usage
        verifyUniqueAccount(transactions, events, declinedTransactionTracker);
        return events;
    }

    /**
     * Writes balances to a CSV file.
     *
     * @param filePath The path to the CSV file.
     * @param users   The list of users to write to the file.
     *
     */
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

    /**
     * Writes events to a CSV file.
     *
     * @param filePath The path to the CSV file.
     * @param events   The list of events to write to the file.
     * @throws IOException If an I/O error occurs while writing the file.
     */
    private static void writeEvents(final Path filePath, final List<Event> events) throws IOException {
        try (final FileWriter writer = new FileWriter(filePath.toFile(), false)) {
            writer.append("transaction_id,status,message\n");
            for (final var event : events) {
                writer.append(event.transactionId).append(",").append(event.status).append(",").append(event.message).append("\n");
            }
        }
    }

    /**
     * Verifies the uniqueness of a transaction ID and checks if the associated user is valid and not frozen.
     * Adds corresponding events to the list and updates declinedTransactionTracker if the transaction or user is invalid.
     *
     * @param usedTransactionIds       The set of used transaction IDs to check for uniqueness.
     * @param transaction              The transaction to validate.
     * @param events                   The list of events to update if a condition is violated.
     * @param users                    The list of users to compare transaction details with.
     * @param declinedTransactionTracker The map tracking declined transactions and their associated accounts.
     * @return                         True if the transaction ID is not unique or the user is invalid or frozen, false otherwise.
     */
    private static boolean verifyTransactionIdAndUser(Set<String> usedTransactionIds, Transaction transaction, List<Event> events, List<User> users, TreeMap<String, String> declinedTransactionTracker) {
        if (usedTransactionIds.contains(transaction.getTransaction_id())) {
            // Transaction ID is not unique
            events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Non-unique transaction ID"));
            declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
            return true;
        }
        usedTransactionIds.add(transaction.getTransaction_id());
        //  Verify that the user exists and is not frozen (users are loaded from a file, see "inputs").

        // Verify that the user exists and is not frozen
        List<String> validUser_id = new ArrayList<>();
        for (User user : users) {
            validUser_id.add(user.getUser_id());
            if (user.getFrozen() == 1) {
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "User is frozen"));
                declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                return true;
            }
        }
        // Case where user_id from Transactions does not exist in Users
        if (!validUser_id.contains(transaction.getUser_id())) {
            events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, transaction.getUser_id() + "user_id from Transactions not in Users"));
            declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
            return true;
        }
        return false;
    }

    /**
     * Validates the payment method of a transaction.
     * Adds corresponding events to the list and updates declinedTransactionTracker if the method is invalid.
     *
     * @param transaction              The transaction to validate.
     * @param events                   The list of events to update if a condition is violated.
     * @param users                    The list of users to compare transaction details with.
     * @param binMappings              The list of BIN mappings for card transactions.
     * @param declinedTransactionTracker The map tracking declined transactions and their associated accounts.
     * @return                         True if the transaction is invalid, false otherwise.
     */
    private static boolean validatePaymentMethod(Transaction transaction, List<Event> events, List<User> users, List<BinMapping> binMappings, TreeMap<String, String> declinedTransactionTracker) {
        // Case where the payment method is a transfer
        if (Objects.equals(transaction.getMethod(), "TRANSFER")) {
            // Remove whitespace from IBAN and ensure correct country code
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
            // Validate IBAN by calculating remainder
            BigInteger remainder = ibanValue.remainder(BigInteger.valueOf(97));
            // If remainder does not equal 1, then the iban is invalid
            if (!remainder.equals(BigInteger.ONE)) {
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Invalid IBAN number"));
                declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                return true;
            }
        // Case where the payment method is a card
        } else if (Objects.equals(transaction.getMethod(), "CARD")) {
            // Get the first 10 digits of account number for BIN matching
            String accountNumberPrefix = transaction.getAccount_Number().substring(0, 10);
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
                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Country code does not exist or is wrong"));
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

    /**
     * Method to verify if the deposit or withdrawal transaction is valid for the given user.
     * Adds corresponding events to the list and updates declinedTransactionTracker and successfulDeposits maps accordingly.
     *
     * @param transaction              The transaction to verify.
     * @param events                   The list of events to update if a condition is violated.
     * @param users                    The list of users to compare transaction details with.
     * @param declinedTransactionTracker The map tracking declined transactions and their associated accounts.
     * @param successfulDeposits       The map tracking successful deposits for each user.
     * @return                         True if the transaction is invalid, false otherwise.
     */
    private static boolean verifyDepositWithDraw(Transaction transaction, List<Event> events, List<User> users, TreeMap<String, String> declinedTransactionTracker, Map<String, Set<String>> successfulDeposits) {
        double amount = transaction.getAmount();
        for (User user : users) {
            // Check if the user ID matches with the user ID in the transaction
            if (Objects.equals(transaction.getUser_id(), user.getUser_id())) {
                // Validate the deposit transactions
                if (Objects.equals(transaction.getType(), "DEPOSIT")) {
                    // Check if the deposit amount is valid
                    if (amount <= 0 ){
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, amount + "is invalid amount"));
                        return true;
                    // Check if the amount is within the bounds of deposit
                    } else if ((amount < user.getDeposit_min() || amount > user.getDeposit_max())) {
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, amount + " amount not within the bounds of deposit"));
                        return true;
                    }

                } else if (Objects.equals(transaction.getType(), "WITHDRAW")) {
                    // Check if the deposit amount is valid
                    if (amount <= 0 ) {
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, amount + "is invalid amount"));
                        return true;
                    // Check if the amount is within the bounds of withdrawal
                    } else if (amount > user.getBalance() || (amount < user.getWithdraw_min() || amount > user.getWithdraw_max())) {
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, amount + " amount not within the bounds of withdrawal"));
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        return true;
                    } else {
                        successfulDeposits.computeIfAbsent(transaction.getUser_id(), k -> new HashSet<>()).add(transaction.getAccount_Number());
                    }

                    // Check if the account has been used for a successful deposit before allowing withdrawal
                    if (!successfulDeposits.containsKey(transaction.getUser_id()) || !successfulDeposits.get(transaction.getUser_id()).contains(transaction.getAccount_Number())) {
                        events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Withdrawal not allowed with this account - no previous successful deposits made"));
                        declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                        return true;
                    }
                } else {
                    // Decline a transaction if it is neither deposit or withdrawal
                    events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Transaction is neither deposit nor withdrawal"));
                    declinedTransactionTracker.put(transaction.getTransaction_id(), transaction.getAccount_Number());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Method to verify that each user_id with a CARD method uses the same account, unless the previous transaction has been cancelled.
     * In addition, ensures that each user_id has a different CARD account compared to other user_ids.
     *
     * @param transactions             The list of transactions to verify.
     * @param events                   The list of events to update if a condition is violated.
     * @param declinedTransactionTracker The map tracking declined transactions and their associated accounts.
     */
    private static void verifyUniqueAccount(List<Transaction> transactions, List<Event> events, TreeMap<String, String> declinedTransactionTracker) {
        // Map to store user_ids along with the set of account numbers associated with each user_id
        Map<String, Set<String>> userAccounts = new HashMap<>();

        for (Transaction transaction : transactions) {
            String user = transaction.getUser_id(); // Get user from each transaction
            String acc = transaction.getAccount_Number(); // Get account from each transaction

            // Check if the transaction method is CARD
            if ("CARD".equals(transaction.getMethod())) {
                // If user ID is not in the map, add it with a new empty set
                userAccounts.putIfAbsent(user, new HashSet<>());
                // Add the account number to the set associated with the current user ID
                userAccounts.get(user).add(acc);

            }

        }
        // Variable to store the account number to be kept if multiple accounts are found for a user_id
        String foundAcc = null;
        for (Map.Entry<String, Set<String>> entry : userAccounts.entrySet()) {
            Set<String> accountNumbers = entry.getValue(); // Get the set of account numbers for the current user_id
            // Boolean flag to track if the previous account is valid
            boolean previousAccountValid = true;
            // Counter to track the number of accounts found for the user_id
            int count = 0;
            // Variable to store the account number to be kept if multiple accounts are found
            String account = null;
            // Check if there are multiple accounts associated with the current user_id
            if (accountNumbers.size() > 1) {
                for (String accountNumber : accountNumbers) {
                    // Check if the account number has been previously declined
                    if (declinedTransactionTracker.containsValue(accountNumber)) {
                        count += 1;
                        account = accountNumber;
                        // Mark the previous account as invalid and break the loop
                        previousAccountValid = false;
                        break;
                    } else {
                        count += 1;
                    }
                }
                // Check if multiple accounts were found and the previous account is invalid
                if (count >= 2 && !previousAccountValid) {
                    // Set the account number to be kept
                    foundAcc = account;
                }
            }
        }

        int count = 0;
        for (Transaction transaction : transactions) {
            // Check if the current transaction's account number matches the foundAcc account
            if (Objects.equals(transaction.getAccount_Number(), foundAcc) && count <= 0) {
                // If it is the first occurrence, increment c
                count += 1;
            } else if (Objects.equals(transaction.getAccount_Number(), foundAcc)) {
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, "Cannot withdraw with new account"));
            }
        }
    }

    /**
     * Method to verify if a user's account has been used by another account before.
     *
     * @param transaction               The transaction to verify.
     * @param events                    The list of events to update if the account has been used by another account.
     * @param acceptedTransactionTracker The map tracking accepted transactions and their associated accounts.
     * @return true if the account has been used by another account, false otherwise.
     */
    private static boolean verifyUser(Transaction transaction, List<Event> events, Map<String, String>acceptedTransactionTracker){
        for (Map.Entry<String, String> entry : acceptedTransactionTracker.entrySet()) {
            String trans = entry.getKey(); // Get the transaction ID from the tracker
            String account = entry.getValue(); // Get the account associated with the transaction

            // Check that the current transaction is not the same as the one in the tracker and that the account number matches.
            if(!Objects.equals(trans, transaction.getUser_id()) && Objects.equals(transaction.getAccount_Number(), account)){
                // If conditions are met, decline transaction
                events.add(new Event(transaction.getTransaction_id(), Event.STATUS_DECLINED, transaction.getUser_id() + " used by another account"));
                return true; // indicates that the account has been used by another account.
            }
        }
        return false;
    }

    /**
     * Method to update user balances based on transactions and events.
     * @param users        The list of users whose balances need to be updated.
     * @param transactions The list of transactions to consider for updating balances.
     * @param events       The list of events containing transaction status information.
     */
    private static void updateBalances(List<User> users, List<Transaction> transactions, List<Event> events) {
        for (Transaction transaction : transactions) {
            // Get the user ID associated with the transaction
            String userId = transaction.getUser_id();
            // Get the transaction amount
            double amount = transaction.getAmount();
            // Check if the transaction is accepted based on events
            boolean transactionAccepted = isTransactionAccepted(transaction, events);
            for (User user : users) {
                // Find the user corresponding to the transaction
                if (user.getUser_id().equals(userId)) {
                    // If the transaction is accepted, and it's a deposit, update the user balance by adding amount
                    if (transactionAccepted && transaction.getType().equals("DEPOSIT")) {
                        user.updateBalance(amount);
                    // If the transaction is accepted, and it's a withdrawal, update the user balance by subtracting amount
                    } else if (transactionAccepted && transaction.getType().equals("WITHDRAW")) {
                        user.updateBalance(-amount);
                    }
                    break; // Break after finding the user
                }
            }
        }
    }

    /**
     * Method to check if a transaction is accepted based on events.
     *
     * @param transaction The transaction to check.
     * @param events      The list of events to search for the corresponding transaction.
     * @return true if the transaction is accepted, false otherwise.
     */
    private static boolean isTransactionAccepted(Transaction transaction, List<Event> events) {
        for (Event event : events) {
            // Check if the event corresponds to the transaction ID
            if (event.getTransaction_id().equals(transaction.getTransaction_id())) {
                // Return true if the transaction is accepted
                return event.getStatus().equals(Event.STATUS_APPROVED);
            }
        }
        // Return false if no corresponding event is found
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
