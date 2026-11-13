package com.example.airehabpostureapp.network;

import static org.junit.Assert.assertEquals;

import com.example.airehabpostureapp.R;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

// L15: Supabase Auth 오류 → 화면 문구
public class AuthErrorMapperTest {

    private static Response<Object> error(int code, String body) {
        return Response.error(code, ResponseBody.create(body, MediaType.get("application/json")));
    }

    @Test
    public void fromResponse_errorCode() {
        assertEquals(R.string.error_invalid_credentials,
                AuthErrorMapper.fromResponse(error(400, "{\"error_code\":\"invalid_credentials\",\"msg\":\"x\"}")));
        assertEquals(R.string.error_email_already_registered,
                AuthErrorMapper.fromResponse(error(422, "{\"error_code\":\"user_already_exists\"}")));
        assertEquals(R.string.error_email_already_registered,
                AuthErrorMapper.fromResponse(error(422, "{\"error_code\":\"email_exists\"}")));
        assertEquals(R.string.error_password_too_short,
                AuthErrorMapper.fromResponse(error(422, "{\"error_code\":\"weak_password\"}")));
    }

    @Test
    public void fromResponse_oldTokenErrorField() {
        assertEquals(R.string.error_invalid_credentials,
                AuthErrorMapper.fromResponse(error(400, "{\"error\":\"invalid_grant\"}")));
    }

    @Test
    public void fromResponse_unknownOrBrokenBody_isGeneric() {
        assertEquals(R.string.error_auth_unknown,
                AuthErrorMapper.fromResponse(error(500, "{\"error_code\":\"something_new\"}")));
        assertEquals(R.string.error_auth_unknown, AuthErrorMapper.fromResponse(error(502, "<html>bad gateway")));
        assertEquals(R.string.error_auth_unknown, AuthErrorMapper.fromResponse(error(400, "[]")));
        assertEquals(R.string.error_auth_unknown, AuthErrorMapper.fromResponse(error(400, "{\"error_code\":null}")));
    }

    @Test
    public void fromFailure_networkVsOther() {
        assertEquals(R.string.error_network, AuthErrorMapper.fromFailure(new IOException("offline")));
        assertEquals(R.string.error_network, AuthErrorMapper.fromFailure(new SocketTimeoutException()));
        assertEquals(R.string.error_auth_unknown, AuthErrorMapper.fromFailure(new IllegalStateException()));
        assertEquals(R.string.error_record_load_failed,
                AuthErrorMapper.fromFailure(new RuntimeException(), R.string.error_record_load_failed));
    }
}
