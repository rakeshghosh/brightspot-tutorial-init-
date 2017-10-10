package custom.password;

import com.psddev.dari.util.PasswordException;
import com.psddev.dari.util.UserPasswordPolicy;

import java.util.Map;

public class CustomUserPasswordPolicy implements UserPasswordPolicy {

    private static final int MINIMUM_PASSWORD_LENGTH = 5;

    @Override
    public void validate(Object user, String password) throws PasswordException {
        System.out.println("Here I am");
        if (password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new PasswordException("Your new password must be " + MINIMUM_PASSWORD_LENGTH + " characters long.");
        }
    }

    @Override
    public void initialize(String s, Map<String, Object> map) {

    }
}
