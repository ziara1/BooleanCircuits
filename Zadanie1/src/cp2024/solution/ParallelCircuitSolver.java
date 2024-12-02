package cp2024.solution;

import cp2024.circuit.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class ParallelCircuitSolver implements CircuitSolver {
    private AtomicBoolean acceptingComputations = new AtomicBoolean(true);
    private ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public synchronized CircuitValue solve(Circuit c) {
        if (!acceptingComputations.get()) {
            return new BrokenCircuitValue();
        }
        ParallelCircuitValue returnValue = new ParallelCircuitValue();
        executor.execute(() -> {
            try {
                returnValue.setValue(solveNode(c.getRoot()));
            } catch (InterruptedException e) {
                returnValue.setException();
                Thread.currentThread().interrupt();
            }
        });
        return returnValue;
    }

    @Override
    public void stop() {
        acceptingComputations.set(false); // Zatrzymanie przyjmowania obliczeń
        executor.shutdownNow(); // Przerywa wszystkie aktywne zadania
    }

    private boolean solveNode(CircuitNode node) throws InterruptedException {
        try {
            if (!acceptingComputations.get()) {
                Thread.currentThread().interrupt();
                throw new InterruptedException("Calculation terminated for this circuit.");
            }
            // rozwiązuje AND i OR używając solveThreshold, ponieważ
            // OR = GT0, a AND = GTn-1, gdzie n to ilość argumentów
            return switch (node.getType()) {
                case LEAF -> ((LeafNode) node).getValue();
                case IF -> solveIF(node);
                case AND -> solveThreshold(node, true, node.getArgs().length - 1);
                case OR -> solveThreshold(node, true, 0);
                case NOT -> !solveNode(node.getArgs()[0]);
                case GT -> solveThreshold(node, true, ((ThresholdNode) node).getThreshold());
                case LT -> solveThreshold(node, false, ((ThresholdNode) node).getThreshold());
                default -> throw new RuntimeException("Illegal type: " + node.getType());
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Calculation terminated for this circuit.");
        }
    }

    private boolean solveIF(CircuitNode node) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        try {
            CircuitNode[] args = node.getArgs();
            AtomicReference<Boolean>[] results = new AtomicReference[3];
            CountDownLatch firstLatch = new CountDownLatch(1); // kontroluje ukończenie pierwszego wyniku
            CountDownLatch secondLatch = new CountDownLatch(2); // kontroluje ukończenie drugiego wyniku
            CountDownLatch[] personalLatches = new CountDownLatch[3]; // kontroluje ukończenie konkretnego wyniku
            for (int i = 0; i < 3; i++) {
                results[i] = new AtomicReference<>();
                personalLatches[i] = new CountDownLatch(1);
                int finalI = i;
                // uruchamia wątek do obliczenia każdego argumentu
                Thread thread = new Thread(() -> {
                    try {
                        results[finalI].set(solveNode(args[finalI]));
                        personalLatches[finalI].countDown();
                        firstLatch.countDown();
                        secondLatch.countDown();
                    } catch (InterruptedException e) {
                        interruptAll(threads);
                        Thread.currentThread().interrupt();
                    }
                });
                threads.add(thread);
                thread.start();
            }
            firstLatch.await(); // oczekuje na pierwszy wynik
            // jeśli pierwszy wynik to A (if A then B else C), to przerywa niepotzebny wątek
            if (results[0].get() != null) {
                if (results[0].get()) {
                    threads.get(2).interrupt();
                    personalLatches[1].await();
                    return results[1].get();
                }
                else {
                    threads.get(1).interrupt();
                    personalLatches[2].await();
                    return results[2].get();
                }
            }
            secondLatch.await(); // oczekuje na drugi wynik
            if (results[0].get() == null) {
                if (results[1].get() == results[2].get()){ // jeśli B == C to zwracamy którykolwiek
                    threads.get(0).interrupt();
                    return results[1].get();
                }
                personalLatches[0].await(); // jeśli B != C to czekamy na ukończenie A
                return results[0].get() ? results[1].get() : results[2].get();
            }
            // jeśli drugi wynik to A (if A then B else C), to przerywa niepotzebny wątek
            if (results[0].get()) {
                threads.get(2).interrupt();
                personalLatches[1].await();
                return results[1].get();
            }
            else {
                threads.get(1).interrupt();
                personalLatches[2].await();
                return results[2].get();
            }
        } catch (InterruptedException e){
            interruptAll(threads);
            Thread.currentThread().interrupt();
            throw new InterruptedException();
        }
    }

    private boolean solveThreshold(CircuitNode node, boolean isGT, int threshold) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        try {
            CircuitNode[] args = node.getArgs();
            int n = args.length;
            int countP = 0; // liczba wyników true
            int countN = 0; // liczba wyników false
            BlockingQueue<Boolean> resultsQueue = new LinkedBlockingQueue<>();
            for (CircuitNode arg : args) {
                Thread thread = new Thread(() -> {
                    try {
                        resultsQueue.add(solveNode(arg));
                    } catch (InterruptedException e) {
                        interruptAll(threads);
                        Thread.currentThread().interrupt();
                    }
                });
                threads.add(thread);
                thread.start();
            }
            for (int i = 0; i < args.length; i++) {
                boolean result = resultsQueue.take();
                if (result)
                    countP++;
                else
                    countN++;
                // jeśli wynik częściowy pozwala już określić wynik końcowy to przerywa resztę
                if (isGT && (countP > threshold || countN >= n - threshold)) {
                    interruptAll(threads);
                    return countP > threshold;
                } else if (!isGT && (countP >= threshold || countN > n - threshold)) {
                    interruptAll(threads);
                    return countP < threshold;
                }
            }
            return isGT == (countP > threshold);
        } catch (InterruptedException e) {
            interruptAll(threads);
            Thread.currentThread().interrupt();
            throw new InterruptedException("Calculation terminated for this circuit.");
        }
    }

    private void interruptAll(List<Thread> threads) {
        // przerywa wszystkie wątki z listy
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }
}