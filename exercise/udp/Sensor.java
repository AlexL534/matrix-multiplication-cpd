import java.util.ArrayList;
import java.util.List;

public class Sensor {
    private final int id;
    private final List<Float> readings;

    public Sensor (int id) {
        this.id = id;
        this.readings = new ArrayList<>();
    }

    public void addValue(float value) {
        readings.add(value);
    }

    public double getAverage() {
        if (readings.isEmpty()) {
            return 0.0;
        }
        return readings.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
    }
}
