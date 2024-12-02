package cp2024.solution;

import cp2024.circuit.CircuitValue;

import java.util.concurrent.atomic.AtomicBoolean;

public class ParallelCircuitValue implements CircuitValue {
    private AtomicBoolean wasSet = new AtomicBoolean(false); // czy wartość została ustawiona
    private AtomicBoolean broken = new AtomicBoolean(false); // czy zwraca InterruptedException
    private Boolean value = null;

    public ParallelCircuitValue() {}

    // Ustawia wynik obliczeń
    protected synchronized void setValue(Boolean value) {
        if (wasSet.get()) return; // Unika nadpisania już ustawionej wartości
        this.value = value;
        wasSet.set(true);
        notifyAll(); // Powiadamia wszystkie czekające wątki
    }

    // Ustawia stan jako błędny
    protected synchronized void setException() {
        if (wasSet.get()) return; // Unika nadpisania już ustawionego stanu
        broken.set(true);
        wasSet.set(true);
        notifyAll(); // Powiadamia wszystkie czekające wątki
    }

    // Pobiera wynik obliczeń lub blokuje się do czasu, aż wynik będzie dostępny
    public synchronized boolean getValue() throws InterruptedException {
        while (!wasSet.get()) {
            wait(); // Blokuje, dopóki wartość nie zostanie ustawiona
        }
        if (!broken.get()) {
            return value;
        }
        throw new InterruptedException("Computation failed or was interrupted.");
    }
}


