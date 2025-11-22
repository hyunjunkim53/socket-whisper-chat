package whisperchat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * [서버 컴포넌트: 회원 관리]
 * 과제 요구사항: "사용자 정보를 파일로 관리", "비밀번호 해시+솔트 저장"
 * * 역할:
 * 1. users.dat 파일에 사용자 정보를 저장하고 읽어옵니다.
 * 2. 로그인 시 비밀번호가 맞는지 검증합니다.
 * 3. 회원가입 시 비밀번호를 암호화해서 저장합니다.
 * * 멀티스레드 환경(여러 ClientHandler가 동시 접근)이므로 synchronized를 사용하여 동기화합니다.
 */
public class MemberManager {

    private static final String DB_FILE = "users.dat"; // 회원 정보 파일명
    private static final String DELIMITER = "::";      // 데이터를 구분할 구분자 (예: id::pw::salt...)

    /**
     * [기능 1] 회원가입 (REGISTER)
     * - 비밀번호를 그대로 저장하지 않고 'Salt'를 쳐서 'Hash'로 변환해 저장함 (보안 필수)
     */
    public synchronized boolean register(String id, String pw, String name, String email) {
        // 1. 이미 있는 ID인지 확인
        if (isUserExists(id)) {
            return false; 
        }

        try {
            // 2. 암호화 준비 (솔트 생성 -> 해시 생성)
            byte[] salt = generateSalt();
            String saltStr = Base64.getEncoder().encodeToString(salt);
            String hashPw = hashPassword(pw, salt);

            // 3. 파일에 저장 (append 모드)
            // 포맷: id::hashPw::salt::name::email
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(DB_FILE, true))) {
                String line = String.join(DELIMITER, id, hashPw, saltStr, name, email);
                bw.write(line);
                bw.newLine(); // 줄바꿈
            }
            
            System.out.println("[MemberManager] 신규 회원 등록: " + id);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * [기능 2] 로그인 인증 (LOGIN)
     * - 사용자가 입력한 비번을 저장된 Salt와 합쳐서 다시 해시를 만들고,
     * 저장된 해시와 똑같은지 비교함.
     */
    public synchronized boolean login(String id, String inputPw) {
        // 파일에서 해당 ID의 정보를 가져옴
        Map<String, String> userInfo = findUser(id);
        
        if (userInfo == null) {
            return false; // ID 없음
        }

        try {
            // 저장돼있던 해시값과 솔트값
            String storedHash = userInfo.get("hash");
            String storedSaltStr = userInfo.get("salt");
            
            // 솔트 복원 (String -> byte[])
            byte[] salt = Base64.getDecoder().decode(storedSaltStr);

            // 입력받은 비밀번호를 똑같은 방식으로 암호화해봄
            String inputHash = hashPassword(inputPw, salt);

            // 두 해시값이 같으면 비밀번호 일치!
            return storedHash.equals(inputHash);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * [기능 3] ID 중복 확인 (CHECK_ID)
     */
    public synchronized boolean isUserExists(String id) {
        return findUser(id) != null;
    }

    // --- 내부 도우미 메서드들 (Private) ---

    // 파일에서 특정 ID 한 줄 찾아서 맵으로 리턴
    private Map<String, String> findUser(String targetId) {
        File file = new File(DB_FILE);
        if (!file.exists()) return null;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(DELIMITER);
                // parts[0]=id, [1]=hash, [2]=salt, [3]=name, [4]=email
                if (parts.length >= 5 && parts[0].equals(targetId)) {
                    Map<String, String> map = new HashMap<>();
                    map.put("id", parts[0]);
                    map.put("hash", parts[1]);
                    map.put("salt", parts[2]);
                    map.put("name", parts[3]);
                    return map;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // 못 찾음
    }

    // 비밀번호 해시 생성 (SHA-256 알고리즘 사용)
    private String hashPassword(String pw, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt); // 소금 치기
        byte[] hash = md.digest(pw.getBytes(StandardCharsets.UTF_8)); // 해시 굽기
        return Base64.getEncoder().encodeToString(hash); // 보기 좋게 포장
    }

    // 임의의 솔트(Salt) 생성 - 보안성을 위해 매번 랜덤하게 생성
    private byte[] generateSalt() throws NoSuchAlgorithmException {
        SecureRandom sr = new SecureRandom();
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        return salt;
    }
}
