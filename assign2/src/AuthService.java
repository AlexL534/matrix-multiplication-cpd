import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

// Should implement token refresh

public class AuthService {

    private static final Map<String, Long> activeTokens = new HashMap<>(); // stores active tokens and their expiration times (in milliseconds)
    private static final ReentrantLock tokensLock = new ReentrantLock();

    private static final long TOKEN_TIMEOUT = 30 * 60 * 1000; // timeout is 30 minutes

    /*
     * Handles the signin process. 
     * Finds the user using the provided credentials and returns a token
     */
    public static String signin(String username, String password) throws Exception{
        //returns the auth token
        String token = null;
        try (BufferedReader reader = new BufferedReader(new FileReader("auth.txt"))) {
            String line;

            while ((line = reader.readLine()) != null) {
               String[] credentials = line.split(":");
               if(credentials.length != 2){
                    throw new Error("Something wrong happened when parsing the auth file!");
               }
               if(!credentials[0].trim().equals(username)){
                    //User does not match
                    continue;
               }
               if(!hashPassword(password).equals(credentials[1].trim())){
                    // passwords don't match
                    return null;
               }
               token = generateSecureToken();
               tokensLock.lock();
               try {
                activeTokens.put(token, System.currentTimeMillis() + TOKEN_TIMEOUT); // set expiration time for the token
               }
               finally {
                tokensLock.unlock();
               }
               break;
            }

            if(token == null){
                //no user found. Insert a new user with the provided credentials
                token = signup(username, password);
            }

        } catch (IOException e) {
            throw new Exception(e);
        }
        return token;
    } 

    /*
     * Handles the criation of a new account.
     * Inserts the new user account into the file with the user credentials
     */
    private static String signup(String username, String password) throws Exception{
        String token = null;

        try(PrintWriter writer = new PrintWriter(new FileWriter("auth.txt", true), true)){
            writer.println(username + ":" + hashPassword(password));
            token = generateSecureToken();
            activeTokens.put(token, System.currentTimeMillis() + TOKEN_TIMEOUT); // set expiration time for the token
        } catch (IOException e) {
            throw new Exception(e); 
        }
        return token;

    }

    /*
     * Hashes the user passwords to improve the authentication security
     */
    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error: SHA-256 algorithm not found.", e);
        }
    }

    /*
     * Generates tokens to be used to identify users in the server
     */
    private static String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[24]; 
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /*
     * Verifies the validity of a token
     */
    public static boolean isTokenValid(String token) {
        tokensLock.lock();
        try {
            Long expirationTime = activeTokens.get(token);
            if (expirationTime == null) {
                return false; // token was not found
            }
            if (System.currentTimeMillis() > expirationTime) {
                activeTokens.remove(token);
                return false;
            }
            return true;
        }
        finally {
            tokensLock.unlock();
        }

    }

    /*
     * Replaces a token by a fresh one
     */
    public static void refreshToken(String token) {
        tokensLock.lock();
        try {
            activeTokens.get(token);
            activeTokens.remove(token);
            activeTokens.put(token, System.currentTimeMillis() + TOKEN_TIMEOUT);
        }
        finally {
            tokensLock.unlock();
        }
    }

}
