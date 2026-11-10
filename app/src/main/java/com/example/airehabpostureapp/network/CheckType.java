package com.example.airehabpostureapp.network;

/**
 * session_checkins.check_type 값 (L12). DB 제약: 'pre' 또는 'post'
 */
public final class CheckType {

    /** 운동 전 컨디션 체크 (11_PreCheck) */
    public static final String PRE = "pre";
    /** 운동 후 컨디션 체크 (15_PostCheck) */
    public static final String POST = "post";

    private CheckType() {
    }
}
