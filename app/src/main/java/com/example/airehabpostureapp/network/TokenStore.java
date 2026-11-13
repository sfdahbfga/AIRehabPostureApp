package com.example.airehabpostureapp.network;

/**
 * 토큰 갱신(TokenAuthenticator)이 쓰는 로그인 세션 저장소.
 * 실제로는 SessionManager(SharedPreferences), 단위 테스트에서는 메모리 구현을 쓴다.
 */
public interface TokenStore {

    /** "Bearer {access token}". 로그인 상태가 아니면 null */
    String getBearerToken();

    String getRefreshToken();

    void saveSession(String accessToken, String refreshToken, String userId);

    /** 서버가 refresh token을 거부한 경우: 세션을 지우고 "로그인 만료" 표시를 남긴다 */
    void expireSession();
}
