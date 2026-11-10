package com.example.airehabpostureapp.network;

/**
 * 요청에 필요한 로그인 정보 (실제: SessionManager, 단위 테스트: 메모리 구현)
 */
public interface SessionInfo {

    /** "Bearer {access token}". 로그인 상태가 아니면 null */
    String getBearerToken();

    String getUserId();
}
