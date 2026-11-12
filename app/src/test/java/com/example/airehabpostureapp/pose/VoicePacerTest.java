package com.example.airehabpostureapp.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** 안내 문구 음성 간격: 최소 2초, 읽는 중이면 기다림, 기다리는 동안 바뀌면 최근 것 하나만 */
public class VoicePacerTest {

    @Test
    public void firstMessage_spokenImmediately() {
        VoicePacer p = new VoicePacer();
        p.offer("A");
        assertEquals("A", p.poll(1000, false));
        assertFalse(p.hasPending());
    }

    @Test
    public void within2Seconds_waits_thenSpeaksLatestOnly() {
        VoicePacer p = new VoicePacer();
        p.offer("A");
        p.poll(0, false);
        p.offer("B");
        p.offer("C");
        p.offer("D");
        assertNull(p.poll(1500, false));
        assertEquals(500, p.waitMs(1500, false));
        // B, C는 버리고 D만
        assertEquals("D", p.poll(2000, false));
        assertNull(p.poll(2100, false));
    }

    @Test
    public void whileSpeaking_waitsUntilDone() {
        VoicePacer p = new VoicePacer();
        p.offer("A");
        p.poll(0, false);
        p.offer("B");
        assertNull(p.poll(3000, true));
        assertEquals("B", p.poll(3200, false));
    }

    @Test
    public void countOrGoal_resetsGap_forNextMessage() {
        // "3회"를 읽은 뒤 2초 안에 온 안내 문구는 기다렸다가 읽음 (횟수 자체는 바로 읽음)
        VoicePacer p = new VoicePacer();
        p.onUtteranceStarted(10000);
        p.offer("좋아요");
        assertNull(p.poll(11000, false));
        assertEquals("좋아요", p.poll(12000, false));
    }

    @Test
    public void clear_dropsPending() {
        VoicePacer p = new VoicePacer();
        p.offer("A");
        p.clear();
        assertNull(p.poll(0, false));
    }
}
