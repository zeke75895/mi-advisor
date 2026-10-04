package com.miadvisor.voice;

public class VoiceNotConfiguredException extends VoiceException {

    public VoiceNotConfiguredException() {
        super("Voice briefings are turned off: ELEVENLABS_API_KEY is not set on the server");
    }
}
