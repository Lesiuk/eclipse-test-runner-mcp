package fixtures;

public class SelectedMethods {
    @org.junit.Test @org.junit.jupiter.api.Test
    public void alpha() { System.out.println("EXECUTED_ALPHA"); }
    @org.junit.Test @org.junit.jupiter.api.Test
    public void beta() { System.out.println("EXECUTED_BETA"); }
    @org.junit.Test @org.junit.jupiter.api.Test
    public void gamma() { System.out.println("EXECUTED_GAMMA"); }
}
