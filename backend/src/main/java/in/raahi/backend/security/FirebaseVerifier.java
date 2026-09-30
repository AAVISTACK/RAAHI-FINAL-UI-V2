package in.raahi.backend.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import in.raahi.backend.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;

@Service
public class FirebaseVerifier {

    @Value("${raahi.firebase.credentials-path}")
    private String credentialsPath;

    @PostConstruct
    public void init() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            if (credentialsPath == null || credentialsPath.isBlank()) {
                throw new IllegalStateException(
                        "FIREBASE_CREDENTIALS_PATH is not set. Service account JSON must never be committed to source; " +
                        "mount it as a secret file / env-provided path in each environment.");
            }
            try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
            }
        }
    }

    /** Verifies the Firebase ID token and returns the decoded token, or throws 401. */
    public FirebaseToken verify(String idToken) {
        try {
            return FirebaseAuth.getInstance().verifyIdToken(idToken, true);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Firebase token invalid or expired");
        }
    }
}
