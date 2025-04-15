import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class AuthService {


    public static String signin(String username, String password) throws Exception{
        //returns the auth token
        String token = null;
        try (BufferedReader reader = new BufferedReader(new FileReader("auth.txt"))) {
            String line;

            while ((line = reader.readLine()) != null) {
               String[] credentials = line.split(":");
               if(credentials.length != 2){
                    throw new Exception("Something wrong happened when parsing the auth file!");
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

    private static String signup(String username, String password) throws Exception{
        String token = null;

        try(PrintWriter writer = new PrintWriter(new FileWriter("auth.txt", true), true)){
            writer.println(username + ":" + hashPassword(password));
            token = generateSecureToken();
        } catch (IOException e) {
            throw new Exception(e); 
        }
        return token;

    }

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

    private static String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[24]; 
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
}
