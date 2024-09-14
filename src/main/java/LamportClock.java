public class LamportClock {
    private int t; // local Lamport clock time

    // Constructor to initialize the Lamport clock
    public LamportClock() {
        t = 0; // initial time set to 0
    }

    // Method to send a message, including the Lamport time with the message
    public void increaseTime() {
        t = t + 1;
    }

    // Method to receive a message, updating the Lamport clock based on the received time
    public void increaseTime(int receivedTime) {
        t = Math.max(t, receivedTime) + 1;
    }

    // Getter for the current Lamport time
    public int getTime() {
        return t;
    }
}
