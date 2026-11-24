package android.media;
public class AudioTrack {
    public static final int MODE_STREAM = 1, MODE_STATIC = 0;
    public static class Builder {
        public Builder setAudioAttributes(Object a) { return this; }
        public Builder setAudioFormat(Object f) { return this; }
        public Builder setTransferMode(int m) { return this; }
        public Builder setBufferSizeInBytes(int b) { return this; }
        public AudioTrack build() { return new AudioTrack(); }
    }
    public void play() {}
    public void pause() {}
    public int write(short[] b, int o, int n) { return n; }
    public int write(byte[] b, int o, int n) { return n; }
    public int setLoopPoints(int a, int b, int c) { return 0; }
}
