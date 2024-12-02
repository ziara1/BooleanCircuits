package cp2024.solution;

import cp2024.circuit.CircuitValue;

import java.util.concurrent.atomic.AtomicBoolean;

public class ParallelCircuitValue implements CircuitValue {
    private AtomicBoolean wasSet = new AtomicBoolean(false);
    private AtomicBoolean broken = new AtomicBoolean(false);
    private Boolean value = null;

    public ParallelCircuitValue() {}

    // Ustawia wynik obliczeń
    protected synchronized void setValue(Boolean value) {
        if (wasSet.get()) return; // Unikamy nadpisania już ustawionej wartości
        this.value = value;
        wasSet.set(true);
        notifyAll(); // Powiadamiamy wszystkie czekające wątki
    }

    // Ustawia stan jako błędny
    protected synchronized void setException() {
        if (wasSet.get()) return; // Unikamy nadpisania już ustawionego stanu
        broken.set(true);
        wasSet.set(true);
        notifyAll(); // Powiadamiamy wszystkie czekające wątki
    }

    // Pobiera wynik obliczeń lub blokuje się do czasu, aż wynik będzie dostępny
    public synchronized boolean getValue() throws InterruptedException {
        while (!wasSet.get()) {
            wait(); // Blokujemy się, dopóki wartość nie zostanie ustawiona
        }
        if (broken.get()) {
            throw new InterruptedException("Computation failed or was interrupted.");
        }
        return value;
    }
}