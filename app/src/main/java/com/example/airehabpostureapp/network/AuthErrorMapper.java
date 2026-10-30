package com.example.airehabpostureapp.network;

import androidx.annotation.StringRes;

import com.example.airehabpostureapp.R;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Supabase Auth 오류를 화면에 보여줄 한국어 문자열로 바꾼다.
 * 오류 문장(msg)은 버전마다 바뀔 수 있어서 error_code로 구분한다.
 */
public final class AuthErrorMapper {

    private AuthErrorMapper() {
    }

    // 서버가 응답했지만 실패한 경우 (4xx, 5xx)
    @StringRes
    public static int fromResponse(Response<?> response) {
        String errorCode = readErrorCode(response.errorBody());
        if (errorCode == null) {
            return R.string.error_auth_unknown;
        }
        switch (errorCode) {
            case "invalid_credentials":
            case "invalid_grant":
                return R.string.error_invalid_credentials;
            case "user_already_exists":
            case "email_exists":
                return R.string.error_email_already_registered;
            case "weak_password":
                return R.string.error_password_too_short;
            default:
                return R.string.error_auth_unknown;
        }
    }

    // 서버에 닿지 못한 경우 (인터넷 끊김, 타임아웃 등)
    @StringRes
    public static int fromFailure(Throwable t) {
        return fromFailure(t, R.string.error_auth_unknown);
    }

    // 네트워크 오류는 공통 문구, 그 외는 화면별 문구(otherMessage)
    @StringRes
    public static int fromFailure(Throwable t, @StringRes int otherMessage) {
        return t instanceof IOException ? R.string.error_network : otherMessage;
    }

    // 최신 응답은 "error_code", 예전 토큰 응답은 "error" 필드를 사용한다.
    private static String readErrorCode(ResponseBody errorBody) {
        if (errorBody == null) {
            return null;
        }
        try {
            JsonElement json = JsonParser.parseString(errorBody.string());
            if (!json.isJsonObject()) {
                return null;
            }
            JsonObject obj = json.getAsJsonObject();
            if (obj.has("error_code") && !obj.get("error_code").isJsonNull()) {
                return obj.get("error_code").getAsString();
            }
            if (obj.has("error") && !obj.get("error").isJsonNull()) {
                return obj.get("error").getAsString();
            }
        } catch (Exception e) {
            // JSON이 아니거나 읽기 실패 → 일반 오류로 처리
        }
        return null;
    }
}
