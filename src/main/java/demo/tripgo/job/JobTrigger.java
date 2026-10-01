package demo.tripgo.job;

// Ai khởi động lần chạy: lịch @Scheduled hay admin bấm "Chạy ngay".
public enum JobTrigger {
    SCHEDULED("Theo lịch"),
    MANUAL("Chạy tay");

    private final String label;

    JobTrigger(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
